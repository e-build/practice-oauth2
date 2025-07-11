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
}