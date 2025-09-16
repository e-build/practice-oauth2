package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Component

/**
 * Microsoft (Azure AD) OAuth2 제공자별 예외 매핑
 *
 * Microsoft Azure AD 오류 코드 (AADSTS* 패턴):
 * - AADSTS50001: 애플리케이션을 찾을 수 없음
 * - AADSTS50011: 리다이렉트 URI 불일치
 * - AADSTS50020: 사용자 계정이 존재하지 않음
 * - AADSTS50034: 사용자 계정이 디렉터리에 없음
 * - AADSTS50055: 비밀번호 만료
 * - AADSTS50057: 사용자 계정 비활성화
 * - AADSTS50076: 다단계 인증 필요
 * - AADSTS50105: 로그인한 사용자가 앱에 역할 할당되지 않음
 * - AADSTS65001: 사용자 또는 관리자가 동의하지 않음
 * - AADSTS700016: 잘못된 클라이언트 ID
 */
@Component
class MicrosoftOAuth2FailureMapper : OAuth2FailureMapper {

    override fun canHandle(providerType: ProviderType): Boolean {
        return providerType == ProviderType.MICROSOFT
    }

    override fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType? {
        val errorCode = exception.error?.errorCode ?: ""
        val description = exception.error?.description?.lowercase() ?: ""

        return when {
            // Microsoft 특화 AADSTS 오류 코드 매핑
            errorCode.startsWith("AADSTS") -> mapAadsts(errorCode, description)

            // 표준 OAuth2 오류 코드
            errorCode == "access_denied" -> FailureReasonType.ACCESS_DENIED
            errorCode == "invalid_client" -> FailureReasonType.INVALID_CLIENT
            errorCode == "invalid_scope" -> FailureReasonType.INVALID_SCOPE
            errorCode == "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            errorCode == "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Description 기반 매핑
            description.contains("aadsts") -> mapAadsts(description, description)
            description.contains("access_denied") || description.contains("user denied") -> FailureReasonType.ACCESS_DENIED
            description.contains("invalid_client") -> FailureReasonType.INVALID_CLIENT
            description.contains("redirect_uri_mismatch") -> FailureReasonType.INVALID_CLIENT
            description.contains("invalid scope") -> FailureReasonType.INVALID_SCOPE
            description.contains("server error") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            description.contains("service unavailable") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Microsoft 특화 패턴
            description.contains("consent required") -> FailureReasonType.ACCESS_DENIED
            description.contains("application not found") -> FailureReasonType.INVALID_CLIENT
            description.contains("account disabled") -> FailureReasonType.ACCOUNT_DISABLED
            description.contains("password expired") -> FailureReasonType.PASSWORD_EXPIRED
            description.contains("rate limit") || description.contains("throttled") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            else -> null // 처리할 수 없는 오류는 다음 매퍼로 전달
        }
    }

    private fun mapAadsts(errorCode: String, description: String): FailureReasonType {
        val code = errorCode.uppercase()
        return when {
            // 클라이언트/애플리케이션 관련 오류
            code.contains("AADSTS50001") || code.contains("AADSTS700016") -> FailureReasonType.INVALID_CLIENT
            code.contains("AADSTS50011") -> FailureReasonType.INVALID_CLIENT // 리다이렉트 URI 불일치

            // 사용자 계정 관련 오류
            code.contains("AADSTS50020") || code.contains("AADSTS50034") -> FailureReasonType.INVALID_CREDENTIALS
            code.contains("AADSTS50055") -> FailureReasonType.PASSWORD_EXPIRED
            code.contains("AADSTS50057") -> FailureReasonType.ACCOUNT_DISABLED

            // 권한 관련 오류
            code.contains("AADSTS50105") || code.contains("AADSTS65001") -> FailureReasonType.ACCESS_DENIED

            // MFA 관련 오류
            code.contains("AADSTS50076") -> FailureReasonType.SSO_ERROR // MFA 필요는 일반적인 SSO 오류로 분류

            // 일반적인 패턴 매칭
            description.contains("invalid_client") || description.contains("application") -> FailureReasonType.INVALID_CLIENT
            description.contains("access_denied") || description.contains("consent") -> FailureReasonType.ACCESS_DENIED
            description.contains("invalid_grant") || description.contains("token") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            description.contains("disabled") -> FailureReasonType.ACCOUNT_DISABLED
            description.contains("expired") -> FailureReasonType.PASSWORD_EXPIRED

            else -> FailureReasonType.SSO_ERROR
        }
    }
}