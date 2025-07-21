package me.practice.oauth2.client.api.configuration

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider {

	/**
	 * JWT 토큰에서 사용자 ID 추출
	 */
	fun getUserIdFromToken(jwt: Jwt): Long? {
		return try {
			jwt.getClaim<Any>("user_id")?.toString()?.toLongOrNull()
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * JWT 토큰에서 사용자명 추출
	 */
	fun getUsernameFromToken(jwt: Jwt): String? {
		return try {
			jwt.getClaim<String>("username") ?: jwt.subject
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * JWT 토큰에서 역할 정보 추출
	 */
	fun getRoleFromToken(jwt: Jwt): String? {
		return try {
			jwt.getClaim<String>("role")
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * JWT 토큰에서 스코프 목록 추출
	 */
	fun getScopesFromToken(jwt: Jwt): List<String> {
		return try {
			jwt.getClaimAsStringList("scope") ?: emptyList()
		} catch (e: Exception) {
			emptyList()
		}
	}

	/**
	 * JWT 토큰이 특정 스코프를 가지고 있는지 확인
	 */
	fun hasScope(jwt: Jwt, scope: String): Boolean {
		return getScopesFromToken(jwt).contains(scope)
	}

	/**
	 * JWT 토큰이 특정 역할을 가지고 있는지 확인
	 */
	fun hasRole(jwt: Jwt, role: String): Boolean {
		return getRoleFromToken(jwt) == role
	}

	/**
	 * 현재 인증된 사용자의 JWT 토큰 정보를 가져오는 유틸리티 메서드
	 */
	fun getCurrentUserJwt(): Jwt? {
		return try {
			val authentication = SecurityContextHolder.getContext().authentication
			if (authentication is JwtAuthenticationToken) {
				authentication.token
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * 현재 인증된 사용자의 ID 추출
	 */
	fun getCurrentUserId(): Long? {
		return getCurrentUserJwt()?.let { getUserIdFromToken(it) }
	}

	/**
	 * 현재 인증된 사용자명 추출
	 */
	fun getCurrentUsername(): String? {
		return getCurrentUserJwt()?.let { getUsernameFromToken(it) }
	}

	/**
	 * 현재 인증된 사용자의 역할 추출
	 */
	fun getCurrentUserRole(): String? {
		return getCurrentUserJwt()?.let { getRoleFromToken(it) }
	}
}