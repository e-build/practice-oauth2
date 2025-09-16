package me.practice.oauth2.service

import jakarta.servlet.http.HttpSession
import me.practice.oauth2.entity.ProviderType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * OAuth2 인증 과정에서 세션 기반 사용자 컨텍스트를 관리하는 서비스
 *
 * 보안 고려사항:
 * - 민감한 사용자 정보는 해시 처리하여 저장
 * - 세션 컨텍스트는 5분 TTL로 자동 만료
 * - 개인정보 보호를 위해 최소한의 정보만 저장
 *
 * @author DON-49 OAuth2 사용자 식별 개선
 * @since 2.1.0
 */
@Component
class SessionUserContextManager {

    private val logger = LoggerFactory.getLogger(SessionUserContextManager::class.java)

    companion object {
        private const val SESSION_CONTEXT_KEY = "oauth2_user_context"
        private const val CONTEXT_TTL_MINUTES = 5L
    }

    /**
     * 세션에 최소한의 사용자 컨텍스트 저장
     *
     * @param session HTTP 세션
     * @param userIdentifier 사용자 식별자 (이메일, ID 등)
     * @param providerType OAuth2 제공자 타입
     * @param clientId Shopl 클라이언트 ID
     */
    fun saveMinimalUserContext(
        session: HttpSession,
        userIdentifier: String?,
        providerType: ProviderType,
        clientId: String
    ) {
        if (userIdentifier.isNullOrBlank()) {
            logger.debug("User identifier is null or blank, skipping context save")
            return
        }

        try {
            val context = MinimalUserContext(
                hashedIdentifier = hashIdentifier(userIdentifier),
                originalIdentifier = userIdentifier, // 개발 편의를 위해 원본도 저장 (운영에서는 제거 고려)
                providerType = providerType,
                clientId = clientId,
                timestamp = LocalDateTime.now()
            )

            session.setAttribute(SESSION_CONTEXT_KEY, context)
            logger.debug("Saved user context to session: provider=${providerType}, clientId=${clientId}")
        } catch (e: Exception) {
            logger.warn("Failed to save user context to session", e)
        }
    }

    /**
     * 세션에서 사용자 컨텍스트 조회 및 TTL 검증
     *
     * @param session HTTP 세션
     * @return 유효한 사용자 컨텍스트, 없거나 만료된 경우 null
     */
    fun getMinimalUserContext(session: HttpSession?): MinimalUserContext? {
        if (session == null) {
            return null
        }

        try {
            val context = session.getAttribute(SESSION_CONTEXT_KEY) as? MinimalUserContext
                ?: return null

            // TTL 검증
            val ageInMinutes = ChronoUnit.MINUTES.between(context.timestamp, LocalDateTime.now())
            if (ageInMinutes > CONTEXT_TTL_MINUTES) {
                logger.debug("User context expired (${ageInMinutes}min > ${CONTEXT_TTL_MINUTES}min), removing from session")
                session.removeAttribute(SESSION_CONTEXT_KEY)
                return null
            }

            logger.debug("Retrieved valid user context: provider=${context.providerType}, age=${ageInMinutes}min")
            return context
        } catch (e: Exception) {
            logger.warn("Failed to retrieve user context from session", e)
            return null
        }
    }

    /**
     * 세션에서 사용자 컨텍스트 제거
     *
     * @param session HTTP 세션
     */
    fun clearUserContext(session: HttpSession?) {
        session?.removeAttribute(SESSION_CONTEXT_KEY)
        logger.debug("Cleared user context from session")
    }

    /**
     * 사용자 식별자를 안전하게 해시 처리
     *
     * @param identifier 원본 식별자
     * @return SHA-256 해시값
     */
    private fun hashIdentifier(identifier: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(identifier.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.warn("Failed to hash identifier, using fallback", e)
            "hashed_${identifier.hashCode()}"
        }
    }

    /**
     * 해시된 식별자에서 사용자 ID 추정
     *
     * 실제 구현에서는 더 정교한 매핑 로직을 사용할 수 있습니다.
     * 예: 별도 매핑 테이블, 캐시된 사용자 정보 등
     *
     * @param context 사용자 컨텍스트
     * @return 추정된 사용자 ID
     */
    fun deriveUserIdFromContext(context: MinimalUserContext): String? {
        // 개발 단계에서는 원본 식별자 사용 (운영에서는 더 안전한 방법 필요)
        return context.originalIdentifier?.let { identifier ->
            when {
                identifier.contains("@") -> {
                    // 이메일인 경우 로컬 부분을 사용자 ID로 활용
                    identifier.substringBefore("@")
                }
                identifier.matches(Regex("\\d+")) -> {
                    // 숫자로만 구성된 경우 그대로 사용
                    identifier
                }
                else -> {
                    // 기타 경우 식별자를 그대로 사용
                    identifier
                }
            }
        }
    }
}

/**
 * 세션에 저장되는 최소한의 사용자 컨텍스트
 *
 * 보안을 위해 필요 최소한의 정보만 포함하며,
 * 민감한 정보는 해시 처리하여 저장합니다.
 *
 * @property hashedIdentifier 해시 처리된 사용자 식별자
 * @property originalIdentifier 원본 식별자 (개발용, 운영에서는 제거 고려)
 * @property providerType OAuth2 제공자 타입
 * @property clientId Shopl 클라이언트 ID
 * @property timestamp 컨텍스트 생성 시간 (TTL 관리용)
 */
data class MinimalUserContext(
    val hashedIdentifier: String,
    val originalIdentifier: String?, // 개발용, 운영에서는 보안 고려하여 제거
    val providerType: ProviderType,
    val clientId: String,
    val timestamp: LocalDateTime
)