package me.practice.oauth2.configuration

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.utils.JsonUtils
import org.slf4j.LoggerFactory
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

	val logger = LoggerFactory.getLogger(this::class.java)

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
		ioIdpAccountRepository: IoIdpAccountRepository,
	): OAuth2Authorization {
		val rc = requireNotNull(registeredClientRepository.findById(dto.registeredClientId)) {
			"RegisteredClient not found: ${dto.registeredClientId}"
		}

		val coercedAttrs = coerceAttributes(
			attrs = dto.attributes,
			ioIdpAccountRepository = ioIdpAccountRepository
		)

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

	private fun coerceAttributes(
		attrs: Map<String, Any?>,
		ioIdpAccountRepository: IoIdpAccountRepository
	): Map<String, Any?> {
		logger.trace("Coercing attributes with keys: ${attrs.keys}")
		
		val authzReqRaw = attrs[AttrKeys.AUTHZ_REQ]
		val authzReq = when (authzReqRaw) {
			null -> {
				logger.trace("No authorization request found in attributes")
				null
			}
			is OAuth2AuthorizationRequest -> {
				logger.trace("Authorization request already in correct type")
				authzReqRaw
			}
			is Map<*, *> -> {
				runCatching { 
					rebuildAuthorizationRequest(authzReqRaw) 
				}.onFailure { e ->
					logger.warn("Failed to rebuild authorization request: ${e.message}")
				}.getOrNull()
			}
			else -> {
				logger.warn("Unknown authorization request type: ${authzReqRaw?.javaClass}")
				null
			}
		}

		val principalRaw = attrs[AttrKeys.PRINCIPAL]
		val principal = when (principalRaw) {
			null -> {
				logger.trace("No principal found in attributes")
				null
			}
			is Principal -> {
				logger.trace("Principal already in correct type: ${principalRaw.javaClass.simpleName}")
				principalRaw
			}
			is Map<*, *> -> {
				runCatching {
					rebuildPrincipal(principalRaw, ioIdpAccountRepository)
				}.onFailure { e ->
					logger.error("Failed to rebuild principal from map: ${e.message}", e)
				}.getOrNull()
			}
			else -> {
				logger.warn("Unknown principal type: ${principalRaw?.javaClass}")
				null
			}
		}

		if (authzReq == null && principal == null) {
			logger.trace("No attributes to coerce")
			return attrs
		}

		return attrs.toMutableMap().apply {
			if (authzReq != null) {
				put(AttrKeys.AUTHZ_REQ, authzReq)
				logger.trace("Coerced authorization request")
			}
			if (principal != null) {
				put(AttrKeys.PRINCIPAL, principal)
				logger.debug("Coerced principal to ${principal.javaClass.simpleName}")
			}
		}
	}

	private fun rebuildPrincipal(
		src: Map<*, *>,
		ioIdpAccountRepository: IoIdpAccountRepository
	): Principal {
		val principalData = src["principal"] ?: src
		val accountMap = when (principalData) {
			is Map<*, *> -> principalData["account"].asOrNull<Map<*, *>>()
			else -> null
		}
		
		return if (accountMap != null) {
			rebuildBasicPrincipal(src, accountMap)
		} else {
			rebuildSsoPrincipal(src, principalData, ioIdpAccountRepository)
		}
	}

	private fun rebuildBasicPrincipal(src: Map<*, *>, accountMap: Map<*, *>): Principal {
		val authorities = extractAuthorities(src)
		val account = createBasicAccount(accountMap)
		val userDetails = CustomUserDetails(account)
		
		return UsernamePasswordAuthenticationToken(userDetails, null, authorities).apply {
			src["details"]?.let { this.details = it }
		}
	}

	private fun rebuildSsoPrincipal(
		src: Map<*, *>, 
		principalData: Any?, 
		ioIdpAccountRepository: IoIdpAccountRepository
	): Principal {
		val authorities = extractAuthorities(src)
		val principalMap = principalData.asOrNull<Map<*, *>>() 
			?: error("SSO principal data is not a map")
		
		logger.debug("Rebuilding SSO principal from data: ${principalMap.keys}")
		
		// OAuth2User/OidcUser의 속성을 바탕으로 실제 계정 조회
		val account = findOrCreateSsoAccount(principalMap, ioIdpAccountRepository)
		val userDetails = CustomUserDetails(account)
		
		return UsernamePasswordAuthenticationToken(userDetails, null, authorities).apply {
			src["details"]?.let { this.details = it }
		}
	}
	
	/**
	 * SSO 계정 조회/생성
	 * UserProvisioningService와 일관성 있게 처리
	 */
	private fun findOrCreateSsoAccount(
		principalMap: Map<*, *>,
		ioIdpAccountRepository: IoIdpAccountRepository
	): IoIdpAccount {
		// OAuth2User 속성에서 기본 정보 추출
		val providerUserId = extractProviderUserId(principalMap)
		val email = principalMap["email"] as? String
		val name = extractDisplayName(principalMap)
		val shoplClientId = extractShoplClientId(principalMap)
		
		logger.debug("Looking for SSO account - providerUserId: $providerUserId, email: $email, clientId: $shoplClientId")
		
		// 1. 이메일로 기존 계정 검색 (가장 안전한 방법)
		if (email != null) {
			val existingAccount = ioIdpAccountRepository.findByShoplClientIdAndEmail(shoplClientId, email)
			if (existingAccount != null) {
				logger.debug("Found existing account by email: ${existingAccount.id}")
				return existingAccount
			}
		}
		
		// 2. provider_user_id 패턴으로 계정 ID 생성 후 검색
		val generatedAccountId = "sso_${providerUserId}"
		val accountById = runCatching {
			ioIdpAccountRepository.findById(generatedAccountId).orElse(null)
		}.getOrNull()
		
		if (accountById != null) {
			logger.debug("Found existing account by generated ID: ${accountById.id}")
			return accountById
		}
		
		// 3. 계정이 없는 경우 최소한의 정보로 생성 (방어 코드)
		logger.warn("Creating fallback account for SSO user: $providerUserId (email: $email)")
		
		return IoIdpAccount(
			id = generatedAccountId,
			shoplClientId = shoplClientId,
			shoplUserId = providerUserId,
			shoplLoginId = email ?: "${providerUserId}@sso.fallback",
			email = email,
			phone = null,
			name = name,
			status = "ACTIVE",
			isCertEmail = email != null,
			isTempPwd = false,
			regDt = LocalDateTime.now()
		)
	}
	
	/**
	 * 다양한 OAuth2 제공자에서 사용자 ID 추출
	 */
	private fun extractProviderUserId(principalMap: Map<*, *>): String {
		val candidates = listOf(
			"sub" to principalMap["sub"] as? String,
			"id" to principalMap["id"]?.toString(),
			"preferred_username" to principalMap["preferred_username"] as? String,
			"name" to principalMap["name"] as? String,
			"oid" to principalMap["oid"] as? String,
			"response.id" to (principalMap["response"] as? Map<*, *>)?.get("id")?.toString()
		)
		
		val found = candidates.firstOrNull { it.second != null }
		if (found != null) {
			logger.debug("Extracted provider user ID from '${found.first}': ${found.second}")
			return found.second!!
		}
		
		val availableKeys = principalMap.keys.joinToString(", ")
		val errorMsg = "Could not extract provider user ID from principal. Available keys: [$availableKeys]"
		logger.error(errorMsg)
		throw IllegalArgumentException(errorMsg)
	}
	
	/**
	 * 사용자 표시 이름 추출
	 */
	private fun extractDisplayName(principalMap: Map<*, *>): String? {
		return principalMap["name"] as? String
			?: principalMap["given_name"] as? String
			?: principalMap["nickname"] as? String
			?: (principalMap["response"] as? Map<*, *>)?.get("name") as? String
	}
	
	/**
	 * Shopl 클라이언트 ID 추출
	 */
	private fun extractShoplClientId(principalMap: Map<*, *>): String {
		return principalMap["client_id"] as? String
			?: principalMap["aud"] as? String  // audience claim
			?: "CLIENT001"  // 기본값
	}

	private fun extractAuthorities(src: Map<*, *>): List<SimpleGrantedAuthority> {
		val authoritiesSrc: Collection<*>? = src["authorities"].asOrNull<Collection<*>>()
		return authoritiesSrc
			?.mapNotNull { auth ->
				when (auth) {
					is String -> auth
					is Map<*, *> -> auth["authority"].asOrNull<String>()
					else -> null
				}
			}
			?.map(::SimpleGrantedAuthority)
			?: emptyList()
	}

	private fun createBasicAccount(accountMap: Map<*, *>): IoIdpAccount {
		return IoIdpAccount(
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
