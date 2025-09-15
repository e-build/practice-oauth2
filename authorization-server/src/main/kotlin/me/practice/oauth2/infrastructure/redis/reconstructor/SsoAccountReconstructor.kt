package me.practice.oauth2.infrastructure.redis.reconstructor

import me.practice.oauth2.entity.IoIdpAccount
import me.practice.oauth2.entity.IoIdpAccountRepository
import me.practice.oauth2.infrastructure.redis.extractor.ProviderUserIdExtractor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * SSO 계정 재구성 담당 클래스
 * 단일 책임: OAuth2User 속성에서 SSO 계정 조회/생성
 */
@Component
class SsoAccountReconstructor(
    providerUserIdExtractors: List<ProviderUserIdExtractor>
) {

    // 우선순위 순으로 정렬
    private val providerUserIdExtractors = providerUserIdExtractors.sortedBy { it.getPriority() }

    private val logger = LoggerFactory.getLogger(SsoAccountReconstructor::class.java)

    /**
     * SSO 계정 조회/생성
     * UserProvisioningService와 일관성 있게 처리
     */
    fun findOrCreateSsoAccount(
        principalMap: Map<*, *>,
        ioIdpAccountRepository: IoIdpAccountRepository
    ): IoIdpAccount {
        // OAuth2User 속성에서 기본 정보 추출
        val providerUserId = extractProviderUserId(principalMap)
        val email = principalMap["email"] as? String
        val name = extractDisplayName(principalMap)
        val shoplClientId = extractShoplClientId(principalMap)

        logger.debug("Looking for SSO account - providerUserId: $providerUserId, email: $email, clientId: $shoplClientId")

        // 1. 이메일로 기존 계정 검색 (가장 안전한 방법)
        if (email != null) {
            val existingAccount = ioIdpAccountRepository.findByShoplClientIdAndEmail(shoplClientId, email)
            if (existingAccount != null) {
                logger.debug("Found existing account by email: ${existingAccount.id}")
                return existingAccount
            }
        }

        // 2. provider_user_id 패턴으로 계정 ID 생성 후 검색
        val generatedAccountId = "sso_${providerUserId}"
        val accountById = runCatching {
            ioIdpAccountRepository.findById(generatedAccountId).orElse(null)
        }.getOrNull()

        if (accountById != null) {
            logger.debug("Found existing account by generated ID: ${accountById.id}")
            return accountById
        }

        // 3. 계정이 없는 경우 최소한의 정보로 생성 (방어 코드)
        logger.warn("Creating fallback account for SSO user: $providerUserId (email: $email)")

        return IoIdpAccount(
            id = generatedAccountId,
            shoplClientId = shoplClientId,
            shoplUserId = providerUserId,
            shoplLoginId = email ?: "${providerUserId}@sso.fallback",
            email = email,
            phone = null,
            name = name,
            status = "ACTIVE",
            isCertEmail = email != null,
            isTempPwd = false,
            regDt = LocalDateTime.now()
        )
    }

    /**
     * 다양한 OAuth2 제공자에서 사용자 ID 추출
     * Strategy Pattern 적용하여 확장 가능하도록 구성
     */
    private fun extractProviderUserId(principalMap: Map<*, *>): String {
        // 등록된 추출기들을 순서대로 시도
        for (extractor in providerUserIdExtractors) {
            val userId = extractor.extractUserId(principalMap)
            if (userId != null) {
                logger.debug("Extracted provider user ID using ${extractor.javaClass.simpleName}: $userId")
                return userId
            }
        }

        // 모든 추출기가 실패한 경우 폴백 로직
        val availableKeys = principalMap.keys.joinToString(", ")
        val errorMsg = "Could not extract provider user ID from principal. Available keys: [$availableKeys]"
        logger.error(errorMsg)
        throw IllegalArgumentException(errorMsg)
    }

    /**
     * 사용자 표시 이름 추출
     */
    private fun extractDisplayName(principalMap: Map<*, *>): String? {
        return principalMap["name"] as? String
            ?: principalMap["given_name"] as? String
            ?: principalMap["nickname"] as? String
            ?: (principalMap["response"] as? Map<*, *>)?.get("name") as? String
    }

    /**
     * Shopl 클라이언트 ID 추출
     */
    private fun extractShoplClientId(principalMap: Map<*, *>): String {
        return principalMap["client_id"] as? String
            ?: principalMap["aud"] as? String  // audience claim
            ?: "CLIENT001"  // 기본값
    }
}