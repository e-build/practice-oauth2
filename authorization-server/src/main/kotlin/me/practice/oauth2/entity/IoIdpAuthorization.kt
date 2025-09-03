package me.practice.oauth2.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "io_idp_authorization")
data class IoIdpAuthorization(
    @Id
    val id: String,
    
    @Column(name = "registered_client_id", nullable = false)
    val registeredClientId: String,
    
    @Column(name = "principal_name", nullable = false)
    val principalName: String,
    
    @Column(name = "authorization_grant_type", nullable = false)
    val authorizationGrantType: String,
    
    @Column(name = "authorized_scopes", columnDefinition = "TEXT")
    val authorizedScopes: String? = null,
    
    @Column(name = "attributes", columnDefinition = "TEXT")
    val attributes: String? = null,
    
    @Column(name = "state", length = 500)
    val state: String? = null,
    
    @Column(name = "authorization_code_value", columnDefinition = "TEXT")
    val authorizationCodeValue: String? = null,
    
    @Column(name = "authorization_code_issued_at")
    val authorizationCodeIssuedAt: LocalDateTime? = null,
    
    @Column(name = "authorization_code_expires_at")
    val authorizationCodeExpiresAt: LocalDateTime? = null,
    
    @Column(name = "authorization_code_metadata", columnDefinition = "TEXT")
    val authorizationCodeMetadata: String? = null,
    
    @Column(name = "access_token_value", columnDefinition = "TEXT")
    val accessTokenValue: String? = null,
    
    @Column(name = "access_token_issued_at")
    val accessTokenIssuedAt: LocalDateTime? = null,
    
    @Column(name = "access_token_expires_at")
    val accessTokenExpiresAt: LocalDateTime? = null,
    
    @Column(name = "access_token_metadata", columnDefinition = "TEXT")
    val accessTokenMetadata: String? = null,
    
    @Column(name = "access_token_type")
    val accessTokenType: String? = null,
    
    @Column(name = "access_token_scopes", columnDefinition = "TEXT")
    val accessTokenScopes: String? = null,
    
    @Column(name = "refresh_token_value", columnDefinition = "TEXT")
    val refreshTokenValue: String? = null,
    
    @Column(name = "refresh_token_issued_at")
    val refreshTokenIssuedAt: LocalDateTime? = null,
    
    @Column(name = "refresh_token_expires_at")
    val refreshTokenExpiresAt: LocalDateTime? = null,
    
    @Column(name = "refresh_token_metadata", columnDefinition = "TEXT")
    val refreshTokenMetadata: String? = null,
    
    @Column(name = "oidc_id_token_value", columnDefinition = "TEXT")
    val oidcIdTokenValue: String? = null,
    
    @Column(name = "oidc_id_token_issued_at")
    val oidcIdTokenIssuedAt: LocalDateTime? = null,
    
    @Column(name = "oidc_id_token_expires_at")
    val oidcIdTokenExpiresAt: LocalDateTime? = null,
    
    @Column(name = "oidc_id_token_metadata", columnDefinition = "TEXT")
    val oidcIdTokenMetadata: String? = null,
    
    @Column(name = "oidc_id_token_claims", columnDefinition = "TEXT")
    val oidcIdTokenClaims: String? = null,
    
    @Column(name = "user_code_value", columnDefinition = "TEXT")
    val userCodeValue: String? = null,
    
    @Column(name = "user_code_issued_at")
    val userCodeIssuedAt: LocalDateTime? = null,
    
    @Column(name = "user_code_expires_at")
    val userCodeExpiresAt: LocalDateTime? = null,
    
    @Column(name = "user_code_metadata", columnDefinition = "TEXT")
    val userCodeMetadata: String? = null,
    
    @Column(name = "device_code_value", columnDefinition = "TEXT")
    val deviceCodeValue: String? = null,
    
    @Column(name = "device_code_issued_at")
    val deviceCodeIssuedAt: LocalDateTime? = null,
    
    @Column(name = "device_code_expires_at")
    val deviceCodeExpiresAt: LocalDateTime? = null,
    
    @Column(name = "device_code_metadata", columnDefinition = "TEXT")
    val deviceCodeMetadata: String? = null
)