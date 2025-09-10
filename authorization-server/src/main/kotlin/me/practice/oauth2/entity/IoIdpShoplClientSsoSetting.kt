package me.practice.oauth2.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
	name = "io_idp_shopl_client_sso_setting",
	catalog = "shopl_authorization"
)
data class IoIdpShoplClientSsoSetting(
    @Id
    @Column(length = 20)
    val id: String,
    
    @Column(name = "shopl_client_id", length = 20, nullable = false, unique = true)
    val shoplClientId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sso_type", nullable = false)
    val ssoType: SsoType,
    
    // OIDC 필드들
	@Column(name = "oidc_client_id", length = 50)
	val oidcClientId: String? = null,

	@Column(name = "oidc_secret", length = 500)
	val oidcClientSecret: String? = null,

    @Column(name = "oidc_issuer", length = 500)
    val oidcIssuer: String? = null,
    
    @Column(name = "oidc_scopes", length = 500)
    val oidcScopes: String? = "openid email profile",
    
    @Column(name = "oidc_response_type", length = 100)
    val oidcResponseType: String? = "code",
    
    @Column(name = "oidc_response_mode", length = 50)
    val oidcResponseMode: String? = null,
    
    @Column(name = "oidc_claims_mapping", columnDefinition = "JSON")
    val oidcClaimsMapping: String? = null,
    
    // SAML 필드들
    @Column(name = "saml_entity_id", length = 500)
    val samlEntityId: String? = null,
    
    @Column(name = "saml_sso_url", length = 500)
    val samlSsoUrl: String? = null,
    
    @Column(name = "saml_slo_url", length = 500)
    val samlSloUrl: String? = null,
    
    @Column(name = "saml_x509_cert", columnDefinition = "TEXT")
    val samlX509Cert: String? = null,
    
    @Column(name = "saml_name_id_format", length = 200)
    val samlNameIdFormat: String? = "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent",
    
    @Enumerated(EnumType.STRING)
    @Column(name = "saml_binding_sso")
    val samlBindingSso: SamlBinding? = SamlBinding.HTTP_POST,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "saml_binding_slo")
    val samlBindingSlo: SamlBinding? = SamlBinding.HTTP_POST,
    
    @Column(name = "saml_want_assertions_signed")
    val samlWantAssertionsSigned: Boolean? = true,
    
    @Column(name = "saml_want_response_signed")
    val samlWantResponseSigned: Boolean? = true,
    
    @Column(name = "saml_signature_algorithm", length = 100)
    val samlSignatureAlgorithm: String? = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256",
    
    @Column(name = "saml_digest_algorithm", length = 100)
    val samlDigestAlgorithm: String? = "http://www.w3.org/2001/04/xmlenc#sha256",
    
    @Column(name = "saml_attribute_mapping", columnDefinition = "JSON")
    val samlAttributeMapping: String? = null,
    
    // 공통 필드들
    @Column(name = "redirect_uris", columnDefinition = "JSON")
    val redirectUris: String? = null,
    
    @Column(name = "auto_provision", nullable = false)
    val autoProvision: Boolean = true,
    
    @Column(name = "default_role", length = 50)
    val defaultRole: String? = null,
    
    @Column(name = "reg_dt", nullable = false)
    val regDt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "mod_dt")
    val modDt: LocalDateTime? = null,
    
    @Column(name = "del_dt")
    val delDt: LocalDateTime? = null
)

enum class SsoType {
    OIDC, SAML
}

enum class SamlBinding {
    @Enumerated(EnumType.STRING)
    HTTP_POST,
    @Enumerated(EnumType.STRING) 
    HTTP_Redirect
}