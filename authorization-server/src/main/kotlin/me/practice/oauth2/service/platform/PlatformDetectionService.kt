package me.practice.oauth2.service.platform

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.domain.IdpClient

/**
 * 플랫폼 감지 서비스 인터페이스
 *
 * HTTP 요청의 다양한 특성을 분석하여 사용자가 접근한 플랫폼을 동적으로 감지합니다.
 * 이를 통해 플랫폼별 차별화된 로깅, 통계, 보안 정책을 적용할 수 있습니다.
 *
 * @author OAuth2 Team
 * @since 2.1.0
 */
interface PlatformDetectionService {

    /**
     * HTTP 요청으로부터 플랫폼을 감지합니다.
     *
     * @param request HTTP 요청 (null일 경우 기본 플랫폼 반환)
     * @return 감지된 플랫폼
     */
    fun detectPlatform(request: HttpServletRequest?): IdpClient.Platform

    /**
     * 플랫폼 감지 결과와 함께 상세 정보를 반환합니다.
     *
     * @param request HTTP 요청
     * @return 감지된 플랫폼과 감지 근거
     */
    fun detectPlatformWithDetails(request: HttpServletRequest?): PlatformDetectionResult

    /**
     * 특정 User-Agent로부터 플랫폼을 감지합니다.
     *
     * @param userAgent User-Agent 문자열
     * @return 감지된 플랫폼
     */
    fun detectPlatformFromUserAgent(userAgent: String?): IdpClient.Platform

    /**
     * 감지 로직의 성능 통계를 반환합니다.
     *
     * @return 성능 통계 정보
     */
    fun getDetectionStatistics(): PlatformDetectionStatistics
}

/**
 * 플랫폼 감지 결과
 */
data class PlatformDetectionResult(
    /**
     * 감지된 플랫폼
     */
    val platform: IdpClient.Platform,

    /**
     * 감지 근거
     */
    val detectionReason: DetectionReason,

    /**
     * 감지에 사용된 값 (User-Agent, 헤더값 등)
     */
    val detectionValue: String?,

    /**
     * 감지 신뢰도 (0.0 ~ 1.0)
     */
    val confidence: Double,

    /**
     * 감지 소요 시간 (밀리초)
     */
    val detectionTimeMs: Long
)

/**
 * 플랫폼 감지 근거
 */
enum class DetectionReason {
    /**
     * User-Agent 패턴 매칭으로 감지
     */
    USER_AGENT_PATTERN,

    /**
     * 커스텀 헤더로 감지
     */
    CUSTOM_HEADER,

    /**
     * API 키 헤더 존재로 감지
     */
    API_KEY_HEADER,

    /**
     * 요청 경로 패턴으로 감지
     */
    PATH_PATTERN,

    /**
     * User-Agent 없음으로 감지
     */
    EMPTY_USER_AGENT,

    /**
     * 기본값 적용 (감지 실패)
     */
    DEFAULT_FALLBACK,

    /**
     * 캐시된 결과 사용
     */
    CACHED_RESULT
}

/**
 * 플랫폼 감지 성능 통계
 */
data class PlatformDetectionStatistics(
    /**
     * 총 감지 횟수
     */
    val totalDetections: Long,

    /**
     * 플랫폼별 감지 횟수
     */
    val detectionsByPlatform: Map<IdpClient.Platform, Long>,

    /**
     * 감지 근거별 횟수
     */
    val detectionsByReason: Map<DetectionReason, Long>,

    /**
     * 평균 감지 시간 (밀리초)
     */
    val averageDetectionTimeMs: Double,

    /**
     * 캐시 히트율
     */
    val cacheHitRatio: Double,

    /**
     * 마지막 통계 업데이트 시간
     */
    val lastUpdated: java.time.LocalDateTime
)