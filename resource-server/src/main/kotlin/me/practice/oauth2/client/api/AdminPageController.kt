package me.practice.oauth2.client.api

import me.practice.oauth2.client.configuration.AppProperties
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import jakarta.servlet.http.HttpServletRequest

@Controller
@RequestMapping("/admin")
class AdminPageController(
	private val appProperties: AppProperties
) {

	/**
	 * 관리자 홈 페이지를 반환합니다 (HTML 템플릿만 제공, 데이터는 프론트엔드에서 처리)
	 */
	@GetMapping("/home")
	fun adminHome(model: Model, request: HttpServletRequest): String {
		val currentServerUrl = "${request.scheme}://${request.serverName}:${request.serverPort}"
		model.addAttribute("authServerBaseUrl", appProperties.authorizationServer.baseUrl)
		model.addAttribute("resourceServerBaseUrl", currentServerUrl)
		return "admin/home"
	}

    /**
     * SSO 설정 목록 페이지 (HTML 템플릿만 제공)
     */
    @GetMapping("/sso/configurations")
    fun ssoConfigurationList(): String {
        return "admin/sso-list"
    }

    /**
     * SSO 설정 등록/수정 폼 페이지 (HTML 템플릿만 제공)
     */
    @GetMapping("/sso/configurations/form")
    fun ssoConfigurationForm(): String {
        return "admin/sso-form"
    }
}