package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.security.Principal
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ===== Helpers & Constants =====

private object AttrKeys {
	val AUTHZ_REQ: String = OAuth2AuthorizationRequest::class.java.name
	val PRINCIPAL: String = Principal::class.java.name
}

private inline fun <reified T> Any?.asOrNull(): T? = this as? T
private inline fun <reified T> Map<String, Any?>.getAs(key: String): T? = this[key].asOrNull<T>()
private fun Map<*, *>.toStringAnyMap(): Map<String, Any?> =
	entries.associate { it.key.toString() to it.value }

private fun String?.toLdtOrNull(): LocalDateTime? =
	this?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

// ===== 저장용 DTO =====

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

data class TokenDTO(
	val tokenValue: String,
	val issuedAt: Instant?,
	val expiresAt: Instant?,
	val metadata: Map<String, Any?> = emptyMap(),
)

data class AccessTokenDTO(
	val tokenValue: String,
	val issuedAt: Instant?,
	val expiresAt: Instant?,
	val tokenType: String = "Bearer",
	val scopes: Set<String> = emptySet(),
	val metadata: Map<String, Any?> = emptyMap(),
)

// ===== Converter =====

object RedisAuthorizationConverter {

	fun toDTO(auth: OAuth2Authorization): RedisAuthorizationDTO {
		val state = auth.getAttribute<String>("state")
		val code = auth.getToken(OAuth2AuthorizationCode::class.java)
		val access = auth.accessToken
		val refresh = auth.refreshToken
		val idToken = auth.getToken(OidcIdToken::class.java)

		return RedisAuthorizationDTO(
			id = auth.id,
			registeredClientId = auth.registeredClientId,
			principalName = auth.principalName,
			authorizationGrantType = auth.authorizationGrantType.value,
			attributes = auth.attributes.toMap(),  // 방어적 복사

			state = state,

			authorizationCode = code?.let { toTokenDTO(it) },
			accessToken = access?.let { toAccessDTO(it) },
			refreshToken = refresh?.let { toTokenDTO(it) },
			oidcIdToken = idToken?.let { toTokenDTO(it) },
		)
	}

	// OAuth2Authorization.Token<out OAuth2Token> → TokenDTO
	private fun toTokenDTO(token: OAuth2Authorization.Token<out OAuth2Token>): TokenDTO {
		val raw = token.token
		val abs = raw as? AbstractOAuth2Token
		return TokenDTO(
			tokenValue = raw.tokenValue,
			issuedAt = abs?.issuedAt,
			expiresAt = abs?.expiresAt,
			metadata = token.metadata.toMap()
		)
	}

	private fun toAccessDTO(token: OAuth2Authorization.Token<OAuth2AccessToken>): AccessTokenDTO {
		val raw = token.token
		return AccessTokenDTO(
			tokenValue = raw.tokenValue,
			issuedAt = raw.issuedAt,
			expiresAt = raw.expiresAt,
			tokenType = raw.tokenType?.value ?: "Bearer",
			scopes = raw.scopes,
			metadata = token.metadata.toMap()
		)
	}

	fun fromDTO(
		dto: RedisAuthorizationDTO,
		registeredClientRepository: RegisteredClientRepository,
	): OAuth2Authorization {
		val rc = requireNotNull(registeredClientRepository.findById(dto.registeredClientId)) {
			"RegisteredClient not found: ${dto.registeredClientId}"
		}

		val coercedAttrs = coerceAttributes(dto.attributes)

		val builder = OAuth2Authorization.withRegisteredClient(rc)
			.id(dto.id)
			.principalName(dto.principalName)
			.authorizationGrantType(AuthorizationGrantType(dto.authorizationGrantType))
			.attributes { it.putAll(coercedAttrs) }

		dto.authorizationCode?.let {
			builder.token(OAuth2AuthorizationCode(it.tokenValue, it.issuedAt, it.expiresAt)) { meta ->
				meta.putAll(it.metadata)
			}
		}

		dto.accessToken?.let {
			val type = if (it.tokenType.equals("Bearer", ignoreCase = true))
				OAuth2AccessToken.TokenType.BEARER else null
			builder.token(
				OAuth2AccessToken(type, it.tokenValue, it.issuedAt, it.expiresAt, it.scopes)
			) { meta -> meta.putAll(it.metadata) }
		}

		dto.refreshToken?.let {
			builder.token(OAuth2RefreshToken(it.tokenValue, it.issuedAt, it.expiresAt)) { meta ->
				meta.putAll(it.metadata)
			}
		}

		dto.oidcIdToken?.let {
			val claims: Map<String, Any> =
				it.metadata["claims"].asOrNull<Map<String, Any>>()
					?: it.metadata["claimsSet"].asOrNull<Map<String, Any>>() // 혹시 다른 키로 저장된 경우
					?: emptyMap()
			builder.token(OidcIdToken(it.tokenValue, it.issuedAt, it.expiresAt, claims)) { meta ->
				meta.putAll(it.metadata)
			}
		}

		return builder.build()
	}

	private fun coerceAttributes(attrs: Map<String, Any?>): Map<String, Any?> {
		val authzReqRaw = attrs[AttrKeys.AUTHZ_REQ]
		val authzReq = when (authzReqRaw) {
			null -> null
			is OAuth2AuthorizationRequest -> authzReqRaw
			is Map<*, *> -> runCatching { rebuildAuthorizationRequest(authzReqRaw) }.getOrNull()
			else -> null // 알 수 없으면 그대로 둔다
		}

		val principalRaw = attrs[AttrKeys.PRINCIPAL]  // (버그 픽스) 잘못된 키 체크 수정
		val principal = when (principalRaw) {
			null -> null
			is Principal -> principalRaw
			is Map<*, *> -> runCatching { rebuildPrincipal(principalRaw) }.getOrNull()
			else -> null
		}

		if (authzReq == null && principal == null) return attrs

		return attrs.toMutableMap().apply {
			if (authzReq != null) put(AttrKeys.AUTHZ_REQ, authzReq)
			if (principal != null) put(AttrKeys.PRINCIPAL, principal)
		}
	}

	private fun rebuildPrincipal(src: Map<*, *>): Principal {
		// principal > account 구조 가정
		val principalMap = src["principal"].asOrNull<Map<*, *>>()
			?: error("principal missing in map")
		val accountMap = principalMap["account"].asOrNull<Map<*, *>>()
			?: error("principal.account missing")

		val ioIdpAccount = IoIdpAccount(
			id = accountMap["id"] as String,
			shoplClientId = accountMap["shoplClientId"] as String,
			shoplUserId = accountMap["shoplUserId"] as String,
			shoplLoginId = accountMap["shoplLoginId"] as String,
			email = accountMap["email"] as String,
			phone = accountMap["phone"] as String,
			name = accountMap["name"] as String,
			status = accountMap["status"] as String,
			isCertEmail = accountMap["isCertEmail"] as Boolean,
			isTempPwd = accountMap["isTempPwd"] as Boolean,
			pwd = accountMap["pwd"] as String,
			beforePwd = accountMap["beforePwd"].asOrNull<String>(),
			pwdUpdateDt = accountMap["pwdUpdateDt"].asOrNull<String>().toLdtOrNull(),
			regDt = (accountMap["regDt"] as String).toLdtOrNull()
				?: error("regDt missing or invalid"),
			modDt = accountMap["modDt"].asOrNull<String>().toLdtOrNull(),
			delDt = accountMap["delDt"].asOrNull<String>().toLdtOrNull(),
		)

		val userDetails = CustomUserDetails(ioIdpAccount)

		// 권한 복원: ["ROLE_X", ...] 또는 [{authority:"ROLE_X"}, ...] 모두 허용
		val authoritiesSrc: Collection<*>? =
			src["authorities"].asOrNull<Collection<*>>()
				?: principalMap["authorities"].asOrNull<Collection<*>>()

		val authorities = authoritiesSrc
			?.mapNotNull {
				when (it) {
					is String -> it
					is Map<*, *> -> it["authority"].asOrNull<String>()
					else -> null
				}
			}
			?.map(::SimpleGrantedAuthority)
			?: emptyList()

		return UsernamePasswordAuthenticationToken(userDetails, null, authorities).apply {
			src["details"]?.let { this.details = it }
		}
	}

	private fun rebuildAuthorizationRequest(src: Map<*, *>): OAuth2AuthorizationRequest {
		val authorizationUri = src["authorizationUri"] as? String
			?: error("authorizationUri missing")
		val clientId = src["clientId"] as? String
			?: error("clientId missing")
		val redirectUri = src["redirectUri"] as? String
		val scopes: Set<String> =
			(src["scopes"].asOrNull<Collection<*>>()?.filterIsInstance<String>()?.toSet())
				?: emptySet()
		val state = src["state"] as? String
		val additional = src["additionalParameters"].asOrNull<Map<*, *>>()
			?.toStringAnyMap() ?: emptyMap()
		val attrs = src["attributes"].asOrNull<Map<*, *>>()
			?.toStringAnyMap() ?: emptyMap()

		return OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri(authorizationUri)
			.clientId(clientId)
			.redirectUri(redirectUri)
			.scopes(scopes)
			.state(state)
			.additionalParameters(additional)
			.attributes { it.putAll(attrs) }
			.build()
	}
}
