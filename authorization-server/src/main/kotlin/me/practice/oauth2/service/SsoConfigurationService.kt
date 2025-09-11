package me.practice.oauth2.service

import me.practice.oauth2.entity.IoIdpShoplClientSsoSetting
import me.practice.oauth2.entity.IoIdpShoplClientSsoSettingRepository
import me.practice.oauth2.entity.SsoType
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * SSO 설정 관리 서비스
 * 데이터베이스의 SSO 설정을 동적으로 로드하고 관리
 */
@Service
class SsoConfigurationService(
    private val ioIdpShoplSsoSettingRepository: IoIdpShoplClientSsoSettingRepository
) {

    /**
     * 특정 클라이언트의 SSO 설정 조회
     */
    @Cacheable("ssoSettings", key = "#shoplClientId")
    @Transactional(readOnly = true)
    fun getSsoSettings(shoplClientId: String): IoIdpShoplClientSsoSetting? {
        return ioIdpShoplSsoSettingRepository.findByShoplClientId(shoplClientId)
    }

    /**
     * SSO 설정이 존재하는지 확인
     */
    @Transactional(readOnly = true)
    fun hasSsoConfiguration(shoplClientId: String): Boolean {
        return ioIdpShoplSsoSettingRepository.existsByShoplClientId(shoplClientId)
    }

    /**
     * 특정 SSO 타입의 모든 설정 조회
     */
    @Transactional(readOnly = true)
    fun getSsoSettingsByType(ssoType: SsoType): List<IoIdpShoplClientSsoSetting> {
        return ioIdpShoplSsoSettingRepository.findBySsoType(ssoType)
    }

    /**
     * 모든 SSO 설정 조회
     */
    @Transactional(readOnly = true)
    fun getAllSsoSettings(): List<IoIdpShoplClientSsoSetting> {
        return ioIdpShoplSsoSettingRepository.findAll()
    }

    /**
     * OIDC 설정이 유효한지 검증
     */
    fun isValidOidcConfiguration(settings: IoIdpShoplClientSsoSetting): Boolean {
        if (settings.ssoType != SsoType.OIDC) return false
        
        return !settings.oidcClientId.isNullOrBlank() &&
                !settings.oidcClientSecret.isNullOrBlank() &&
                !settings.oidcIssuer.isNullOrBlank()
    }

    /**
     * SAML 설정이 유효한지 검증
     */
    fun isValidSamlConfiguration(settings: IoIdpShoplClientSsoSetting): Boolean {
        if (settings.ssoType != SsoType.SAML) return false
        
        return !settings.samlEntityId.isNullOrBlank() &&
                !settings.samlSsoUrl.isNullOrBlank() &&
                !settings.samlX509Cert.isNullOrBlank()
    }

    /**
     * SSO 제공자의 고유 식별자 생성
     */
    fun generateProviderRegistrationId(settings: IoIdpShoplClientSsoSetting): String {
        return "${settings.ssoType.name.lowercase()}-${settings.shoplClientId}"
    }

    /**
     * OIDC 스코프 파싱
     */
    fun parseOidcScopes(settings: IoIdpShoplClientSsoSetting): Set<String> {
        return settings.oidcScopes?.split(",")?.map { it.trim() }?.toSet() ?: setOf("openid", "profile", "email")
    }

    /**
     * 리다이렉트 URI 파싱
     */
    fun parseRedirectUris(settings: IoIdpShoplClientSsoSetting): Set<String> {
        return settings.redirectUris?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
    }
}