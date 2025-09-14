package me.practice.oauth2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.practice.oauth2.domain.IdpClient
import java.time.LocalDateTime

@Entity
@Table(
	name = "io_idp_client",
	catalog = "shopl_authorization"
)
data class IoIdpClient(
	@Id
	val id: String,

	@Column(name = "idp_client_id", nullable = false)
	val idpClientId: String,

	@Column(name = "shopl_client_id", nullable = false)
	val shoplClientId: String,

	@Column(name = "platform", nullable = false)
	@Enumerated(EnumType.STRING)
	val platform: IdpClient.Platform,

	@Column(name = "client_id_issued_at", nullable = false)
	val clientIdIssuedAt: LocalDateTime = LocalDateTime.now(),

	@Column(name = "client_secret")
	val clientSecret: String? = null,

	@Column(name = "client_secret_expires_at")
	val clientSecretExpiresAt: LocalDateTime? = null,

	@Column(name = "client_name", nullable = false)
	val clientName: String,

	@Column(name = "client_authentication_methods", length = 1000, nullable = false)
	val clientAuthenticationMethods: String,

	@Column(name = "authorization_grant_types", length = 1000, nullable = false)
	val authorizationGrantTypes: String,

	@Column(name = "redirect_uris", length = 1000)
	val redirectUris: String? = null,

	@Column(name = "post_logout_redirect_uris", length = 1000)
	val postLogoutRedirectUris: String? = null,

	@Column(name = "scopes", length = 1000, nullable = false)
	val scopes: String,

	@Column(name = "client_settings", length = 2000, nullable = false)
	val clientSettings: String,

	@Column(name = "token_settings", length = 2000, nullable = false)
	val tokenSettings: String,
)
