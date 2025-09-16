package me.practice.oauth2.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.entity.FailureReasonType
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.service.OAuth2UserRecoveryService
import me.practice.oauth2.domain.IdpClient
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
 */
@Component
class OAuth2AuthenticationFailureHandler(
    private val loginHistoryService: LoginHistoryService,
    private val oAuth2UserRecoveryService: OAuth2UserRecoveryService
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
                logger.info("Successfully recovered user ID during OAuth2 failure: {} -> {}",
                    providerType, shoplUserId.take(3) + "***")
            }
            val loginType = when (providerType) {
                ProviderType.GOOGLE, ProviderType.KAKAO, ProviderType.NAVER,
                ProviderType.APPLE, ProviderType.MICROSOFT, ProviderType.GITHUB -> LoginType.SOCIAL
                ProviderType.SAML, ProviderType.OIDC -> LoginType.SSO
                else -> LoginType.SSO
            }
            
            val failureReason = mapOAuth2ExceptionToFailureReason(exception)
            
            loginHistoryService.recordFailedLogin(
                shoplClientId = shoplClientId,
                shoplUserId = shoplUserId,
                platform = IdpClient.Platform.DASHBOARD,
                loginType = loginType,
                provider = providerType.name,
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
     * OAuth2 예외를 구체적인 실패 사유로 매핑
     */
    private fun mapOAuth2ExceptionToFailureReason(exception: AuthenticationException): FailureReasonType {
        return when (exception) {
            is OAuth2AuthenticationException -> {
                when (exception.error?.errorCode) {
                    "invalid_client" -> FailureReasonType.INVALID_CLIENT
                    "invalid_scope" -> FailureReasonType.INVALID_SCOPE
                    "unsupported_grant_type" -> FailureReasonType.UNSUPPORTED_GRANT_TYPE
                    "server_error" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    "temporarily_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    "service_unavailable" -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    "invalid_grant" -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
                    "network_error" -> FailureReasonType.NETWORK_ERROR
                    else -> {
                        // HTTP 상태 코드나 description으로 추가 분류
                        when {
                            exception.error?.description?.contains("invalid_client") == true -> FailureReasonType.INVALID_CLIENT
                            exception.error?.description?.contains("invalid_scope") == true -> FailureReasonType.INVALID_SCOPE
                            exception.error?.description?.contains("server error") == true -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                            exception.error?.description?.contains("service unavailable") == true -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                            exception.error?.description?.contains("token exchange") == true -> FailureReasonType.SSO_TOKEN_EXCHANGE_FAILED
                            exception.error?.description?.contains("network") == true -> FailureReasonType.NETWORK_ERROR
                            else -> FailureReasonType.SSO_ERROR
                        }
                    }
                }
            }
            // 네트워크 관련 예외
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException -> FailureReasonType.NETWORK_ERROR

            // 외부 제공자 서버 오류
            is org.springframework.web.client.HttpServerErrorException -> {
                when (exception.statusCode.value()) {
                    500, 502, 503, 504 -> FailureReasonType.SSO_PROVIDER_UNAVAILABLE
                    else -> FailureReasonType.EXTERNAL_PROVIDER_ERROR
                }
            }

            // 기타 모든 OAuth2 관련 오류
            else -> FailureReasonType.SSO_ERROR
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