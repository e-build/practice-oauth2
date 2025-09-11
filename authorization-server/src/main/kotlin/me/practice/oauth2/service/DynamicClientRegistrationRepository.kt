package me.practice.oauth2.service

import me.practice.oauth2.entity.SsoType
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.stereotype.Component

/**
 * 동적 OAuth2 클라이언트 등록 리포지토리
 * 데이터베이스의 SSO 설정을 기반으로 OAuth2 클라이언트 등록 정보를 동적으로 제공
 */
@Component
class DynamicClientRegistrationRepository(
    private val ssoConfigurationService: SsoConfigurationService
) : ClientRegistrationRepository {

    /**
     * 등록 ID로 클라이언트 등록 정보 조회
     */
    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        // 등록 ID에서 클라이언트 ID 추출
        val parts = registrationId.split("-")
        if (parts.size < 2) return null
        
        val ssoTypeStr = parts[0]
        val shoplClientId = parts.drop(1).joinToString("-")
        
        val ssoType = try {
            SsoType.valueOf(ssoTypeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            return null
        }

        val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId) ?: return null
        
        if (ssoSettings.ssoType != ssoType) return null

        return when (ssoType) {
            SsoType.OIDC -> buildOidcClientRegistration(ssoSettings, registrationId)
            SsoType.SAML -> null // SAML은 별도 처리 필요
        }
    }

    /**
     * OIDC 클라이언트 등록 정보 생성
     */
    private fun buildOidcClientRegistration(
        settings: me.practice.oauth2.entity.IoIdpShoplClientSsoSetting,
        registrationId: String
    ): ClientRegistration? {
        if (!ssoConfigurationService.isValidOidcConfiguration(settings)) {
            return null
        }

        val scopes = ssoConfigurationService.parseOidcScopes(settings)
        val redirectUris = ssoConfigurationService.parseRedirectUris(settings)
        
        val redirectUri = if (redirectUris.isNotEmpty()) {
            redirectUris.first()
        } else {
            "http://localhost:9000/login/oauth2/code/$registrationId"
        }

        return ClientRegistration.withRegistrationId(registrationId)
            .clientId(settings.oidcClientId!!)
            .clientSecret(settings.oidcClientSecret!!)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope(scopes)
            .authorizationUri("${settings.oidcIssuer}/auth")
            .tokenUri("${settings.oidcIssuer}/token")
            .userInfoUri("${settings.oidcIssuer}/userinfo")
            .userNameAttributeName("sub")
            .jwkSetUri("${settings.oidcIssuer}/certs")
            .issuerUri(settings.oidcIssuer)
            .clientName("SSO Provider - ${settings.shoplClientId}")
            .build()
    }

    /**
     * 모든 활성화된 SSO 제공자의 등록 ID 목록 반환
     */
    fun getAllActiveRegistrationIds(): List<String> {
        return ssoConfigurationService.getAllSsoSettings()
            .filter { it.ssoType == SsoType.OIDC && ssoConfigurationService.isValidOidcConfiguration(it) }
            .map { ssoConfigurationService.generateProviderRegistrationId(it) }
    }

    /**
     * 특정 클라이언트의 SSO 제공자 등록 ID 목록 반환
     */
    fun getRegistrationIdsForClient(shoplClientId: String): List<String> {
        val ssoSettings = ssoConfigurationService.getSsoSettings(shoplClientId)
        return if (ssoSettings != null && ssoSettings.ssoType == SsoType.OIDC && 
                   ssoConfigurationService.isValidOidcConfiguration(ssoSettings)) {
            listOf(ssoConfigurationService.generateProviderRegistrationId(ssoSettings))
        } else {
            emptyList()
        }
    }
}