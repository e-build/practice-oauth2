package me.practice.oauth2.infrastructure.redis.extractor

import org.springframework.stereotype.Component

/**
 * Kakao OAuth2 사용자 ID 추출기
 */
@Component
class KakaoUserIdExtractor : ProviderUserIdExtractor {

    override fun extractUserId(principalMap: Map<*, *>): String? {
        if (!canHandle(principalMap)) {
            return null
        }

        // Kakao는 "id" 필드를 사용자 식별자로 사용
        return principalMap["id"]?.toString()
    }

    override fun canHandle(principalMap: Map<*, *>): Boolean {
        // Kakao 제공자인지 확인
        // Kakao는 response 구조를 가지고 있거나, id가 숫자 형태로 제공됨
        val hasKakaoStructure = principalMap["response"] != null
        val hasKakaoId = principalMap["id"] != null &&
                        (principalMap["id"] is Number ||
                         (principalMap["id"] as? String)?.matches(Regex("\\d+")) == true)

        return hasKakaoStructure || hasKakaoId
    }

    override fun getPriority(): Int = 20
}