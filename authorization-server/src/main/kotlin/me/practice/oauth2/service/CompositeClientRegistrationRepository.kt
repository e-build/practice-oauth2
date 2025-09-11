package me.practice.oauth2.service

import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component

/**
 * 복합 클라이언트 등록 리포지토리
 * 기존 application.yml 설정과 동적 데이터베이스 설정을 모두 지원
 */
@Component
class CompositeClientRegistrationRepository(
    private val staticClientRegistrationRepository: ClientRegistrationRepository,
    private val dynamicClientRegistrationRepository: DynamicClientRegistrationRepository
) : ClientRegistrationRepository {

    /**
     * 등록 ID로 클라이언트 등록 정보 조회
     * 1. 먼저 정적 설정(application.yml)에서 찾기
     * 2. 없으면 동적 설정(데이터베이스)에서 찾기
     */
    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        // 1. 먼저 정적 설정에서 찾기 (application.yml의 keycloak-acme 등)
        staticClientRegistrationRepository.findByRegistrationId(registrationId)?.let { 
            return it 
        }
        
        // 2. 동적 설정에서 찾기 (데이터베이스의 SSO 설정)
        return dynamicClientRegistrationRepository.findByRegistrationId(registrationId)
    }

    /**
     * 모든 사용 가능한 등록 ID 목록 반환
     */
    fun getAllRegistrationIds(): List<String> {
        val staticIds = getStaticRegistrationIds()
        val dynamicIds = dynamicClientRegistrationRepository.getAllActiveRegistrationIds()
        
        return (staticIds + dynamicIds).distinct()
    }

    /**
     * 특정 클라이언트의 사용 가능한 등록 ID 목록 반환
     */
    fun getRegistrationIdsForClient(shoplClientId: String): List<String> {
        val staticIds = getStaticRegistrationIds() // 정적 설정은 모든 클라이언트에서 사용 가능
        val dynamicIds = dynamicClientRegistrationRepository.getRegistrationIdsForClient(shoplClientId)
        
        return (staticIds + dynamicIds).distinct()
    }

    /**
     * 정적 등록 ID 목록 (application.yml에서 설정된 것들)
     */
    private fun getStaticRegistrationIds(): List<String> {
        // 알려진 정적 등록 ID들을 확인
        val knownStaticIds = listOf("keycloak-acme", "google", "github", "facebook")
        
        return knownStaticIds.filter { registrationId ->
            staticClientRegistrationRepository.findByRegistrationId(registrationId) != null
        }
    }

    /**
     * 등록 ID가 정적 설정인지 확인
     */
    fun isStaticRegistration(registrationId: String): Boolean {
        return staticClientRegistrationRepository.findByRegistrationId(registrationId) != null
    }

    /**
     * 등록 ID가 동적 설정인지 확인
     */
    fun isDynamicRegistration(registrationId: String): Boolean {
        return dynamicClientRegistrationRepository.findByRegistrationId(registrationId) != null
    }
}