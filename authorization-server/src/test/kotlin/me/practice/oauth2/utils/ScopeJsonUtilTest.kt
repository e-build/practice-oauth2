package me.practice.oauth2.utils

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScopeJsonUtilTest {

    @Test
    @DisplayName("scopeSetToJson은 스코프 집합을 JSON 배열로 변환한다")
    fun scopeSetToJsonShouldConvertScopeSetToJsonArray() {
        val scopes = setOf("read", "write", "admin")
        
        val result = ScopeJsonUtil.scopeSetToJson(scopes)
        
        assertTrue(result.contains("read"))
        assertTrue(result.contains("write"))
        assertTrue(result.contains("admin"))
        assertTrue(result.startsWith("["))
        assertTrue(result.endsWith("]"))
    }

    @Test
    @DisplayName("scopeSetToJson은 빈 집합을 처리할 수 있다")
    fun scopeSetToJsonShouldHandleEmptySet() {
        val scopes = emptySet<String>()
        
        val result = ScopeJsonUtil.scopeSetToJson(scopes)
        
        assertEquals("[]", result)
    }

    @Test
    @DisplayName("jsonToScopeSet은 JSON 배열을 스코프 집합으로 변환한다")
    fun jsonToScopeSetShouldConvertJsonArrayToScopeSet() {
        val json = """["read","write","admin"]"""
        
        val result = ScopeJsonUtil.jsonToScopeSet(json)
        
        assertEquals(setOf("read", "write", "admin"), result)
    }

    @Test
    @DisplayName("jsonToScopeSet은 빈 JSON 배열을 처리할 수 있다")
    fun jsonToScopeSetShouldHandleEmptyJsonArray() {
        val json = "[]"
        
        val result = ScopeJsonUtil.jsonToScopeSet(json)
        
        assertEquals(emptySet(), result)
    }

    @Test
    @DisplayName("jsonToScopeSet은 빈 JSON을 처리할 수 있다")
    fun jsonToScopeSetShouldHandleBlankJson() {
        val result1 = ScopeJsonUtil.jsonToScopeSet("")
        val result2 = ScopeJsonUtil.jsonToScopeSet("   ")
        
        assertEquals(emptySet(), result1)
        assertEquals(emptySet(), result2)
    }

    @Test
    @DisplayName("jsonToScopeSet은 잘못된 JSON을 처리할 수 있다")
    fun jsonToScopeSetShouldHandleInvalidJson() {
        val result1 = ScopeJsonUtil.jsonToScopeSet("invalid json")
        val result2 = ScopeJsonUtil.jsonToScopeSet("{invalid}")
        val result3 = ScopeJsonUtil.jsonToScopeSet("[invalid")
        
        assertEquals(emptySet(), result1)
        assertEquals(emptySet(), result2)
        assertEquals(emptySet(), result3)
    }

    @Test
    @DisplayName("jsonToScopeSet은 중복을 제거한다")
    fun jsonToScopeSetShouldRemoveDuplicates() {
        val json = """["read","write","read","admin"]"""
        
        val result = ScopeJsonUtil.jsonToScopeSet(json)
        
        assertEquals(setOf("read", "write", "admin"), result)
    }

    @Test
    @DisplayName("validateScopeJson은 올바른 JSON에 대해 true를 반환한다")
    fun validateScopeJsonShouldReturnTrueForValidJson() {
        assertTrue(ScopeJsonUtil.validateScopeJson("""["read","write"]"""))
        assertTrue(ScopeJsonUtil.validateScopeJson("[]"))
        assertTrue(ScopeJsonUtil.validateScopeJson(""))
        assertTrue(ScopeJsonUtil.validateScopeJson("   "))
    }

    @Test
    @DisplayName("validateScopeJson은 잘못된 JSON에 대해 false를 반환한다")
    fun validateScopeJsonShouldReturnFalseForInvalidJson() {
        assertFalse(ScopeJsonUtil.validateScopeJson("invalid json"))
        assertFalse(ScopeJsonUtil.validateScopeJson("{invalid}"))
        assertFalse(ScopeJsonUtil.validateScopeJson("[invalid"))
        assertFalse(ScopeJsonUtil.validateScopeJson("""["read",invalid]"""))
    }

    @Test
    @DisplayName("mergeScopeJson은 기존 스코프와 추가 스코프를 병합한다")
    fun mergeScopeJsonShouldMergeExistingAndAdditionalScopes() {
        val existingJson = """["read","write"]"""
        val additionalScopes = setOf("admin", "delete")
        
        val result = ScopeJsonUtil.mergeScopeJson(existingJson, additionalScopes)
        val mergedScopes = ScopeJsonUtil.jsonToScopeSet(result)
        
        assertEquals(setOf("read", "write", "admin", "delete"), mergedScopes)
    }

    @Test
    @DisplayName("mergeScopeJson은 빈 기존 JSON을 처리할 수 있다")
    fun mergeScopeJsonShouldHandleEmptyExistingJson() {
        val existingJson = ""
        val additionalScopes = setOf("read", "write")
        
        val result = ScopeJsonUtil.mergeScopeJson(existingJson, additionalScopes)
        val mergedScopes = ScopeJsonUtil.jsonToScopeSet(result)
        
        assertEquals(setOf("read", "write"), mergedScopes)
    }

    @Test
    @DisplayName("mergeScopeJson은 빈 추가 스코프를 처리할 수 있다")
    fun mergeScopeJsonShouldHandleEmptyAdditionalScopes() {
        val existingJson = """["read","write"]"""
        val additionalScopes = emptySet<String>()
        
        val result = ScopeJsonUtil.mergeScopeJson(existingJson, additionalScopes)
        val mergedScopes = ScopeJsonUtil.jsonToScopeSet(result)
        
        assertEquals(setOf("read", "write"), mergedScopes)
    }

    @Test
    @DisplayName("mergeScopeJson은 중복 스코프를 처리할 수 있다")
    fun mergeScopeJsonShouldHandleDuplicateScopes() {
        val existingJson = """["read","write"]"""
        val additionalScopes = setOf("read", "admin")
        
        val result = ScopeJsonUtil.mergeScopeJson(existingJson, additionalScopes)
        val mergedScopes = ScopeJsonUtil.jsonToScopeSet(result)
        
        assertEquals(setOf("read", "write", "admin"), mergedScopes)
    }

    @Test
    @DisplayName("왕복 변환은 스코프를 보존해야 한다")
    fun roundtripConversionShouldPreserveScopes() {
        val originalScopes = setOf("read", "write", "admin", "delete")
        
        val json = ScopeJsonUtil.scopeSetToJson(originalScopes)
        val convertedScopes = ScopeJsonUtil.jsonToScopeSet(json)
        
        assertEquals(originalScopes, convertedScopes)
    }
}