package me.practice.oauth2.client.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class HomeController {

    /**
     * 대시보드 페이지를 반환합니다 (HTML 템플릿)
     */
    @GetMapping("/dashboard")
    fun dashboard(): String {
        return "dashboard"
    }

    /**
     * 대시보드 API - JWT 클레임 정보를 JSON으로 반환합니다
     * JavaScript에서 호출하는 API 엔드포인트
     */
    @GetMapping("/api/dashboard")
    @ResponseBody
    fun dashboardApi(@AuthenticationPrincipal jwt: Jwt?): Map<String, Any?> {
        if (jwt == null) {
            return mapOf(
                "authenticated" to false,
                "error" to "No authentication token found"
            )
        }

        return mapOf(
            "authenticated" to true,
            // 기본 사용자 정보
            "username" to jwt.getClaimAsString("username"),
            "role" to jwt.getClaimAsString("role"),
            "user_id" to jwt.getClaimAsString("user_id"),
            
            // shopl 관련 정보
            "account_id" to jwt.getClaimAsString("account_id"),
            "shopl_client_id" to jwt.getClaimAsString("shopl_client_id"),
            "shopl_user_id" to jwt.getClaimAsString("shopl_user_id"),
            "email" to jwt.getClaimAsString("email"),
            "name" to jwt.getClaimAsString("name"),
            
            // JWT 표준 클레임
            "sub" to jwt.subject,
            "iss" to jwt.issuer?.toString(),
            "aud" to jwt.audience,
            "exp" to jwt.expiresAt?.epochSecond,
            "iat" to jwt.issuedAt?.epochSecond,
            "scope" to jwt.getClaimAsString("scope"),
            
            // 전체 클레임 정보 (디버깅용)
            "all_claims" to jwt.claims
        )
    }
}