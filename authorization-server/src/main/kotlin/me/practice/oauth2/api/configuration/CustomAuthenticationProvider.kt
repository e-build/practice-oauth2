package me.practice.oauth2.api.configuration

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationProvider : AuthenticationProvider {
	override fun authenticate(authentication: Authentication): Authentication {
		val username = authentication.name
		val rawPassword = authentication.credentials.toString()

		// 여기서 사용자 인증 로직 직접 구현 (예: DB, API, 등)
		val user = findUser(username)

		if (user == null || !passwordMatches(rawPassword, user.password)) {
			throw BadCredentialsException("Invalid username or password")
		}

		val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
		return UsernamePasswordAuthenticationToken(
			username,
			rawPassword,
			authorities
		)
	}

	override fun supports(authentication: Class<*>): Boolean {
		return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
	}

	data class User(
		val username: String,
		val password: String,
		val role: String
	)

	private fun findUser(username: String): User? {
		// 예시: DB 대신 하드코딩된 사용자
		return when (username) {
			"staff" -> User("staff", "staff", "STAFF")
			"leader" -> User("leader", "leader", "LEADER")
			else -> null
		}
	}

	private fun passwordMatches(raw: String, stored: String): Boolean {
		// 테스트용: 암호화 없이 문자열 비교
		return raw == stored
	}
}