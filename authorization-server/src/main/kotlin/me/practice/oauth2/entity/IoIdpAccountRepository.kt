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
	
	// 추가: shopl client ID와 phone으로 조회
	fun findByShoplClientIdAndPhone(shoplClientId: String, phone: String): IoIdpAccount?

	@Query("SELECT a FROM IoIdpAccount a WHERE a.status = :status AND a.delDt IS NULL")
	fun findByStatus(status: String): List<IoIdpAccount>

	@Query("SELECT a FROM IoIdpAccount a WHERE a.lockedUntilDt IS NOT NULL AND a.lockedUntilDt < :now")
	fun findAccountsToUnlock(now: LocalDateTime): List<IoIdpAccount>

	fun existsByEmail(email: String): Boolean
	fun existsByPhone(phone: String): Boolean
}