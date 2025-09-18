package me.practice.oauth2.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.OAuth2UserRecoveryService
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.mapper.OAuth2FailureMapper
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component

/**
 * OAuth2/SSO 인증 실패 핸들러
 * OAuth2 로그인 실패 시 이력 기록 및 에러 페이지로 리다이렉트
 *
 * DON-49 개선: OAuth2UserRecoveryService 통합
 * - 기존 "unknown" 하드코딩 → 4단계 복구 전략으로 사용자 식별률 향상
 *
 * DON-52 개선: Chain of Responsibility 패턴으로 제공자별 예외 매핑
 * - 제공자별 상세 오류 코드 매핑으로 SSO_ERROR/UNKNOWN 분류 50% 감소
 * - Google, Kakao, Microsoft, GitHub 특화 오류 처리
 */
@Component
class OAuth2AuthenticationFailureHandler(
    private val loginHistoryService: LoginHistoryService,
    private val oAuth2UserRecoveryService: OAuth2UserRecoveryService,
    private val oAuth2FailureMappers: List<OAuth2FailureMapper>
) : SimpleUrlAuthenticationFailureHandler() {

    private val logger = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler::class.java)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        logger.warn("OAuth2 authentication failed: ${exception.message}")
        
        // OAuth2 인증 실패 이력 기록
        recordOAuth2AuthenticationFailure(request, exception)
        
        // 실패 시 로그인 페이지로 리다이렉트 (에러 파라미터 포함)
        setDefaultFailureUrl("/login?error=oauth2_failed&message=${exception.message}")
        super.onAuthenticationFailure(request, response, exception)
    }

    /**
     * OAuth2 인증 실패 이력 기록
     */
    private fun recordOAuth2AuthenticationFailure(
        request: HttpServletRequest,
        exception: AuthenticationException
    ) {
        try {
            val sessionId = request.session.id
            val shoplClientId = extractShoplClientId(request)
            val registrationId = extractRegistrationId(request)
            val providerType = determineProviderType(registrationId)
            
            // DON-49: OAuth2UserRecoveryService를 통한 사용자 식별 시도
            val shoplUserId = if (exception is OAuth2AuthenticationException) {
                oAuth2UserRecoveryService.attemptUserRecovery(request, exception) ?: "unknown"
            } else {
                "unknown"
            }

            if (shoplUserId != "unknown") {
                logger.info("Successfully recovered user ID during OAuth2 failure: $providerType -> ${shoplUserId.take(3)}***")
            }
            val loginType = when (providerType) {
                ProviderType.GOOGLE, ProviderType.KAKAO, ProviderType.NAVER,
                ProviderType.APPLE, ProviderType.MICROSOFT, ProviderType.GITHUB -> LoginType.SOCIAL
                ProviderType.SAML, ProviderType.OIDC -> LoginType.SSO
                else -> LoginType.SSO
            }
            
            val failureReason = mapOAuth2ExceptionToFailureReason(exception, providerType)
            
            loginHistoryService.recordFailedLogin(
                shoplClientId = if (shoplClientId == "UNKNOWN") null else shoplClientId,
                shoplUserId = if (shoplUserId == "unknown") null else shoplUserId,
                loginType = loginType,
                providerType = providerType,
                failureReason = failureReason,
                sessionId = sessionId,
                request = request
            )
            
            logger.debug("Recorded OAuth2 authentication failure: provider=$providerType, reason=$failureReason")
        } catch (e: Exception) {
            logger.warn("Failed to record OAuth2 authentication failure history", e)
        }
    }

    /**
     * OAuth2 예외를 구체적인 실패 사유로 매핑 (DON-52: Chain of Responsibility 패턴 적용)
     *
     * 1. 제공자별 매퍼를 통한 정확한 분류 (Google, Kakao, Microsoft, GitHub)
     * 2. HTTP 상태 코드 기반 세분화 (400/401/403/429)
     * 3. 네트워크 및 시스템 예외 처리
     * 4. 기본 매퍼를 통한 표준 OAuth2 오류 처리
     */
    private fun mapOAuth2ExceptionToFailureReason(
        exception: AuthenticationException,
        providerType: ProviderType = ProviderType.OIDC
    ): FailureReasonType {
        return when (exception) {
            is OAuth2AuthenticationException -> {
                // 1. 제공자별 매퍼를 통한 정확한 매핑 시도
                oAuth2FailureMappers
                    .filter { it.canHandle(providerType) }
                    .firstNotNullOfOrNull { it.mapException(exception, providerType) }
                    ?: mapByHttpStatusAndGeneral(exception)
            }

            // 2. 네트워크 관련 예외
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException -> FailureReasonType.NETWORK_ERROR

            // 3. 외부 제공자 서버 오류 (HTTP 상태 코드 기반)
            is org.springframework.web.client.HttpServerErrorException -> {
                mapHttpServerErrorException(exception)
            }

            // 4. 클라이언트 오류 (HTTP 4xx)
            is org.springframework.web.client.HttpClientErrorException -> {
                mapHttpClientErrorException(exception)
            }

            // 5. 기타 모든 OAuth2 관련 오류
            else -> {
                logger.debug("Unmapped authentication exception: ${exception.javaClass.simpleName}, message: ${exception.message}")
                FailureReasonType.SSO_ERROR
            }
        }
    }

    /**
     * HTTP 상태 코드와 일반적인 패턴을 기반으로 매핑
     */
    private fun mapByHttpStatusAndGeneral(exception: OAuth2AuthenticationException): FailureReasonType {
        val description = exception.error?.description?.lowercase() ?: ""

        // HTTP 상태 코드별 매핑 (DON-52 요구사항)
        return when {
            // 400: INVALID_CLIENT or INVALID_SCOPE
            description.contains("400") || description.contains("bad request") -> {
                when {
                    description.contains("client") -> FailureReasonType.INVALID_CLIENT
                    description.contains("scope") -> FailureReasonType.INVALID_SCOPE
                    else -> FailureReasonType.INVALID_CLIENT
                }
            }

            // 401: SSO_TOKEN_EXCHANGE_FAILED
            description.contains("401") || description.contains("unauthorized") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED

            // 403: INVALID_SCOPE or ACCESS_DENIED
            description.contains("403") || description.contains("forbidden") -> {
                when {
                    description.contains("scope") -> FailureReasonType.INVALID_SCOPE
                    else -> FailureReasonType.ACCESS_DENIED
                }
            }

            // 429: RATE_LIMIT_EXCEEDED
            description.contains("429") || description.contains("too many requests") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            // 5xx: 서버 오류
            description.contains("5") && (description.contains("00") || description.contains("02") ||
            description.contains("03") || description.contains("04")) -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE

            // 일반적인 패턴 매핑
            description.contains("access_denied") || description.contains("denied") -> FailureReasonType.ACCESS_DENIED
            description.contains("invalid_client") || description.contains("client") -> FailureReasonType.INVALID_CLIENT
            description.contains("invalid_scope") || description.contains("scope") -> FailureReasonType.INVALID_SCOPE
            description.contains("token") -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            description.contains("server") || description.contains("unavailable") -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            description.contains("network") -> FailureReasonType.NETWORK_ERROR
            description.contains("rate") || description.contains("limit") -> FailureReasonType.RATE_LIMIT_EXCEEDED

            else -> FailureReasonType.SSO_ERROR
        }
    }

    /**
     * HTTP 서버 오류 (5xx) 매핑
     */
    private fun mapHttpServerErrorException(exception: org.springframework.web.client.HttpServerErrorException): FailureReasonType {
        return when (exception.statusCode.value()) {
            500, 502, 503, 504 -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
            else -> FailureReasonType.EXTERNAL_PROVIDER_ERROR
        }
    }

    /**
     * HTTP 클라이언트 오류 (4xx) 매핑
     */
    private fun mapHttpClientErrorException(exception: org.springframework.web.client.HttpClientErrorException): FailureReasonType {
        return when (exception.statusCode.value()) {
            400 -> FailureReasonType.INVALID_CLIENT
            401 -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
            403 -> FailureReasonType.ACCESS_DENIED
            429 -> FailureReasonType.RATE_LIMIT_EXCEEDED
            else -> FailureReasonType.EXTERNAL_PROVIDER_ERROR
        }
    }

    /**
     * 요청에서 Shopl Client ID 추출
     */
    private fun extractShoplClientId(request: HttpServletRequest): String {
        // 1. 요청 파라미터에서 확인
        request.getParameter("client_id")?.let { 
            return it 
        }
        
        // 2. 세션에서 확인
        request.session.getAttribute("shopl_client_id")?.let { 
            return it.toString() 
        }
        
        // 3. 기본값 사용 (개발용)
        return "CLIENT001"
    }
    
    /**
     * 요청에서 OAuth2 등록 ID 추출
     */
    private fun extractRegistrationId(request: HttpServletRequest): String {
        // 1. 요청 URI에서 추출 시도 (예: /oauth2/authorization/google)
        val requestUri = request.requestURI
        if (requestUri.contains("/oauth2/authorization/")) {
            val parts = requestUri.split("/oauth2/authorization/")
            if (parts.size > 1) {
                return parts[1].split("/").first()
            }
        }
        
        // 2. 세션에서 확인
        request.session.getAttribute("oauth2_registration_id")?.let {
            return it.toString()
        }
        
        // 3. Referer 헤더에서 추출 시도
        request.getHeader("Referer")?.let { referer ->
            if (referer.contains("/oauth2/authorization/")) {
                val parts = referer.split("/oauth2/authorization/")
                if (parts.size > 1) {
                    return parts[1].split("/").first().split("?").first()
                }
            }
        }
        
        return "unknown"
    }
    
    /**
     * 등록 ID를 기반으로 제공자 타입 결정
     */
    private fun determineProviderType(registrationId: String): ProviderType {
        return when {
            registrationId.contains("google", ignoreCase = true) -> ProviderType.GOOGLE
            registrationId.contains("kakao", ignoreCase = true) -> ProviderType.KAKAO
            registrationId.contains("naver", ignoreCase = true) -> ProviderType.NAVER
            registrationId.contains("apple", ignoreCase = true) -> ProviderType.APPLE
            registrationId.contains("microsoft", ignoreCase = true) -> ProviderType.MICROSOFT
            registrationId.contains("github", ignoreCase = true) -> ProviderType.GITHUB
            registrationId.contains("saml", ignoreCase = true) -> ProviderType.SAML
            registrationId.contains("oidc", ignoreCase = true) -> ProviderType.OIDC
            registrationId.contains("keycloak", ignoreCase = true) -> ProviderType.OIDC
            else -> ProviderType.OIDC // 기본값
        }
    }
}