package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository

interface IoIdpAuthorizationConsentRepository : JpaRepository<IoIdpAuthorizationConsent, Long>