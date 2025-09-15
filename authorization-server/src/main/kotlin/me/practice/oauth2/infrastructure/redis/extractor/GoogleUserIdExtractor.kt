package me.practice.oauth2.infrastructure.redis.extractor

import org.springframework.stereotype.Component

/**
 * Google OAuth2 사용자 ID 추출기
 */
@Component
class GoogleUserIdExtractor : ProviderUserIdExtractor {

    override fun extractUserId(principalMap: Map<*, *>): String? {
        if (!canHandle(principalMap)) {
            return null
        }

        // Google은 "sub" 클레임을 사용자 식별자로 사용
        return principalMap["sub"] as? String
    }

    override fun canHandle(principalMap: Map<*, *>): Boolean {
        // Google 제공자인지 확인
        val issuer = principalMap["iss"] as? String
        return issuer?.contains("accounts.google.com") == true ||
               issuer?.contains("googleapis.com") == true
    }

    override fun getPriority(): Int = 10 // 높은 우선순위
}