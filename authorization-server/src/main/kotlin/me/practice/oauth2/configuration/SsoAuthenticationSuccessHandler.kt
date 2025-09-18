package me.practice.oauth2.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.practice.oauth2.entity.ProviderType
import me.practice.oauth2.entity.LoginType
import me.practice.oauth2.service.UserProvisioningService
import me.practice.oauth2.service.LoginHistoryService
import me.practice.oauth2.domain.IdpClient
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.savedrequest.RequestCache
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.stereotype.Component

/**
 * SSO 인증 성공 핸들러
 * OAuth2/OIDC 로그인 성공 시 사용자 프로비저닝 및 세션 설정 처리
 */
@Component
class SsoAuthenticationSuccessHandler(
    private val userProvisioningService: UserProvisioningService,
    private val loginHistoryService: LoginHistoryService
) : SimpleUrlAuthenticationSuccessHandler() {

    private val logger = LoggerFactory.getLogger(SsoAuthenticationSuccessHandler::class.java)
    private val requestCache: RequestCache = HttpSessionRequestCache()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        if (authentication is OAuth2AuthenticationToken) {
            try {
                handleOAuth2Authentication(authentication, request)
            } catch (e: Exception) {
                logger.error("Error handling OAuth2 authentication success", e)
                // 에러 발생 시 에러 페이지로 리다이렉트
                setDefaultTargetUrl("/error?message=sso_authentication_failed")
                super.onAuthenticationSuccess(request, response, authentication)
                return
            }
        }

        // 성공 시 기본 리다이렉트 URL로 이동
        setDefaultTargetUrl(getDefaultTargetUrl(request))
        super.onAuthenticationSuccess(request, response, authentication)
    }

    /**
     * OAuth2 인증 처리
     */
    private fun handleOAuth2Authentication(authentication: OAuth2AuthenticationToken, request: HttpServletRequest) {
        val registrationId = authentication.authorizedClientRegistrationId
        val principal = authentication.principal
        
        // 클라이언트 ID 추출 (요청 파라미터 또는 세션에서)
        val shoplClientId = extractShoplClientId(request)
        
        logger.info("Processing OAuth2 authentication - registrationId: $registrationId, shoplClientId: $shoplClientId")

        // 제공자 타입 결정
        val providerType = determineProviderType(registrationId)
        
        // 사용자 프로비저닝
        val account = when (principal) {
            is OidcUser -> {
                userProvisioningService.provisionOidcUser(principal, shoplClientId, registrationId)
            }
            is OAuth2User -> {
                userProvisioningService.provisionUser(principal, shoplClientId, providerType, registrationId)
            }
            else -> {
                throw IllegalArgumentException("Unsupported principal type: ${principal.javaClass}")
            }
        }

        // 세션에 계정 정보 저장
        request.session.setAttribute("authenticated_account", account)
        request.session.setAttribute("sso_provider", providerType.name)
        request.session.setAttribute("registration_id", registrationId)

        // SSO 로그인 이력 기록
        recordSsoLoginHistory(account, providerType, registrationId, request)

        logger.info("Successfully provisioned user: ${account.id} via provider: $providerType")
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
        
        // 3. Referer 헤더에서 추출 시도
        request.getHeader("Referer")?.let { referer ->
            extractClientIdFromReferer(referer)?.let { 
                return it 
            }
        }
        
        // 4. 기본값 사용 (개발용)
        return "CLIENT001"
    }

    /**
     * Referer 헤더에서 클라이언트 ID 추출
     */
    private fun extractClientIdFromReferer(referer: String): String? {
        return try {
            val uri = java.net.URI(referer)
            val params = uri.query?.split("&")?.associate { param ->
                val parts = param.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            params?.get("client_id")
        } catch (e: Exception) {
            null
        }
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

    /**
     * 기본 타겟 URL 결정
     */
    private fun getDefaultTargetUrl(request: HttpServletRequest): String {
        // 1. RequestCache에서 저장된 요청 복원
        val savedRequest = requestCache.getRequest(request, null)
        if (savedRequest != null) {
            val originalUrl = savedRequest.redirectUrl
            logger.debug("Restoring saved request URL: $originalUrl")
            return originalUrl
        }
        
        // 2. 세션에 저장된 OAuth2 파라미터로 authorization URL 구성
        val clientId = request.session.getAttribute("oauth2_client_id") as? String
        val redirectUri = request.session.getAttribute("oauth2_redirect_uri") as? String
        val scope = request.session.getAttribute("oauth2_scope") as? String
        val state = request.session.getAttribute("oauth2_state") as? String
        
        if (clientId != null && redirectUri != null) {
            val authUrl = buildString {
                append("/oauth2/authorize")
                append("?response_type=code")
                append("&client_id=").append(clientId)
                append("&redirect_uri=").append(java.net.URLEncoder.encode(redirectUri, "UTF-8"))
                if (!scope.isNullOrBlank()) {
                    append("&scope=").append(java.net.URLEncoder.encode(scope, "UTF-8"))
                }
                if (!state.isNullOrBlank()) {
                    append("&state=").append(state)
                }
            }
            logger.debug("Built OAuth2 authorization URL: $authUrl")
            return authUrl
        }
        
        // 3. 요청 파라미터에서 직접 구성
        val paramClientId = request.getParameter("client_id")
        val paramRedirectUri = request.getParameter("redirect_uri")
        val paramScope = request.getParameter("scope")
        val paramState = request.getParameter("state")
        
        if (paramClientId != null && paramRedirectUri != null) {
            val authUrl = buildString {
                append("/oauth2/authorize")
                append("?response_type=code")
                append("&client_id=").append(paramClientId)
                append("&redirect_uri=").append(java.net.URLEncoder.encode(paramRedirectUri, "UTF-8"))
                if (!paramScope.isNullOrBlank()) {
                    append("&scope=").append(java.net.URLEncoder.encode(paramScope, "UTF-8"))
                }
                if (!paramState.isNullOrBlank()) {
                    append("&state=").append(paramState)
                }
            }
            logger.debug("Built OAuth2 authorization URL from params: $authUrl")
            return authUrl
        }
        
        // 4. 기본 대시보드로 (환경변수 사용)
        val resourceServerBaseUrl = System.getProperty("RESOURCE_SERVER_BASE_URL")
            ?: System.getenv("RESOURCE_SERVER_BASE_URL")
            ?: "http://localhost:9001"
        logger.debug("No OAuth2 parameters found, redirecting to admin auth-dashboard: $resourceServerBaseUrl")
        return "$resourceServerBaseUrl/admin/home"
    }

    /**
     * SSO 로그인 이력 기록
     */
    private fun recordSsoLoginHistory(
        account: me.practice.oauth2.entity.IoIdpAccount,
        providerType: ProviderType,
        registrationId: String,
        request: HttpServletRequest
    ) {
        try {
            val sessionId = request.session.id
            val loginType = when (providerType) {
                ProviderType.GOOGLE, ProviderType.KAKAO, ProviderType.NAVER, 
                ProviderType.APPLE, ProviderType.MICROSOFT, ProviderType.GITHUB -> LoginType.SOCIAL
                ProviderType.SAML, ProviderType.OIDC -> LoginType.SSO
                else -> LoginType.SSO
            }
            
            loginHistoryService.recordSuccessfulLogin(
                shoplClientId = account.shoplClientId,
                shoplUserId = account.shoplUserId,
                loginType = loginType,
                provider = providerType.name,
                sessionId = sessionId,
                request = request
            )
            
            logger.debug("Recorded SSO login history for user: ${account.shoplUserId}, provider: $providerType")
        } catch (e: Exception) {
            // 로그인 이력 기록 실패가 SSO 인증 자체를 방해하지 않도록 예외 처리
            logger.warn("Failed to record SSO login history for user: ${account.shoplUserId}, provider: $providerType", e)
        }
    }
}