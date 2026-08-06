package com.ronglin.nameveil.core

data class StorageLocation(
    val rootId: String,
    val displayName: String,
    val displayPath: String,
)

data class StorageItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
)

interface StorageGateway {
    suspend fun pickDirectory(): StorageLocation?
    suspend fun listChildren(directoryId: String): List<StorageItem>
    suspend fun readBytes(fileId: String): ByteArray
    suspend fun createFile(directoryId: String, name: String, mimeType: String, content: ByteArray): String
    suspend fun rename(itemId: String, newName: String): String
    suspend fun delete(fileId: String)
}

interface CryptoSupport {
    fun secureRandomBytes(size: Int): ByteArray
    fun encryptAesGcm(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray
    fun decryptAesGcm(
        ciphertext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray
    fun currentTimeMillis(): Long
}

interface PlatformGateway : StorageGateway, CryptoSupport
