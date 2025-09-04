package me.practice.oauth2.entity

import jakarta.persistence.*

@Entity
@Table(
	name = "io_idp_authorizationconsent",
	catalog = "shopl_authentication"
)
data class IoIdpAuthorizationConsent(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null,

	@Column(name = "registered_client_id")
	val registeredClientId: String,

	@Column(name = "principal_name")
	val principalName: String,

	@Column(name = "authorities", length = 1000, nullable = false)
	val authorities: String,
)