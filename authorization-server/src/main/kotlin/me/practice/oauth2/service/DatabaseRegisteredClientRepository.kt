
package me.practice.oauth2.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.entity.IoIdpClient
import me.practice.oauth2.entity.IoIdpClientRepository
import me.practice.oauth2.entity.IoIdpShoplClientMapping
import me.practice.oauth2.entity.IoIdpShoplClientMappingRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * 데이터베이스 기반 RegisteredClientRepository 구현체
 * IoIdpClient 엔티티와 연동하여 OAuth 클라이언트 정보를 관리합니다.
 */
@Service
class DatabaseRegisteredClientRepository(
    private val clientRepository: IoIdpClientRepository,
    private val mappingRepository: IoIdpShoplClientMappingRepository,
    private val objectMapper: ObjectMapper
) : RegisteredClientRepository {

    @Transactional(readOnly = true)
    override fun findById(id: String): RegisteredClient? {
        return clientRepository.findById(id)
            .map { convertToRegisteredClient(it) }
            .orElse(null)
    }

    @Transactional(readOnly = true)
    override fun findByClientId(clientId: String): RegisteredClient? {
        return clientRepository.findByClientId(clientId)
            ?.let { convertToRegisteredClient(it) }
    }

    @Transactional
    override fun save(registeredClient: RegisteredClient) {
        val ioIdpClient = convertToIoIdpClient(registeredClient)
        clientRepository.save(ioIdpClient)
    }

    /**
     * IoIdpClient 엔티티를 RegisteredClient로 변환
     */
    private fun convertToRegisteredClient(client: IoIdpClient): RegisteredClient {
        val builder = RegisteredClient.withId(client.id)
            .clientId(client.clientId)
            .clientName(client.clientName)
            .clientIdIssuedAt(client.clientIdIssuedAt.atZone(ZoneId.systemDefault()).toInstant())

        // Client Secret 설정
        client.clientSecret?.let { builder.clientSecret(it) }
        client.clientSecretExpiresAt?.let { 
            builder.clientSecretExpiresAt(it.atZone(ZoneId.systemDefault()).toInstant())
        }

        // Client Authentication Methods 파싱
        parseClientAuthenticationMethods(client.clientAuthenticationMethods)
            .forEach { builder.clientAuthenticationMethod(it) }

        // Authorization Grant Types 파싱
        parseAuthorizationGrantTypes(client.authorizationGrantTypes)
            .forEach { builder.authorizationGrantType(it) }

        // Redirect URIs 파싱
        client.redirectUris?.let { uris ->
            parseStringList(uris).forEach { builder.redirectUri(it) }
        }

        // Post Logout Redirect URIs 파싱
        client.postLogoutRedirectUris?.let { uris ->
            parseStringList(uris).forEach { builder.postLogoutRedirectUri(it) }
        }

        // Scopes 파싱
        parseStringList(client.scopes).forEach { builder.scope(it) }

        // Client Settings 파싱
        builder.clientSettings(parseClientSettings(client.clientSettings))

        // Token Settings 파싱
        builder.tokenSettings(parseTokenSettings(client.tokenSettings))

        return builder.build()
    }

    /**
     * RegisteredClient를 IoIdpClient 엔티티로 변환
     */
    private fun convertToIoIdpClient(registeredClient: RegisteredClient): IoIdpClient {
        return IoIdpClient(
            id = registeredClient.id,
            clientId = registeredClient.clientId,
            clientIdIssuedAt = registeredClient.clientIdIssuedAt
                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                ?: java.time.LocalDateTime.now(),
            clientSecret = registeredClient.clientSecret,
            clientSecretExpiresAt = registeredClient.clientSecretExpiresAt
                ?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
            clientName = registeredClient.clientName,
            clientAuthenticationMethods = serializeClientAuthenticationMethods(
                registeredClient.clientAuthenticationMethods
            ),
            authorizationGrantTypes = serializeAuthorizationGrantTypes(
                registeredClient.authorizationGrantTypes
            ),
            redirectUris = serializeStringSet(registeredClient.redirectUris),
            postLogoutRedirectUris = serializeStringSet(registeredClient.postLogoutRedirectUris),
            scopes = serializeStringSet(registeredClient.scopes)!!,
            clientSettings = serializeClientSettings(registeredClient.clientSettings),
            tokenSettings = serializeTokenSettings(registeredClient.tokenSettings)
        )
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
     * 콤마로 구분된 문자열을 리스트로 파싱
     */
    private fun parseStringList(str: String): List<String> {
        return str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * ClientSettings JSON 문자열을 파싱
     */
    private fun parseClientSettings(settingsJson: String): ClientSettings {
        try {
            val settingsMap = objectMapper.readValue(settingsJson, Map::class.java) as Map<String, Any>
            val builder = ClientSettings.builder()

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
    private fun parseTokenSettings(settingsJson: String): TokenSettings {
        try {
            val settingsMap = objectMapper.readValue(settingsJson, Map::class.java) as Map<String, Any>
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
        settingsMap["settings.token.reuse-refresh-tokens"] = settings.isReuseRefreshTokens()
        settingsMap["settings.token.access-token-time-to-live"] = 
            listOf("java.time.Duration", settings.accessTokenTimeToLive.seconds.toDouble())
        settingsMap["settings.token.refresh-token-time-to-live"] = 
            listOf("java.time.Duration", settings.refreshTokenTimeToLive.seconds.toDouble())
        settingsMap["settings.token.authorization-code-time-to-live"] = 
            listOf("java.time.Duration", settings.authorizationCodeTimeToLive.seconds.toDouble())
        
        return objectMapper.writeValueAsString(settingsMap)
    }
}