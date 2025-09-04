package me.practice.oauth2.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IoIdpShoplClientMappingRepository : JpaRepository<IoIdpShoplClientMapping, Long> {
    // 하나의 Shopl Client가 여러 OAuth Client에 매핑될 수 있음
    fun findByShoplClientId(shoplClientId: String): List<IoIdpShoplClientMapping>
    
    // 특정 OAuth Client가 어떤 Shopl Client에 속하는지 조회 (1:1 관계)
    fun findByIdpClientId(idpClientId: String): IoIdpShoplClientMapping?
    
    // 특정 매핑 조회
    fun findByShoplClientIdAndIdpClientId(shoplClientId: String, idpClientId: String): IoIdpShoplClientMapping?
    
    // 매핑 삭제
    fun deleteByShoplClientIdAndIdpClientId(shoplClientId: String, idpClientId: String)
    fun deleteByShoplClientId(shoplClientId: String)
    fun deleteByIdpClientId(idpClientId: String)
    
    // OAuth Client의 존재 여부 확인
    fun existsByIdpClientId(idpClientId: String): Boolean
    fun existsByShoplClientIdAndIdpClientId(shoplClientId: String, idpClientId: String): Boolean
}