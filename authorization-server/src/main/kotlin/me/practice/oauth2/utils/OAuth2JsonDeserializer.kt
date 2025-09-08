package me.practice.oauth2.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

// Jackson ObjectMapper 설정 및 사용 예제
object OAuth2JsonDeserializer {
    
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
        configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
    }
    
    fun parseAsMap(jsonString: String): Map<String, Any>? {
        return try {
            objectMapper.readValue<Map<String, Any>>(jsonString)
        } catch (e: Exception) {
            println("Map 파싱 실패: ${e.message}")
            null
        }
    }
}
