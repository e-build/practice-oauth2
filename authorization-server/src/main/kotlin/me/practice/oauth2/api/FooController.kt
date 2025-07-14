package me.practice.oauth2.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FooController {

	@GetMapping("/authorized")
	fun foo(): ResponseEntity<String> {
		return ResponseEntity.ok("authorized")
	}

//	@GetMapping("/")
//	fun home(authentication: Authentication?): String {
//		return if (authentication?.isAuthenticated == true) {
//			"redirect:/swagger-ui/index.html"  // 인증된 사용자는 Swagger로
//		} else {
//			"redirect:/login"  // 인증되지 않은 사용자는 로그인으로
//		}
//	}
}