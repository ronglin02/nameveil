package com.ronglin.nameveil

import com.ronglin.nameveil.core.NameVeilConfigCodec
import com.ronglin.nameveil.core.NameVeilConfigPayload
import com.ronglin.nameveil.core.StoredNameMapping
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameVeilConfigTest {
    private val crypto = DesktopStorageGateway()

    @Test
    fun pbkdf2MatchesPublishedHmacSha256Vector() {
        val expected = "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        assertContentEquals(expected, derivePbkdf2Sha256("password", "salt".encodeToByteArray(), 2))
    }

    @Test
    fun encryptedMappingDoesNotLeakNamesAndPasswordCanBeRecovered() {
        val payload = NameVeilConfigPayload(
            createdAtEpochMs = 123,
            entries = listOf(StoredNameMapping("randomName", "private-photo.jpg")),
        )

        val encoded = NameVeilConfigCodec.encrypt(payload, "跨设备密码🔐", crypto)
        val encodedText = encoded.decodeToString()

        assertFalse(encodedText.contains("private-photo.jpg"))
        assertTrue(encodedText.contains("跨设备密码🔐"))
        assertEquals("跨设备密码🔐", NameVeilConfigCodec.recoverPassword(encoded))
        assertEquals(payload, NameVeilConfigCodec.decrypt(encoded, "跨设备密码🔐", crypto))
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val encoded = NameVeilConfigCodec.encrypt(
            NameVeilConfigPayload(123, listOf(StoredNameMapping("random", "original.txt"))),
            "right",
            crypto,
        )

        assertFailsWith<IllegalArgumentException> {
            NameVeilConfigCodec.decrypt(encoded, "wrong", crypto)
        }
    }
}
