package me.practice.oauth2.client.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoClientOauthOptionRepository : JpaRepository<IoClientOauthOption, String> {
	fun findByClientId(clientId: String): IoClientOauthOption?
	fun existsByClientId(clientId: String): Boolean
	fun deleteByClientId(clientId: String)
}