package me.practice.oauth2.client.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoClientInfoRepository : JpaRepository<IoClientInfo, String> {
	fun findByName(name: String): IoClientInfo?
	fun findByNameContainingIgnoreCase(name: String): List<IoClientInfo>
	fun existsByName(name: String): Boolean
}