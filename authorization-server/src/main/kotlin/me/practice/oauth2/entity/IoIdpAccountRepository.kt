package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface IoIdpAccountRepository : JpaRepository<IoIdpAccount, String> {
	fun findByEmail(email: String): IoIdpAccount?
	fun findByPhone(phone: String): IoIdpAccount?
	fun findByShoplClientIdAndShoplUserId(shoplClientId: String, shoplUserId: String): IoIdpAccount?
	fun findByShoplClientIdAndEmail(shoplClientId: String, email: String): IoIdpAccount?
	fun findByShoplClientIdAndPhone(shoplClientId: String, phone: String): IoIdpAccount?
}