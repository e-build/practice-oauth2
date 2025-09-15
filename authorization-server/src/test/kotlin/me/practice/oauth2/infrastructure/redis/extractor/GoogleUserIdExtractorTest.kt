package me.practice.oauth2.infrastructure.redis.extractor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GoogleUserIdExtractorTest {

    private val extractor = GoogleUserIdExtractor()

    @Test
    fun `Google 제공자인지 확인 - accounts_google_com 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "sub" to "123456789"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Google 제공자인지 확인 - googleapis_com 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://googleapis.com",
            "sub" to "123456789"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Google 제공자가 아닌 경우`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://kakao.com",
            "id" to "123456789"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertFalse(canHandle)
    }

    @Test
    fun `Google 사용자 ID 추출 성공`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "sub" to "google_user_123",
            "email" to "test@gmail.com"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("google_user_123", userId)
    }

    @Test
    fun `Google이 아닌 제공자의 경우 null 반환`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://kakao.com",
            "id" to "kakao_user_123"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertNull(userId)
    }

    @Test
    fun `sub 클레임이 없는 경우 null 반환`() {
        // given
        val principalMap = mapOf(
            "iss" to "https://accounts.google.com",
            "email" to "test@gmail.com"
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
        assertEquals(10, priority)
    }
}