package me.practice.oauth2.service.platform

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.domain.IdpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * 기본 플랫폼 감지 서비스 구현체
 *
 * User-Agent, 헤더, 경로 등을 분석하여 플랫폼을 감지합니다.
 * 성능 최적화를 위해 패턴 컴파일 결과와 감지 결과를 캐싱합니다.
 *
 * 플랫폼 감지 규칙은 소스코드에 하드코딩되어 있어 성능과 명확성을 제공합니다.
 *
 * @author OAuth2 Team
 * @since 2.1.0
 */
@Service
class DefaultPlatformDetectionService(
    @Value("\${shopl.oauth2.platform.detection.default-platform:DASHBOARD}")
    private val defaultPlatformName: String
) : PlatformDetectionService {

    private val logger = LoggerFactory.getLogger(DefaultPlatformDetectionService::class.java)

    // === 하드코딩된 플랫폼 감지 설정 ===

    // 모바일 플랫폼 감지 설정
    private val mobileUserAgentPatterns = listOf(
        // iOS 앱
        "ShoplMobileApp/iOS.*",
        "ShoplaceMobile/iOS.*",
        "CFNetwork.*Darwin.*iOS",
        ".*iPhone.*Mobile.*",

        // Android 앱
        "ShoplMobileApp/Android.*",
        "ShoplaceMobile/Android.*",
        "okhttp.*Android",
        ".*Android.*Mobile.*",

        // 범용 모바일 패턴
        ".*Mobile.*App.*",
        ".*App.*Mobile.*"
    )

    private val mobileCustomHeaders = mapOf(
        "X-Platform" to listOf("mobile", "ios", "android"),
        "X-App-Version" to listOf(".*"), // 앱 버전이 있으면 모바일로 간주
        "X-Device-Type" to listOf("mobile", "phone", "tablet")
    )

    // API 플랫폼 감지 설정
    private val apiUserAgentPatterns = listOf(
        "curl/.*",
        "wget/.*",
        "Postman.*",
        "Insomnia.*",
        "HTTPie.*",
        ".*API.*Client.*",
        ".*SDK.*",
        "Python-urllib.*",
        "Java/.*",
        "okhttp/.*",
        "Apache-HttpClient.*"
    )

    private val apiKeyHeaders = listOf(
        "X-API-Key",
        "X-App-Key",
        "Authorization" // Bearer 토큰이 있으면서 User-Agent가 특수한 경우
    )

    // 대시보드 플랫폼 감지 설정
    private val dashboardPathPatterns = listOf(
        "/admin/.*",
        "/dashboard/.*",
        "/management/.*"
    )

    private val dashboardUserAgentPatterns = listOf(
        ".*Chrome.*",
        ".*Firefox.*",
        ".*Safari.*",
        ".*Edge.*",
        ".*Opera.*"
    )

    private val dashboardCustomHeaders = mapOf(
        "X-Platform" to listOf("dashboard", "admin", "web-admin"),
        "Referer" to listOf(".*dashboard.*", ".*admin.*")
    )

    // 웹 플랫폼 감지 설정
    private val webUserAgentPatterns = listOf(
        ".*Mozilla.*",
        ".*WebKit.*",
        ".*Gecko.*"
    )

    // 성능 설정
    private val cacheEnabled = true
    private val cacheExpirationSeconds = 300L // 5분
    private val patternCacheSize = 1000
    private val detectEmptyUserAgent = true

    // 플랫폼 우선순위 (높을수록 우선)
    private val mobilePriority = 100
    private val apiPriority = 90
    private val dashboardPriority = 80
    private val webPriority = 10

    // 성능 통계
    private val totalDetections = AtomicLong(0)
    private val detectionsByPlatform = mutableMapOf<IdpClient.Platform, AtomicLong>()
    private val detectionsByReason = mutableMapOf<DetectionReason, AtomicLong>()
    private val detectionTimes = mutableListOf<Long>()
    private val cacheHits = AtomicLong(0)

    // 패턴 캐시 (컴파일된 정규식)
    private val patternCache: Cache<String, Pattern> = Caffeine.newBuilder()
        .maximumSize(patternCacheSize.toLong())
        .expireAfterAccess(Duration.ofHours(1))
        .build<String, Pattern>()

    // 감지 결과 캐시
    private val detectionCache: Cache<String, PlatformDetectionResult>? = if (cacheEnabled) {
        Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofSeconds(cacheExpirationSeconds))
            .build<String, PlatformDetectionResult>()
    } else {
        null
    }

    init {
        // 통계 맵 초기화
        IdpClient.Platform.values().forEach { platform ->
            detectionsByPlatform[platform] = AtomicLong(0)
        }
        DetectionReason.values().forEach { reason ->
            detectionsByReason[reason] = AtomicLong(0)
        }

        logger.info("Platform detection service initialized with hardcoded configuration: defaultPlatform={}",
            defaultPlatformName)
    }

    override fun detectPlatform(request: HttpServletRequest?): IdpClient.Platform {
        return detectPlatformWithDetails(request).platform
    }

    override fun detectPlatformWithDetails(request: HttpServletRequest?): PlatformDetectionResult {
        val startTime = System.currentTimeMillis()
        totalDetections.incrementAndGet()

        try {
            // 요청이 null인 경우
            if (request == null) {
                return createDefaultResult(DetectionReason.DEFAULT_FALLBACK, null, startTime)
            }

            // 캐시 확인
            val cacheKey = createCacheKey(request)
            detectionCache?.getIfPresent(cacheKey)?.let { cachedResult ->
                cacheHits.incrementAndGet()
                detectionsByReason[DetectionReason.CACHED_RESULT]?.incrementAndGet()
                return cachedResult.copy(detectionTimeMs = System.currentTimeMillis() - startTime)
            }

            // 실제 감지 수행
            val result = performDetection(request, startTime)

            // 결과 캐싱
            detectionCache?.put(cacheKey, result)

            // 통계 업데이트
            updateStatistics(result)

            return result

        } catch (e: Exception) {
            logger.warn("Platform detection failed, using default platform", e)
            return createDefaultResult(DetectionReason.DEFAULT_FALLBACK, null, startTime)
        }
    }

    override fun detectPlatformFromUserAgent(userAgent: String?): IdpClient.Platform {
        if (userAgent == null) {
            return getDefaultPlatform()
        }

        // 우선순위 순으로 검사 (모바일 -> API -> 대시보드 -> 웹)

        // 1. 모바일 감지 (최고 우선순위)
        if (matchesAnyPattern(userAgent, mobileUserAgentPatterns)) {
            return IdpClient.Platform.MOBILE
        }

        // 2. API 감지
        if (matchesAnyPattern(userAgent, apiUserAgentPatterns)) {
            return IdpClient.Platform.API
        }

        // 3. 대시보드 감지
        if (matchesAnyPattern(userAgent, dashboardUserAgentPatterns)) {
            return IdpClient.Platform.DASHBOARD
        }

        // 4. 웹 감지
        if (matchesAnyPattern(userAgent, webUserAgentPatterns)) {
            return IdpClient.Platform.WEB
        }

        return getDefaultPlatform()
    }

    override fun getDetectionStatistics(): PlatformDetectionStatistics {
        val avgTime = if (detectionTimes.isNotEmpty()) {
            detectionTimes.average()
        } else {
            0.0
        }

        val totalDetectionsValue = totalDetections.get()
        val cacheHitRatio = if (totalDetectionsValue > 0) {
            cacheHits.get().toDouble() / totalDetectionsValue
        } else {
            0.0
        }

        return PlatformDetectionStatistics(
            totalDetections = totalDetectionsValue,
            detectionsByPlatform = detectionsByPlatform.mapValues { it.value.get() },
            detectionsByReason = detectionsByReason.mapValues { it.value.get() },
            averageDetectionTimeMs = avgTime,
            cacheHitRatio = cacheHitRatio,
            lastUpdated = LocalDateTime.now()
        )
    }

    /**
     * 실제 플랫폼 감지 수행
     */
    private fun performDetection(request: HttpServletRequest, startTime: Long): PlatformDetectionResult {
        val userAgent = request.getHeader("User-Agent")
        val requestPath = request.requestURI

        // 1. 모바일 감지 (최고 우선순위)
        checkMobilePlatform(request, userAgent)?.let { return it }

        // 2. API 감지
        checkApiPlatform(request, userAgent)?.let { return it }

        // 3. 대시보드 감지
        checkDashboardPlatform(request, userAgent, requestPath)?.let { return it }

        // 4. 웹 감지 (기본)
        checkWebPlatform(request, userAgent)?.let { return it }

        // 5. 기본값
        return createDefaultResult(DetectionReason.DEFAULT_FALLBACK, userAgent, startTime)
    }

    /**
     * 모바일 플랫폼 감지
     */
    private fun checkMobilePlatform(request: HttpServletRequest, userAgent: String?): PlatformDetectionResult? {
        // User-Agent 패턴 확인
        if (userAgent != null && matchesAnyPattern(userAgent, mobileUserAgentPatterns)) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.MOBILE,
                detectionReason = DetectionReason.USER_AGENT_PATTERN,
                detectionValue = userAgent,
                confidence = 0.9,
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        // 커스텀 헤더 확인
        for ((headerName, expectedValues) in mobileCustomHeaders) {
            val headerValue = request.getHeader(headerName)
            if (headerValue != null && matchesAnyPattern(headerValue, expectedValues)) {
                return PlatformDetectionResult(
                    platform = IdpClient.Platform.MOBILE,
                    detectionReason = DetectionReason.CUSTOM_HEADER,
                    detectionValue = "$headerName: $headerValue",
                    confidence = 0.95,
                    detectionTimeMs = System.currentTimeMillis()
                )
            }
        }

        return null
    }

    /**
     * API 플랫폼 감지
     */
    private fun checkApiPlatform(request: HttpServletRequest, userAgent: String?): PlatformDetectionResult? {
        // API 키 헤더 확인
        for (headerName in apiKeyHeaders) {
            val headerValue = request.getHeader(headerName)
            if (headerValue != null && headerValue.isNotBlank()) {
                // Authorization 헤더의 경우 Bearer 토큰과 특수 User-Agent 조합 확인
                if (headerName == "Authorization" && headerValue.startsWith("Bearer ")) {
                    if (userAgent == null || matchesAnyPattern(userAgent, apiUserAgentPatterns)) {
                        return PlatformDetectionResult(
                            platform = IdpClient.Platform.API,
                            detectionReason = DetectionReason.API_KEY_HEADER,
                            detectionValue = "$headerName: Bearer ***",
                            confidence = 0.85,
                            detectionTimeMs = System.currentTimeMillis()
                        )
                    }
                } else {
                    return PlatformDetectionResult(
                        platform = IdpClient.Platform.API,
                        detectionReason = DetectionReason.API_KEY_HEADER,
                        detectionValue = "$headerName: ***",
                        confidence = 0.9,
                        detectionTimeMs = System.currentTimeMillis()
                    )
                }
            }
        }

        // User-Agent 패턴 확인
        if (userAgent != null && matchesAnyPattern(userAgent, apiUserAgentPatterns)) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.API,
                detectionReason = DetectionReason.USER_AGENT_PATTERN,
                detectionValue = userAgent,
                confidence = 0.8,
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        // User-Agent 없음 확인
        if (detectEmptyUserAgent && (userAgent == null || userAgent.isBlank())) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.API,
                detectionReason = DetectionReason.EMPTY_USER_AGENT,
                detectionValue = null,
                confidence = 0.7,
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * 대시보드 플랫폼 감지
     */
    private fun checkDashboardPlatform(request: HttpServletRequest, userAgent: String?, requestPath: String): PlatformDetectionResult? {
        // 경로 패턴 확인
        if (matchesAnyPattern(requestPath, dashboardPathPatterns)) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.DASHBOARD,
                detectionReason = DetectionReason.PATH_PATTERN,
                detectionValue = requestPath,
                confidence = 0.9,
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        // 커스텀 헤더 확인
        for ((headerName, expectedValues) in dashboardCustomHeaders) {
            val headerValue = request.getHeader(headerName)
            if (headerValue != null && matchesAnyPattern(headerValue, expectedValues)) {
                return PlatformDetectionResult(
                    platform = IdpClient.Platform.DASHBOARD,
                    detectionReason = DetectionReason.CUSTOM_HEADER,
                    detectionValue = "$headerName: $headerValue",
                    confidence = 0.85,
                    detectionTimeMs = System.currentTimeMillis()
                )
            }
        }

        // User-Agent 패턴 확인 (브라우저)
        if (userAgent != null && matchesAnyPattern(userAgent, dashboardUserAgentPatterns)) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.DASHBOARD,
                detectionReason = DetectionReason.USER_AGENT_PATTERN,
                detectionValue = userAgent,
                confidence = 0.6, // 브라우저는 웹과 구분이 어려우므로 낮은 신뢰도
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * 웹 플랫폼 감지
     */
    private fun checkWebPlatform(request: HttpServletRequest, userAgent: String?): PlatformDetectionResult? {
        // 웹 브라우저 User-Agent 패턴 확인
        if (userAgent != null && matchesAnyPattern(userAgent, webUserAgentPatterns)) {
            return PlatformDetectionResult(
                platform = IdpClient.Platform.WEB,
                detectionReason = DetectionReason.USER_AGENT_PATTERN,
                detectionValue = userAgent,
                confidence = 0.5,
                detectionTimeMs = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * 패턴 매칭 수행
     */
    private fun matchesAnyPattern(input: String, patterns: List<String>): Boolean {
        return patterns.any { pattern ->
            try {
                val compiledPattern = patternCache.get(pattern) {
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                }
                compiledPattern.matcher(input).find()
            } catch (e: Exception) {
                logger.warn("Invalid regex pattern: $pattern", e)
                false
            }
        }
    }

    /**
     * 캐시 키 생성
     */
    private fun createCacheKey(request: HttpServletRequest): String {
        val userAgent = request.getHeader("User-Agent") ?: ""
        val path = request.requestURI
        val relevantHeaders = (mobileCustomHeaders.keys +
                             dashboardCustomHeaders.keys +
                             apiKeyHeaders).joinToString(",") { headerName ->
            "$headerName:${request.getHeader(headerName) ?: ""}"
        }
        return "$userAgent|$path|$relevantHeaders".hashCode().toString()
    }

    /**
     * 기본 결과 생성
     */
    private fun createDefaultResult(reason: DetectionReason, detectionValue: String?, startTime: Long): PlatformDetectionResult {
        return PlatformDetectionResult(
            platform = getDefaultPlatform(),
            detectionReason = reason,
            detectionValue = detectionValue,
            confidence = 0.0,
            detectionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * 기본 플랫폼 반환
     */
    private fun getDefaultPlatform(): IdpClient.Platform {
        return try {
            IdpClient.Platform.valueOf(defaultPlatformName)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid default platform: $defaultPlatformName, using DASHBOARD")
            IdpClient.Platform.DASHBOARD
        }
    }

    /**
     * 통계 업데이트
     */
    private fun updateStatistics(result: PlatformDetectionResult) {
        detectionsByPlatform[result.platform]?.incrementAndGet()
        detectionsByReason[result.detectionReason]?.incrementAndGet()

        synchronized(detectionTimes) {
            detectionTimes.add(result.detectionTimeMs)
            // 최근 1000개만 유지
            if (detectionTimes.size > 1000) {
                detectionTimes.removeAt(0)
            }
        }
    }
}