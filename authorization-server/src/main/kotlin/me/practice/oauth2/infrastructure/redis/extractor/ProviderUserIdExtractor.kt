package me.practice.oauth2.infrastructure.redis.extractor

/**
 * OAuth2 제공자별 사용자 ID 추출 인터페이스
 * Strategy Pattern 적용
 */
interface ProviderUserIdExtractor {
    /**
     * OAuth2User 속성에서 사용자 ID 추출
     * @param principalMap OAuth2User의 속성 맵
     * @return 추출된 사용자 ID, 해당 제공자가 아니거나 추출 실패 시 null
     */
    fun extractUserId(principalMap: Map<*, *>): String?

    /**
     * 이 추출기가 처리할 수 있는 제공자인지 확인
     */
    fun canHandle(principalMap: Map<*, *>): Boolean

    /**
     * 추출기 우선순위 (낮을수록 우선)
     */
    fun getPriority(): Int = 100
}