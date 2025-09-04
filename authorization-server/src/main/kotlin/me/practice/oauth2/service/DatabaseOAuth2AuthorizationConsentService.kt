package me.practice.oauth2.service

import me.practice.oauth2.entity.IoIdpAuthorizationConsent
import me.practice.oauth2.entity.IoIdpAuthorizationConsentRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 데이터베이스 기반 OAuth2AuthorizationConsentService 구현체
 */
@Service
class DatabaseOAuth2AuthorizationConsentService(
    private val consentRepository: IoIdpAuthorizationConsentRepository,
    private val registeredClientRepository: RegisteredClientRepository
) : OAuth2AuthorizationConsentService {

    @Transactional
    override fun save(authorizationConsent: OAuth2AuthorizationConsent) {
        val ioIdpConsent = convertToIoIdpAuthorizationConsent(authorizationConsent)
        consentRepository.save(ioIdpConsent)
    }

    @Transactional
    override fun remove(authorizationConsent: OAuth2AuthorizationConsent) {
        consentRepository.deleteByRegisteredClientIdAndPrincipalName(
            authorizationConsent.registeredClientId,
            authorizationConsent.principalName
        )
    }

    @Transactional(readOnly = true)
    override fun findById(registeredClientId: String, principalName: String): OAuth2AuthorizationConsent? {
        return consentRepository.findByRegisteredClientIdAndPrincipalName(registeredClientId, principalName)
            ?.let { convertToOAuth2AuthorizationConsent(it) }
    }

    private fun convertToIoIdpAuthorizationConsent(consent: OAuth2AuthorizationConsent): IoIdpAuthorizationConsent {
        return IoIdpAuthorizationConsent(
            id = null, // Auto increment
            registeredClientId = consent.registeredClientId,
            principalName = consent.principalName,
            authorities = consent.authorities.joinToString(",") { it.authority }
        )
    }

    private fun convertToOAuth2AuthorizationConsent(consent: IoIdpAuthorizationConsent): OAuth2AuthorizationConsent {
        val registeredClient = registeredClientRepository.findById(consent.registeredClientId)
            ?: throw IllegalArgumentException("Registered client not found: ${consent.registeredClientId}")

        val authorities = consent.authorities.split(",")
            .map { SimpleGrantedAuthority(it.trim()) }
            .toSet()

        return OAuth2AuthorizationConsent.withId(consent.registeredClientId, consent.principalName)
            .authorities { it.addAll(authorities) }
            .build()
    }
}