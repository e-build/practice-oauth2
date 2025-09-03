package me.practice.oauth2.client.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "io_client_info")
data class IoClientInfo(
    @Id
    @Column(length = 20)
    val id: String,
    
    @Column(name = "NAME", length = 200, nullable = false)
    val name: String,
    
    @Column(name = "reg_dt", nullable = false)
    val regDt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "mod_dt")
    val modDt: LocalDateTime? = null,
    
    @OneToMany(mappedBy = "clientId", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val oauthOptions: List<IoClientOauthOption> = emptyList()
)