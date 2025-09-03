package me.practice.oauth2.entity

import jakarta.persistence.*

@Entity
@Table(name = "io_idp_shopl_client_mapping")
data class IoIdpShoplClientMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "shopl_client_id", length = 20, nullable = false)
    val shoplClientId: String,
    
    @Column(name = "idp_client_id", nullable = false)
    val idpClientId: String
)