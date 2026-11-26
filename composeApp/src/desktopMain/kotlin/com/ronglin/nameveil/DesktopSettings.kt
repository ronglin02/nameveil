package com.ronglin.nameveil

import com.ronglin.nameveil.core.MAX_WORKERS
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopSettings(private val file: Path = defaultFile()) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): RunSettings = runCatching {
        val saved = json.decodeFromString<RunSettings>(Files.readString(file))
        saved.copy(manualWorkers = saved.manualWorkers.coerceIn(1, MAX_WORKERS))
    }.getOrDefault(RunSettings())

    fun save(settings: RunSettings) {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, "settings-", ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(settings))
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        private fun defaultFile(): Path {
            val base = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                System.getenv("APPDATA")
            } else {
                System.getenv("XDG_CONFIG_HOME")
            }
            return (base?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".config")).resolve("NameVeil/settings.json")
        }
    }
}
