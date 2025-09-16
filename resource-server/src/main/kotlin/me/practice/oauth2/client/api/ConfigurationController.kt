package me.practice.oauth2.client.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/config")
class ConfigurationController(
    @Value("\${authorization-server.base-url}")
    private val authorizationServerBaseUrl: String
) {

    /**
     * 프론트엔드에서 사용할 서버 URL 정보를 제공합니다
     */
    @GetMapping("/urls")
    fun getUrls(request: HttpServletRequest): Map<String, String> {
        val currentServerUrl = "${request.scheme}://${request.serverName}:${request.serverPort}"

        return mapOf(
            "authorizationServerBaseUrl" to authorizationServerBaseUrl,
            "resourceServerBaseUrl" to currentServerUrl
        )
    }
}