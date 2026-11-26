package com.ronglin.nameveil

import com.ronglin.nameveil.core.CONFIG_FILE_NAME
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class NameVeilCliTest {
    @Test
    fun protectAndRestoreWorkWithoutLaunchingGui() = runTest {
        val directory = createTempDirectory("nameveil-cli-")
        try {
            Files.writeString(directory.resolve("private photo.jpg"), "content")
            val gateway = DesktopStorageGateway()

            val protectCode = NameVeilCli.run(
                arrayOf("protect", directory.toString(), "--password", "test-password", "--yes", "--threads", "off", "--no-progress"),
                gateway,
            )
            assertEquals(0, protectCode)
            assertTrue(Files.exists(directory.resolve(CONFIG_FILE_NAME)))
            assertFalse(Files.exists(directory.resolve("private photo.jpg")))
            assertTrue(fileNames(directory).filter { it != CONFIG_FILE_NAME }.none { '.' in it })

            val restoreCode = NameVeilCli.run(
                arrayOf("restore", directory.toString(), "--password", "test-password", "--yes", "--threads", "6"),
                gateway,
            )
            assertEquals(0, restoreCode)
            assertEquals(setOf("private photo.jpg"), fileNames(directory))
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun fileNames(directory: Path): Set<String> = Files.list(directory).use { paths ->
        paths.map { it.fileName.toString() }.toList().toSet()
    }
}
