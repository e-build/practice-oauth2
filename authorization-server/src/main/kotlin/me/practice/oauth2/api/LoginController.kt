package me.practice.oauth2.api

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class LoginController {
	/**
	 * 커스텀 로그인 페이지를 반환합니다.
	 * @param error 로그인 실패 시 'error' 파라미터가 전달됩니다.
	 * @param model 뷰에 데이터를 전달하기 위한 모델 객체
	 * @return 로그인 페이지 템플릿 이름
	 */
	@GetMapping("/login")
	fun login(
		@RequestParam(value = "error", required = false) error: String?,
		model: Model,
	): String {
		// 로그인 실패 시 에러 메시지를 모델에 추가하여 뷰에 전달
		if (error != null) {
			model.addAttribute("errorMessage", "아이디 또는 비밀번호가 올바르지 않습니다.")
		}
		return "login-custom" // src/main/resources/templates/login.html 을 반환
	}
}

