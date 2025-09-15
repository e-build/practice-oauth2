package me.practice.oauth2.service.http

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

class HttpRequestInfoExtractorTest {

    private lateinit var extractor: HttpRequestInfoExtractor
    private lateinit var mockRequest: HttpServletRequest

    @BeforeEach
    fun setUp() {
        extractor = HttpRequestInfoExtractor()
        mockRequest = mock(HttpServletRequest::class.java)
    }

    @Test
    fun `X-Forwarded-For 헤더에서 IP 주소 추출 성공`() {
        // given
        whenever(mockRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1")
        whenever(mockRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.2")
        whenever(mockRequest.remoteAddr).thenReturn("127.0.0.1")

        // when
        val ipAddress = extractor.extractIpAddress(mockRequest)

        // then
        assertEquals("192.168.1.100", ipAddress)
    }

    @Test
    fun `X-Real-IP 헤더에서 IP 주소 추출 성공`() {
        // given
        whenever(mockRequest.getHeader("X-Forwarded-For")).thenReturn("")
        whenever(mockRequest.getHeader("X-Real-IP")).thenReturn("10.0.0.2")
        whenever(mockRequest.remoteAddr).thenReturn("127.0.0.1")

        // when
        val ipAddress = extractor.extractIpAddress(mockRequest)

        // then
        assertEquals("10.0.0.2", ipAddress)
    }

    @Test
    fun `remoteAddr에서 IP 주소 추출 성공`() {
        // given
        whenever(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null)
        whenever(mockRequest.getHeader("X-Real-IP")).thenReturn(null)
        whenever(mockRequest.remoteAddr).thenReturn("127.0.0.1")

        // when
        val ipAddress = extractor.extractIpAddress(mockRequest)

        // then
        assertEquals("127.0.0.1", ipAddress)
    }

    @Test
    fun `request가 null인 경우 null 반환`() {
        // when
        val ipAddress = extractor.extractIpAddress(null)

        // then
        assertNull(ipAddress)
    }

    @Test
    fun `User-Agent 헤더 추출 성공`() {
        // given
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        whenever(mockRequest.getHeader("User-Agent")).thenReturn(userAgent)

        // when
        val extractedUserAgent = extractor.extractUserAgent(mockRequest)

        // then
        assertEquals(userAgent, extractedUserAgent)
    }

    @Test
    fun `User-Agent가 없는 경우 null 반환`() {
        // given
        whenever(mockRequest.getHeader("User-Agent")).thenReturn(null)

        // when
        val userAgent = extractor.extractUserAgent(mockRequest)

        // then
        assertNull(userAgent)
    }

    @Test
    fun `클라이언트 정보를 한번에 추출`() {
        // given
        val expectedIp = "192.168.1.100"
        val expectedUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
        whenever(mockRequest.getHeader("X-Forwarded-For")).thenReturn("$expectedIp, 10.0.0.1")
        whenever(mockRequest.getHeader("User-Agent")).thenReturn(expectedUserAgent)

        // when
        val clientInfo = extractor.extractClientInfo(mockRequest)

        // then
        assertEquals(expectedIp, clientInfo.ipAddress)
        assertEquals(expectedUserAgent, clientInfo.userAgent)
    }

    @Test
    fun `null request로 클라이언트 정보 추출 시 모든 필드가 null`() {
        // when
        val clientInfo = extractor.extractClientInfo(null)

        // then
        assertNull(clientInfo.ipAddress)
        assertNull(clientInfo.userAgent)
    }
}