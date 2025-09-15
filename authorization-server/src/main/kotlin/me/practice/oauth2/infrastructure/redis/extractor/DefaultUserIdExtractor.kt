package me.practice.oauth2.infrastructure.redis.extractor

import org.springframework.stereotype.Component

/**
 * 기본 사용자 ID 추출기
 * 다른 제공자별 추출기가 모두 실패했을 때 사용하는 폴백 추출기
 */
@Component
class DefaultUserIdExtractor : ProviderUserIdExtractor {

    override fun extractUserId(principalMap: Map<*, *>): String? {
        // 일반적인 클레임들을 순서대로 시도
        val candidates = listOf(
            "sub",                    // OIDC 표준
            "id",                     // 일반적인 ID 필드
            "preferred_username",     // OIDC 표준
            "name",                   // 이름 필드
            "oid",                    // Microsoft
            "email"                   // 이메일 (최후 수단)
        )

        for (candidate in candidates) {
            val value = principalMap[candidate]?.toString()
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    override fun canHandle(principalMap: Map<*, *>): Boolean {
        // 모든 경우에 처리 가능 (폴백 역할)
        return true
    }

    override fun getPriority(): Int = Int.MAX_VALUE // 가장 낮은 우선순위
}