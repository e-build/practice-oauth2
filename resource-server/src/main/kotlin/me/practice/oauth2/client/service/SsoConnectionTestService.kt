package me.practice.oauth2.client.service

import me.practice.oauth2.client.api.dto.SsoConnectionTestRequest
import me.practice.oauth2.client.api.dto.SsoConnectionTestRequestDto
import me.practice.oauth2.client.api.dto.SsoConnectionTestResponse
import me.practice.oauth2.client.api.dto.SsoConnectionTestResponseDto
import me.practice.oauth2.client.entity.IoClientSsoSetting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.net.URL

@Service
class SsoConnectionTestService(
	private val restTemplate: RestTemplate,
) {

	private val logger = LoggerFactory.getLogger(SsoConnectionTestService::class.java)

	/**
	 * OIDC 연결 테스트 실행
	 */
	fun testOidcConnection(
		setting: IoClientSsoSetting,
		request: SsoConnectionTestRequestDto,
	): SsoConnectionTestResponseDto {
		return try {
			logger.info("OIDC 연결 테스트 시작: issuer=${setting.oidcIssuer}")

			val testResults = mutableMapOf<String, Any>()

			// 1. OIDC Discovery Document 조회 테스트
			val discoveryResult = testOidcDiscoveryFromIssuer(setting.oidcIssuer!!)
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
	fun testSamlConnection(
		setting: IoClientSsoSetting,
		request: SsoConnectionTestRequestDto,
	): SsoConnectionTestResponseDto {
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
	fun testOauth2Connection(
		setting: IoClientSsoSetting,
		request: SsoConnectionTestRequestDto,
	): SsoConnectionTestResponseDto {
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
	 * OIDC Discovery Document 조회 (기존 메서드)
	 */
	private fun testOidcDiscoveryFromIssuer(issuer: String): Map<String, Any> {
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

	/**
	 * 웹에서 전송된 연결 테스트 요청 처리
	 */
	fun testConnection(request: SsoConnectionTestRequest): SsoConnectionTestResponse {
		logger.info("웹 연결 테스트 시작: providerType=${request.providerType}")

		return try {
			val startTime = System.currentTimeMillis()
			val testResults = mutableMapOf<String, Any>()

			when (request.providerType.uppercase()) {
				"SAML" -> {
					if (request.samlMetadataUrl.isNullOrBlank()) {
						return SsoConnectionTestResponse(
							success = false,
							message = "SAML 메타데이터 URL이 필요합니다",
							responseTime = System.currentTimeMillis() - startTime
						)
					}

					val metadataResult = testEndpointAccess(request.samlMetadataUrl)
					testResults["metadata_test"] = metadataResult

					val success = metadataResult["success"] == true
					SsoConnectionTestResponse(
						success = success,
						message = if (success) "SAML 메타데이터 URL 접근 성공" else "SAML 메타데이터 URL 접근 실패",
						responseTime = System.currentTimeMillis() - startTime,
						details = testResults
					)
				}

				"OIDC" -> {
					if (request.oidcDiscoveryUrl.isNullOrBlank()) {
						return SsoConnectionTestResponse(
							success = false,
							message = "OIDC Discovery URL이 필요합니다",
							responseTime = System.currentTimeMillis() - startTime
						)
					}

					// Discovery URL 테스트
					val discoveryResult = testOidcDiscoveryFromUrl(request.oidcDiscoveryUrl)
					testResults["discovery_test"] = discoveryResult

					val success = discoveryResult["success"] == true
					SsoConnectionTestResponse(
						success = success,
						message = if (success) "OIDC Discovery 엔드포인트 접근 성공" else "OIDC Discovery 엔드포인트 접근 실패",
						responseTime = System.currentTimeMillis() - startTime,
						details = testResults
					)
				}

				"OAUTH2" -> {
					if (request.oauth2AuthUrl.isNullOrBlank() || request.oauth2TokenUrl.isNullOrBlank()) {
						return SsoConnectionTestResponse(
							success = false,
							message = "OAuth2 인증 URL과 토큰 URL이 필요합니다",
							responseTime = System.currentTimeMillis() - startTime
						)
					}

					// 인증 URL 테스트
					val authUrlResult = testEndpointAccess(request.oauth2AuthUrl)
					testResults["auth_url_test"] = authUrlResult

					// 토큰 URL 테스트
					val tokenUrlResult = testEndpointAccess(request.oauth2TokenUrl)
					testResults["token_url_test"] = tokenUrlResult

					// 사용자 정보 URL 테스트 (선택사항)
					if (!request.oauth2UserInfoUrl.isNullOrBlank()) {
						val userInfoResult = testEndpointAccess(request.oauth2UserInfoUrl)
						testResults["userinfo_url_test"] = userInfoResult
					}

					val success = authUrlResult["success"] == true && tokenUrlResult["success"] == true
					SsoConnectionTestResponse(
						success = success,
						message = if (success) "OAuth2 엔드포인트 접근 성공" else "일부 OAuth2 엔드포인트 접근 실패",
						responseTime = System.currentTimeMillis() - startTime,
						details = testResults
					)
				}

				else -> {
					SsoConnectionTestResponse(
						success = false,
						message = "지원하지 않는 SSO 제공자 유형: ${request.providerType}",
						responseTime = System.currentTimeMillis() - startTime
					)
				}
			}

		} catch (e: Exception) {
			logger.error("웹 연결 테스트 중 오류 발생", e)
			SsoConnectionTestResponse(
				success = false,
				message = "연결 테스트 중 오류 발생: ${e.message}",
				responseTime = 0L
			)
		}
	}

	/**
	 * OIDC Discovery 엔드포인트 테스트
	 */
	private fun testOidcDiscoveryFromUrl(discoveryUrl: String): Map<String, Any> {
		return try {
			logger.debug("OIDC Discovery URL 테스트: $discoveryUrl")

			val response = restTemplate.getForEntity(discoveryUrl, String::class.java)

			if (response.statusCode.is2xxSuccessful) {
				val body = response.body
				if (body?.contains("authorization_endpoint") == true &&
					body.contains("token_endpoint") == true
				) {
					mapOf(
						"success" to true,
						"message" to "OIDC Discovery 구성 유효함",
						"status_code" to response.statusCode.value(),
						"has_auth_endpoint" to body.contains("authorization_endpoint"),
						"has_token_endpoint" to body.contains("token_endpoint"),
						"has_userinfo_endpoint" to body.contains("userinfo_endpoint")
					)
				} else {
					mapOf(
						"success" to false,
						"message" to "OIDC Discovery 응답이 유효하지 않음"
					)
				}
			} else {
				mapOf(
					"success" to false,
					"message" to "HTTP 오류: ${response.statusCode}",
					"status_code" to response.statusCode.value()
				)
			}
		} catch (e: Exception) {
			mapOf(
				"success" to false,
				"message" to "Discovery URL 접근 실패: ${e.message}"
			)
		}
	}
}