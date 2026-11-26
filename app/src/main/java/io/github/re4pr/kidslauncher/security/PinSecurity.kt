package io.github.re4pr.kidslauncher.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 hashing for the Parent Master Passcode.
 * Passcodes are never stored in plaintext.
 */
object PinSecurity {
    private const val ITERATIONS = 32_000
    private const val KEY_BITS = 256

    fun generateSaltHex(bytes: Int = 16): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return buf.toHex()
    }

    fun hashPasscode(passcode: String, saltHex: String): String {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(passcode.trim().toCharArray(), salt, ITERATIONS, KEY_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded.toHex()
    }

    fun verifyPasscode(candidate: String, expectedHashHex: String, saltHex: String): Boolean {
        if (expectedHashHex.isBlank() || saltHex.isBlank() || candidate.isBlank()) return false
        val actual = hashPasscode(candidate, saltHex).hexToBytes()
        val expected = expectedHashHex.hexToBytes()
        return MessageDigest.isEqual(actual, expected)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val clean = trim()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(clean.length / 2) { idx ->
            clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
    }
}
