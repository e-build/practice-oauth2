package me.practice.oauth2.infrastructure.redis.extractor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NaverUserIdExtractorTest {

    private val extractor = NaverUserIdExtractor()

    @Test
    fun `Naver 제공자인지 확인 - response 구조의 id 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf(
                "id" to "naver_user_123",
                "email" to "test@naver.com"
            )
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Naver 제공자인지 확인 - response 구조의 email 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf(
                "email" to "test@naver.com",
                "name" to "테스트유저"
            )
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Naver 제공자인지 확인 - response 구조의 name 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf(
                "name" to "테스트유저",
                "nickname" to "테스트"
            )
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Naver 제공자가 아닌 경우`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "sub" to "google_user_123"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertFalse(canHandle)
    }

    @Test
    fun `Naver 사용자 ID 추출 성공 - response 구조에서`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf(
                "id" to "naver_user_123",
                "email" to "test@naver.com",
                "name" to "테스트유저"
            )
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("naver_user_123", userId)
    }

    @Test
    fun `Naver 사용자 ID 추출 성공 - 직접 id 필드에서`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf("email" to "test@naver.com"),
            "id" to "fallback_user_123"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("fallback_user_123", userId)
    }

    @Test
    fun `Naver이 아닌 제공자의 경우 null 반환`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "sub" to "google_user_123"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertNull(userId)
    }

    @Test
    fun `우선순위 확인`() {
        // when
        val priority = extractor.getPriority()

        // then
        assertEquals(30, priority)
    }
}