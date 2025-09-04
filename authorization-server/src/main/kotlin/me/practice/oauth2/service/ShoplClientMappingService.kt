package me.practice.oauth2.service

import me.practice.oauth2.entity.IoIdpShoplClientMapping
import me.practice.oauth2.entity.IoIdpShoplClientMappingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Shopl Client와 OAuth Client 매핑 관리 서비스
 * 
 * 비즈니스 관계:
 * - 1개 Shopl Client → N개 OAuth Client (1:N)
 * - 1개 OAuth Client → 1개 Shopl Client (N:1)
 */
@Service
class ShoplClientMappingService(
    private val ioIdpShoplClientMappingRepository: IoIdpShoplClientMappingRepository
) {

    /**
     * Shopl Client ID로 매핑된 모든 OAuth Client ID 조회
     * 하나의 Shopl Client가 여러 OAuth Client를 가질 수 있음
     */
    @Transactional(readOnly = true)
    fun findOAuthClientIdsByShoplClientId(shoplClientId: String): List<String> {
        return ioIdpShoplClientMappingRepository.findByShoplClientId(shoplClientId)
            .map { it.idpClientId }
    }

    /**
     * OAuth Client ID로 소속된 Shopl Client ID 조회
     * 하나의 OAuth Client는 하나의 Shopl Client에만 속함
     */
    @Transactional(readOnly = true)
    fun findShoplClientIdByIdpClientId(oauthClientId: String): String? {
        return ioIdpShoplClientMappingRepository.findByIdpClientId(oauthClientId)?.shoplClientId
    }

    /**
     * Shopl Client의 기본 OAuth Client ID 조회 (첫 번째 클라이언트)
     * 레거시 호환성을 위해 유지
     */
    @Transactional(readOnly = true)
    fun findPrimaryOAuthClientIdByShoplClientId(shoplClientId: String): String? {
        return ioIdpShoplClientMappingRepository.findByShoplClientId(shoplClientId)
            .firstOrNull()?.idpClientId
    }

    /**
     * 특정 OAuth Client가 특정 Shopl Client에 속하는지 확인
     */
    @Transactional(readOnly = true)
    fun isOAuthClientBelongsToShoplClient(shoplClientId: String, oauthClientId: String): Boolean {
        return ioIdpShoplClientMappingRepository.existsByShoplClientIdAndIdpClientId(shoplClientId, oauthClientId)
    }

    /**
     * Shopl Client와 OAuth Client 매핑 생성
     */
    @Transactional
    fun createMapping(shoplClientId: String, oauthClientId: String): IoIdpShoplClientMapping {
        // 이미 매핑이 존재하는지 확인
        if (ioIdpShoplClientMappingRepository.existsByShoplClientIdAndIdpClientId(shoplClientId, oauthClientId)) {
            throw IllegalArgumentException("Mapping already exists: $shoplClientId -> $oauthClientId")
        }
        
        // OAuth Client가 다른 Shopl Client에 이미 매핑되어 있는지 확인
        val existingMapping = ioIdpShoplClientMappingRepository.findByIdpClientId(oauthClientId)
        if (existingMapping != null && existingMapping.shoplClientId != shoplClientId) {
            throw IllegalArgumentException("OAuth Client $oauthClientId is already mapped to ${existingMapping.shoplClientId}")
        }

        val mapping = IoIdpShoplClientMapping(
            id = null, // Auto increment
            shoplClientId = shoplClientId,
            idpClientId = oauthClientId
        )
        return ioIdpShoplClientMappingRepository.save(mapping)
    }

    /**
     * 특정 매핑 삭제
     */
    @Transactional
    fun deleteMapping(shoplClientId: String, oauthClientId: String) {
        ioIdpShoplClientMappingRepository.deleteByShoplClientIdAndIdpClientId(shoplClientId, oauthClientId)
    }

    /**
     * Shopl Client의 모든 OAuth Client 매핑 삭제
     */
    @Transactional
    fun deleteAllMappingsForShoplClient(shoplClientId: String) {
        ioIdpShoplClientMappingRepository.deleteByShoplClientId(shoplClientId)
    }

    /**
     * OAuth Client 매핑 삭제 (해당 OAuth Client 삭제 시 사용)
     */
    @Transactional
    fun deleteMappingForOAuthClient(oauthClientId: String) {
        ioIdpShoplClientMappingRepository.deleteByIdpClientId(oauthClientId)
    }

    /**
     * Shopl Client별 OAuth Client 개수 조회
     */
    @Transactional(readOnly = true)
    fun countOAuthClientsByShoplClient(shoplClientId: String): Int {
        return ioIdpShoplClientMappingRepository.findByShoplClientId(shoplClientId).size
    }

    /**
     * 모든 Shopl Client의 OAuth Client 매핑 정보 조회
     */
    @Transactional(readOnly = true)
    fun getAllMappings(): Map<String, List<String>> {
        return ioIdpShoplClientMappingRepository.findAll()
            .groupBy({ it.shoplClientId }, { it.idpClientId })
    }
}