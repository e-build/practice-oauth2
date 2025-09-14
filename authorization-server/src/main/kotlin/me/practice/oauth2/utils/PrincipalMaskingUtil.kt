package me.practice.oauth2.utils

object PrincipalMaskingUtil {

    private const val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    private const val PHONE_REGEX = "^\\d{3}-\\d{3,4}-\\d{4}$|^\\d{10,11}$"
    private const val MASK_CHAR = "*"

    fun maskPrincipal(principal: String): String {
        return when {
            principal.matches(EMAIL_REGEX.toRegex()) -> maskEmail(principal)
            principal.matches(PHONE_REGEX.toRegex()) -> maskPhoneNumber(principal)
            else -> maskGeneral(principal)
        }
    }

    private fun maskEmail(email: String): String {
        val parts = email.split("@")
        val localPart = parts[0]
        val domainPart = parts[1]
        
        val maskedLocalPart = if (localPart.length <= 2) {
            MASK_CHAR.repeat(localPart.length)
        } else {
            localPart.take(1) + MASK_CHAR.repeat(localPart.length - 2) + localPart.takeLast(1)
        }
        
        return "$maskedLocalPart@$domainPart"
    }

    private fun maskPhoneNumber(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace("-", "")
        return when (cleanNumber.length) {
            10 -> "${cleanNumber.substring(0, 3)}-${MASK_CHAR.repeat(3)}-${cleanNumber.substring(6)}"
            11 -> "${cleanNumber.substring(0, 3)}-${MASK_CHAR.repeat(4)}-${cleanNumber.substring(7)}"
            else -> maskGeneral(phoneNumber)
        }
    }

    private fun maskGeneral(value: String): String {
        return when {
            value.length <= 2 -> MASK_CHAR.repeat(value.length)
            value.length <= 4 -> value.take(1) + MASK_CHAR.repeat(value.length - 1)
            else -> value.take(2) + MASK_CHAR.repeat(value.length - 4) + value.takeLast(2)
        }
    }
}