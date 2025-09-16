package me.practice.oauth2.service

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

/**
 * OAuth2 인증 실패 시 사용자 식별 복구를 시도하는 서비스 인터페이스
 *
 * DON-49: OAuth2 사용자 식별 개선의 핵심 컴포넌트
 * 목표: 사용자 식별률 10% → 60% 달성
 *
 * 4단계 복구 전략:
 * 1. 세션 기반 사용자 컨텍스트 복구 (최우선) - 새로 추가된 핵심 기능
 * 2. OAuth2 예외 분석 기반 복구
 * 3. HTTP Referer 기반 복구
 * 4. Request 속성 기반 복구
 *
 * @author DON-49 OAuth2 사용자 식별 개선
 * @since 2.1.0
 */
interface OAuth2UserRecoveryService {

    /**
     * OAuth2 인증 실패 시 사용자 식별자 복구 시도
     *
     * @param request HTTP 요청 객체
     * @param exception OAuth2 인증 예외
     * @return 복구된 사용자 식별자, 복구 실패 시 null
     */
    fun attemptUserRecovery(request: HttpServletRequest, exception: OAuth2AuthenticationException): String?

    /**
     * 복구 통계 정보 반환
     *
     * @return OAuth2UserRecoveryStatistics 복구 성공률 및 성능 통계
     */
    fun getRecoveryStatistics(): OAuth2UserRecoveryStatistics
}

/**
 * OAuth2 사용자 복구 통계 정보
 *
 * @property totalAttempts 총 복구 시도 횟수
 * @property successfulRecoveries 성공한 복구 횟수
 * @property sessionBasedRecoveries 세션 기반 복구 성공 횟수
 * @property exceptionBasedRecoveries 예외 분석 기반 복구 성공 횟수
 * @property refererBasedRecoveries Referer 기반 복구 성공 횟수
 * @property requestBasedRecoveries Request 속성 기반 복구 성공 횟수
 * @property averageResponseTimeMs 평균 응답 시간(밀리초)
 * @property successRate 복구 성공률 (0.0 ~ 1.0)
 */
data class OAuth2UserRecoveryStatistics(
    val totalAttempts: Long = 0,
    val successfulRecoveries: Long = 0,
    val sessionBasedRecoveries: Long = 0,
    val exceptionBasedRecoveries: Long = 0,
    val refererBasedRecoveries: Long = 0,
    val requestBasedRecoveries: Long = 0,
    val averageResponseTimeMs: Double = 0.0,
    val successRate: Double = 0.0
) {
    init {
        require(successRate in 0.0..1.0) { "Success rate must be between 0.0 and 1.0" }
        require(averageResponseTimeMs >= 0.0) { "Average response time must be non-negative" }
    }
}