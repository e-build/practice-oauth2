package me.practice.oauth2.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "io_idp_account_oauth_link")
data class IoIdpAccountOauthLink(
    @Id
    @Column(length = 20)
    val id: String,
    
    @Column(name = "account_id", length = 20, nullable = false)
    val accountId: String,
    
    @Column(name = "shopl_client_id", length = 20, nullable = false)
    val shoplClientId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    val providerType: ProviderType,
    
    @Column(name = "provider_user_id", length = 191, nullable = false)
    val providerUserId: String,
    
    @Column(name = "email_at_provider", length = 320)
    val emailAtProvider: String? = null,
    
    @Column(name = "name_at_provider", length = 100)
    val nameAtProvider: String? = null,
    
    @Column(name = "raw_claims", columnDefinition = "JSON")
    val rawClaims: String? = null,
    
    @Column(name = "reg_dt", nullable = false)
    val regDt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "mod_dt")
    val modDt: LocalDateTime? = null,
    
    @Column(name = "del_dt")
    val delDt: LocalDateTime? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", foreignKey = ForeignKey(name = "fk_oauth_account_id"), insertable = false, updatable = false)
    val account: IoIdpAccount? = null
)

enum class ProviderType {
    GOOGLE, NAVER, KAKAO, APPLE, MICROSOFT, GITHUB, SAML, OIDC
}