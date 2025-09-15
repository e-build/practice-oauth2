package me.practice.oauth2.infrastructure.redis.extractor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KakaoUserIdExtractorTest {

    private val extractor = KakaoUserIdExtractor()

    @Test
    fun `Kakao 제공자인지 확인 - response 구조 포함된 경우`() {
        // given
        val principalMap = mapOf(
            "response" to mapOf("id" to "123456789"),
            "id" to "123456789"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Kakao 제공자인지 확인 - 숫자 ID인 경우`() {
        // given
        val principalMap = mapOf(
            "id" to 123456789
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Kakao 제공자인지 확인 - 문자열 숫자 ID인 경우`() {
        // given
        val principalMap = mapOf(
            "id" to "123456789"
        )

        // when
        val canHandle = extractor.canHandle(principalMap)

        // then
        assertTrue(canHandle)
    }

    @Test
    fun `Kakao 제공자가 아닌 경우`() {
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
    fun `Kakao 사용자 ID 추출 성공 - 숫자 타입`() {
        // given
        val principalMap = mapOf(
            "id" to 123456789,
            "properties" to mapOf("nickname" to "테스트유저")
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("123456789", userId)
    }

    @Test
    fun `Kakao 사용자 ID 추출 성공 - 문자열 타입`() {
        // given
        val principalMap = mapOf(
            "id" to "987654321",
            "properties" to mapOf("nickname" to "테스트유저")
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("987654321", userId)
    }

    @Test
    fun `Kakao이 아닌 제공자의 경우 null 반환`() {
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
        assertEquals(20, priority)
    }
}