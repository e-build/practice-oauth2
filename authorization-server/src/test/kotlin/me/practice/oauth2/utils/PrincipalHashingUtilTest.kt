package me.practice.oauth2.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PrincipalHashingUtilTest {

    @Test
    @DisplayName("hashPrincipal은 콜론으로 구분된 base64 인코딩된 솔트와 해시를 반환한다")
    fun hashPrincipalShouldReturnBase64EncodedSaltAndHashSeparatedByColon() {
        val principal = "test@example.com"
        
        val result = PrincipalHashingUtil.hashPrincipal(principal)
        
        val parts = result.split(":")
        assertEquals(2, parts.size)
        
        val salt = Base64.getDecoder().decode(parts[0])
        val hash = Base64.getDecoder().decode(parts[1])
        
        assertEquals(16, salt.size)
        assertEquals(32, hash.size)
    }

    @Test
    @DisplayName("hashPrincipal은 같은 principal에 대해 다른 해시를 생성한다")
    fun hashPrincipalShouldGenerateDifferentHashesForSamePrincipal() {
        val principal = "test@example.com"
        
        val hash1 = PrincipalHashingUtil.hashPrincipal(principal)
        val hash2 = PrincipalHashingUtil.hashPrincipal(principal)
        
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("hashPrincipal은 다른 principal에 대해 다른 해시를 생성한다")
    fun hashPrincipalShouldGenerateDifferentHashesForDifferentPrincipals() {
        val principal1 = "test1@example.com"
        val principal2 = "test2@example.com"
        
        val hash1 = PrincipalHashingUtil.hashPrincipal(principal1)
        val hash2 = PrincipalHashingUtil.hashPrincipal(principal2)
        
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("hashPrincipal은 빈 문자열을 처리할 수 있다")
    fun hashPrincipalShouldHandleEmptyString() {
        val principal = ""
        
        val result = PrincipalHashingUtil.hashPrincipal(principal)
        
        val parts = result.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotBlank())
        assertTrue(parts[1].isNotBlank())
    }

    @Test
    @DisplayName("hashPrincipal은 유니코드 문자를 처리할 수 있다")
    fun hashPrincipalShouldHandleUnicodeCharacters() {
        val principal = "테스트@한글.com"
        
        val result = PrincipalHashingUtil.hashPrincipal(principal)
        
        val parts = result.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotBlank())
        assertTrue(parts[1].isNotBlank())
    }

    @Test
    @DisplayName("hashPrincipal은 긴 문자열을 처리할 수 있다")
    fun hashPrincipalShouldHandleLongString() {
        val principal = "a".repeat(1000)
        
        val result = PrincipalHashingUtil.hashPrincipal(principal)
        
        val parts = result.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotBlank())
        assertTrue(parts[1].isNotBlank())
    }
}