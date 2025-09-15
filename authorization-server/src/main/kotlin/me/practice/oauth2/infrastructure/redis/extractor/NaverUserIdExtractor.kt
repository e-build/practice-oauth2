package me.practice.oauth2.infrastructure.redis.extractor

import org.springframework.stereotype.Component

/**
 * Naver OAuth2 사용자 ID 추출기
 */
@Component
class NaverUserIdExtractor : ProviderUserIdExtractor {

    override fun extractUserId(principalMap: Map<*, *>): String? {
        if (!canHandle(principalMap)) {
            return null
        }

        // Naver는 response.id 구조를 사용
        val response = principalMap["response"] as? Map<*, *>
        return response?.get("id")?.toString()
            ?: principalMap["id"]?.toString() // 혹시 직접 id가 있는 경우도 처리
    }

    override fun canHandle(principalMap: Map<*, *>): Boolean {
        // Naver 제공자인지 확인
        // Naver는 response 객체 안에 사용자 정보를 담는 구조
        val response = principalMap["response"] as? Map<*, *>
        return response?.containsKey("id") == true ||
               response?.containsKey("email") == true ||
               response?.containsKey("name") == true
    }

    override fun getPriority(): Int = 30
}