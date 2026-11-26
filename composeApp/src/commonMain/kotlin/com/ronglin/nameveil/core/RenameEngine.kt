package com.ronglin.nameveil.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

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
    internal val buildPendingConfig: (() -> ByteArray)? = null,
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
    val completedFiles: Int,
    val totalFiles: Int,
)

data class OperationReport(
    val renamedFiles: Int,
    val completedDirectories: Int,
    val failures: List<String>,
    val warnings: List<String>,
    val elapsedMs: Long = 0,
    val stagedFiles: Int = 0,
) {
    val isSuccess: Boolean get() = failures.isEmpty()
}

class PlanException(message: String) : IllegalStateException(message)

class RenameEngine(
    private val storage: StorageGateway,
    private val crypto: CryptoSupport,
    private val workers: Int = automaticWorkers(),
) {
    init {
        require(workers in 1..MAX_WORKERS)
    }

    suspend fun createPlan(
        location: StorageLocation,
        mode: OperationMode,
        password: String,
        includeSubdirectories: Boolean,
        preserveExtension: Boolean,
    ): RenamePlan = coroutineScope {
        if (password.isEmpty()) throw PlanException("请输入配置密码")

        val problems = mutableListOf<String>()
        val plans = mutableListOf<DirectoryPlan>()
        val pending = ArrayDeque<Deferred<Result<DirectoryPlan?>>>()
        suspend fun collectNext() {
            pending.removeFirst().await().fold(
                onSuccess = { if (it != null) plans += it },
                onFailure = { problems += it.message.orEmpty() },
            )
        }
        walkDirectories(location, includeSubdirectories) { directory, children ->
            pending.addLast(async(Dispatchers.Default) {
                try {
                    Result.success(when (mode) {
                        OperationMode.Transform -> planTransform(directory, children, password, preserveExtension)
                        OperationMode.Restore -> planRestore(directory, children, password)
                    })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(PlanException("${directory.relativePath}：${error.message ?: "无法读取目录"}"))
                }
            })
            if (pending.size >= workers) collectNext()
        }
        while (pending.isNotEmpty()) collectNext()

        if (problems.isNotEmpty()) throw PlanException(problems.joinToString("\n"))
        if (plans.isEmpty()) {
            val message = if (mode == OperationMode.Transform) {
                "所选范围内没有可改名的文件"
            } else {
                "所选范围内未发现 $CONFIG_FILE_NAME 配置"
            }
            throw PlanException(message)
        }

        RenamePlan(mode, location, plans)
    }

    suspend fun recoverPassword(
        location: StorageLocation,
        includeSubdirectories: Boolean,
    ): String {
        val passwords = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        walkDirectories(location, includeSubdirectories) { directory, children ->
            try {
                children
                    .filter { !it.isDirectory && it.name in configFileNames }
                    .forEach { config ->
                        passwords += NameVeilConfigCodec.recoverPassword(storage.readBytes(config.id))
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        val start = TimeSource.Monotonic.markNow()
        var renamedFiles = 0
        var stagedFiles = 0
        var completedDirectories = 0
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        plan.directories.forEachIndexed { index, directory ->
            currentCoroutineContext().ensureActive()
            fun publish(filesInDirectory: Int) {
                onProgress(OperationProgress(index, plan.directories.size, directory.relativePath,
                    renamedFiles + filesInDirectory, plan.mappings.size))
            }
            publish(0)
            try {
                when (plan.mode) {
                    OperationMode.Transform -> executeTransform(directory, ::publish)
                    OperationMode.Restore -> stagedFiles += executeRestore(directory, warnings, ::publish)
                }
                renamedFiles += directory.mappings.size
                completedDirectories++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += "${directory.relativePath}：${error.message ?: "操作失败"}"
            }
        }

        onProgress(OperationProgress(plan.directories.size, plan.directories.size, "", renamedFiles, plan.mappings.size))
        return OperationReport(renamedFiles, completedDirectories, failures, warnings, start.elapsedNow().inWholeMilliseconds, stagedFiles)
    }

    private suspend fun walkDirectories(
        location: StorageLocation,
        recursive: Boolean,
        visit: suspend (DirectoryRef, List<StorageItem>) -> Unit,
    ) {
        val remaining = ArrayDeque<DirectoryRef>()
        remaining.addLast(DirectoryRef(location.rootId, location.displayName.ifBlank { "." }))
        while (remaining.isNotEmpty()) {
            val directory = remaining.removeLast()
            val children = storage.listChildren(directory.id)
            if (recursive) {
                children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.asReversed().forEach { child ->
                    remaining.addLast(DirectoryRef(child.id, "${directory.relativePath}/${child.name}"))
                }
            }
            visit(directory, children)
        }
    }

    private fun planTransform(
        directory: DirectoryRef,
        children: List<StorageItem>,
        password: String,
        preserveExtension: Boolean,
    ): DirectoryPlan? {
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

        return DirectoryPlan(
            directoryId = directory.id,
            relativePath = directory.relativePath,
            mappings = mappings,
            buildPendingConfig = {
                val config = NameVeilConfigPayload(
                    createdAtEpochMs = crypto.currentTimeMillis(),
                    entries = mappings.map { StoredNameMapping(it.toName, it.fromName) },
                )
                NameVeilConfigCodec.encrypt(config, password, crypto)
            },
        )
    }

    private suspend fun planRestore(directory: DirectoryRef, children: List<StorageItem>, password: String): DirectoryPlan? {
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
                // Overlapping source and target names are staged before the final rename.
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

    private suspend fun executeTransform(plan: DirectoryPlan, onFiles: (Int) -> Unit) {
        val configBytes = requireNotNull(plan.buildPendingConfig).invoke()
        currentCoroutineContext().ensureActive()
        val pendingId = withContext(NonCancellable) {
            storage.createFile(plan.directoryId, PENDING_CONFIG_FILE_NAME, "application/octet-stream", configBytes)
        }
        val states = plan.mappings.map(::RenameState)

        try {
            renameInBatches(states, onFiles) { it.mapping.toName }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { storage.rename(pendingId, CONFIG_FILE_NAME) }
        } catch (error: Exception) {
            val rollbackErrors = withContext(NonCancellable) {
                val errors = rollback(states, staged = false)
                if (errors.isEmpty()) {
                    try {
                        storage.delete(pendingId)
                    } catch (deleteError: Exception) {
                        errors += "无法删除 pending 配置：${deleteError.message}"
                    }
                }
                errors
            }
            if (error is CancellationException) throw error
            val suffix = if (rollbackErrors.isEmpty()) "" else "；回滚异常：${rollbackErrors.joinToString("；")}" 
            throw IllegalStateException("改名未完成，已尝试回滚：${error.message}$suffix", error)
        }
    }

    private suspend fun executeRestore(plan: DirectoryPlan, warnings: MutableList<String>, onFiles: (Int) -> Unit): Int {
        val states = plan.mappings.map(::RenameState)
        val targets = plan.mappings.mapTo(mutableSetOf()) { it.toName.lowercase() }
        val blocking = states.filter { it.mapping.fromName.lowercase() in targets }
        try {
            renameInBatches(blocking, {}) { ".NAMEVEIL.restore.${randomName()}" }
            renameInBatches(states, onFiles) { it.mapping.toName }
        } catch (error: Exception) {
            val rollbackErrors = withContext(NonCancellable) { rollback(states, staged = blocking.isNotEmpty()) }
            if (error is CancellationException) throw error
            val suffix = if (rollbackErrors.isEmpty()) "" else "；回滚异常：${rollbackErrors.joinToString("；")}" 
            throw IllegalStateException("恢复未完成，已尝试回滚：${error.message}$suffix", error)
        }

        try {
            withContext(NonCancellable) { storage.delete(requireNotNull(plan.configId)) }
        } catch (error: Exception) {
            warnings += "${plan.relativePath}：文件名已恢复，但 NameVeil 配置删除失败（${error.message}）"
        }
        return blocking.size
    }

    // Await every started storage operation (including its returned SAF ID) before rollback.
    private suspend fun renameInBatches(
        states: List<RenameState>,
        onFiles: (Int) -> Unit,
        target: (RenameState) -> String,
    ) {
        for (start in states.indices step workers) {
            currentCoroutineContext().ensureActive()
            val end = minOf(start + workers, states.size)
            val failures = withContext(NonCancellable) {
                (start until end).map { index ->
                    async {
                        val state = states[index]
                        try {
                            state.currentId = storage.rename(state.currentId, target(state))
                            state.moved = true
                            null
                        } catch (error: Exception) {
                            error
                        }
                    }
                }.awaitAll()
            }
            failures.firstOrNull { it != null }?.let { throw it }
            onFiles(end)
        }
    }

    private suspend fun rollback(states: List<RenameState>, staged: Boolean): MutableList<String> {
        val errors = mutableListOf<String>()
        val moved = states.asReversed().filter { it.moved }
        if (staged) {
            for (state in moved) {
                try {
                    state.currentId = storage.rename(state.currentId, ".NAMEVEIL.rollback.${randomName()}")
                } catch (error: Exception) {
                    errors += "无法准备回滚 ${state.mapping.fromName}：${error.message}"
                }
            }
        }
        for (state in moved) {
            try {
                storage.rename(state.currentId, state.mapping.fromName)
            } catch (error: Exception) {
                errors += "${state.mapping.fromName}：${error.message}"
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
    private class RenameState(val mapping: RenameMapping) {
        var currentId = mapping.itemId
        var moved = false
    }

    private companion object {
        val configFileNames = setOf(CONFIG_FILE_NAME, PENDING_CONFIG_FILE_NAME)
    }
}
