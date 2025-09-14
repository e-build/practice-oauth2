package me.practice.oauth2.client.api

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
class SsoAdminController {

    /**
     * 관리자 인증 대시보드 (기존 dashboard와 구분)
     */
    @GetMapping("/auth-dashboard")
    fun authDashboard(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        model.addAttribute("username", jwt.getClaimAsString("username"))
        model.addAttribute("userRole", jwt.getClaimAsString("role"))
        model.addAttribute("companyId", jwt.getClaimAsString("account_id"))
        return "admin/auth-dashboard"
    }

    /**
     * SSO 관리 메인 페이지 (레거시 호환)
     */
    @GetMapping("/sso")
    fun ssoMain(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        // auth-dashboard로 리다이렉트
        return "redirect:/admin/auth-dashboard"
    }

    /**
     * SSO 설정 목록 페이지
     */
    @GetMapping("/configurations")
    fun ssoConfigurationList(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        model.addAttribute("username", jwt.getClaimAsString("username"))
        model.addAttribute("companyId", jwt.getClaimAsString("account_id"))
        return "admin/sso-list"
    }

    /**
     * SSO 설정 등록/수정 폼 페이지
     */
    @GetMapping("/configurations/form")
    fun ssoConfigurationForm(@AuthenticationPrincipal jwt: Jwt, model: Model): String {
        model.addAttribute("username", jwt.getClaimAsString("username"))
        model.addAttribute("companyId", jwt.getClaimAsString("account_id"))
        return "admin/sso-form"
    }

    /**
     * 관리자 권한 확인 API
     */
    @GetMapping("/api/check-permission")
    @ResponseBody
    fun checkAdminPermission(@AuthenticationPrincipal jwt: Jwt?): Map<String, Any> {
        if (jwt == null) {
            return mapOf(
                "hasPermission" to false,
                "error" to "No authentication token found"
            )
        }

        val role = jwt.getClaimAsString("role")
        val hasAdminRole = role == "ADMIN"

        return mapOf(
            "hasPermission" to hasAdminRole,
            "username" to jwt.getClaimAsString("username"),
            "role" to role,
            "companyId" to jwt.getClaimAsString("account_id")
        )
    }
}