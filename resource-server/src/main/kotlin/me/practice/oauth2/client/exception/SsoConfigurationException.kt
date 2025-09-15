package me.practice.oauth2.client.exception

/**
 * SSO 설정 관련 예외
 */
open class SsoConfigurationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * SSO 설정 검증 실패 예외
 */
class SsoValidationException(
    val field: String,
    message: String
) : SsoConfigurationException("[$field] $message")

/**
 * SSO 설정을 찾을 수 없는 경우 예외
 */
class SsoConfigurationNotFoundException(
    val clientId: String
) : SsoConfigurationException("클라이언트 ID '$clientId'에 대한 SSO 설정을 찾을 수 없습니다")

/**
 * 중복된 SSO 설정 예외
 */
class DuplicateSsoConfigurationException(
    val field: String,
    val value: String
) : SsoConfigurationException("중복된 $field: $value")

/**
 * SSO 연결 테스트 실패 예외
 */
class SsoConnectionTestException(
    message: String,
    cause: Throwable? = null
) : SsoConfigurationException("SSO 연결 테스트 실패: $message", cause)