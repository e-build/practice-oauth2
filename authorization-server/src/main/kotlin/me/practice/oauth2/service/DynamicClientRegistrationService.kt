package me.practice.oauth2.service

import me.practice.oauth2.domain.IdpClient
import me.practice.oauth2.entity.IoIdpShoplClientSsoSetting
import me.practice.oauth2.entity.SsoType
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * 동적 OAuth2 클라이언트 등록 서비스
 * SSO 설정을 기반으로 OAuth2 클라이언트를 동적으로 생성하고 관리
 */
@Service
class DynamicClientRegistrationService(
    private val registeredClientRepository: RegisteredClientRepository,
    private val ssoConfigurationService: SsoConfigurationService
) {

    /**
     * SSO 설정으로부터 OAuth2 클라이언트 동적 등록
     */
    fun registerClientFromSsoSettings(settings: IoIdpShoplClientSsoSetting): RegisteredClient? {
        return when (settings.ssoType) {
            SsoType.OIDC -> registerOidcClient(settings)
            SsoType.SAML -> registerSamlClient(settings)
        }
    }

    /**
     * OIDC 클라이언트 등록
     */
    private fun registerOidcClient(settings: IoIdpShoplClientSsoSetting): RegisteredClient? {
        if (!ssoConfigurationService.isValidOidcConfiguration(settings)) {
            return null
        }

        val registrationId = ssoConfigurationService.generateProviderRegistrationId(settings)
        val redirectUris = ssoConfigurationService.parseRedirectUris(settings)
        val scopes = ssoConfigurationService.parseOidcScopes(settings)

        val clientBuilder = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(settings.oidcClientId!!)
            .clientSecret(settings.oidcClientSecret!!)
            .clientName("SSO Client - ${settings.shoplClientId}")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .clientIdIssuedAt(Instant.now())

        // 리다이렉트 URI 설정
        if (redirectUris.isNotEmpty()) {
            redirectUris.forEach { uri ->
                clientBuilder.redirectUri(uri)
            }
        } else {
            // 기본 리다이렉트 URI
            clientBuilder.redirectUri("http://localhost:9000/login/oauth2/code/$registrationId")
        }

        // 스코프 설정
        scopes.forEach { scope ->
            clientBuilder.scope(scope)
        }

        // 클라이언트 설정
        val clientSettings = ClientSettings.builder()
            .requireAuthorizationConsent(true)
            .requireProofKey(false)
            .setting("shoplClientId", settings.shoplClientId)
            .setting("platform", IdpClient.Platform.DASHBOARD)
            .build()

        // 토큰 설정
        val tokenSettings = TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofMinutes(60))
            .refreshTokenTimeToLive(Duration.ofDays(30))
            .authorizationCodeTimeToLive(Duration.ofMinutes(10))
            .reuseRefreshTokens(true)
            .build()

        val registeredClient = clientBuilder
            .clientSettings(clientSettings)
            .tokenSettings(tokenSettings)
            .build()

        // 클라이언트 저장
        registeredClientRepository.save(registeredClient)
        
        return registeredClient
    }

    /**
     * SAML 클라이언트 등록 (향후 구현)
     */
    private fun registerSamlClient(settings: IoIdpShoplClientSsoSetting): RegisteredClient? {
        // SAML은 OAuth2 RegisteredClient로 직접 등록하지 않음
        // 별도의 SAML 설정 관리가 필요
        return null
    }

    /**
     * 특정 클라이언트의 모든 SSO 제공자 등록
     */
    fun registerAllSsoProvidersForClient(shoplClientId: String): List<RegisteredClient> {
        val registeredClients = mutableListOf<RegisteredClient>()
        
        val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId)
        ssoSettings?.let { settings ->
            registerClientFromSsoSettings(settings)?.let { client ->
                registeredClients.add(client)
            }
        }
        
        return registeredClients
    }

    /**
     * 모든 SSO 설정에 대한 클라이언트 등록
     */
    fun registerAllSsoProviders(): List<RegisteredClient> {
        val registeredClients = mutableListOf<RegisteredClient>()
        
        val allSsoSettings = ssoConfigurationService.getAllSsoSettings()
        allSsoSettings.forEach { settings ->
            registerClientFromSsoSettings(settings)?.let { client ->
                registeredClients.add(client)
            }
        }
        
        return registeredClients
    }

    /**
     * 클라이언트 등록 여부 확인
     */
    fun isClientRegistered(clientId: String): Boolean {
        return registeredClientRepository.findByClientId(clientId) != null
    }

    /**
     * SSO 설정 기반 클라이언트 ID 생성
     */
    fun generateClientId(settings: IoIdpShoplClientSsoSetting): String {
        return when (settings.ssoType) {
            SsoType.OIDC -> settings.oidcClientId ?: "oidc-${settings.shoplClientId}"
            SsoType.SAML -> "saml-${settings.shoplClientId}"
        }
    }

    /**
     * 등록된 클라이언트 제거
     */
    fun unregisterClient(clientId: String) {
        val client = registeredClientRepository.findByClientId(clientId)
        // Spring Security OAuth2에는 기본적으로 remove 메서드가 없으므로
        // 필요시 별도 구현 필요
    }
}