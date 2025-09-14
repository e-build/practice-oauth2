package me.practice.oauth2.utils

import com.fasterxml.jackson.core.type.TypeReference

object ScopeJsonUtil {

    fun scopeSetToJson(scopes: Set<String>): String {
        return JsonUtils.toJson(scopes.toList())
    }

    fun jsonToScopeSet(json: String): Set<String> {
        if (json.isBlank()) return emptySet()
        
        return try {
            val scopeList: List<String> = JsonUtils.fromJson(json, object : TypeReference<List<String>>() {}.type)
            scopeList.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun validateScopeJson(json: String): Boolean {
        if (json.isBlank()) return true
        
        return try {
            JsonUtils.fromJson<List<String>>(json, object : TypeReference<List<String>>() {}.type)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun mergeScopeJson(existingJson: String, additionalScopes: Set<String>): String {
        val existingScopes = jsonToScopeSet(existingJson)
        val mergedScopes = existingScopes + additionalScopes
        return scopeSetToJson(mergedScopes)
    }
}