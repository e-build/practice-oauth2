package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Component

/**
 * Google OAuth2 제공자별 예외 매핑
 *
 * Google OAuth2 오류 코드:
 * - access_denied: 사용자가 권한 요청을 거부했을 때
 * - server_error: Google 서버에서 예기치 않은 오류 발생
 * - temporarily_unavailable: Google 서비스가 일시적으로 사용 불가
 * - invalid_client: 클라이언트 ID가 잘못됨
 * - redirect_uri_mismatch: 리다이렉트 URI 불일치
 * - invalid_scope: 요청한 스코프가 잘못됨
 */
@Component
class GoogleOAuth2FailureMapper : OAuth2FailureMapper {

    override fun canHandle(providerType: ProviderType): Boolean {
        return providerType == ProviderType.GOOGLE
    }

    override fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType? {
        val errorCode = exception.error?.errorCode ?: ""
        val description = exception.error?.description?.lowercase() ?: ""

        return when {
            // Google 특화 오류 코드 매핑
            errorCode == "access_denied" -> FailureReasonType.ACCESS_DENIED
            errorCode == "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            errorCode == "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            errorCode == "redirect_uri_mismatch" -> FailureReasonType.INVALID_CLIENT
            errorCode == "invalid_client" -> FailureReasonType.INVALID_CLIENT
            errorCode == "invalid_scope" -> FailureReasonType.INVALID_SCOPE

            // Description 기반 매핑
            description.contains("user denied") || description.contains("access_denied") -> FailureReasonType.ACCESS_DENIED
            description.contains("redirect_uri_mismatch") -> FailureReasonType.INVALID_CLIENT
            description.contains("invalid client") -> FailureReasonType.INVALID_CLIENT
            description.contains("invalid scope") -> FailureReasonType.INVALID_SCOPE
            description.contains("server error") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            description.contains("temporarily unavailable") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Google API 쿼터 관련 오류
            description.contains("quota exceeded") || description.contains("rate limit") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            else -> null // 처리할 수 없는 오류는 다음 매퍼로 전달
        }
    }
}