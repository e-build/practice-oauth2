package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpAuthorizationConsentRepository : JpaRepository<IoIdpAuthorizationConsent, Long> {
    fun findByRegisteredClientIdAndPrincipalName(
        registeredClientId: String, 
        principalName: String
    ): IoIdpAuthorizationConsent?
    
    fun deleteByRegisteredClientIdAndPrincipalName(
        registeredClientId: String, 
        principalName: String
    )
    
    fun findByRegisteredClientId(registeredClientId: String): List<IoIdpAuthorizationConsent>
    fun findByPrincipalName(principalName: String): List<IoIdpAuthorizationConsent>
    fun deleteByRegisteredClientId(registeredClientId: String)
    fun deleteByPrincipalName(principalName: String)
}