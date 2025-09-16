package me.practice.oauth2.client.client

import me.practice.oauth2.client.api.dto.SsoConfigurationRequestDto
import me.practice.oauth2.client.api.dto.SsoConfigurationResponseDto
import me.practice.oauth2.client.configuration.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class AuthorizationServerClient(
    private val restTemplate: RestTemplate,
    private val appProperties: AppProperties
) {
    private val authServerBaseUrl: String
        get() = appProperties.authorizationServer.baseUrl
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getSsoConfiguration(jwt: Jwt, shoplClientId: String): SsoConfigurationResponseDto? {
        return try {
            val headers = HttpHeaders().apply {
                setBearerAuth(jwt.tokenValue)
                contentType = MediaType.APPLICATION_JSON
            }

            val entity = HttpEntity<Any>(headers)
            val url = "$authServerBaseUrl/api/sso/configuration?shoplClientId=$shoplClientId"

            logger.debug("Authorization Server API 호출: GET $url")

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                SsoConfigurationResponseDto::class.java
            )

            response.body
        } catch (e: Exception) {
            logger.error("SSO 설정 조회 실패: ${e.message}")
            null
        }
    }

    fun createSsoConfiguration(jwt: Jwt, shoplClientId: String, request: SsoConfigurationRequestDto): SsoConfigurationResponseDto? {
        return try {
            val headers = HttpHeaders().apply {
                setBearerAuth(jwt.tokenValue)
                contentType = MediaType.APPLICATION_JSON
            }

            val entity = HttpEntity(request, headers)
            val url = "$authServerBaseUrl/api/sso/configuration?shoplClientId=$shoplClientId"

            logger.debug("Authorization Server API 호출: POST $url")

            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                SsoConfigurationResponseDto::class.java
            )

            response.body
        } catch (e: Exception) {
            logger.error("SSO 설정 생성 실패: ${e.message}")
            throw e
        }
    }

    fun updateSsoConfiguration(jwt: Jwt, shoplClientId: String, request: SsoConfigurationRequestDto): SsoConfigurationResponseDto? {
        return try {
            val headers = HttpHeaders().apply {
                setBearerAuth(jwt.tokenValue)
                contentType = MediaType.APPLICATION_JSON
            }

            val entity = HttpEntity(request, headers)
            val url = "$authServerBaseUrl/api/sso/configuration?shoplClientId=$shoplClientId"

            logger.debug("Authorization Server API 호출: PUT $url")

            val response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                SsoConfigurationResponseDto::class.java
            )

            response.body
        } catch (e: Exception) {
            logger.error("SSO 설정 수정 실패: ${e.message}")
            throw e
        }
    }

    fun deleteSsoConfiguration(jwt: Jwt, shoplClientId: String): Boolean {
        return try {
            val headers = HttpHeaders().apply {
                setBearerAuth(jwt.tokenValue)
                contentType = MediaType.APPLICATION_JSON
            }

            val entity = HttpEntity<Any>(headers)
            val url = "$authServerBaseUrl/api/sso/configuration?shoplClientId=$shoplClientId"

            logger.debug("Authorization Server API 호출: DELETE $url")

            restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                entity,
                Void::class.java
            )

            true
        } catch (e: Exception) {
            logger.error("SSO 설정 삭제 실패: ${e.message}")
            false
        }
    }
}