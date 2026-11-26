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
    fun readsManifestWrittenByPreviousBuild() {
        val bytes = """{
            "format":"nameveil-config","version":1,"recoveryPassword":"test",
            "kdf":"PBKDF2-HMAC-SHA256","iterations":310000,"salt":"87fb4efecd373810ac34c1e2920dc154",
            "cipher":"AES-256-GCM","nonce":"84ee2ed95014f904058f7f07",
            "ciphertext":"7c0b4878fbb246e9c13053890d7f59b4b3b329dff0cd74de5873d802a73784db3ec4769d6792917967b663921e49fad5e7962b8ff4dca5f7f4cdd0ccc71fad7674fe8bb5604119f33b3cfb463a40e316829fb05c789ba3d782d96ac4bd15dc46c27cd997593f24dcfd0d2f87d5b3f737f2f4511e27c69bd7edfa0cc546d7495ab702a37e379c75d7800469a945a3c3daa8b1a9f25706cd36988e05b2f92d397f380947f9fc58"
        }""".encodeToByteArray()
        assertEquals(listOf(StoredNameMapping("masked", "photo.jpg")),
            NameVeilConfigCodec.decrypt(bytes, "test", crypto).entries)
    }

    @Test
    fun pbkdf2MatchesPublishedHmacSha256Vector() {
        val expected = "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        assertContentEquals(expected, derivePbkdf2Sha256("password", "salt".encodeToByteArray(), 2))
    }

    @Test
    fun unicodePasswordMatchesKeyProducedByPreviousRelease() {
        val expected = "5109a94f760610c57b756125e811c72e63ad61b94fe924ade6d5544d1821f9ab"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertContentEquals(expected, derivePbkdf2Sha256("中文🔐", byteArrayOf(1, 2, 3, 4), 2000))
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
