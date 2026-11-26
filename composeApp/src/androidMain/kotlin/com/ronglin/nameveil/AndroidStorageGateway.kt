package com.ronglin.nameveil

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.ronglin.nameveil.core.PlatformGateway
import com.ronglin.nameveil.core.StorageItem
import com.ronglin.nameveil.core.StorageLocation
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidStorageGateway(
    context: Context,
) : PlatformGateway {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val secureRandom = SecureRandom()
    private var launchPicker: (() -> Unit)? = null
    private var pendingPicker: Continuation<StorageLocation?>? = null

    fun bindPicker(launcher: () -> Unit) {
        launchPicker = launcher
    }

    fun unbindPicker(launcher: () -> Unit) {
        if (launchPicker === launcher) launchPicker = null
    }

    fun completePicker(treeUri: Uri?) {
        val continuation = pendingPicker ?: return
        pendingPicker = null
        if (treeUri == null) {
            continuation.resume(null)
            return
        }

        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val name = queryName(documentUri).ifBlank { "已选目录" }
        continuation.resume(
            StorageLocation(
                rootId = documentUri.toString(),
                displayName = name,
                displayPath = "Android / $name",
            ),
        )
    }

    override suspend fun pickDirectory(): StorageLocation? = suspendCancellableCoroutine { continuation ->
        check(pendingPicker == null) { "已有目录选择窗口正在打开" }
        val launcher = checkNotNull(launchPicker) { "目录选择器尚未初始化" }
        pendingPicker = continuation
        continuation.invokeOnCancellation { pendingPicker = null }
        launcher()
    }

    override suspend fun listChildren(directoryId: String): List<StorageItem> = withContext(Dispatchers.IO) {
        val directoryUri = Uri.parse(directoryId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        val result = mutableListOf<StorageItem>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(
                    directoryUri,
                    cursor.getString(idIndex),
                )
                result += StorageItem(
                    id = childUri.toString(),
                    name = cursor.getString(nameIndex).orEmpty(),
                    isDirectory = cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        } ?: error("Android 无法读取该目录，请重新授权")
        result
    }

    override suspend fun readBytes(fileId: String): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(Uri.parse(fileId))?.use { it.readBytes() }
            ?: error("无法打开 NameVeil 配置")
    }

    override suspend fun createFile(
        directoryId: String,
        name: String,
        mimeType: String,
        content: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.createDocument(resolver, Uri.parse(directoryId), mimeType, name)
            ?: error("目录提供方拒绝创建 NameVeil 配置")
        try {
            resolver.openOutputStream(uri, "wt")?.use { it.write(content) }
                ?: error("无法写入 NameVeil 配置")
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
        uri.toString()
    }

    override suspend fun rename(itemId: String, newName: String): String = withContext(Dispatchers.IO) {
        require('/' !in newName) { "文件名不能包含路径分隔符" }
        DocumentsContract.renameDocument(resolver, Uri.parse(itemId), newName)?.toString()
            ?: error("目录提供方拒绝改名：$newName")
    }

    override suspend fun delete(fileId: String) = withContext(Dispatchers.IO) {
        check(DocumentsContract.deleteDocument(resolver, Uri.parse(fileId))) { "目录提供方拒绝删除 NameVeil 配置" }
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

    private fun queryName(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }

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
