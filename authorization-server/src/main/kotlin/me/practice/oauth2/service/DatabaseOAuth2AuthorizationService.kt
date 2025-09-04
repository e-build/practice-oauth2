package me.practice.oauth2.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import me.practice.oauth2.configuration.CustomUserDetails
import me.practice.oauth2.entity.IoIdpAuthorization
import me.practice.oauth2.entity.IoIdpAuthorizationRepository
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.security.jackson2.SecurityJackson2Modules
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import org.springframework.util.StringUtils
import java.time.Instant
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

/**
 * 데이터베이스 기반 OAuth2AuthorizationService 구현체
 * IoIdpAuthorization 엔티티와 연동하여 OAuth2 Authorization 정보를 관리합니다.
 */
@Service
class DatabaseOAuth2AuthorizationService(
	private val authorizationRepository: IoIdpAuthorizationRepository,
	private val registeredClientRepository: RegisteredClientRepository,
) : OAuth2AuthorizationService {

	private val objectMapper = ObjectMapper()

	init {
		val classLoader = DatabaseOAuth2AuthorizationService::class.java.getClassLoader()
		val securityModules = SecurityJackson2Modules.getModules(classLoader)
		this.objectMapper.registerModules(securityModules)
		this.objectMapper.registerModule(OAuth2AuthorizationServerJackson2Module())

		val typeValiator = BasicPolymorphicTypeValidator.builder()
			.allowIfBaseType(CustomUserDetails::class.java)
			.allowIfSubType("me.practice.oauth2.configuration.CustomUserDetails")
			.build()

		this.objectMapper.activateDefaultTyping(typeValiator, ObjectMapper.DefaultTyping.NON_FINAL)
	}

	override fun save(authorization: OAuth2Authorization?) {
		Assert.notNull(authorization, "authorization cannot be null")
		this.authorizationRepository.save<IoIdpAuthorization?>(toEntity(authorization!!))
	}

	override fun remove(authorization: OAuth2Authorization?) {
		Assert.notNull(authorization, "authorization cannot be null")
		this.authorizationRepository.deleteById(authorization!!.id)
	}

	override fun findById(id: String): OAuth2Authorization? {
		return this.authorizationRepository.findById(id)
			.map<OAuth2Authorization?>(Function { entity: IoIdpAuthorization? -> entity!!.toDomain() }).orElse(null)
	}

	override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
		Assert.hasText(token, "token cannot be empty")

		val result: Optional<IoIdpAuthorization?> =
			when (tokenType?.value) {
				null -> this.authorizationRepository.findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValueOrOidcIdTokenValueOrUserCodeValueOrDeviceCodeValue(
					token
				)

				OAuth2ParameterNames.STATE -> this.authorizationRepository.findByState(token)
				OAuth2ParameterNames.CODE -> this.authorizationRepository.findByAuthorizationCodeValue(token)
				OAuth2ParameterNames.ACCESS_TOKEN -> this.authorizationRepository.findByAccessTokenValue(token)
				OAuth2ParameterNames.REFRESH_TOKEN -> this.authorizationRepository.findByRefreshTokenValue(token)
				OidcParameterNames.ID_TOKEN -> this.authorizationRepository.findByOidcIdTokenValue(token)
				OAuth2ParameterNames.USER_CODE -> this.authorizationRepository.findByUserCodeValue(token)
				OAuth2ParameterNames.DEVICE_CODE -> this.authorizationRepository.findByDeviceCodeValue(token)
				else -> Optional.empty<IoIdpAuthorization?>() as Optional<IoIdpAuthorization?>
			}

		return result.map<OAuth2Authorization?>(Function { entity: IoIdpAuthorization? -> entity!!.toDomain() })
			.orElse(null)
	}

	private fun IoIdpAuthorization.toDomain(): OAuth2Authorization? {
		val registeredClient = registeredClientRepository.findById(this.registeredClientId)
			?: throw DataRetrievalFailureException(
				"The RegisteredClient with id '${this.registeredClientId}' was not found in the RegisteredClientRepository."
			)

		val builder = OAuth2Authorization.withRegisteredClient(registeredClient)
			.id(this.id)
			.principalName(this.principalName)
			.authorizationGrantType(resolveAuthorizationGrantType(this.authorizationGrantType))
			.authorizedScopes(StringUtils.commaDelimitedListToSet(this.authorizedScopes))
			.attributes { attributes: MutableMap<String?, Any?>? -> attributes!!.putAll(parseMap(this.attributes)!!) }
		if (this.state != null) {
			builder.attribute(OAuth2ParameterNames.STATE, this.state)
		}

		if (this.authorizationCodeValue != null) {
			val authorizationCode = OAuth2AuthorizationCode(
				this.authorizationCodeValue,
				this.authorizationCodeIssuedAt,
				this.authorizationCodeExpiresAt
			)
			builder.token<OAuth2AuthorizationCode?>(
				authorizationCode
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.authorizationCodeMetadata)!!) }
		}

		if (this.accessTokenValue != null) {
			val accessToken = OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER,
				this.accessTokenValue,
				this.accessTokenIssuedAt,
				this.accessTokenExpiresAt,
				StringUtils.commaDelimitedListToSet(this.accessTokenScopes)
			)
			builder.token<OAuth2AccessToken?>(
				accessToken
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.accessTokenMetadata)!!) }
		}

		if (this.refreshTokenValue != null) {
			val refreshToken = OAuth2RefreshToken(
				this.refreshTokenValue,
				this.refreshTokenIssuedAt,
				this.refreshTokenExpiresAt
			)
			builder.token<OAuth2RefreshToken?>(
				refreshToken
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.refreshTokenMetadata)!!) }
		}

		if (this.oidcIdTokenValue != null) {
			val idToken = OidcIdToken(
				this.oidcIdTokenValue,
				this.oidcIdTokenIssuedAt,
				this.oidcIdTokenExpiresAt,
				parseMap(this.oidcIdTokenClaims)
			)
			builder.token<OidcIdToken?>(
				idToken
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.oidcIdTokenMetadata)!!) }
		}

		if (this.userCodeValue != null) {
			val userCode = OAuth2UserCode(
				this.userCodeValue,
				this.userCodeIssuedAt,
				this.userCodeExpiresAt
			)
			builder.token<OAuth2UserCode?>(
				userCode
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.userCodeMetadata)!!) }
		}

		if (this.deviceCodeValue != null) {
			val deviceCode = OAuth2DeviceCode(
				this.deviceCodeValue,
				this.deviceCodeIssuedAt,
				this.deviceCodeExpiresAt
			)
			builder.token<OAuth2DeviceCode?>(
				deviceCode
			) { metadata: MutableMap<String?, Any?>? -> metadata!!.putAll(parseMap(this.deviceCodeMetadata)!!) }
		}

		return builder.build()
	}

	private fun toEntity(authorization: OAuth2Authorization): IoIdpAuthorization {
		val entity = IoIdpAuthorization()
		entity.id = authorization.id
		entity.registeredClientId = authorization.registeredClientId
		entity.principalName = authorization.principalName
		entity.authorizationGrantType = authorization.authorizationGrantType.value
		entity.authorizedScopes = StringUtils.collectionToDelimitedString(authorization.authorizedScopes, ",")
		entity.attributes = writeMap(authorization.attributes)
		entity.state = authorization.getAttribute<String?>(OAuth2ParameterNames.STATE)

		val authorizationCode =
			authorization.getToken(OAuth2AuthorizationCode::class.java)
		setTokenValues(
			authorizationCode,
			{ entity.authorizationCodeValue = it },
			{ entity.authorizationCodeIssuedAt = it },
			{ entity.authorizationCodeExpiresAt = it },
			{ entity.authorizationCodeMetadata = it }
		)

		val accessToken =
			authorization.getToken(OAuth2AccessToken::class.java)
		setTokenValues(
			accessToken,
			{ entity.accessTokenValue = it },
			{ entity.accessTokenIssuedAt = it },
			{ entity.accessTokenExpiresAt = it },
			{ entity.accessTokenMetadata = it }
		)
		if (accessToken != null && accessToken.getToken().scopes != null) {
			entity.accessTokenScopes = StringUtils.collectionToDelimitedString(accessToken.getToken().scopes, ",")
		}

		val refreshToken =
			authorization.getToken<OAuth2RefreshToken?>(OAuth2RefreshToken::class.java)
		setTokenValues(
			refreshToken,
			{ entity.refreshTokenValue = it },
			{ entity.refreshTokenIssuedAt = it },
			{ entity.refreshTokenExpiresAt = it },
			{ entity.refreshTokenMetadata = it }
		)

		val oidcIdToken =
			authorization.getToken<OidcIdToken?>(OidcIdToken::class.java)
		setTokenValues(
			oidcIdToken,
			{ entity.oidcIdTokenValue = it },
			{ entity.oidcIdTokenIssuedAt = it },
			{ entity.oidcIdTokenExpiresAt = it },
			{ entity.oidcIdTokenMetadata = it }
		)
		if (oidcIdToken != null) {
			entity.oidcIdTokenClaims = writeMap(oidcIdToken.claims)
		}

		val userCode =
			authorization.getToken<OAuth2UserCode?>(OAuth2UserCode::class.java)
		setTokenValues(
			userCode,
			{ entity.userCodeValue = it },
			{ entity.userCodeIssuedAt = it },
			{ entity.userCodeExpiresAt = it },
			{ entity.userCodeMetadata = it }
		)

		val deviceCode =
			authorization.getToken<OAuth2DeviceCode?>(OAuth2DeviceCode::class.java)
		setTokenValues(
			deviceCode,
			{ entity.deviceCodeValue = it },
			{ entity.deviceCodeIssuedAt = it },
			{ entity.deviceCodeExpiresAt = it },
			{ entity.deviceCodeMetadata = it }
		)

		return entity
	}

	private fun setTokenValues(
		token: OAuth2Authorization.Token<*>?,
		tokenValueConsumer: Consumer<String?>,
		issuedAtConsumer: Consumer<Instant?>,
		expiresAtConsumer: Consumer<Instant?>,
		metadataConsumer: Consumer<String?>,
	) {
		if (token != null) {
			val oAuth2Token: OAuth2Token = token.getToken()
			tokenValueConsumer.accept(oAuth2Token.tokenValue)
			issuedAtConsumer.accept(oAuth2Token.issuedAt)
			expiresAtConsumer.accept(oAuth2Token.expiresAt)
			metadataConsumer.accept(writeMap(token.metadata))
		}
	}

	private fun parseMap(data: String?): MutableMap<String?, Any?>? {
		try {
			return this.objectMapper.readValue<MutableMap<String?, Any?>?>(
				data,
				object : TypeReference<MutableMap<String?, Any?>?>() {
				})
		} catch (ex: Exception) {
			throw IllegalArgumentException(ex.message, ex)
		}
	}

	private fun writeMap(metadata: MutableMap<String?, Any?>?): String? {
		try {
			return this.objectMapper.writeValueAsString(metadata)
		} catch (ex: Exception) {
			throw IllegalArgumentException(ex.message, ex)
		}
	}

	companion object {
		private fun resolveAuthorizationGrantType(authorizationGrantType: String?): AuthorizationGrantType =
			when (authorizationGrantType) {
				AuthorizationGrantType.AUTHORIZATION_CODE.value -> AuthorizationGrantType.AUTHORIZATION_CODE
				AuthorizationGrantType.CLIENT_CREDENTIALS.value -> AuthorizationGrantType.CLIENT_CREDENTIALS
				AuthorizationGrantType.REFRESH_TOKEN.value -> AuthorizationGrantType.REFRESH_TOKEN
				AuthorizationGrantType.DEVICE_CODE.value -> AuthorizationGrantType.DEVICE_CODE
				// Custom authorization grant type
				else -> AuthorizationGrantType(authorizationGrantType)

			}
	}
}