package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Component

/**
 * GitHub OAuth2 제공자별 예외 매핑
 *
 * GitHub OAuth2 오류 코드:
 * - access_denied: 사용자가 애플리케이션 권한 요청을 거부
 * - incorrect_client_credentials: 클라이언트 ID 또는 시크릿이 잘못됨
 * - redirect_uri_mismatch: 리다이렉트 URI가 등록된 것과 일치하지 않음
 * - bad_verification_code: 인증 코드가 잘못되거나 만료됨
 * - unverified_email: 이메일이 검증되지 않음
 * - unsupported_grant_type: 지원하지 않는 grant type
 */
@Component
class GitHubOAuth2FailureMapper : OAuth2FailureMapper {

    override fun canHandle(providerType: ProviderType): Boolean {
        return providerType == ProviderType.GITHUB
    }

    override fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType? {
        val errorCode = exception.error?.errorCode ?: ""
        val description = exception.error?.description?.lowercase() ?: ""

        return when {
            // GitHub 특화 오류 코드 매핑
            errorCode == "access_denied" -> FailureReasonType.ACCESS_DENIED
            errorCode == "incorrect_client_credentials" -> FailureReasonType.INVALID_CLIENT
            errorCode == "redirect_uri_mismatch" -> FailureReasonType.INVALID_CLIENT
            errorCode == "bad_verification_code" -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            errorCode == "unverified_email" -> FailureReasonType.ACCESS_DENIED
            errorCode == "unsupported_grant_type" -> FailureReasonType.UNSUPPORTED_GRANT_TYPE

            // 표준 OAuth2 오류 코드
            errorCode == "invalid_client" -> FailureReasonType.INVALID_CLIENT
            errorCode == "invalid_scope" -> FailureReasonType.INVALID_SCOPE
            errorCode == "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            errorCode == "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Description 기반 매핑
            description.contains("access_denied") || description.contains("user denied") -> FailureReasonType.ACCESS_DENIED
            description.contains("incorrect_client_credentials") || description.contains("client credentials") -> FailureReasonType.INVALID_CLIENT
            description.contains("redirect_uri_mismatch") || description.contains("redirect uri") -> FailureReasonType.INVALID_CLIENT
            description.contains("bad_verification_code") || description.contains("verification code") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            description.contains("unverified_email") || description.contains("email not verified") -> FailureReasonType.ACCESS_DENIED
            description.contains("invalid_client") -> FailureReasonType.INVALID_CLIENT
            description.contains("invalid scope") -> FailureReasonType.INVALID_SCOPE
            description.contains("server error") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            description.contains("service unavailable") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // GitHub API 특화 패턴
            description.contains("not found") && description.contains("application") -> FailureReasonType.INVALID_CLIENT
            description.contains("suspended") || description.contains("disabled") -> FailureReasonType.ACCOUNT_DISABLED
            description.contains("rate limit") || description.contains("api limit") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            // GitHub 서비스 상태 관련
            description.contains("github is down") || description.contains("maintenance") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            else -> null // 처리할 수 없는 오류는 다음 매퍼로 전달
        }
    }
}