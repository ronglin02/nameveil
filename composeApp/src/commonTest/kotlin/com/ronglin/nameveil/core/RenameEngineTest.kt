package com.ronglin.nameveil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RenameEngineTest {
    @Test
    fun transformAndRestoreRoundTrip() = runTest {
        val storage = MemoryStorage().apply {
            addFile("photo one.jpg", "image")
            addFile("notes.txt", "hello")
        }
        val engine = RenameEngine(storage, FakeCrypto())

        val transform = engine.createPlan(
            storage.location,
            OperationMode.Transform,
            password = "secret",
            includeSubdirectories = false,
            preserveExtension = false,
        )

        assertEquals(2, transform.mappings.size)
        assertTrue(transform.mappings.none { '.' in it.toName })
        val encryptedConfig = transform.directories.single().configBytes!!.decodeToString()
        assertTrue(encryptedConfig.contains("secret")) // 君子模式允许找回密码
        assertFalse(encryptedConfig.contains("photo one.jpg"))
        assertTrue(engine.execute(transform).isSuccess)
        assertTrue(CONFIG_FILE_NAME in storage.fileNames())
        assertFalse("photo one.jpg" in storage.fileNames())
        assertEquals("secret", engine.recoverPassword(storage.location, false))

        val restore = engine.createPlan(
            storage.location,
            OperationMode.Restore,
            password = "secret",
            includeSubdirectories = false,
            preserveExtension = true,
        )
        assertTrue(engine.execute(restore).isSuccess)
        assertEquals(setOf("photo one.jpg", "notes.txt"), storage.fileNames())
    }

    @Test
    fun wrongPasswordDoesNotProducePlan() = runTest {
        val storage = MemoryStorage().apply { addFile("document.pdf", "pdf") }
        val crypto = FakeCrypto()
        val engine = RenameEngine(storage, crypto)
        val plan = engine.createPlan(storage.location, OperationMode.Transform, "right", false, true)
        engine.execute(plan)

        val error = assertFailsWith<PlanException> {
            engine.createPlan(storage.location, OperationMode.Restore, "wrong", false, true)
        }
        assertTrue(error.message.orEmpty().contains("密码"))
    }

    @Test
    fun failedTransformRollsBackNamesAndPendingConfig() = runTest {
        val storage = MemoryStorage().apply {
            addFile("a.txt", "a")
            addFile("b.txt", "b")
        }
        val engine = RenameEngine(storage, FakeCrypto())
        val plan = engine.createPlan(storage.location, OperationMode.Transform, "pw", false, false)
        storage.failRenameAt = 2

        val report = engine.execute(plan)

        assertFalse(report.isSuccess)
        assertEquals(setOf("a.txt", "b.txt"), storage.fileNames())
        assertFalse(CONFIG_FILE_NAME in storage.fileNames())
        assertFalse(PENDING_CONFIG_FILE_NAME in storage.fileNames())
    }

    @Test
    fun interruptedTransformCanBeRestoredFromMixedPendingState() = runTest {
        val storage = MemoryStorage().apply {
            addFile("first photo.jpg", "a")
            addFile("second photo.jpg", "b")
        }
        val engine = RenameEngine(storage, FakeCrypto())
        val transform = engine.createPlan(storage.location, OperationMode.Transform, "pw", false, false)
        val directory = transform.directories.single()

        storage.createFile(
            directory.directoryId,
            PENDING_CONFIG_FILE_NAME,
            "application/octet-stream",
            requireNotNull(directory.configBytes),
        )
        val completedBeforeShutdown = directory.mappings.first()
        storage.rename(completedBeforeShutdown.itemId, completedBeforeShutdown.toName)

        val restore = engine.createPlan(
            storage.location,
            OperationMode.Restore,
            password = "pw",
            includeSubdirectories = false,
            preserveExtension = false,
        )

        assertEquals(1, restore.mappings.size)
        assertTrue(engine.execute(restore).isSuccess)
        assertEquals(setOf("first photo.jpg", "second photo.jpg"), storage.fileNames())
    }
}

private class FakeCrypto : CryptoSupport {
    private var counter = 1

    override fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size) { index -> (counter + index).toByte() }.also { counter += size }

    private fun hash(input: ByteArray): ByteArray = ByteArray(32) { index ->
        input.fold(index) { value, byte -> (value * 31) xor byte.toInt() }.toByte()
    }

    override fun encryptAesGcm(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray {
        val key = hash(password.encodeToByteArray() + salt + nonce + associatedData)
        return key.copyOfRange(0, 8) + ByteArray(plaintext.size) { index ->
            (plaintext[index].toInt() xor key[index % key.size].toInt()).toByte()
        }
    }

    override fun decryptAesGcm(
        ciphertext: ByteArray,
        password: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int,
        associatedData: ByteArray,
    ): ByteArray {
        val key = hash(password.encodeToByteArray() + salt + nonce + associatedData)
        check(ciphertext.copyOfRange(0, 8).contentEquals(key.copyOfRange(0, 8)))
        return ByteArray(ciphertext.size - 8) { index ->
            (ciphertext[index + 8].toInt() xor key[index % key.size].toInt()).toByte()
        }
    }

    override fun currentTimeMillis(): Long = 1_700_000_000_000
}

private class MemoryStorage : StorageGateway {
    val location = StorageLocation("/root", "root", "/root")
    var failRenameAt: Int? = null
    private var renameCount = 0
    private val nodes = linkedMapOf(
        "/root" to Node("/root", "root", "", isDirectory = true, ByteArray(0)),
    )

    fun addFile(name: String, content: String) {
        addBytes(name, content.encodeToByteArray())
    }

    fun addBytes(name: String, content: ByteArray) {
        val id = "/root/$name"
        nodes[id] = Node(id, name, "/root", isDirectory = false, content)
    }

    fun fileNames(): Set<String> = nodes.values.filter { !it.isDirectory }.mapTo(mutableSetOf()) { it.name }

    override suspend fun pickDirectory(): StorageLocation = location

    override suspend fun listChildren(directoryId: String): List<StorageItem> = nodes.values
        .filter { it.parentId == directoryId }
        .map { StorageItem(it.id, it.name, it.isDirectory, it.content.size.toLong()) }

    override suspend fun readBytes(fileId: String): ByteArray = requireNotNull(nodes[fileId]).content

    override suspend fun createFile(
        directoryId: String,
        name: String,
        mimeType: String,
        content: ByteArray,
    ): String {
        val id = "$directoryId/$name"
        check(id !in nodes) { "文件已存在" }
        nodes[id] = Node(id, name, directoryId, false, content)
        return id
    }

    override suspend fun rename(itemId: String, newName: String): String {
        renameCount++
        if (failRenameAt == renameCount) error("模拟改名失败")
        val node = requireNotNull(nodes[itemId]) { "文件不存在：$itemId" }
        val newId = "${node.parentId}/$newName"
        check(newId !in nodes) { "目标已存在：$newName" }
        nodes.remove(itemId)
        node.id = newId
        node.name = newName
        nodes[newId] = node
        return newId
    }

    override suspend fun delete(fileId: String) {
        check(nodes.remove(fileId) != null) { "文件不存在" }
    }

    private data class Node(
        var id: String,
        var name: String,
        val parentId: String,
        val isDirectory: Boolean,
        val content: ByteArray,
    )
}
