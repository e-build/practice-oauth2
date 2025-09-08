package me.practice.oauth2.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.IoIdpClient
import me.practice.oauth2.entity.IoIdpClientRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 데이터베이스 기반 RegisteredClientRepository 구현체
 * IoIdpClient 엔티티와 연동하여 OAuth 클라이언트 정보를 관리합니다.
 */
@Service
class DatabaseRegisteredClientRepository(
	private val ioIdpClientRepository: IoIdpClientRepository,
	private val objectMapper: ObjectMapper,
) : RegisteredClientRepository {

	@Transactional(readOnly = true)
	override fun findById(id: String): IdpClient? {
		return ioIdpClientRepository.findById(id)
			.map { it.convertToRegisteredClient() }
			.orElse(null)
	}

	@Transactional(readOnly = true)
	override fun findByClientId(clientId: String): IdpClient? {
		return ioIdpClientRepository.findByClientId(clientId)?.convertToRegisteredClient()
	}

	@Transactional
	override fun save(registeredClient: RegisteredClient) {
		ioIdpClientRepository.save(registeredClient.toEntity())
	}

	/**
	 * IoIdpClient 엔티티를 RegisteredClient로 변환
	 */
	private fun IoIdpClient.convertToRegisteredClient(): IdpClient {
		val idpClient = this.toDomain()
		idpClient.initRegisteredClient(objectMapper)
		return idpClient
	}

	private fun IoIdpClient.toDomain(): IdpClient {
		return IdpClient(
			doId = this.id,
			doClientId = this.clientId,
			doShoplClientId = this.shoplClientId,
			doPlatform = this.platform,
			doClientIdIssuedAt = this.clientIdIssuedAt,
			doClientSecret = this.clientSecret,
			doClientSecretExpiresAt = this.clientSecretExpiresAt,
			doClientName = this.clientName,
			doClientAuthenticationMethods = this.clientAuthenticationMethods,
			doAuthorizationGrantTypes = this.authorizationGrantTypes,
			doRedirectUris = this.redirectUris,
			doPostLogoutRedirectUris = this.postLogoutRedirectUris,
			doScopes = this.scopes,
			doClientSettings = this.clientSettings,
			doTokenSettings = this.tokenSettings
		)
	}

	/**
	 * RegisteredClient를 IoIdpClient 엔티티로 변환
	 */
	private fun RegisteredClient.toEntity(): IoIdpClient {
		val shoplClientId = this.clientSettings.settings["shoplClientId"] as String
		val platform = this.clientSettings.settings["platform"] as IdpClient.Platform

		return IoIdpClient(
			id = this.id,
			clientId = this.clientId,
			clientIdIssuedAt = this.clientIdIssuedAt
				?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
				?: LocalDateTime.now(),
			clientSecret = this.clientSecret,
			clientSecretExpiresAt = this.clientSecretExpiresAt
				?.atZone(ZoneId.systemDefault())
				?.toLocalDateTime(),
			clientName = this.clientName,
			clientAuthenticationMethods = serializeClientAuthenticationMethods(
				this.clientAuthenticationMethods
			),
			authorizationGrantTypes = serializeAuthorizationGrantTypes(
				this.authorizationGrantTypes
			),
			redirectUris = serializeStringSet(this.redirectUris),
			postLogoutRedirectUris = serializeStringSet(this.postLogoutRedirectUris),
			scopes = serializeStringSet(this.scopes)!!,
			clientSettings = serializeClientSettings(this.clientSettings),
			tokenSettings = serializeTokenSettings(this.tokenSettings),
			shoplClientId = shoplClientId,
			platform = platform,
		)
	}

	/**
	 * 클라이언트 인증 방법을 문자열로 직렬화
	 */
	private fun serializeClientAuthenticationMethods(methods: Set<ClientAuthenticationMethod>): String {
		return methods.joinToString(",") { it.value }
	}

	/**
	 * 권한 부여 타입을 문자열로 직렬화
	 */
	private fun serializeAuthorizationGrantTypes(grantTypes: Set<AuthorizationGrantType>): String {
		return grantTypes.joinToString(",") { it.value }
	}

	/**
	 * 문자열 세트를 콤마로 구분된 문자열로 직렬화
	 */
	private fun serializeStringSet(stringSet: Set<String>?): String? {
		return stringSet?.joinToString(",")
	}

	/**
	 * ClientSettings를 JSON 문자열로 직렬화
	 */
	private fun serializeClientSettings(settings: ClientSettings): String {
		val settingsMap = mutableMapOf<String, Any>()
		settingsMap["@class"] = "java.util.Collections\$UnmodifiableMap"
		settingsMap["settings.client.require-proof-key"] = settings.isRequireProofKey
		settingsMap["settings.client.require-authorization-consent"] = settings.isRequireAuthorizationConsent
		settings.jwkSetUrl?.let {
			settingsMap["settings.client.jwk-set-url"] = it
		}
		return objectMapper.writeValueAsString(settingsMap)
	}

	/**
	 * TokenSettings를 JSON 문자열로 직렬화
	 */
	private fun serializeTokenSettings(settings: TokenSettings): String {
		val settingsMap = mutableMapOf<String, Any>()
		settingsMap["@class"] = "java.util.Collections\$UnmodifiableMap"
		settingsMap["settings.token.reuse-refresh-tokens"] = settings.isReuseRefreshTokens
		settingsMap["settings.token.access-token-time-to-live"] =
			listOf("java.time.Duration", settings.accessTokenTimeToLive.seconds.toDouble())
		settingsMap["settings.token.refresh-token-time-to-live"] =
			listOf("java.time.Duration", settings.refreshTokenTimeToLive.seconds.toDouble())
		settingsMap["settings.token.authorization-code-time-to-live"] =
			listOf("java.time.Duration", settings.authorizationCodeTimeToLive.seconds.toDouble())

		return objectMapper.writeValueAsString(settingsMap)
	}
}