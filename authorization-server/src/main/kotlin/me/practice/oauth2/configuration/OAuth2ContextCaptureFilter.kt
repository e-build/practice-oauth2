package me.practice.oauth2.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.service.SessionUserContextManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * OAuth2 인증 시작 시점에서 사용자 컨텍스트를 세션에 저장하는 필터
 *
 * DON-49 핵심 이슈 해결:
 * OAuth2 로그인 시작 시점에서 컨텍스트 저장이 누락되어
 * 세션 기반 복구(1순위 전략)가 작동하지 않는 문제 해결
 *
 * 동작 원리:
 * 1. /oauth2/authorization/{providerId} 요청 감지
 * 2. 요청 파라미터나 Referer에서 사용자 식별 정보 추출
 * 3. SessionUserContextManager를 통해 세션에 저장
 * 4. OAuth2 실패 시 OAuth2UserRecoveryService가 이 정보를 활용하여 사용자 복구
 *
 * @author DON-49 OAuth2 사용자 식별 개선
 * @since 2.1.0
 */
@Component
class OAuth2ContextCaptureFilter(
    private val sessionUserContextManager: SessionUserContextManager
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(OAuth2ContextCaptureFilter::class.java)

    companion object {
        private const val OAUTH2_AUTHORIZATION_PATH = "/oauth2/authorization/"
        private const val LOGIN_PATH = "/login"

        // 사용자 식별에 사용할 수 있는 파라미터들
        private val USER_IDENTIFIER_PARAMS = listOf("username", "email", "user_id", "login_id")
        private val CLIENT_ID_PARAMS = listOf("client_id", "shopl_client_id", "company_id")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            if (isOAuth2AuthorizationRequest(request)) {
                captureUserContext(request)
            }
        } catch (e: Exception) {
            logger.warn("Failed to capture OAuth2 user context", e)
            // 컨텍스트 저장 실패가 OAuth2 인증 플로우를 방해하지 않도록 예외를 무시하고 계속 진행
        }

        filterChain.doFilter(request, response)
    }

    /**
     * OAuth2 인증 시작 요청인지 확인
     */
    private fun isOAuth2AuthorizationRequest(request: HttpServletRequest): Boolean {
        return request.requestURI.startsWith(OAUTH2_AUTHORIZATION_PATH)
    }

    /**
     * 요청에서 사용자 컨텍스트를 추출하고 세션에 저장
     */
    private fun captureUserContext(request: HttpServletRequest) {
        val providerId = extractProviderId(request)
        val providerType = mapProviderIdToType(providerId)

        // 사용자 식별 정보 추출 시도
        val userIdentifier = extractUserIdentifier(request)
        val clientId = extractClientId(request)

        if (userIdentifier != null && clientId != null) {
            sessionUserContextManager.saveMinimalUserContext(
                session = request.session,
                userIdentifier = userIdentifier,
                providerType = providerType,
                clientId = clientId
            )

            logger.info("Captured OAuth2 user context: provider=${providerType.name}, user=${maskUserIdentifier(userIdentifier)}, client=$clientId")
        } else {
            logger.debug("Could not extract complete user context: user=${userIdentifier != null}, client=${clientId != null}, provider=${providerType.name}")
        }
    }

    /**
     * URL에서 OAuth2 제공자 ID 추출
     * /oauth2/authorization/{providerId} → providerId
     */
    private fun extractProviderId(request: HttpServletRequest): String {
        val path = request.requestURI
        return path.substringAfter(OAUTH2_AUTHORIZATION_PATH).substringBefore("?")
    }

    /**
     * 제공자 ID를 ProviderType으로 매핑
     */
    private fun mapProviderIdToType(providerId: String): ProviderType {
        return when (providerId.lowercase()) {
            "google" -> ProviderType.GOOGLE
            "kakao" -> ProviderType.KAKAO
            "naver" -> ProviderType.NAVER
            "microsoft" -> ProviderType.MICROSOFT
            "github" -> ProviderType.GITHUB
            "apple" -> ProviderType.APPLE
            else -> ProviderType.OIDC // 기본값으로 OIDC 사용
        }
    }

    /**
     * 요청에서 사용자 식별 정보 추출
     *
     * 추출 전략:
     * 1. 요청 파라미터에서 사용자 식별 정보 찾기
     * 2. HTTP Referer 헤더에서 로그인 폼 정보 추출
     * 3. 세션에서 이전에 저장된 사용자 정보 활용
     */
    private fun extractUserIdentifier(request: HttpServletRequest): String? {
        // 1. 요청 파라미터에서 사용자 식별 정보 추출
        for (paramName in USER_IDENTIFIER_PARAMS) {
            request.getParameter(paramName)?.let { value ->
                if (value.isNotBlank()) {
                    logger.debug("Found user identifier in parameter '$paramName': ${maskUserIdentifier(value)}")
                    return value
                }
            }
        }

        // 2. HTTP Referer 헤더에서 로그인 폼 정보 추출
        val referer = request.getHeader("Referer")
        if (referer != null && referer.contains(LOGIN_PATH)) {
            val refererIdentifier = extractUserFromReferer(referer)
            if (refererIdentifier != null) {
                logger.debug("Found user identifier in referer: ${maskUserIdentifier(refererIdentifier)}")
                return refererIdentifier
            }
        }

        // 3. 세션에서 이전에 저장된 사용자 정보 활용
        val existingContext = sessionUserContextManager.getMinimalUserContext(request.session)
        if (existingContext != null) {
            logger.debug("Reusing existing session context: ${existingContext.providerType.name}")
            return existingContext.originalIdentifier
        }

        logger.debug("Could not extract user identifier from request")
        return null
    }

    /**
     * 요청에서 클라이언트 ID 추출
     */
    private fun extractClientId(request: HttpServletRequest): String? {
        // 1. 요청 파라미터에서 클라이언트 ID 추출
        for (paramName in CLIENT_ID_PARAMS) {
            request.getParameter(paramName)?.let { value ->
                if (value.isNotBlank()) {
                    logger.debug("Found client ID in parameter '$paramName': $value")
                    return value
                }
            }
        }

        // 2. HTTP Referer에서 클라이언트 정보 추출
        val referer = request.getHeader("Referer")
        if (referer != null) {
            val refererClientId = extractClientFromReferer(referer)
            if (refererClientId != null) {
                logger.debug("Found client ID in referer: $refererClientId")
                return refererClientId
            }
        }

        // 3. 기본값 - 일단 "default" 클라이언트 사용 (실제 구현에서는 더 정교한 로직 필요)
        logger.debug("Using default client ID")
        return "default"
    }

    /**
     * Referer URL에서 사용자 식별 정보 추출
     * 예: http://localhost:9000/login?username=test@example.com → test@example.com
     */
    private fun extractUserFromReferer(referer: String): String? {
        return try {
            val query = referer.substringAfter("?", "")
            if (query.isBlank()) return null

            val params = query.split("&")
                .mapNotNull { param ->
                    val (key, value) = param.split("=", limit = 2)
                        .let { it[0] to (it.getOrNull(1) ?: "") }
                    if (key in USER_IDENTIFIER_PARAMS && value.isNotBlank()) {
                        java.net.URLDecoder.decode(value, "UTF-8")
                    } else null
                }

            params.firstOrNull()
        } catch (e: Exception) {
            logger.debug("Failed to extract user from referer: $referer", e)
            null
        }
    }

    /**
     * Referer URL에서 클라이언트 ID 추출
     */
    private fun extractClientFromReferer(referer: String): String? {
        return try {
            val query = referer.substringAfter("?", "")
            if (query.isBlank()) return null

            val params = query.split("&")
                .mapNotNull { param ->
                    val (key, value) = param.split("=", limit = 2)
                        .let { it[0] to (it.getOrNull(1) ?: "") }
                    if (key in CLIENT_ID_PARAMS && value.isNotBlank()) {
                        java.net.URLDecoder.decode(value, "UTF-8")
                    } else null
                }

            params.firstOrNull()
        } catch (e: Exception) {
            logger.debug("Failed to extract client from referer: $referer", e)
            null
        }
    }

    /**
     * 로그용 사용자 식별자 마스킹
     */
    private fun maskUserIdentifier(identifier: String): String {
        return when {
            identifier.contains("@") -> {
                // 이메일: test@example.com → t***@example.com
                val (local, domain) = identifier.split("@", limit = 2)
                "${local.take(1)}***@$domain"
            }
            identifier.length > 3 -> {
                // 일반 문자열: testuser → t***r
                "${identifier.take(1)}***${identifier.takeLast(1)}"
            }
            else -> "***"
        }
    }
}