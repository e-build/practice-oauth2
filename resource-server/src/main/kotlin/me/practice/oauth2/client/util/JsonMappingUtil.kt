package me.practice.oauth2.client.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class JsonMappingUtil(
    private val objectMapper: ObjectMapper
) {

    /**
     * Map을 JSON 문자열로 변환
     */
    fun mapToJson(map: Map<String, String>?): String? {
        return if (map.isNullOrEmpty()) {
            null
        } else {
            try {
                objectMapper.writeValueAsString(map)
            } catch (e: Exception) {
                throw IllegalArgumentException("Map을 JSON으로 변환하는데 실패했습니다", e)
            }
        }
    }

    /**
     * JSON 문자열을 Map으로 변환
     */
    fun jsonToMap(json: String?): Map<String, String>? {
        return if (json.isNullOrBlank()) {
            null
        } else {
            try {
                objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
            } catch (e: Exception) {
                throw IllegalArgumentException("JSON을 Map으로 변환하는데 실패했습니다: $json", e)
            }
        }
    }

    /**
     * List를 JSON 문자열로 변환
     */
    fun listToJson(list: List<String>?): String? {
        return if (list.isNullOrEmpty()) {
            null
        } else {
            try {
                objectMapper.writeValueAsString(list)
            } catch (e: Exception) {
                throw IllegalArgumentException("List를 JSON으로 변환하는데 실패했습니다", e)
            }
        }
    }

    /**
     * JSON 문자열을 List로 변환
     */
    fun jsonToList(json: String?): List<String>? {
        return if (json.isNullOrBlank()) {
            null
        } else {
            try {
                objectMapper.readValue(json, object : TypeReference<List<String>>() {})
            } catch (e: Exception) {
                throw IllegalArgumentException("JSON을 List로 변환하는데 실패했습니다: $json", e)
            }
        }
    }
}