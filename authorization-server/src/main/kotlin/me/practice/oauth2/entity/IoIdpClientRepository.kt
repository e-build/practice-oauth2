package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpClientRepository : JpaRepository<IoIdpClient, String> {
	fun findByIdpClientId(clientId: String): IoIdpClient?
	fun existsByIdpClientId(clientId: String): Boolean
}