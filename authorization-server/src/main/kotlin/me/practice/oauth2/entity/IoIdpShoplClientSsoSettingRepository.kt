package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpShoplClientSsoSettingRepository : JpaRepository<IoIdpShoplClientSsoSetting, String> {
	fun findByShoplClientId(shoplClientId: String): IoIdpShoplClientSsoSetting?
	fun findBySsoType(ssoType: SsoType): List<IoIdpShoplClientSsoSetting>
	fun existsByShoplClientId(shoplClientId: String): Boolean
}