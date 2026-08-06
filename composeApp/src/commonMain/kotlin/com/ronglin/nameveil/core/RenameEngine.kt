package com.ronglin.nameveil.core

enum class OperationMode { Transform, Restore }

data class RenameMapping(
    val itemId: String,
    val directoryPath: String,
    val fromName: String,
    val toName: String,
)

data class DirectoryPlan(
    val directoryId: String,
    val relativePath: String,
    val mappings: List<RenameMapping>,
    val configId: String? = null,
    val configBytes: ByteArray? = null,
)

data class RenamePlan(
    val mode: OperationMode,
    val location: StorageLocation,
    val directories: List<DirectoryPlan>,
) {
    val mappings: List<RenameMapping> = directories.flatMap { it.mappings }
}

data class OperationProgress(
    val completedDirectories: Int,
    val totalDirectories: Int,
    val currentDirectory: String,
)

data class OperationReport(
    val renamedFiles: Int,
    val completedDirectories: Int,
    val failures: List<String>,
    val warnings: List<String>,
) {
    val isSuccess: Boolean get() = failures.isEmpty()
}

class PlanException(message: String) : IllegalStateException(message)

class RenameEngine(
    private val storage: StorageGateway,
    private val crypto: CryptoSupport,
) {
    suspend fun createPlan(
        location: StorageLocation,
        mode: OperationMode,
        password: String,
        includeSubdirectories: Boolean,
        preserveExtension: Boolean,
    ): RenamePlan {
        if (password.isEmpty()) throw PlanException("请输入配置密码")

        val directories = mutableListOf<DirectoryRef>()
        collectDirectories(
            directoryId = location.rootId,
            relativePath = location.displayName.ifBlank { "." },
            recursive = includeSubdirectories,
            output = directories,
        )

        val problems = mutableListOf<String>()
        val plans = directories.mapNotNull { directory ->
            try {
                when (mode) {
                    OperationMode.Transform -> planTransform(directory, password, preserveExtension)
                    OperationMode.Restore -> planRestore(directory, password)
                }
            } catch (error: Exception) {
                problems += "${directory.relativePath}：${error.message ?: "无法读取目录"}"
                null
            }
        }

        if (problems.isNotEmpty()) throw PlanException(problems.joinToString("\n"))
        if (plans.isEmpty()) {
            val message = if (mode == OperationMode.Transform) {
                "所选范围内没有可改名的文件"
            } else {
                "所选范围内未发现 $CONFIG_FILE_NAME 配置"
            }
            throw PlanException(message)
        }

        return RenamePlan(mode, location, plans)
    }

    suspend fun recoverPassword(
        location: StorageLocation,
        includeSubdirectories: Boolean,
    ): String {
        val directories = mutableListOf<DirectoryRef>()
        collectDirectories(
            directoryId = location.rootId,
            relativePath = location.displayName.ifBlank { "." },
            recursive = includeSubdirectories,
            output = directories,
        )

        val passwords = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        directories.forEach { directory ->
            try {
                storage.listChildren(directory.id)
                    .filter { !it.isDirectory && it.name in configFileNames }
                    .forEach { config ->
                        passwords += NameVeilConfigCodec.recoverPassword(storage.readBytes(config.id))
                    }
            } catch (error: Exception) {
                problems += "${directory.relativePath}：${error.message ?: "无法读取配置"}"
            }
        }

        if (problems.isNotEmpty()) throw PlanException(problems.joinToString("\n"))
        if (passwords.isEmpty()) throw PlanException("所选范围内未发现 $CONFIG_FILE_NAME 配置")
        if (passwords.size > 1) throw PlanException("所选目录包含多组不同密码，请分别选择对应目录找回")
        return passwords.single()
    }

    suspend fun execute(
        plan: RenamePlan,
        onProgress: (OperationProgress) -> Unit = {},
    ): OperationReport {
        var renamedFiles = 0
        var completedDirectories = 0
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        plan.directories.forEachIndexed { index, directory ->
            onProgress(OperationProgress(index, plan.directories.size, directory.relativePath))
            try {
                when (plan.mode) {
                    OperationMode.Transform -> executeTransform(directory)
                    OperationMode.Restore -> executeRestore(directory, warnings)
                }
                renamedFiles += directory.mappings.size
                completedDirectories++
            } catch (error: Exception) {
                failures += "${directory.relativePath}：${error.message ?: "操作失败"}"
            }
        }

        onProgress(OperationProgress(plan.directories.size, plan.directories.size, ""))
        return OperationReport(renamedFiles, completedDirectories, failures, warnings)
    }

    private suspend fun collectDirectories(
        directoryId: String,
        relativePath: String,
        recursive: Boolean,
        output: MutableList<DirectoryRef>,
    ) {
        output += DirectoryRef(directoryId, relativePath)
        if (!recursive) return
        storage.listChildren(directoryId)
            .asSequence()
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .forEach { child ->
                collectDirectories(child.id, "$relativePath/${child.name}", true, output)
            }
    }

    private suspend fun planTransform(
        directory: DirectoryRef,
        password: String,
        preserveExtension: Boolean,
    ): DirectoryPlan? {
        val children = storage.listChildren(directory.id)
        val markers = children.filter { !it.isDirectory && it.name in configFileNames }
        require(markers.isEmpty()) {
            "已存在 NameVeil 配置或未完成操作配置；请先选择“恢复文件名”"
        }

        val files = children.filter { !it.isDirectory }.sortedBy { it.name.lowercase() }
        if (files.isEmpty()) return null

        val unavailableNames = children.mapTo(mutableSetOf()) { it.name }
        unavailableNames += configFileNames
        val mappings = files.map { file ->
            val extension = if (preserveExtension) extensionOf(file.name) else ""
            var target: String
            do {
                target = randomName() + extension
            } while (!unavailableNames.add(target))
            RenameMapping(file.id, directory.relativePath, file.name, target)
        }

        val config = NameVeilConfigPayload(
            createdAtEpochMs = crypto.currentTimeMillis(),
            entries = mappings.map { StoredNameMapping(it.toName, it.fromName) },
        )

        return DirectoryPlan(
            directoryId = directory.id,
            relativePath = directory.relativePath,
            mappings = mappings,
            configBytes = NameVeilConfigCodec.encrypt(config, password, crypto),
        )
    }

    private suspend fun planRestore(directory: DirectoryRef, password: String): DirectoryPlan? {
        val children = storage.listChildren(directory.id)
        val configs = children.filter { !it.isDirectory && it.name in configFileNames }
        if (configs.isEmpty()) return null
        require(configs.size == 1) { "发现多个 NameVeil 配置，无法判断应使用哪一份" }

        val configFile = configs.single()
        val entries = NameVeilConfigCodec.decrypt(storage.readBytes(configFile.id), password, crypto).entries

        require(entries.isNotEmpty()) { "配置中没有文件记录" }
        require(entries.map { it.currentName }.distinct().size == entries.size) { "配置包含重复的当前文件名" }
        require(entries.map { it.originalName }.distinct().size == entries.size) { "配置包含重复的原始文件名" }

        val filesByName = children.filter { !it.isDirectory }.associateBy { it.name }
        val currentNames = entries.mapTo(mutableSetOf()) { it.currentName }
        val mappings = entries.mapNotNull { entry ->
            val source = filesByName[entry.currentName]
            val existingTarget = filesByName[entry.originalName]

            if (source == null) {
                // A pending configuration can survive a process/device shutdown. Files
                // that still have their original name were never transformed, so they
                // are already restored and must not make recovery impossible.
                require(existingTarget != null && entry.originalName !in currentNames) {
                    "找不到配置记录的文件：${entry.currentName}（原名：${entry.originalName}）"
                }
                null
            } else {
                // A target occupied by another configured source is safe: every source
                // enters a unique temporary name before final names are restored.
                require(existingTarget == null || existingTarget.name in currentNames) {
                    "原始文件名已被占用：${entry.originalName}"
                }
                RenameMapping(source.id, directory.relativePath, entry.currentName, entry.originalName)
            }
        }

        return DirectoryPlan(
            directoryId = directory.id,
            relativePath = directory.relativePath,
            mappings = mappings,
            configId = configFile.id,
        )
    }

    private suspend fun executeTransform(plan: DirectoryPlan) {
        val configBytes = requireNotNull(plan.configBytes)
        var pendingId: String? = storage.createFile(
            plan.directoryId,
            PENDING_CONFIG_FILE_NAME,
            "application/octet-stream",
            configBytes,
        )
        val completed = mutableListOf<CompletedRename>()

        try {
            for (mapping in plan.mappings) {
                val newId = storage.rename(mapping.itemId, mapping.toName)
                completed += CompletedRename(newId, mapping.fromName)
            }
            pendingId = storage.rename(requireNotNull(pendingId), CONFIG_FILE_NAME)
        } catch (error: Exception) {
            val rollbackErrors = rollback(completed)
            try {
                pendingId?.let { storage.delete(it) }
            } catch (deleteError: Exception) {
                rollbackErrors += "无法删除 pending 配置：${deleteError.message}"
            }
            val suffix = if (rollbackErrors.isEmpty()) "" else "；回滚异常：${rollbackErrors.joinToString("；")}" 
            throw IllegalStateException("改名未完成，已尝试回滚：${error.message}$suffix", error)
        }
    }

    private suspend fun executeRestore(plan: DirectoryPlan, warnings: MutableList<String>) {
        val states = plan.mappings.map { mapping ->
            RestoreState(
                currentId = mapping.itemId,
                currentName = mapping.fromName,
                originalCurrentName = mapping.fromName,
                targetName = mapping.toName,
            )
        }
        try {
            // 所有源文件先进入唯一临时名，因此即使包含名称交换也能安全恢复。
            states.forEach { state ->
                val temporaryName = ".NAMEVEIL.restore.${randomName()}"
                state.currentId = storage.rename(state.currentId, temporaryName)
                state.currentName = temporaryName
                state.hasMoved = true
            }
            states.forEach { state ->
                state.currentId = storage.rename(state.currentId, state.targetName)
                state.currentName = state.targetName
            }
        } catch (error: Exception) {
            val rollbackErrors = rollbackRestore(states.filter { it.hasMoved })
            val suffix = if (rollbackErrors.isEmpty()) "" else "；回滚异常：${rollbackErrors.joinToString("；")}" 
            throw IllegalStateException("恢复未完成，已尝试回滚：${error.message}$suffix", error)
        }

        try {
            storage.delete(requireNotNull(plan.configId))
        } catch (error: Exception) {
            warnings += "${plan.relativePath}：文件名已恢复，但 NameVeil 配置删除失败（${error.message}）"
        }
    }

    private suspend fun rollback(completed: List<CompletedRename>): MutableList<String> {
        val errors = mutableListOf<String>()
        completed.asReversed().forEach { rename ->
            try {
                storage.rename(rename.currentId, rename.originalName)
            } catch (error: Exception) {
                errors += "${rename.originalName}：${error.message}"
            }
        }
        return errors
    }

    private suspend fun rollbackRestore(states: List<RestoreState>): MutableList<String> {
        val errors = mutableListOf<String>()

        // 再次汇聚到临时名称，先清空所有可能互相占用的最终名/原当前名。
        states.forEach { state ->
            try {
                val temporaryName = ".NAMEVEIL.rollback.${randomName()}"
                state.currentId = storage.rename(state.currentId, temporaryName)
                state.currentName = temporaryName
            } catch (error: Exception) {
                errors += "无法准备回滚 ${state.originalCurrentName}：${error.message}"
            }
        }
        states.forEach { state ->
            try {
                state.currentId = storage.rename(state.currentId, state.originalCurrentName)
                state.currentName = state.originalCurrentName
            } catch (error: Exception) {
                errors += "${state.originalCurrentName}：${error.message}"
            }
        }
        return errors
    }

    private fun randomName(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(20) {
            crypto.secureRandomBytes(20).forEach { byte ->
                append(alphabet[byte.toUByte().toInt() % alphabet.length])
            }
        }
    }

    private fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.lastIndex) name.substring(dot) else ""
    }

    private data class DirectoryRef(val id: String, val relativePath: String)
    private data class CompletedRename(val currentId: String, val originalName: String)
    private data class RestoreState(
        var currentId: String,
        var currentName: String,
        val originalCurrentName: String,
        val targetName: String,
        var hasMoved: Boolean = false,
    )

    private companion object {
        val configFileNames = setOf(CONFIG_FILE_NAME, PENDING_CONFIG_FILE_NAME)
    }
}
