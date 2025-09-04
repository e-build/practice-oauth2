package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpAccountOauthLinkRepository : JpaRepository<IoIdpAccountOauthLink, String> {
	fun findByAccountId(accountId: String): List<IoIdpAccountOauthLink>
	fun findByShoplClientIdAndProviderTypeAndProviderUserId(
		shoplClientId: String,
		providerType: ProviderType,
		providerUserId: String,
	): IoIdpAccountOauthLink?

	fun findByProviderTypeAndEmailAtProvider(
		providerType: ProviderType,
		emailAtProvider: String,
	): List<IoIdpAccountOauthLink>

	fun deleteByAccountId(accountId: String)
}