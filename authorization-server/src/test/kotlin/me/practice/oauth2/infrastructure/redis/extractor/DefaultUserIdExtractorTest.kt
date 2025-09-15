package me.practice.oauth2.infrastructure.redis.extractor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DefaultUserIdExtractorTest {

    private val extractor = DefaultUserIdExtractor()

    @Test
    fun `모든 경우에 처리 가능`() {
        // given
        val principalMap1 = mapOf("sub" to "test_user")
        val principalMap2 = mapOf("id" to "123")
        val principalMap3 = emptyMap<String, Any>()

        // when & then
        assertTrue(extractor.canHandle(principalMap1))
        assertTrue(extractor.canHandle(principalMap2))
        assertTrue(extractor.canHandle(principalMap3))
    }

    @Test
    fun `sub 클레임에서 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "sub" to "user_sub_123",
            "id" to "user_id_456"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("user_sub_123", userId)
    }

    @Test
    fun `id 필드에서 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "id" to "user_id_456",
            "preferred_username" to "testuser"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("user_id_456", userId)
    }

    @Test
    fun `preferred_username에서 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "preferred_username" to "testuser",
            "name" to "Test User"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("testuser", userId)
    }

    @Test
    fun `name에서 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "name" to "Test User",
            "oid" to "microsoft_oid_123"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("Test User", userId)
    }

    @Test
    fun `oid에서 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "oid" to "microsoft_oid_123",
            "email" to "test@example.com"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("microsoft_oid_123", userId)
    }

    @Test
    fun `email을 최후 수단으로 사용자 ID 추출`() {
        // given
        val principalMap = mapOf(
            "email" to "test@example.com"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("test@example.com", userId)
    }

    @Test
    fun `빈 문자열은 무시하고 다음 후보 사용`() {
        // given
        val principalMap = mapOf(
            "sub" to "",
            "id" to "   ",
            "preferred_username" to "testuser"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertEquals("testuser", userId)
    }

    @Test
    fun `모든 필드가 비어있는 경우 null 반환`() {
        // given
        val principalMap = mapOf(
            "sub" to "",
            "id" to "   ",
            "other_field" to "value"
        )

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertNull(userId)
    }

    @Test
    fun `빈 맵인 경우 null 반환`() {
        // given
        val principalMap = emptyMap<String, Any>()

        // when
        val userId = extractor.extractUserId(principalMap)

        // then
        assertNull(userId)
    }

    @Test
    fun `우선순위가 가장 낮음`() {
        // when
        val priority = extractor.getPriority()

        // then
        assertEquals(Int.MAX_VALUE, priority)
    }
}