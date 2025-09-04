package me.practice.oauth2.client.configuration

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.util.SerializationUtils
import java.util.*

object CookieUtils {

	fun getCookie(request: HttpServletRequest, name: String): Cookie? {
		return request.cookies?.find { it.name == name }
	}

	fun addCookie(response: HttpServletResponse, name: String, value: String, maxAge: Int) {
		val cookie = Cookie(name, value).apply {
			path = "/"
			isHttpOnly = true
			this.maxAge = maxAge
			// 개발환경에서는 secure = false
			// 운영환경에서는 secure = true 설정 필요
		}
		response.addCookie(cookie)
	}

	fun deleteCookie(request: HttpServletRequest, response: HttpServletResponse, name: String) {
		getCookie(request, name)?.let {
			val cookie = Cookie(name, "").apply {
				path = "/"
				isHttpOnly = true
				maxAge = 0
			}
			response.addCookie(cookie)
		}
	}

	fun serialize(obj: Any): String {
		return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(obj))
	}

	fun <T> deserialize(cookie: Cookie): T? {
		return try {
			val bytes = Base64.getUrlDecoder().decode(cookie.value)
			@Suppress("UNCHECKED_CAST")
			SerializationUtils.deserialize(bytes) as T?
		} catch (ex: Exception) {
			null
		}
	}

	/**
	 * 쿠키 값을 문자열로 저장/조회하는 간편 메서드들
	 */
	fun addSimpleCookie(response: HttpServletResponse, name: String, value: String, maxAge: Int) {
		addCookie(response, name, value, maxAge)
	}

	fun getSimpleCookieValue(request: HttpServletRequest, name: String): String? {
		return getCookie(request, name)?.value
	}

	/**
	 * 객체를 직렬화해서 쿠키에 저장하는 메서드
	 */
	fun addSerializedCookie(response: HttpServletResponse, name: String, obj: Any, maxAge: Int) {
		addCookie(response, name, serialize(obj), maxAge)
	}

	/**
	 * 쿠키에서 객체를 역직렬화해서 조회하는 메서드
	 */
	fun <T> getDeserializedCookie(request: HttpServletRequest, name: String): T? {
		return getCookie(request, name)?.let { cookie ->
			deserialize<T>(cookie)
		}
	}
}