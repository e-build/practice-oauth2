package me.practice.oauth2.mapper

import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.entity.ProviderType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Component

/**
 * Kakao OAuth2 제공자별 예외 매핑
 *
 * Kakao OAuth2 오류 코드:
 * - KOE101: 잘못된 클라이언트 정보 (invalid_client)
 * - KOE320: 액세스 토큰 만료 (invalid_token)
 * - KOE401: 권한이 없는 앱 키 (unauthorized)
 * - KOE403: 액세스가 금지됨 (forbidden)
 * - access_denied: 사용자가 동의하지 않음
 * - server_error: 서버 내부 오류
 * - temporarily_unavailable: 서비스 일시 중단
 */
@Component
class KakaoOAuth2FailureMapper : OAuth2FailureMapper {

    override fun canHandle(providerType: ProviderType): Boolean {
        return providerType == ProviderType.KAKAO
    }

    override fun mapException(exception: OAuth2AuthenticationException, providerType: ProviderType): FailureReasonType? {
        val errorCode = exception.error?.errorCode ?: ""
        val description = exception.error?.description?.lowercase() ?: ""

        return when {
            // Kakao 특화 오류 코드 매핑
            errorCode == "KOE101" -> FailureReasonType.INVALID_CLIENT
            errorCode == "KOE320" -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            errorCode == "KOE401" -> FailureReasonType.INVALID_CLIENT
            errorCode == "KOE403" -> FailureReasonType.ACCESS_DENIED
            errorCode == "access_denied" -> FailureReasonType.ACCESS_DENIED
            errorCode == "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            errorCode == "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Description에서 Kakao 특화 오류 탐지
            description.contains("koe101") -> FailureReasonType.INVALID_CLIENT
            description.contains("koe320") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            description.contains("koe401") -> FailureReasonType.INVALID_CLIENT
            description.contains("koe403") -> FailureReasonType.ACCESS_DENIED

            // 일반적인 Kakao 오류 패턴
            description.contains("invalid_client") || description.contains("잘못된 클라이언트") -> FailureReasonType.INVALID_CLIENT
            description.contains("access_denied") || description.contains("사용자 거부") -> FailureReasonType.ACCESS_DENIED
            description.contains("invalid_token") || description.contains("토큰 만료") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            description.contains("unauthorized") || description.contains("권한 없음") -> FailureReasonType.INVALID_CLIENT
            description.contains("forbidden") || description.contains("접근 금지") -> FailureReasonType.ACCESS_DENIED
            description.contains("server error") || description.contains("서버 오류") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            description.contains("service unavailable") || description.contains("서비스 중단") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // Kakao API 호출량 제한
            description.contains("rate limit") || description.contains("호출 제한") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            else -> null // 처리할 수 없는 오류는 다음 매퍼로 전달
        }
    }
}