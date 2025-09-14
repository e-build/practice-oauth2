package me.practice.oauth2.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PrincipalHashingUtil {

    private const val ALGORITHM = "SHA-256"
    private const val SALT_LENGTH = 16

    fun hashPrincipal(principal: String): String {
        val salt = generateSalt()
        val hash = hashWithSalt(principal, salt)
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashWithSalt(principal: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(ALGORITHM)
        digest.update(salt)
        return digest.digest(principal.toByteArray(Charsets.UTF_8))
    }
}