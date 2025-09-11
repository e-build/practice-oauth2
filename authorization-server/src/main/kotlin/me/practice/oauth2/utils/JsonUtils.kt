package me.practice.oauth2.utils

import com.fasterxml.jackson.databind.ObjectMapper

object JsonUtils {

	private val mapper = ObjectMapper()

	fun toJson(obj: Any): String {
		return mapper.writeValueAsString(obj)
	}

	fun <T> fromJson(json: String, clazz: Class<T>): T {
		return mapper.readValue(json, clazz)
	}
}
