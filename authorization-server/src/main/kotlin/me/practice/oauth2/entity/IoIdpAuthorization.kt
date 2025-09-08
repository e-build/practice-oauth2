package me.practice.oauth2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
	name = "io_idp_authorization",
	catalog = "shopl_authorization"
)
data class IoIdpAuthorization(
	@Id
	@Column
	var id: String = "",

	var registeredClientId: String? = null,
	var principalName: String? = null,
	var authorizationGrantType: String? = null,

	@Column(length = 1000)
	var authorizedScopes: String? = null,

	@Column(length = 4000)
	var attributes: String? = null,

	@Column(length = 500)
	var state: String? = null,

	@Column(length = 4000)
	var authorizationCodeValue: String? = null,
	var authorizationCodeIssuedAt: Instant? = null,
	var authorizationCodeExpiresAt: Instant? = null,
	var authorizationCodeMetadata: String? = null,

	@Column(length = 4000)
	var accessTokenValue: String? = null,
	var accessTokenIssuedAt: Instant? = null,
	var accessTokenExpiresAt: Instant? = null,

	@Column(length = 2000)
	var accessTokenMetadata: String? = null,

	var accessTokenType: String? = null,

	@Column(length = 1000)
	var accessTokenScopes: String? = null,

	@Column(length = 4000)
	var refreshTokenValue: String? = null,
	var refreshTokenIssuedAt: Instant? = null,
	var refreshTokenExpiresAt: Instant? = null,

	@Column(length = 2000)
	var refreshTokenMetadata: String? = null,

	@Column(length = 4000)
	var oidcIdTokenValue: String? = null,
	var oidcIdTokenIssuedAt: Instant? = null,
	var oidcIdTokenExpiresAt: Instant? = null,

	@Column(length = 2000)
	var oidcIdTokenMetadata: String? = null,

	@Column(length = 2000)
	var oidcIdTokenClaims: String? = null,

	@Column(length = 4000)
	var userCodeValue: String? = null,
	var userCodeIssuedAt: Instant? = null,
	var userCodeExpiresAt: Instant? = null,

	@Column(length = 2000)
	var userCodeMetadata: String? = null,

	@Column(length = 4000)
	var deviceCodeValue: String? = null,
	var deviceCodeIssuedAt: Instant? = null,
	var deviceCodeExpiresAt: Instant? = null,

	@Column(length = 2000)
	var deviceCodeMetadata: String? = null,
)