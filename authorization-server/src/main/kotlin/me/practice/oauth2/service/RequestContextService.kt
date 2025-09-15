package me.practice.oauth2.service

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Component
class RequestContextService {

    fun getCurrentRequest(): HttpServletRequest? {
        return try {
            val attributes = RequestContextHolder.currentRequestAttributes() as? ServletRequestAttributes
            attributes?.request
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentSessionId(): String {
        return getCurrentRequest()?.session?.id ?: "unknown-session"
    }
}