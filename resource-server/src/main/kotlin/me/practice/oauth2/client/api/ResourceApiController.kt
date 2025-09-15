package me.practice.oauth2.client.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/api/admin")
class ResourceApiController {

	/**
	 * 관리자 홈 API - JWT 클레임 정보를 JSON으로 반환합니다
	 * JavaScript에서 호출하는 API 엔드포인트
	 */
	@GetMapping("/home")
	@ResponseBody
	fun adminHomeApi(@AuthenticationPrincipal jwt: Jwt?): Map<String, Any?> {
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

	/**
	 * 관리자 권한 확인 API
	 */
	@GetMapping("/check-permission")
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