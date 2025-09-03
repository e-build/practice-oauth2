package me.practice.oauth2.client.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping


@Controller
class HomeController {


	/**
	 * 보호된 대시보드 페이지를 반환합니다.
	 * 인증된 사용자의 JWT 토큰에서 정보를 추출하여 뷰에 전달합니다.
	 * @param model 뷰에 데이터를 전달하기 위한 모델 객체
	 * @param jwt 인증된 사용자의 JWT 객체
	 * @return 대시보드 페이지 템플릿 이름
	 */
	@GetMapping("/dashboard")
	fun dashboard(
		model: Model,
		@AuthenticationPrincipal jwt: Jwt?,
	): String {
		model.addAttribute("username", jwt?.getClaimAsString("username") ?: "")
		model.addAttribute("role", jwt?.getClaimAsString("role") ?: "")
		model.addAttribute("userId", jwt?.getClaimAsString("user_id") ?: "")
		return "dashboard"
	}

}