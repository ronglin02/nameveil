package com.ronglin.nameveil

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal fun derivePbkdf2Sha256(password: String, salt: ByteArray, iterations: Int): ByteArray {
    // Match the original UTF-8 encoding even for malformed surrogate pairs.
    val chars = password.encodeToByteArray().decodeToString().toCharArray()
    val spec = PBEKeySpec(chars, salt, iterations, 256)
    chars.fill('\u0000')
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}
