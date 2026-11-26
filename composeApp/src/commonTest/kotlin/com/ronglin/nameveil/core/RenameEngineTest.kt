package com.ronglin.nameveil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RenameEngineTest {
    @Test
    fun recursiveRestorePreviewListsEachDirectoryOnceAndKeepsTreeOrder() = runTest {
        val storage = MemoryStorage()
        val crypto = FakeCrypto()
        val config = NameVeilConfigCodec.encrypt(
            NameVeilConfigPayload(0, listOf(StoredNameMapping("hidden", "photo.jpg"))), "pw", crypto,
        )
        val tree = mapOf("root" to listOf("b", "a"), "a" to listOf("deep"))
        val visits = mutableListOf<String>()
        val gateway = object : StorageGateway by storage {
            override suspend fun listChildren(directoryId: String): List<StorageItem> {
                visits += directoryId
                return tree[directoryId].orEmpty().map { StorageItem(it, it, true) } + listOf(
                    StorageItem("$directoryId/hidden", "hidden", false),
                    StorageItem("$directoryId/$CONFIG_FILE_NAME", CONFIG_FILE_NAME, false),
                )
            }

            override suspend fun readBytes(fileId: String) = config
        }
        val engine = RenameEngine(gateway, crypto)
        val root = StorageLocation("root", "root", "root")
        for (recursive in listOf(true, false)) {
            visits.clear()
            val plan = engine.createPlan(root, OperationMode.Restore, "pw", recursive, false)
            val expected = if (recursive) listOf("root", "a", "deep", "b") else listOf("root")
            assertEquals(expected, visits)
            assertEquals(expected, plan.directories.map { it.directoryId })
            assertTrue(plan.mappings.all { it.toName == "photo.jpg" })
        }
    }

    @Test
    fun singleDirectoryUsesBoundedParallelIoAndRestoresEachFileInOneMove() = runTest {
        val storage = MemoryStorage()
        repeat(13) { storage.addFile("photo-$it.jpg", "data-$it") }
        var active = 0
        var peak = 0
        var moves = 0
        val gateway = object : StorageGateway by storage {
            override suspend fun rename(itemId: String, newName: String): String {
                active++
                peak = maxOf(peak, active)
                moves++
                delay(10)
                active--
                return storage.rename(itemId, newName)
            }
        }
        val crypto = FakeCrypto()
        suspend fun checkRun(mode: OperationMode, workers: Int) {
            val engine = RenameEngine(gateway, crypto, workers)
            val plan = engine.createPlan(storage.location, mode, "pw", false, false)
            val counts = mutableListOf<Int>()
            peak = 0
            moves = 0
            assertTrue(engine.execute(plan) { counts += it.completedFiles }.isSuccess)
            assertEquals(workers, peak)
            assertEquals(if (mode == OperationMode.Restore) 13 else 14, moves)
            assertTrue(counts.any { it in 1..12 })
            assertEquals(counts.sorted(), counts)
            assertEquals(13, counts.last())
        }
        for (workers in listOf(1, 3, 8)) {
            checkRun(OperationMode.Transform, workers)
            checkRun(OperationMode.Restore, workers)
        }
        repeat(13) { assertEquals("data-$it", storage.readBytes("/root/photo-$it.jpg").decodeToString()) }
    }

    @Test
    fun cancellationWaitsForInFlightRenamesAndRollsBack() = runTest {
        val storage = MemoryStorage()
        val original = (1..8).map { "file-$it" }.toSet()
        original.forEach { storage.addFile(it, it) }
        val started = CompletableDeferred<Unit>()
        val gateway = object : StorageGateway by storage {
            override suspend fun rename(itemId: String, newName: String): String {
                started.complete(Unit)
                delay(10)
                return storage.rename(itemId, newName)
            }
        }
        val engine = RenameEngine(gateway, FakeCrypto())
        val plan = engine.createPlan(storage.location, OperationMode.Transform, "pw", false, false)
        val job = launch { engine.execute(plan) }
        started.await()
        job.cancelAndJoin()
        assertEquals(original, storage.fileNames())
    }

    @Test
    fun failedParallelRestorePreservesSwapAndCanBeRetried() = runTest {
        val crypto = FakeCrypto()
        val storage = MemoryStorage().apply {
            addFile("a", "first")
            addFile("b", "second")
            addFile("c", "third")
            val payload = NameVeilConfigPayload(0, listOf(
                StoredNameMapping("a", "b"), StoredNameMapping("b", "a"), StoredNameMapping("c", "photo.jpg"),
            ))
            addBytes(CONFIG_FILE_NAME, NameVeilConfigCodec.encrypt(payload, "pw", crypto))
        }
        val engine = RenameEngine(storage, crypto)
        val plan = engine.createPlan(storage.location, OperationMode.Restore, "pw", false, false)
        storage.failRenameAt = 3 // The other final rename in this batch succeeds.
        assertFalse(engine.execute(plan).isSuccess)
        assertEquals(setOf("a", "b", "c", CONFIG_FILE_NAME), storage.fileNames())
        assertEquals("first", storage.readBytes("/root/a").decodeToString())
        assertEquals("second", storage.readBytes("/root/b").decodeToString())
        val report = engine.execute(engine.createPlan(storage.location, OperationMode.Restore, "pw", false, false))
        assertTrue(report.isSuccess)
        assertEquals(2, report.stagedFiles)
        assertEquals("third", storage.readBytes("/root/photo.jpg").decodeToString())
        assertEquals("first", storage.readBytes("/root/b").decodeToString())
        assertEquals("second", storage.readBytes("/root/a").decodeToString())
    }

    @Test
    fun transformAndRestoreRoundTrip() = runTest {
        val storage = MemoryStorage().apply {
            addFile("photo one.jpg", "image")
            addFile("notes.txt", "hello")
        }
        val crypto = FakeCrypto()
        val engine = RenameEngine(storage, crypto)

        val transform = engine.createPlan(
            storage.location,
            OperationMode.Transform,
            password = "secret",
            includeSubdirectories = false,
            preserveExtension = false,
        )

        assertEquals(2, transform.mappings.size)
        assertTrue(transform.mappings.none { '.' in it.toName })
        assertEquals(0, crypto.encryptions)
        assertEquals(setOf("photo one.jpg", "notes.txt"), storage.fileNames())
        assertTrue(engine.execute(transform).isSuccess)
        assertEquals(1, crypto.encryptions)
        val encryptedConfig = storage.readBytes("/root/$CONFIG_FILE_NAME").decodeToString()
        assertTrue(encryptedConfig.contains("secret")) // 君子模式允许找回密码
        assertFalse(encryptedConfig.contains("photo one.jpg"))
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
            requireNotNull(directory.buildPendingConfig).invoke(),
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
    var encryptions = 0
        private set
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
        encryptions++
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
