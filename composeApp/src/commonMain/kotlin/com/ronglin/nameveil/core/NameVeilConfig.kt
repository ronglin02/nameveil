package com.ronglin.nameveil.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val CONFIG_FILE_NAME = "NAMEVEIL"
const val PENDING_CONFIG_FILE_NAME = "NAMEVEIL.pending"
const val CONFIG_KDF_ITERATIONS = 310_000
private const val CONFIG_ASSOCIATED_DATA = "nameveil-config-v1"

@Serializable
data class StoredNameMapping(
    val currentName: String,
    val originalName: String,
)

/** 外层固定格式。recoveryPassword 是刻意保留的“君子模式”找回字段。 */
@Serializable
data class NameVeilConfigEnvelope(
    val format: String = "nameveil-config",
    val version: Int = 1,
    val recoveryPassword: String,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int,
    val salt: String,
    val cipher: String = "AES-256-GCM",
    val nonce: String,
    val ciphertext: String,
)

/** 只在内存中出现，序列化后立即加密。 */
@Serializable
data class NameVeilConfigPayload(
    val createdAtEpochMs: Long,
    val entries: List<StoredNameMapping>,
)

object NameVeilConfigCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encrypt(
        payload: NameVeilConfigPayload,
        password: String,
        crypto: CryptoSupport,
    ): ByteArray {
        val salt = crypto.secureRandomBytes(16)
        val nonce = crypto.secureRandomBytes(12)
        val plaintext = json.encodeToString(payload).encodeToByteArray()
        val ciphertext = crypto.encryptAesGcm(
            plaintext = plaintext,
            password = password,
            salt = salt,
            nonce = nonce,
            iterations = CONFIG_KDF_ITERATIONS,
            associatedData = CONFIG_ASSOCIATED_DATA.encodeToByteArray(),
        )
        plaintext.fill(0)
        return json.encodeToString(
            NameVeilConfigEnvelope(
                recoveryPassword = password,
                iterations = CONFIG_KDF_ITERATIONS,
                salt = salt.toHex(),
                nonce = nonce.toHex(),
                ciphertext = ciphertext.toHex(),
            ),
        ).encodeToByteArray()
    }

    fun decrypt(
        bytes: ByteArray,
        password: String,
        crypto: CryptoSupport,
    ): NameVeilConfigPayload {
        val envelope = decodeEnvelope(bytes)
        val plaintext = try {
            crypto.decryptAesGcm(
                ciphertext = envelope.ciphertext.hexToBytes(),
                password = password,
                salt = envelope.salt.hexToBytes(),
                nonce = envelope.nonce.hexToBytes(),
                iterations = envelope.iterations,
                associatedData = CONFIG_ASSOCIATED_DATA.encodeToByteArray(),
            )
        } catch (_: Exception) {
            throw IllegalArgumentException("密码错误或配置文件已损坏")
        }

        return try {
            json.decodeFromString<NameVeilConfigPayload>(plaintext.decodeToString())
        } finally {
            plaintext.fill(0)
        }
    }

    fun recoverPassword(bytes: ByteArray): String = decodeEnvelope(bytes).recoveryPassword

    private fun decodeEnvelope(bytes: ByteArray): NameVeilConfigEnvelope {
        val envelope = json.decodeFromString<NameVeilConfigEnvelope>(bytes.decodeToString())
        require(envelope.format == "nameveil-config" && envelope.version == 1) {
            "不支持的 NameVeil 配置格式或版本"
        }
        require(envelope.kdf == "PBKDF2-HMAC-SHA256" && envelope.cipher == "AES-256-GCM") {
            "不支持的配置加密算法"
        }
        require(envelope.iterations in 100_000..2_000_000) { "配置 KDF 参数超出安全范围" }
        require(envelope.recoveryPassword.isNotEmpty()) { "配置中没有可找回的密码" }
        return envelope
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "十六进制内容长度无效" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
