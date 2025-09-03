package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpShoplClientMappingRepository : JpaRepository<IoIdpShoplClientMapping, Long> {
	fun findByShoplClientId(shoplClientId: String): List<IoIdpShoplClientMapping>
	fun findByIdpClientId(idpClientId: String): List<IoIdpShoplClientMapping>
	fun findByShoplClientIdAndIdpClientId(shoplClientId: String, idpClientId: String): IoIdpShoplClientMapping?
}