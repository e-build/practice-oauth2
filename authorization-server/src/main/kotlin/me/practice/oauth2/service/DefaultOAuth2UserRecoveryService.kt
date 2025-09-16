package me.practice.oauth2.service

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.ProviderType
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * OAuth2 사용자 복구 서비스 기본 구현체
 *
 * DON-49 핵심 구현:
 * OAuth2 인증 실패 시 사용자 식별률을 10%에서 60%로 향상시키는 4단계 복구 전략
 *
 * 1. 세션 기반 복구 (최우선) - 새로 추가된 핵심 기능
 * 2. OAuth2 예외 분석 기반 복구
 * 3. HTTP Referer 기반 복구
 * 4. Request 속성 기반 복구
 *
 * @author DON-49 OAuth2 사용자 식별 개선
 * @since 2.1.0
 */
@Service
class DefaultOAuth2UserRecoveryService(
    private val sessionUserContextManager: SessionUserContextManager
) : OAuth2UserRecoveryService {

    private val logger = LoggerFactory.getLogger(DefaultOAuth2UserRecoveryService::class.java)

    // 통계 추적을 위한 카운터들
    private val totalAttempts = AtomicLong(0)
    private val successfulRecoveries = AtomicLong(0)
    private val sessionBasedRecoveries = AtomicLong(0)
    private val exceptionBasedRecoveries = AtomicLong(0)
    private val refererBasedRecoveries = AtomicLong(0)
    private val requestBasedRecoveries = AtomicLong(0)

    private val lastResponseTime = AtomicReference<Double>(0.0)

    companion object {
        // 성능 임계값: 100ms 이내 응답 목표
        private const val PERFORMANCE_THRESHOLD_MS = 100L

        // 이메일 패턴 매칭
        private val EMAIL_PATTERN = Pattern.compile(
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
        )

        // 사용자 ID 패턴 매칭 (영문/숫자 조합)
        private val USER_ID_PATTERN = Pattern.compile(
            "(?:user[_-]?id|username|login[_-]?id|account[_-]?id)[\\s:=]+([a-zA-Z0-9._%+-]+)",
            Pattern.CASE_INSENSITIVE
        )
    }

    override fun attemptUserRecovery(
        request: HttpServletRequest,
        exception: OAuth2AuthenticationException
    ): String? {
        val startTime = System.currentTimeMillis()
        totalAttempts.incrementAndGet()

        try {
            // 1. 세션 기반 사용자 컨텍스트 복구 (최우선)
            val sessionRecovery = attemptSessionBasedRecovery(request)
            if (sessionRecovery != null) {
                sessionBasedRecoveries.incrementAndGet()
                successfulRecoveries.incrementAndGet()
                logRecoverySuccess("session-based", sessionRecovery)
                return sessionRecovery
            }

            // 2. OAuth2 예외 분석 기반 복구
            val exceptionRecovery = attemptExceptionBasedRecovery(exception)
            if (exceptionRecovery != null) {
                exceptionBasedRecoveries.incrementAndGet()
                successfulRecoveries.incrementAndGet()
                logRecoverySuccess("exception-based", exceptionRecovery)
                return exceptionRecovery
            }

            // 3. HTTP Referer 기반 복구
            val refererRecovery = attemptRefererBasedRecovery(request)
            if (refererRecovery != null) {
                refererBasedRecoveries.incrementAndGet()
                successfulRecoveries.incrementAndGet()
                logRecoverySuccess("referer-based", refererRecovery)
                return refererRecovery
            }

            // 4. Request 속성 기반 복구
            val requestRecovery = attemptRequestBasedRecovery(request)
            if (requestRecovery != null) {
                requestBasedRecoveries.incrementAndGet()
                successfulRecoveries.incrementAndGet()
                logRecoverySuccess("request-based", requestRecovery)
                return requestRecovery
            }

            logger.debug("All recovery strategies failed for OAuth2 authentication failure")
            return null

        } finally {
            val elapsedTime = System.currentTimeMillis() - startTime
            lastResponseTime.set(elapsedTime.toDouble())

            if (elapsedTime > PERFORMANCE_THRESHOLD_MS) {
                logger.warn("OAuth2 user recovery took longer than expected: {}ms (threshold: {}ms)",
                    elapsedTime, PERFORMANCE_THRESHOLD_MS)
            }
        }
    }

    /**
     * 1단계: 세션 기반 사용자 컨텍스트 복구 (DON-49 핵심 기능)
     *
     * OAuth2 시작 시점에서 저장된 사용자 컨텍스트를 활용하여 복구
     * 가장 높은 성공률과 정확도를 가진 복구 전략
     */
    private fun attemptSessionBasedRecovery(request: HttpServletRequest): String? {
        return try {
            val context = sessionUserContextManager.getMinimalUserContext(request.session)
            if (context != null) {
                val userId = sessionUserContextManager.deriveUserIdFromContext(context)
                if (userId != null) {
                    logger.debug("Session-based recovery successful: provider={}, userId={}",
                        context.providerType, maskUserIdentifier(userId))
                    userId
                } else null
            } else {
                logger.debug("No valid user context found in session")
                null
            }
        } catch (e: Exception) {
            logger.debug("Session-based recovery failed", e)
            null
        }
    }

    /**
     * 2단계: OAuth2 예외 분석 기반 복구
     *
     * OAuth2AuthenticationException의 메시지나 세부 정보에서
     * 사용자 식별 정보를 추출 시도
     */
    private fun attemptExceptionBasedRecovery(exception: OAuth2AuthenticationException): String? {
        return try {
            val errorMessage = exception.message ?: return null
            val errorDescription = exception.error?.description

            // 예외 메시지에서 이메일 패턴 찾기
            var matcher = EMAIL_PATTERN.matcher(errorMessage)
            if (matcher.find()) {
                return extractUserIdFromEmail(matcher.group(1))
            }

            // 오류 설명에서 이메일 패턴 찾기
            if (errorDescription != null) {
                matcher = EMAIL_PATTERN.matcher(errorDescription)
                if (matcher.find()) {
                    return extractUserIdFromEmail(matcher.group(1))
                }

                // 사용자 ID 패턴 찾기
                val userIdMatcher = USER_ID_PATTERN.matcher(errorDescription)
                if (userIdMatcher.find()) {
                    return userIdMatcher.group(1)
                }
            }

            logger.debug("No user identifier found in exception: {}", errorMessage)
            null
        } catch (e: Exception) {
            logger.debug("Exception-based recovery failed", e)
            null
        }
    }

    /**
     * 3단계: HTTP Referer 기반 복구
     *
     * HTTP Referer 헤더에서 로그인 폼 정보나 이전 페이지 컨텍스트를 분석하여
     * 사용자 식별 정보 추출 시도
     */
    private fun attemptRefererBasedRecovery(request: HttpServletRequest): String? {
        return try {
            val referer = request.getHeader("Referer") ?: return null

            // Referer URL에서 쿼리 파라미터 분석
            if (referer.contains("?")) {
                val query = referer.substringAfter("?")
                val params = parseQueryParameters(query)

                // 사용자 식별 가능한 파라미터들 확인
                val userParams = listOf("username", "email", "user_id", "login_id")
                for (param in userParams) {
                    params[param]?.let { value ->
                        if (value.isNotBlank()) {
                            return if (value.contains("@")) {
                                extractUserIdFromEmail(value)
                            } else {
                                value
                            }
                        }
                    }
                }
            }

            // Referer URL 자체에서 사용자 정보 패턴 찾기
            val matcher = EMAIL_PATTERN.matcher(referer)
            if (matcher.find()) {
                return extractUserIdFromEmail(matcher.group(1))
            }

            logger.debug("No user identifier found in referer: {}", referer)
            null
        } catch (e: Exception) {
            logger.debug("Referer-based recovery failed", e)
            null
        }
    }

    /**
     * 4단계: Request 속성 기반 복구
     *
     * HTTP Request의 속성이나 파라미터에서 부분적으로
     * 남아있을 수 있는 사용자 정보 추출 시도
     */
    private fun attemptRequestBasedRecovery(request: HttpServletRequest): String? {
        return try {
            // Request 파라미터에서 사용자 정보 찾기
            val userParams = listOf("username", "email", "user_id", "login_id", "account_id")
            for (param in userParams) {
                request.getParameter(param)?.let { value ->
                    if (value.isNotBlank()) {
                        return if (value.contains("@")) {
                            extractUserIdFromEmail(value)
                        } else {
                            value
                        }
                    }
                }
            }

            // Request 헤더에서 사용자 정보 찾기
            val userHeaders = listOf("X-User-Id", "X-Username", "X-User-Email")
            for (header in userHeaders) {
                request.getHeader(header)?.let { value ->
                    if (value.isNotBlank()) {
                        return if (value.contains("@")) {
                            extractUserIdFromEmail(value)
                        } else {
                            value
                        }
                    }
                }
            }

            logger.debug("No user identifier found in request attributes")
            null
        } catch (e: Exception) {
            logger.debug("Request-based recovery failed", e)
            null
        }
    }

    override fun getRecoveryStatistics(): OAuth2UserRecoveryStatistics {
        val total = totalAttempts.get()
        val successful = successfulRecoveries.get()

        return OAuth2UserRecoveryStatistics(
            totalAttempts = total,
            successfulRecoveries = successful,
            sessionBasedRecoveries = sessionBasedRecoveries.get(),
            exceptionBasedRecoveries = exceptionBasedRecoveries.get(),
            refererBasedRecoveries = refererBasedRecoveries.get(),
            requestBasedRecoveries = requestBasedRecoveries.get(),
            averageResponseTimeMs = lastResponseTime.get(),
            successRate = if (total > 0) successful.toDouble() / total.toDouble() else 0.0
        )
    }

    /**
     * 이메일에서 사용자 ID 추출
     * 예: test@example.com → test
     */
    private fun extractUserIdFromEmail(email: String): String {
        return email.substringBefore("@")
    }

    /**
     * 쿼리 문자열을 파라미터 맵으로 파싱
     */
    private fun parseQueryParameters(query: String): Map<String, String> {
        return try {
            query.split("&")
                .mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) {
                        java.net.URLDecoder.decode(parts[0], "UTF-8") to
                        java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 복구 성공 로그
     */
    private fun logRecoverySuccess(method: String, userId: String) {
        logger.info("OAuth2 user recovery successful: method={}, userId={}, statistics={}",
            method, maskUserIdentifier(userId), getRecoveryStatistics())
    }

    /**
     * 로그용 사용자 식별자 마스킹
     */
    private fun maskUserIdentifier(identifier: String): String {
        return when {
            identifier.length <= 3 -> "***"
            identifier.contains("@") -> "${identifier.take(1)}***@${identifier.substringAfter("@")}"
            else -> "${identifier.take(1)}***${identifier.takeLast(1)}"
        }
    }
}