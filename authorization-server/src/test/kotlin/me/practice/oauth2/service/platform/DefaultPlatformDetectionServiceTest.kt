package me.practice.oauth2.service.platform

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.domain.IdpClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 플랫폼 감지 서비스 테스트
 *
 * 다양한 User-Agent와 헤더 조합을 통해 플랫폼 감지 로직을 검증합니다.
 * 하드코딩된 설정을 사용하여 테스트합니다.
 */
@DisplayName("플랫폼 감지 서비스 테스트")
class DefaultPlatformDetectionServiceTest {

    private lateinit var platformDetectionService: DefaultPlatformDetectionService

    @BeforeEach
    fun setup() {
        platformDetectionService = DefaultPlatformDetectionService("DASHBOARD")
    }

    @Test
    @DisplayName("모바일 앱 User-Agent로 MOBILE 플랫폼 감지")
    fun `should detect MOBILE platform from mobile app user agent`() {
        // Given
        val mobileUserAgents = listOf(
            "ShoplMobileApp/iOS/1.0.0",
            "ShoplaceMobile/Android/2.1.0",
            "Test iPhone Safari Mobile Application", // .* iPhone .* Mobile .*
            "Test Android Mobile App Version" // .* Mobile .* App .*
        )

        // When & Then
        mobileUserAgents.forEach { userAgent ->
            val result = platformDetectionService.detectPlatformWithDetails(createMockRequest(userAgent = userAgent))

            assertAll(
                { assertEquals(IdpClient.Platform.MOBILE, result.platform, "User-Agent: $userAgent") },
                { assertEquals(DetectionReason.USER_AGENT_PATTERN, result.detectionReason) },
                { assertTrue(result.confidence >= 0.9) }
            )
        }
    }

    @Test
    @DisplayName("모바일 커스텀 헤더로 MOBILE 플랫폼 감지")
    fun `should detect MOBILE platform from custom headers`() {
        // Given
        val request = createMockRequest(
            userAgent = "Regular Browser",
            customHeaders = mapOf(
                "X-Platform" to "mobile",
                "X-App-Version" to "1.2.3"
            )
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.MOBILE, result.platform) },
            { assertEquals(DetectionReason.CUSTOM_HEADER, result.detectionReason) },
            { assertTrue(result.confidence >= 0.95) }
        )
    }

    @Test
    @DisplayName("API 클라이언트 User-Agent로 API 플랫폼 감지")
    fun `should detect API platform from API client user agent`() {
        // Given
        val apiUserAgents = listOf(
            "curl/7.68.0",
            "Postman/9.0.0",
            "Python-urllib/3.9",
            "Java/11.0.11",
            "okhttp/4.9.0"
        )

        // When & Then
        apiUserAgents.forEach { userAgent ->
            val result = platformDetectionService.detectPlatformWithDetails(createMockRequest(userAgent = userAgent))

            assertAll(
                { assertEquals(IdpClient.Platform.API, result.platform, "User-Agent: $userAgent") },
                { assertEquals(DetectionReason.USER_AGENT_PATTERN, result.detectionReason) },
                { assertTrue(result.confidence >= 0.8) }
            )
        }
    }

    @Test
    @DisplayName("API 키 헤더로 API 플랫폼 감지")
    fun `should detect API platform from API key headers`() {
        // Given
        val request = createMockRequest(
            userAgent = "Custom API Client",
            customHeaders = mapOf("X-API-Key" to "sk-1234567890abcdef")
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.API, result.platform) },
            { assertEquals(DetectionReason.API_KEY_HEADER, result.detectionReason) },
            { assertTrue(result.confidence >= 0.9) }
        )
    }

    @Test
    @DisplayName("User-Agent 없음으로 API 플랫폼 감지")
    fun `should detect API platform from empty user agent`() {
        // Given
        val request = createMockRequest(userAgent = null)

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.API, result.platform) },
            { assertEquals(DetectionReason.EMPTY_USER_AGENT, result.detectionReason) },
            { assertTrue(result.confidence >= 0.7) }
        )
    }

    @Test
    @DisplayName("대시보드 경로로 DASHBOARD 플랫폼 감지")
    fun `should detect DASHBOARD platform from admin path`() {
        // Given
        val request = createMockRequest(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0",
            requestURI = "/admin/dashboard"
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.DASHBOARD, result.platform) },
            { assertEquals(DetectionReason.PATH_PATTERN, result.detectionReason) },
            { assertTrue(result.confidence >= 0.9) }
        )
    }

    @Test
    @DisplayName("브라우저 User-Agent로 WEB 플랫폼 감지")
    fun `should detect WEB platform from browser user agent`() {
        // Given
        val request = createMockRequest(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) WebKit/537.36",
            requestURI = "/login"
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.WEB, result.platform) },
            { assertEquals(DetectionReason.USER_AGENT_PATTERN, result.detectionReason) },
            { assertTrue(result.confidence >= 0.5) }
        )
    }

    @Test
    @DisplayName("플랫폼 감지 우선순위 테스트 - 모바일이 브라우저보다 우선")
    fun `should prioritize MOBILE over WEB when both patterns match`() {
        // Given - 모바일과 웹 패턴이 모두 매칭되는 User-Agent
        val request = createMockRequest(
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) WebKit/605.1.15 Mobile",
            requestURI = "/login"
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then - 우선순위가 높은 MOBILE이 선택되어야 함
        assertEquals(IdpClient.Platform.MOBILE, result.platform)
    }

    @Test
    @DisplayName("플랫폼 감지 실패 시 기본 플랫폼 반환")
    fun `should return default platform when detection fails`() {
        // Given
        val request = createMockRequest(
            userAgent = "Unknown User Agent That Matches Nothing",
            requestURI = "/unknown-path"
        )

        // When
        val result = platformDetectionService.detectPlatformWithDetails(request)

        // Then
        assertAll(
            { assertEquals(IdpClient.Platform.DASHBOARD, result.platform) }, // 기본값
            { assertEquals(DetectionReason.DEFAULT_FALLBACK, result.detectionReason) },
            { assertEquals(0.0, result.confidence) }
        )
    }

    @Test
    @DisplayName("User-Agent만으로 플랫폼 감지")
    fun `should detect platform from user agent only`() {
        // Given
        val mobileUserAgent = "ShoplMobileApp/iOS/1.0.0"
        val apiUserAgent = "curl/7.68.0"

        // When
        val mobileResult = platformDetectionService.detectPlatformFromUserAgent(mobileUserAgent)
        val apiResult = platformDetectionService.detectPlatformFromUserAgent(apiUserAgent)

        // Then
        assertEquals(IdpClient.Platform.MOBILE, mobileResult)
        assertEquals(IdpClient.Platform.API, apiResult)
    }

    @Test
    @DisplayName("감지 통계 수집 확인")
    fun `should collect detection statistics`() {
        // Given
        val requests = listOf(
            createMockRequest(userAgent = "ShoplMobileApp/iOS/1.0.0"),
            createMockRequest(userAgent = "curl/7.68.0"),
            createMockRequest(userAgent = "Mozilla/5.0 Chrome/91.0", requestURI = "/admin/dashboard")
        )

        // When
        requests.forEach { platformDetectionService.detectPlatform(it) }
        val statistics = platformDetectionService.getDetectionStatistics()

        // Then
        assertAll(
            { assertTrue(statistics.totalDetections >= 3) },
            { assertNotNull(statistics.detectionsByPlatform[IdpClient.Platform.MOBILE]) },
            { assertNotNull(statistics.detectionsByPlatform[IdpClient.Platform.API]) },
            { assertNotNull(statistics.detectionsByPlatform[IdpClient.Platform.DASHBOARD]) },
            { assertTrue(statistics.averageDetectionTimeMs >= 0) }
        )
    }

    @Test
    @DisplayName("플랫폼 enum 확장성 테스트")
    fun `should handle all platform types correctly`() {
        // Given - 모든 플랫폼 타입에 대한 테스트 케이스
        val testCases = mapOf(
            IdpClient.Platform.MOBILE to createMockRequest(userAgent = "ShoplMobileApp/iOS/1.0.0"),
            IdpClient.Platform.API to createMockRequest(userAgent = "curl/7.68.0"),
            IdpClient.Platform.DASHBOARD to createMockRequest(
                userAgent = "Mozilla/5.0 Chrome/91.0",
                requestURI = "/admin/dashboard"
            ),
            IdpClient.Platform.WEB to createMockRequest(
                userAgent = "Mozilla/5.0 WebKit/537.36",
                requestURI = "/public"
            )
        )

        // When & Then
        testCases.forEach { (expectedPlatform, request) ->
            val result = platformDetectionService.detectPlatform(request)
            assertEquals(expectedPlatform, result, "Failed for platform: $expectedPlatform")
        }
    }


    /**
     * 테스트용 HttpServletRequest 모킹
     */
    private fun createMockRequest(
        userAgent: String? = null,
        requestURI: String = "/",
        customHeaders: Map<String, String> = emptyMap()
    ): HttpServletRequest {
        val request = mockk<HttpServletRequest>()

        every { request.getHeader("User-Agent") } returns userAgent
        every { request.requestURI } returns requestURI

        // 먼저 기본적으로 모든 헤더는 null
        every { request.getHeader(any()) } returns null

        // 그 다음에 User-Agent와 커스텀 헤더들을 덮어쓰기
        every { request.getHeader("User-Agent") } returns userAgent
        customHeaders.forEach { (headerName, headerValue) ->
            every { request.getHeader(headerName) } returns headerValue
        }

        return request
    }
}