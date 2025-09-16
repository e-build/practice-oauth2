package me.practice.oauth2.exception

import org.springframework.security.authentication.AccountExpiredException as SpringAccountExpiredException

/**
 * 계정 만료 예외
 * 계정의 delDt가 설정되어 만료된 경우 발생
 */
class AccountExpiredException(
    message: String,
    cause: Throwable? = null
) : SpringAccountExpiredException(message, cause) {

    constructor(username: String) : this("Account has expired: $username", null)
}