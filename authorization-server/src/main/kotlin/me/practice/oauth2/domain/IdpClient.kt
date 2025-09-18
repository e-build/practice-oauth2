package me.practice.oauth2.domain

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class IdpClient(
	val doId: String,
	val doClientId: String,
	val doShoplClientId: String,
	val doPlatform: Platform,
	val doClientIdIssuedAt: LocalDateTime = LocalDateTime.now(),
	val doClientSecret: String? = null,
	val doClientSecretExpiresAt: LocalDateTime? = null,
	val doClientName: String,
	val doClientAuthenticationMethods: String,
	val doAuthorizationGrantTypes: String,
	val doRedirectUris: String? = null,
	val doPostLogoutRedirectUris: String? = null,
	val doScopes: String,
	val doClientSettings: String,
	val doTokenSettings: String,
) : RegisteredClient() {

	private lateinit var registeredClient: RegisteredClient

	fun initRegisteredClient(objectMapper: ObjectMapper) {
		val builder = RegisteredClient.withId(this.doId)
			.clientId(this.doClientId)
			.clientName(this.doClientName)
			.clientIdIssuedAt(this.doClientIdIssuedAt.atZone(ZoneId.systemDefault()).toInstant())

		// Client Secret 설정
		this.doClientSecret?.let { builder.clientSecret(it) }
		this.doClientSecretExpiresAt?.let {
			builder.clientSecretExpiresAt(it.atZone(ZoneId.systemDefault()).toInstant())
		}

		// Client Authentication Methods 파싱
		parseClientAuthenticationMethods(this.doClientAuthenticationMethods)
			.forEach { builder.clientAuthenticationMethod(it) }

		// Authorization Grant Types 파싱
		parseAuthorizationGrantTypes(this.doAuthorizationGrantTypes)
			.forEach { builder.authorizationGrantType(it) }

		// Redirect URIs 파싱
		this.doRedirectUris?.let { uris ->
			parseStringList(uris).forEach { builder.redirectUri(it) }
		}

		// Post Logout Redirect URIs 파싱
		this.doPostLogoutRedirectUris?.let { uris ->
			parseStringList(uris).forEach { builder.postLogoutRedirectUri(it) }
		}

		// Scopes 파싱
		parseStringList(this.doScopes).forEach { builder.scope(it) }

		// Client Settings 파싱
		builder.clientSettings(
			objectMapper.parseClientSettings(
				this.doClientSettings,
				this.doShoplClientId,
				this.doPlatform
			)
		)

		// Token Settings 파싱
		builder.tokenSettings(
			objectMapper.parseTokenSettings(this.doTokenSettings)
		)

		registeredClient = builder.build()
	}

	override fun getId(): String {
		return this.registeredClient.id
	}

	override fun getClientId(): String {
		return this.registeredClient.clientId
	}

	override fun getClientIdIssuedAt(): Instant? {
		return this.registeredClient.clientIdIssuedAt
	}

	override fun getClientSecret(): String? {
		return this.registeredClient.clientSecret
	}

	override fun getClientSecretExpiresAt(): Instant? {
		return this.registeredClient.clientSecretExpiresAt
	}

	override fun getClientName(): String {
		return this.registeredClient.clientName
	}

	override fun getClientAuthenticationMethods(): MutableSet<ClientAuthenticationMethod> {
		return this.registeredClient.clientAuthenticationMethods
	}

	override fun getAuthorizationGrantTypes(): MutableSet<AuthorizationGrantType> {
		return this.registeredClient.authorizationGrantTypes
	}

	override fun getRedirectUris(): MutableSet<String>? {
		return this.registeredClient.redirectUris
	}

	override fun getPostLogoutRedirectUris(): MutableSet<String>? {
		return this.registeredClient.postLogoutRedirectUris
	}

	override fun getScopes(): MutableSet<String>? {
		return this.registeredClient.scopes
	}

	override fun getClientSettings(): ClientSettings? {
		return this.registeredClient.clientSettings
	}

	override fun getTokenSettings(): TokenSettings? {
		return this.registeredClient.tokenSettings
	}

	/**
	 * 클라이언트 인증 방법 문자열을 파싱
	 */
	private fun parseClientAuthenticationMethods(methods: String): Set<ClientAuthenticationMethod> {
		return parseStringList(methods).map { method ->
			when (method) {
				"client_secret_basic" -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC
				"client_secret_post" -> ClientAuthenticationMethod.CLIENT_SECRET_POST
				"client_secret_jwt" -> ClientAuthenticationMethod.CLIENT_SECRET_JWT
				"private_key_jwt" -> ClientAuthenticationMethod.PRIVATE_KEY_JWT
				"none" -> ClientAuthenticationMethod.NONE
				else -> ClientAuthenticationMethod(method)
			}
		}.toSet()
	}

	private fun parseStringList(str: String): List<String> {
		return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
	}

	/**
	 * 권한 부여 타입 문자열을 파싱
	 */
	private fun parseAuthorizationGrantTypes(grantTypes: String): Set<AuthorizationGrantType> {
		return parseStringList(grantTypes).map { grantType ->
			when (grantType) {
				"authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE
				"refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN
				"client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS
				"password" -> AuthorizationGrantType.PASSWORD
				"urn:ietf:params:oauth:grant-type:jwt-bearer" -> AuthorizationGrantType.JWT_BEARER
				"urn:ietf:params:oauth:grant-type:device_code" -> AuthorizationGrantType.DEVICE_CODE
				else -> AuthorizationGrantType(grantType)
			}
		}.toSet()
	}

	/**
	 * ClientSettings JSON 문자열을 파싱
	 */
	private fun ObjectMapper.parseClientSettings(
		settingsJson: String,
		shoplClientId: String,
		platform: Platform,
	): ClientSettings {
		try {
			val settingsMap = this.readValue(settingsJson, Map::class.java) as Map<*, *>
			val builder = ClientSettings.builder()
				.settings { deleveryMap ->
					deleveryMap["shoplClientId"] = shoplClientId
					deleveryMap["platform"] = platform
				}

			settingsMap["settings.client.require-proof-key"]?.let {
				if (it is Boolean) builder.requireProofKey(it)
			}
			settingsMap["settings.client.require-authorization-consent"]?.let {
				if (it is Boolean) builder.requireAuthorizationConsent(it)
			}
			settingsMap["settings.client.jwk-set-url"]?.let {
				if (it is String) builder.jwkSetUrl(it)
			}
			settingsMap["settings.client.token-endpoint-authentication-signing-algorithm"]?.let {
				// 필요시 구현
			}
			return builder.build()
		} catch (e: Exception) {
			return ClientSettings.builder().build()
		}
	}

	/**
	 * TokenSettings JSON 문자열을 파싱
	 */
	private fun ObjectMapper.parseTokenSettings(settingsJson: String): TokenSettings {
		try {
			val settingsMap = this.readValue(settingsJson, Map::class.java) as Map<*, *>
			val builder = TokenSettings.builder()

			settingsMap["settings.token.access-token-time-to-live"]?.let {
				if (it is List<*> && it.size >= 2 && it[0] == "java.time.Duration") {
					val seconds = (it[1] as Number).toLong()
					builder.accessTokenTimeToLive(Duration.ofSeconds(seconds))
				}
			}
			settingsMap["settings.token.refresh-token-time-to-live"]?.let {
				if (it is List<*> && it.size >= 2 && it[0] == "java.time.Duration") {
					val seconds = (it[1] as Number).toLong()
					builder.refreshTokenTimeToLive(Duration.ofSeconds(seconds))
				}
			}
			settingsMap["settings.token.reuse-refresh-tokens"]?.let {
				if (it is Boolean) builder.reuseRefreshTokens(it)
			}

			return builder.build()
		} catch (e: Exception) {
			return TokenSettings.builder().build()
		}
	}

	/**
	 * 플랫폼 타입 정의
	 *
	 * 사용자가 로그인하는 플랫폼을 구분하여 통계 및 보안 분석에 활용합니다.
	 * 각 플랫폼별로 다른 보안 정책이나 인증 방식을 적용할 수 있습니다.
	 */
	enum class Platform {
		/**
		 * 웹 대시보드 (관리자/일반 사용자 웹 인터페이스)
		 * - User-Agent: 일반적인 브라우저 패턴
		 * - 용도: 관리 작업, 대시보드 조회
		 */
		DASHBOARD,

		/**
		 * 모바일 애플리케이션 (iOS/Android 네이티브 앱)
		 * - User-Agent: 모바일 앱 식별자 포함
		 * - 용도: 모바일 전용 기능
		 */
		MOBILE,

		/**
		 * 웹 애플리케이션 (일반 웹 서비스)
		 * - User-Agent: 브라우저이지만 대시보드가 아닌 일반 웹
		 * - 용도: 공개 웹 서비스
		 */
		WEB,

		/**
		 * API 직접 호출 (서버-투-서버, 써드파티 통합)
		 * - User-Agent: API 클라이언트, 없음, 또는 특수 식별자
		 * - 용도: API 키 기반 인증, 시스템 통합
		 */
		API,

		/**
		 * 미확인 플랫폼 (감지 실패 시 기본값)
		 * - 플랫폼 감지에 실패했거나 새로운 타입
		 * - 용도: 로깅 및 분석을 통한 패턴 발견
		 */
		UNKNOWN;

		/**
		 * 플랫폼 이름의 한글 표시명
		 */
		fun getDisplayName(): String = when (this) {
			DASHBOARD -> "웹 대시보드"
			MOBILE -> "모바일 앱"
			WEB -> "웹 애플리케이션"
			API -> "API 직접 호출"
			UNKNOWN -> "미확인 플랫폼"
		}

		/**
		 * 플랫폼이 모바일 계열인지 확인
		 */
		fun isMobile(): Boolean = this == MOBILE

		/**
		 * 플랫폼이 웹 브라우저 계열인지 확인
		 */
		fun isWebBased(): Boolean = this == DASHBOARD || this == WEB

		/**
		 * 플랫폼이 시스템 간 통신용인지 확인
		 */
		fun isSystemLevel(): Boolean = this == API
	}
}