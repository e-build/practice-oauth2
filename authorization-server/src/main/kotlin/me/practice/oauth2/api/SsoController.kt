package me.practice.oauth2.api

import jakarta.servlet.http.HttpServletRequest
import me.practice.oauth2.entity.IoIdpShoplClientSsoSetting
import me.practice.oauth2.entity.SsoType
import me.practice.oauth2.service.CompositeClientRegistrationRepository
import me.practice.oauth2.service.DynamicClientRegistrationService
import me.practice.oauth2.service.SsoConfigurationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

/**
 * SSO 관련 컨트롤러
 * SSO 인증 플로우 및 제공자 관리
 */
@Controller
@RequestMapping("/sso")
class SsoController(
	private val ssoConfigurationService: SsoConfigurationService,
	private val dynamicClientRegistrationService: DynamicClientRegistrationService,
	private val compositeClientRegistrationRepository: CompositeClientRegistrationRepository
) {

    private val logger = LoggerFactory.getLogger(SsoController::class.java)

    /**
     * SSO 제공자 선택 페이지
     */
    @GetMapping("/providers")
    fun showProviders(
		@RequestParam(required = false) clientId: String?,
		@RequestParam(required = false) redirectUri: String?,
		model: Model
    ): String {
        val shoplClientId = clientId ?: "CLIENT001"

        // 해당 클라이언트의 SSO 설정 조회
        val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId)

        model.addAttribute("ssoSettings", ssoSettings)
        model.addAttribute("clientId", shoplClientId)
        model.addAttribute("redirectUri", redirectUri)

        // 사용 가능한 SSO 제공자 목록
        val availableProviders = if (ssoSettings != null) {
            listOf(createProviderInfo(ssoSettings))
        } else {
            emptyList()
        }

        model.addAttribute("providers", availableProviders)

        return "sso/providers" // SSO 제공자 선택 페이지 템플릿
    }

    /**
     * SSO 인증 시작
     */
    @GetMapping("/authenticate/{registrationId}")
    fun startAuthentication(
		@PathVariable registrationId: String,
		@RequestParam(required = false) clientId: String?,
		@RequestParam(required = false) redirectUri: String?,
		request: HttpServletRequest
    ): RedirectView {
        val shoplClientId = clientId ?: "CLIENT001"
        logger.info("Starting SSO authentication with registrationId: $registrationId, shoplClientId: $shoplClientId")

        // 세션에 shopl_client_id 저장
        request.session.setAttribute("shopl_client_id", shoplClientId)
        redirectUri?.let { request.session.setAttribute("redirect_uri", it) }

        val redirectUrl = "/oauth2/authorization/$registrationId"

        val redirectView = RedirectView(redirectUrl)
        redirectView.setExposeModelAttributes(false)

        return redirectView
    }

    /**
     * SSO 콜백 처리 (OAuth2 인증 완료 후)
     */
    @GetMapping("/callback")
    fun handleCallback(
		@RequestParam(required = false) code: String?,
		@RequestParam(required = false) state: String?,
		@RequestParam(required = false) error: String?,
		model: Model
    ): String {
        if (error != null) {
            logger.error("SSO callback error: $error")
            model.addAttribute("error", error)
            return "sso/error"
        }

        // 성공적으로 인증 완료
        logger.info("SSO callback successful with code: $code")

        // 기본적으로 인증 성공 핸들러가 처리하므로 여기는 백업용
        return "redirect:/oauth2/authorize"
    }

    /**
     * SSO 설정 관리 API
     */
    @GetMapping("/api/settings/{shoplClientId}")
    @ResponseBody
    fun getSsoSettings(@PathVariable shoplClientId: String): ResponseEntity<IoIdpShoplClientSsoSetting?> {
        val settings = ssoConfigurationService.getSsoSettings(shoplClientId)
        return ResponseEntity.ok(settings)
    }

    /**
     * 클라이언트의 사용 가능한 SSO 제공자 목록 API
     */
    @GetMapping("/api/providers/{shoplClientId}")
    @ResponseBody
    fun getAvailableProviders(@PathVariable shoplClientId: String): ResponseEntity<List<ProviderInfo>> {
        val providers = mutableListOf<ProviderInfo>()

        // 1. 정적 제공자 추가 (keycloak-acme 등)
        val staticRegistrationIds = compositeClientRegistrationRepository.getAllRegistrationIds()
        staticRegistrationIds.forEach { registrationId ->
            if (compositeClientRegistrationRepository.isStaticRegistration(registrationId)) {
                providers.add(
                    ProviderInfo(
                        registrationId = registrationId,
                        name = registrationId,
                        displayName = getDisplayNameForRegistrationId(registrationId),
                        type = SsoType.OIDC, // 정적 설정은 대부분 OIDC
                        enabled = true,
                        authUrl = "/oauth2/authorization/$registrationId"
                    )
                )
            }
        }

        // 2. 동적 제공자 추가 (데이터베이스 설정)
        val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId)
        if (ssoSettings != null) {
            providers.add(createProviderInfo(ssoSettings))
        }

        return ResponseEntity.ok(providers)
    }

    /**
     * SSO 제공자 등록 강제 실행 (관리용)
     */
    @PostMapping("/api/register/{shoplClientId}")
    @ResponseBody
    fun forceRegisterSsoProviders(@PathVariable shoplClientId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val registeredClients = dynamicClientRegistrationService.registerAllSsoProvidersForClient(shoplClientId)
            val response: Map<String, Any> = mapOf(
                "success" to true,
                "registeredCount" to registeredClients.size,
                "clients" to registeredClients.map { it.clientId }
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Failed to register SSO providers for client: $shoplClientId", e)
            val response: Map<String, Any> = mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            )
            ResponseEntity.badRequest().body(response)
        }
    }

    /**
     * SSO 제공자 정보 생성
     */
    private fun createProviderInfo(settings: IoIdpShoplClientSsoSetting): ProviderInfo {
        val registrationId = ssoConfigurationService.generateProviderRegistrationId(settings)

        return ProviderInfo(
            registrationId = registrationId,
            name = settings.ssoType.name,
            displayName = getDisplayName(settings.ssoType),
            type = settings.ssoType,
            enabled = when (settings.ssoType) {
                SsoType.OIDC -> ssoConfigurationService.isValidOidcConfiguration(settings)
                SsoType.SAML -> ssoConfigurationService.isValidSamlConfiguration(settings)
            },
            authUrl = "/sso/authenticate/$registrationId"
        )
    }

    /**
     * SSO 타입별 표시명 반환
     */
    private fun getDisplayName(ssoType: SsoType): String {
        return when (ssoType) {
            SsoType.OIDC -> "OpenID Connect"
            SsoType.SAML -> "SAML 2.0"
        }
    }

    /**
     * 등록 ID별 표시명 반환
     */
    private fun getDisplayNameForRegistrationId(registrationId: String): String {
        return when (registrationId.lowercase()) {
            "keycloak-acme" -> "Keycloak"
            "google" -> "Google"
            "github" -> "GitHub"
            "facebook" -> "Facebook"
            "microsoft" -> "Microsoft"
            "apple" -> "Apple"
            else -> registrationId.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /**
     * SSO 제공자 정보 DTO
     */
    data class ProviderInfo(
		val registrationId: String,
		val name: String,
		val displayName: String,
		val type: SsoType,
		val enabled: Boolean,
		val authUrl: String
    )
}