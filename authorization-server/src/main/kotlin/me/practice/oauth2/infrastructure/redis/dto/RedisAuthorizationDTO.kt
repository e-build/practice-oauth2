package me.practice.oauth2.infrastructure.redis.dto

import java.time.Instant

/**
 * Redis 저장용 OAuth2 Authorization DTO
 * 순수 데이터 전송 객체로 변환 로직 없음
 */
data class RedisAuthorizationDTO(
    val id: String,
    val registeredClientId: String,
    val principalName: String,
    val authorizationGrantType: String,
    val attributes: Map<String, Any?> = emptyMap(),
    val state: String? = null,
    val authorizationCode: TokenDTO? = null,
    val accessToken: AccessTokenDTO? = null,
    val refreshToken: TokenDTO? = null,
    val oidcIdToken: TokenDTO? = null,
)

/**
 * 일반 토큰 DTO
 */
data class TokenDTO(
    val tokenValue: String,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val metadata: Map<String, Any?> = emptyMap(),
)

/**
 * 액세스 토큰 DTO (스코프 및 토큰 타입 포함)
 */
data class AccessTokenDTO(
    val tokenValue: String,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val tokenType: String = "Bearer",
    val scopes: Set<String> = emptySet(),
    val metadata: Map<String, Any?> = emptyMap(),
)