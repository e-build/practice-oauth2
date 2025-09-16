package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

/**
 * OAuth2 예외를 제공자별 구체적인 실패 사유로 매핑하는 Chain of Responsibility 패턴 인터페이스
 *
 * DON-52: 제공자별 상세 오류 코드 매핑으로 SSO_ERROR/UNKNOWN 분류 50% 감소
 */
interface OAuth2FailureMapper {

    /**
     * 해당 매퍼가 처리 가능한 제공자인지 확인
     */
    fun canHandle(providerType: ProviderType): Boolean

    /**
     * OAuth2 예외를 구체적인 실패 사유로 매핑
     *
     * @param exception OAuth2 인증 예외
     * @param providerType 제공자 타입
     * @return 매핑된 실패 사유 (처리 불가능한 경우 null)
     */
    fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType?
}

/**
 * 기본 OAuth2 매퍼 (모든 제공자의 공통 오류 코드 처리)
 */
class DefaultOAuth2FailureMapper : OAuth2FailureMapper {

    override fun canHandle(providerType: ProviderType): Boolean = true

    override fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType? {
        return when (exception.error?.errorCode) {
            // RFC 6749 표준 OAuth2 오류 코드
            "invalid_client" -> FailureReasonType.INVALID_CLIENT
            "invalid_scope" -> FailureReasonType.INVALID_SCOPE
            "unsupported_grant_type" -> FailureReasonType.UNSUPPORTED_GRANT_TYPE
            "invalid_grant" -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            "access_denied" -> FailureReasonType.ACCESS_DENIED
            "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            "service_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            "network_error" -> FailureReasonType.NETWORK_ERROR

            else -> {
                // Description으로 추가 분류 시도
                val description = exception.error?.description?.lowercase() ?: ""
                when {
                    description.contains("invalid_client") -> FailureReasonType.INVALID_CLIENT
                    description.contains("invalid_scope") -> FailureReasonType.INVALID_SCOPE
                    description.contains("access_denied") || description.contains("access denied") -> FailureReasonType.ACCESS_DENIED
                    description.contains("server error") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    description.contains("service unavailable") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    description.contains("token exchange") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
                    description.contains("network") -> FailureReasonType.NETWORK_ERROR
                    description.contains("rate limit") || description.contains("too many requests") -> FailureReasonType.RATE_LIMIT_EXCEEDED
                    else -> null // 다른 매퍼에서 처리하도록 null 반환
                }
            }
        }
    }
}