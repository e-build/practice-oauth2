package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpAuthorizationConsentRepository : JpaRepository<IoIdpAuthorizationConsent, Long> {
    fun findByIdpClientIdAndPrincipalName(
        registeredClientId: String, 
        principalName: String
    ): IoIdpAuthorizationConsent?
    
    fun deleteByIdpClientIdAndPrincipalName(
        registeredClientId: String, 
        principalName: String
    )
    
    fun findByIdpClientId(registeredClientId: String): List<IoIdpAuthorizationConsent>
    fun findByPrincipalName(principalName: String): List<IoIdpAuthorizationConsent>
    fun deleteByIdpClientId(registeredClientId: String)
    fun deleteByPrincipalName(principalName: String)
}