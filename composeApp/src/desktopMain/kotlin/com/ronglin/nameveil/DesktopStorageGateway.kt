package com.ronglin.nameveil

import com.ronglin.nameveil.core.PlatformGateway
import com.ronglin.nameveil.core.StorageItem
import com.ronglin.nameveil.core.StorageLocation
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class DesktopStorageGateway : PlatformGateway {
    private val secureRandom = SecureRandom()

    override suspend fun pickDirectory(): StorageLocation? = suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                dialogTitle = "选择要处理的目录"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            val selected = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.toPath().toAbsolutePath().normalize()
            } else {
                null
            }
            if (continuation.isActive) {
                continuation.resume(selected?.let { path ->
                    StorageLocation(
                        rootId = path.toString(),
                        displayName = path.fileName?.toString().orEmpty().ifBlank { path.toString() },
                        displayPath = path.toString(),
                    )
                })
            }
        }
    }

    fun locationFor(directory: Path): StorageLocation {
        val normalized = directory.toAbsolutePath().normalize()
        require(Files.isDirectory(normalized)) { "目录不存在或无法读取：$normalized" }
        return StorageLocation(
            rootId = normalized.toString(),
            displayName = normalized.fileName?.toString().orEmpty().ifBlank { normalized.toString() },
            displayPath = normalized.toString(),
        )
    }

    override suspend fun listChildren(directoryId: String): List<StorageItem> = withContext(Dispatchers.IO) {
        val directory = path(directoryId)
        require(Files.isDirectory(directory)) { "目录不存在或无法读取：$directoryId" }
        Files.newDirectoryStream(directory).use { stream ->
            stream.map { child ->
                // 不跟随目录符号链接，避免递归扫描进入循环或越过用户选择的根目录。
                val isDirectory = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                StorageItem(
                    id = child.toAbsolutePath().normalize().toString(),
                    name = child.fileName.toString(),
                    isDirectory = isDirectory,
                )
            }.toList()
        }
    }

    override suspend fun readBytes(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        Files.readAllBytes(path(fileId))
    }

    override suspend fun createFile(
        directoryId: String,
        name: String,
        mimeType: String,
        content: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val target = path(directoryId).resolve(name)
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        target.toAbsolutePath().normalize().toString()
    }

    override suspend fun rename(itemId: String, newName: String): String = withContext(Dispatchers.IO) {
        require('/' !in newName && '\\' !in newName) { "文件名不能包含路径分隔符" }
        val source = path(itemId)
        val target = requireNotNull(source.parent).resolve(newName)
        Files.move(source, target)
        target.toAbsolutePath().normalize().toString()
    }

    override suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        Files.delete(path(fileId))
    }

    override fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    override fun encryptAesGcm(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray = aesGcm(Cipher.ENCRYPT_MODE, plaintext, password, salt, nonce, iterations, associatedData)

    override fun decryptAesGcm(
        ciphertext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray = aesGcm(Cipher.DECRYPT_MODE, ciphertext, password, salt, nonce, iterations, associatedData)

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    private fun path(id: String): Path = Paths.get(id)

    private fun aesGcm(
        mode: Int,
        input: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray {
        val keyBytes = derivePbkdf2Sha256(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(associatedData)
            cipher.doFinal(input)
        } finally {
            keyBytes.fill(0)
        }
    }
}
