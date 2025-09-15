package me.practice.oauth2.client.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class AdminPageController {

	/**
	 * 관리자 홈 페이지를 반환합니다 (HTML 템플릿)
	 */
	@GetMapping("/home")
	fun adminHome(@AuthenticationPrincipal jwt: Jwt?, model: Model): String {
		// JWT가 있으면 모델에 사용자 정보 추가 (Thymeleaf에서 사용)
		jwt?.let {
			model.addAttribute("username", it.getClaimAsString("username"))
			model.addAttribute("userRole", it.getClaimAsString("role"))
			model.addAttribute("companyId", it.getClaimAsString("account_id"))
		}
		return "admin/home"
	}


    /**
     * SSO 설정 목록 페이지
     */
    @GetMapping("/sso/configurations")
    fun ssoConfigurationList(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        model.addAttribute("username", jwt.getClaimAsString("username"))
        model.addAttribute("companyId", jwt.getClaimAsString("account_id"))
        return "admin/sso-list"
    }

    /**
     * SSO 설정 등록/수정 폼 페이지
     */
    @GetMapping("/sso/configurations/form")
    fun ssoConfigurationForm(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        model.addAttribute("username", jwt.getClaimAsString("username"))
        model.addAttribute("companyId", jwt.getClaimAsString("account_id"))
        return "admin/sso-form"
    }
}