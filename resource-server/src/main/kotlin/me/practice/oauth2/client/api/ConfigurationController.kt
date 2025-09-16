package me.practice.oauth2.client.api

import me.practice.oauth2.client.api.dto.ServerUrlsResponseDto
import me.practice.oauth2.client.configuration.AppProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/config")
class ConfigurationController(
    private val appProperties: AppProperties
) {

    /**
     * 프론트엔드에서 사용할 서버 URL 정보를 제공합니다
     */
    @GetMapping("/urls")
    fun getUrls(request: HttpServletRequest): ServerUrlsResponseDto {
        val currentServerUrl = "${request.scheme}://${request.serverName}:${request.serverPort}"

        return ServerUrlsResponseDto(
            authorizationServerBaseUrl = appProperties.authorizationServer.baseUrl,
            resourceServerBaseUrl = currentServerUrl
        )
    }
}