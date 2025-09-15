package me.practice.oauth2.service.http

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * HTTP 요청 정보 추출 담당 클래스
 * 단일 책임: HttpServletRequest에서 클라이언트 정보 추출
 */
@Component
class HttpRequestInfoExtractor {

    /**
     * HTTP 요청에서 클라이언트의 실제 IP 주소를 추출합니다.
     *
     * 프록시나 로드밸런서를 고려하여 다음 순서로 IP 주소를 확인합니다:
     * 1. X-Forwarded-For 헤더 (첫 번째 IP)
     * 2. X-Real-IP 헤더
     * 3. HttpServletRequest.remoteAddr
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트 IP 주소 (null 가능)
     */
    fun extractIpAddress(request: HttpServletRequest?): String? {
        if (request == null) return null

        // X-Forwarded-For 헤더 확인 (프록시/로드밸런서 뒤에 있는 경우)
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",")[0].trim()
        }

        // X-Real-IP 헤더 확인
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp
        }

        // 기본 remote address
        return request.remoteAddr
    }

    /**
     * HTTP 요청에서 User-Agent 정보를 추출합니다.
     *
     * @param request HTTP 요청 객체
     * @return User-Agent 문자열 (null 가능)
     */
    fun extractUserAgent(request: HttpServletRequest?): String? {
        return request?.getHeader("User-Agent")
    }

    /**
     * HTTP 요청에서 클라이언트 정보를 한번에 추출합니다.
     *
     * @param request HTTP 요청 객체
     * @return 클라이언트 정보 객체
     */
    fun extract(request: HttpServletRequest?): LoginRequestSource {
        return LoginRequestSource(
            ipAddress = extractIpAddress(request),
            userAgent = extractUserAgent(request)
        )
    }
}

/**
 * 클라이언트 정보를 담는 데이터 클래스
 *
 * @property ipAddress 클라이언트 IP 주소
 * @property userAgent User-Agent 문자열
 */
data class LoginRequestSource(
    val ipAddress: String?,
    val userAgent: String?
)