package me.practice.oauth2.exception

import org.springframework.security.core.AuthenticationException

/**
 * 로그인 시도 횟수 초과 예외
 * 지정된 시간 내에 너무 많은 실패한 로그인 시도가 있는 경우 발생
 */
class TooManyAttemptsException(
    message: String,
    cause: Throwable? = null
) : AuthenticationException(message, cause) {

    constructor(username: String, attempts: Long, timeFrame: Long) :
        this("Too many failed login attempts for user: $username ($attempts attempts in last $timeFrame hours)")
}