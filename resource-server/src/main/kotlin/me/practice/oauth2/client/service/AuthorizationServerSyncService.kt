package me.practice.oauth2.client.service

import me.practice.oauth2.client.dto.SsoConfigurationResponseDto
import me.practice.oauth2.client.entity.SsoType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@Service
class AuthorizationServerSyncService(
    private val restTemplate: RestTemplate,
    @Value("\${authorization-server.base-url:http://localhost:9000}")
    private val authServerBaseUrl: String
) {

    private val logger = LoggerFactory.getLogger(AuthorizationServerSyncService::class.java)

    data class AuthServerSsoSettingDto(
        val id: String,
        val shoplClientId: String,
        val ssoType: String,

        // OIDC 필드
        val oidcClientId: String? = null,
        val oidcClientSecret: String? = null,
        val oidcIssuer: String? = null,
        val oidcScopes: String? = null,
        val oidcResponseType: String? = null,
        val oidcResponseMode: String? = null,
        val oidcClaimsMapping: String? = null,

        // SAML 필드
        val samlEntityId: String? = null,
        val samlSsoUrl: String? = null,
        val samlSloUrl: String? = null,
        val samlX509Cert: String? = null,
        val samlNameIdFormat: String? = null,
        val samlBindingSso: String? = null,
        val samlBindingSlo: String? = null,
        val samlWantAssertionsSigned: Boolean? = null,
        val samlWantResponseSigned: Boolean? = null,
        val samlSignatureAlgorithm: String? = null,
        val samlDigestAlgorithm: String? = null,
        val samlAttributeMapping: String? = null,

        // OAuth2 필드 (인증 서버에서 추가 지원 시)
        val oauth2ClientId: String? = null,
        val oauth2ClientSecret: String? = null,
        val oauth2AuthorizationUri: String? = null,
        val oauth2TokenUri: String? = null,
        val oauth2UserInfoUri: String? = null,
        val oauth2Scopes: String? = null,
        val oauth2UserNameAttribute: String? = null,

        // 공통 필드
        val redirectUris: String? = null,
        val autoProvision: Boolean = true,
        val defaultRole: String? = null
    )

    data class SyncResponse(
        val success: Boolean,
        val message: String,
        val syncedAt: String? = null,
        val details: Map<String, Any>? = null
    )

    /**
     * 리소스 서버의 SSO 설정을 인증 서버에 동기화
     */
    fun syncToAuthorizationServer(configuration: SsoConfigurationResponseDto): SyncResponse {
        return try {
            logger.info("인증 서버 동기화 시작: clientId=${configuration.clientId}, ssoType=${configuration.ssoType}")

            // OAuth2 타입은 인증 서버에서 아직 지원하지 않을 수 있음
            if (configuration.ssoType == SsoType.OAUTH2) {
                logger.warn("OAuth2 타입은 현재 인증 서버에서 지원하지 않을 수 있습니다")
            }

            val authServerDto = convertToAuthServerDto(configuration)
            val response = sendToAuthorizationServer(authServerDto)

            logger.info("인증 서버 동기화 성공: ${response.message}")
            response
        } catch (e: HttpClientErrorException) {
            logger.error("인증 서버 동기화 클라이언트 오류: ${e.statusCode} - ${e.responseBodyAsString}", e)
            SyncResponse(
                success = false,
                message = "인증 서버 동기화 실패: ${e.message}",
                details = mapOf(
                    "statusCode" to e.statusCode.value(),
                    "response" to e.responseBodyAsString
                )
            )
        } catch (e: HttpServerErrorException) {
            logger.error("인증 서버 동기화 서버 오류: ${e.statusCode} - ${e.responseBodyAsString}", e)
            SyncResponse(
                success = false,
                message = "인증 서버 내부 오류: ${e.message}"
            )
        } catch (e: Exception) {
            logger.error("인증 서버 동기화 예상치 못한 오류", e)
            SyncResponse(
                success = false,
                message = "동기화 중 오류 발생: ${e.message}"
            )
        }
    }

    /**
     * 인증 서버에서 SSO 설정 삭제 동기화
     */
    fun syncDeletionToAuthorizationServer(clientId: String): SyncResponse {
        return try {
            logger.info("인증 서버 삭제 동기화 시작: clientId=$clientId")

            val url = "$authServerBaseUrl/api/sso/settings/$clientId"
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Source", "resource-server")
            }

            restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity<Any>(headers), String::class.java)

            val response = SyncResponse(
                success = true,
                message = "인증 서버에서 SSO 설정 삭제 완료",
                syncedAt = java.time.LocalDateTime.now().toString()
            )

            logger.info("인증 서버 삭제 동기화 성공")
            response
        } catch (e: Exception) {
            logger.error("인증 서버 삭제 동기화 실패", e)
            SyncResponse(
                success = false,
                message = "삭제 동기화 실패: ${e.message}"
            )
        }
    }

    /**
     * 인증 서버의 SSO 설정을 리소스 서버로 가져오기
     */
    fun syncFromAuthorizationServer(clientId: String): SyncResponse {
        return try {
            logger.info("인증 서버에서 SSO 설정 가져오기: clientId=$clientId")

            val url = "$authServerBaseUrl/api/sso/settings/$clientId"
            val headers = HttpHeaders().apply {
                set("X-Source", "resource-server")
            }

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                AuthServerSsoSettingDto::class.java
            )

            if (response.statusCode.is2xxSuccessful && response.body != null) {
                SyncResponse(
                    success = true,
                    message = "인증 서버에서 SSO 설정 조회 성공",
                    details = mapOf("configuration" to response.body!!)
                )
            } else {
                SyncResponse(
                    success = false,
                    message = "인증 서버에서 SSO 설정을 찾을 수 없습니다"
                )
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 404) {
                SyncResponse(
                    success = false,
                    message = "인증 서버에 해당 클라이언트의 SSO 설정이 없습니다"
                )
            } else {
                logger.error("인증 서버 조회 실패: ${e.statusCode}", e)
                SyncResponse(
                    success = false,
                    message = "인증 서버 조회 실패: ${e.message}"
                )
            }
        } catch (e: Exception) {
            logger.error("인증 서버 조회 중 오류", e)
            SyncResponse(
                success = false,
                message = "조회 중 오류 발생: ${e.message}"
            )
        }
    }

    /**
     * 인증 서버 연결 상태 확인
     */
    fun checkAuthorizationServerHealth(): SyncResponse {
        return try {
            val url = "$authServerBaseUrl/actuator/health"
            val response = restTemplate.getForEntity(url, Map::class.java)

            if (response.statusCode.is2xxSuccessful) {
                SyncResponse(
                    success = true,
                    message = "인증 서버 연결 정상",
                    details = response.body as? Map<String, Any>
                )
            } else {
                SyncResponse(
                    success = false,
                    message = "인증 서버 상태 이상: ${response.statusCode}"
                )
            }
        } catch (e: Exception) {
            logger.error("인증 서버 헬스체크 실패", e)
            SyncResponse(
                success = false,
                message = "인증 서버에 연결할 수 없습니다: ${e.message}"
            )
        }
    }

    /**
     * 인증 서버로 HTTP 요청 전송
     */
    private fun sendToAuthorizationServer(dto: AuthServerSsoSettingDto): SyncResponse {
        val url = "$authServerBaseUrl/api/sso/settings"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Source", "resource-server")
        }

        val entity = HttpEntity(dto, headers)
        val response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map::class.java)

        return if (response.statusCode.is2xxSuccessful) {
            SyncResponse(
                success = true,
                message = "인증 서버 동기화 성공",
                syncedAt = java.time.LocalDateTime.now().toString(),
                details = response.body as? Map<String, Any>
            )
        } else {
            SyncResponse(
                success = false,
                message = "인증 서버 응답 오류: ${response.statusCode}"
            )
        }
    }

    /**
     * 리소스 서버 DTO를 인증 서버 DTO로 변환
     */
    private fun convertToAuthServerDto(config: SsoConfigurationResponseDto): AuthServerSsoSettingDto {
        return AuthServerSsoSettingDto(
            id = config.id,
            shoplClientId = config.clientId,
            ssoType = config.ssoType.name,

            // OIDC 필드
            oidcClientId = config.oidcClientId,
            oidcIssuer = config.oidcIssuer,
            oidcScopes = config.oidcScopes,
            oidcResponseType = config.oidcResponseType,
            oidcResponseMode = config.oidcResponseMode,
            oidcClaimsMapping = config.oidcClaimsMapping?.let {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(it)
                } catch (e: Exception) { null }
            },

            // SAML 필드
            samlEntityId = config.samlEntityId,
            samlSsoUrl = config.samlSsoUrl,
            samlSloUrl = config.samlSloUrl,
            samlNameIdFormat = config.samlNameIdFormat,
            samlBindingSso = config.samlBindingSso?.name,
            samlBindingSlo = config.samlBindingSlo?.name,
            samlWantAssertionsSigned = config.samlWantAssertionsSigned,
            samlWantResponseSigned = config.samlWantResponseSigned,
            samlSignatureAlgorithm = config.samlSignatureAlgorithm,
            samlDigestAlgorithm = config.samlDigestAlgorithm,
            samlAttributeMapping = config.samlAttributeMapping?.let {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(it)
                } catch (e: Exception) { null }
            },

            // OAuth2 필드
            oauth2ClientId = config.oauth2ClientId,
            oauth2AuthorizationUri = config.oauth2AuthorizationUri,
            oauth2TokenUri = config.oauth2TokenUri,
            oauth2UserInfoUri = config.oauth2UserInfoUri,
            oauth2Scopes = config.oauth2Scopes,
            oauth2UserNameAttribute = config.oauth2UserNameAttribute,

            // 공통 필드
            redirectUris = config.redirectUris?.let {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(it)
                } catch (e: Exception) { null }
            },
            autoProvision = config.autoProvision,
            defaultRole = config.defaultRole
        )
    }
}