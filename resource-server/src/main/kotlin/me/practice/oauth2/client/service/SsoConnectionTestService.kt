package me.practice.oauth2.client.service

import me.practice.oauth2.client.dto.SsoConnectionTestRequestDto
import me.practice.oauth2.client.dto.SsoConnectionTestResponseDto
import me.practice.oauth2.client.entity.IoClientSsoSetting
import me.practice.oauth2.client.entity.SsoType
import me.practice.oauth2.client.exception.SsoConnectionTestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.net.URL
import java.time.LocalDateTime

@Service
class SsoConnectionTestService(
    private val restTemplate: RestTemplate
) {

    private val logger = LoggerFactory.getLogger(SsoConnectionTestService::class.java)

    /**
     * OIDC 연결 테스트 실행
     */
    fun testOidcConnection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return try {
            logger.info("OIDC 연결 테스트 시작: issuer=${setting.oidcIssuer}")

            val testResults = mutableMapOf<String, Any>()

            // 1. OIDC Discovery Document 조회 테스트
            val discoveryResult = testOidcDiscovery(setting.oidcIssuer!!)
            testResults["discovery"] = discoveryResult as Any

            // 2. Authorization Endpoint 접근 테스트
            if (discoveryResult["authorization_endpoint"] != null) {
                val authEndpointResult = testEndpointAccess(discoveryResult["authorization_endpoint"].toString())
                testResults["authorization_endpoint_test"] = authEndpointResult
            }

            // 3. Token Endpoint 접근 테스트
            if (discoveryResult["token_endpoint"] != null) {
                val tokenEndpointResult = testEndpointAccess(discoveryResult["token_endpoint"].toString())
                testResults["token_endpoint_test"] = tokenEndpointResult
            }

            // 4. JWKs URI 접근 테스트
            if (discoveryResult["jwks_uri"] != null) {
                val jwksResult = testJwksEndpoint(discoveryResult["jwks_uri"].toString())
                testResults["jwks_test"] = jwksResult
            }

            val allTestsPassed = testResults.values.all {
                it is Map<*, *> && it["success"] == true
            }

            SsoConnectionTestResponseDto(
                success = allTestsPassed,
                message = if (allTestsPassed) "OIDC 연결 테스트 성공" else "일부 OIDC 테스트 실패",
                details = testResults
            )

        } catch (e: Exception) {
            logger.error("OIDC 연결 테스트 실패", e)
            SsoConnectionTestResponseDto(
                success = false,
                message = "OIDC 연결 테스트 실패: ${e.message}",
                details = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * SAML 연결 테스트 실행
     */
    fun testSamlConnection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return try {
            logger.info("SAML 연결 테스트 시작: entityId=${setting.samlEntityId}")

            val testResults = mutableMapOf<String, Any>()

            // 1. SAML Metadata 조회 테스트 (일반적으로 entityId가 URL인 경우)
            if (setting.samlEntityId?.startsWith("http") == true) {
                val metadataResult = testSamlMetadata(setting.samlEntityId)
                testResults["metadata"] = metadataResult as Any
            }

            // 2. SSO URL 접근 테스트
            val ssoUrlResult = testEndpointAccess(setting.samlSsoUrl!!)
            testResults["sso_url_test"] = ssoUrlResult as Any

            // 3. SLO URL 접근 테스트 (설정된 경우)
            if (!setting.samlSloUrl.isNullOrBlank()) {
                val sloUrlResult = testEndpointAccess(setting.samlSloUrl)
                testResults["slo_url_test"] = sloUrlResult as Any
            }

            // 4. 인증서 유효성 검사
            val certResult = validateX509Certificate(setting.samlX509Cert!!)
            testResults["certificate_validation"] = certResult as Any

            val allTestsPassed = testResults.values.all {
                it is Map<*, *> && it["success"] == true
            }

            SsoConnectionTestResponseDto(
                success = allTestsPassed,
                message = if (allTestsPassed) "SAML 연결 테스트 성공" else "일부 SAML 테스트 실패",
                details = testResults
            )

        } catch (e: Exception) {
            logger.error("SAML 연결 테스트 실패", e)
            SsoConnectionTestResponseDto(
                success = false,
                message = "SAML 연결 테스트 실패: ${e.message}",
                details = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * OAuth2 연결 테스트 실행
     */
    fun testOauth2Connection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return try {
            logger.info("OAuth2 연결 테스트 시작: authUri=${setting.oauth2AuthorizationUri}")

            val testResults = mutableMapOf<String, Any>()

            // 1. Authorization URI 접근 테스트
            val authUriResult = testEndpointAccess(setting.oauth2AuthorizationUri!!)
            testResults["authorization_uri_test"] = authUriResult

            // 2. Token URI 접근 테스트
            val tokenUriResult = testEndpointAccess(setting.oauth2TokenUri!!)
            testResults["token_uri_test"] = tokenUriResult

            // 3. User Info URI 접근 테스트
            val userInfoResult = testEndpointAccess(setting.oauth2UserInfoUri!!)
            testResults["user_info_uri_test"] = userInfoResult

            // 4. Well-known 문서 조회 시도 (있는 경우)
            try {
                val baseUrl = URL(setting.oauth2AuthorizationUri).let {
                    "${it.protocol}://${it.host}${if (it.port != -1) ":${it.port}" else ""}"
                }
                val wellKnownResult = testWellKnownEndpoint("$baseUrl/.well-known/oauth2")
                testResults["well_known_test"] = wellKnownResult
            } catch (e: Exception) {
                testResults["well_known_test"] = mapOf(
                    "success" to false,
                    "message" to "Well-known 문서 조회 실패 (선택사항): ${e.message}"
                )
            }

            val requiredTestsPassed = listOf(
                testResults["authorization_uri_test"],
                testResults["token_uri_test"],
                testResults["user_info_uri_test"]
            ).all { it is Map<*, *> && it["success"] == true }

            SsoConnectionTestResponseDto(
                success = requiredTestsPassed,
                message = if (requiredTestsPassed) "OAuth2 연결 테스트 성공" else "OAuth2 테스트 실패",
                details = testResults
            )

        } catch (e: Exception) {
            logger.error("OAuth2 연결 테스트 실패", e)
            SsoConnectionTestResponseDto(
                success = false,
                message = "OAuth2 연결 테스트 실패: ${e.message}",
                details = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    /**
     * OIDC Discovery Document 조회
     */
    private fun testOidcDiscovery(issuer: String): Map<String, Any> {
        return try {
            val wellKnownUrl = "${issuer.trimEnd('/')}/.well-known/openid_configuration"
            logger.debug("OIDC Discovery 조회: $wellKnownUrl")

            val response = restTemplate.getForObject(wellKnownUrl, Map::class.java)

            if (response != null) {
                mapOf(
                    "success" to true,
                    "message" to "Discovery Document 조회 성공",
                    "authorization_endpoint" to (response["authorization_endpoint"] ?: ""),
                    "token_endpoint" to (response["token_endpoint"] ?: ""),
                    "jwks_uri" to (response["jwks_uri"] ?: ""),
                    "userinfo_endpoint" to (response["userinfo_endpoint"] ?: "")
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "Discovery Document가 비어있습니다"
                )
            }
        } catch (e: HttpClientErrorException) {
            mapOf(
                "success" to false,
                "message" to "Discovery Document 조회 실패: ${e.statusCode}",
                "details" to e.responseBodyAsString
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Discovery Document 조회 중 오류: ${e.message}"
            )
        }
    }

    /**
     * SAML Metadata 조회
     */
    private fun testSamlMetadata(metadataUrl: String): Map<String, Any> {
        return try {
            logger.debug("SAML Metadata 조회: $metadataUrl")

            val response = restTemplate.getForObject(metadataUrl, String::class.java)

            if (response != null && response.contains("EntityDescriptor")) {
                mapOf(
                    "success" to true,
                    "message" to "SAML Metadata 조회 성공",
                    "metadata_size" to response.length
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "유효하지 않은 SAML Metadata"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "SAML Metadata 조회 실패: ${e.message}"
            )
        }
    }

    /**
     * JWKs URI 테스트
     */
    private fun testJwksEndpoint(jwksUri: String): Map<String, Any> {
        return try {
            logger.debug("JWKs 엔드포인트 테스트: $jwksUri")

            val response = restTemplate.getForObject(jwksUri, Map::class.java)

            if (response != null && response.containsKey("keys")) {
                mapOf(
                    "success" to true,
                    "message" to "JWKs 조회 성공",
                    "keys_count" to ((response["keys"] as? List<*>)?.size ?: 0)
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "유효하지 않은 JWKs 응답"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "JWKs 조회 실패: ${e.message}"
            )
        }
    }

    /**
     * Well-known 엔드포인트 테스트
     */
    private fun testWellKnownEndpoint(wellKnownUrl: String): Map<String, Any> {
        return try {
            logger.debug("Well-known 엔드포인트 테스트: $wellKnownUrl")

            val response = restTemplate.getForObject(wellKnownUrl, Map::class.java)

            if (response != null) {
                mapOf(
                    "success" to true,
                    "message" to "Well-known 문서 조회 성공",
                    "endpoints" to response.keys
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "Well-known 문서가 비어있습니다"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Well-known 조회 실패: ${e.message}"
            )
        }
    }

    /**
     * 일반 엔드포인트 접근 테스트
     */
    private fun testEndpointAccess(url: String): Map<String, Any> {
        return try {
            logger.debug("엔드포인트 접근 테스트: $url")

            val response = restTemplate.getForEntity(url, String::class.java)

            mapOf(
                "success" to true,
                "message" to "엔드포인트 접근 성공",
                "status_code" to response.statusCode.value(),
                "response_size" to (response.body?.length ?: 0)
            )
        } catch (e: HttpClientErrorException) {
            // 4xx 오류도 엔드포인트가 존재한다는 의미이므로 성공으로 처리
            if (e.statusCode.is4xxClientError) {
                mapOf(
                    "success" to true,
                    "message" to "엔드포인트 존재 확인 (${e.statusCode})",
                    "status_code" to e.statusCode.value()
                )
            } else {
                mapOf(
                    "success" to false,
                    "message" to "엔드포인트 접근 실패: ${e.statusCode}",
                    "status_code" to e.statusCode.value()
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "엔드포인트 접근 중 오류: ${e.message}"
            )
        }
    }

    /**
     * X.509 인증서 유효성 검증
     */
    private fun validateX509Certificate(certPem: String): Map<String, Any> {
        return try {
            logger.debug("X.509 인증서 유효성 검증")

            // 간단한 형식 검증
            if (!certPem.contains("BEGIN CERTIFICATE") || !certPem.contains("END CERTIFICATE")) {
                return mapOf(
                    "success" to false,
                    "message" to "인증서 형식이 올바르지 않습니다"
                )
            }

            // TODO: 실제 인증서 파싱 및 유효성 검증 구현
            // 현재는 기본적인 형식 검증만 수행

            mapOf(
                "success" to true,
                "message" to "인증서 형식 검증 성공",
                "certificate_length" to certPem.length
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "인증서 검증 실패: ${e.message}"
            )
        }
    }
}