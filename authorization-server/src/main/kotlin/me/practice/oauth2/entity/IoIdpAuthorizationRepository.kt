package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IoIdpAuthorizationRepository : JpaRepository<IoIdpAuthorization, String> {
	fun findByRegisteredClientIdAndPrincipalName(
		registeredClientId: String,
		principalName: String,
	): List<IoIdpAuthorization>

	@Query("SELECT a FROM IoIdpAuthorization a WHERE a.authorizationCodeValue = :token OR a.accessTokenValue = :token OR a.refreshTokenValue = :token")
	fun findByToken(token: String): IoIdpAuthorization?

	fun deleteByRegisteredClientIdAndPrincipalName(registeredClientId: String, principalName: String)
}