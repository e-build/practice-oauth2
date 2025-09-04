package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface IoIdpAuthorizationRepository : JpaRepository<IoIdpAuthorization, String> {
	fun findByState(state: String): Optional<IoIdpAuthorization?>

	fun findByAuthorizationCodeValue(authorizationCode: String): Optional<IoIdpAuthorization?>

	fun findByAccessTokenValue(accessToken: String): Optional<IoIdpAuthorization?>

	fun findByRefreshTokenValue(refreshToken: String): Optional<IoIdpAuthorization?>

	fun findByOidcIdTokenValue(idToken: String): Optional<IoIdpAuthorization?>

	fun findByUserCodeValue(userCode: String): Optional<IoIdpAuthorization?>

	fun findByDeviceCodeValue(deviceCode: String): Optional<IoIdpAuthorization?>

	@Query(
		("select a from IoIdpAuthorization a where a.state = :token" +
				" or a.authorizationCodeValue = :token" +
				" or a.accessTokenValue = :token" +
				" or a.refreshTokenValue = :token" +
				" or a.oidcIdTokenValue = :token" +
				" or a.userCodeValue = :token" +
				" or a.deviceCodeValue = :token")
	)
	fun findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValueOrOidcIdTokenValueOrUserCodeValueOrDeviceCodeValue(@Param("token") token: String): Optional<IoIdpAuthorization?>
}