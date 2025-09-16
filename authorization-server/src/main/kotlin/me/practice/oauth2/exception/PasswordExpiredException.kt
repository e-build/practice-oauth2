package me.practice.oauth2.exception

import org.springframework.security.authentication.CredentialsExpiredException

/**
 * 비밀번호 만료 예외
 * 비밀번호 업데이트 후 90일이 지난 경우 발생
 */
class PasswordExpiredException(
    message: String,
    cause: Throwable? = null
) : CredentialsExpiredException(message, cause) {

    constructor(username: String) : this("Password has expired: $username", null)
}