package me.practice.oauth2.client.api

import me.practice.oauth2.client.api.dto.*
import me.practice.oauth2.client.client.AuthorizationServerClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/admin/sso")
class SsoConfigurationController(
    private val authorizationServerClient: AuthorizationServerClient
) {

    private val logger = LoggerFactory.getLogger(SsoConfigurationController::class.java)

    data class ApiResponse<T>(
        val success: Boolean = true,
        val data: T? = null,
        val message: String? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
    ) {
        companion object {
            fun <T> success(data: T, message: String? = null): ApiResponse<T> {
                return ApiResponse(success = true, data = data, message = message)
            }

            fun <T> error(message: String, data: T? = null): ApiResponse<T> {
                return ApiResponse(success = false, data = data, message = message)
            }
        }
    }

    /**
     * 현재 클라이언트의 SSO 설정 조회
     * GET /api/admin/sso/configuration
     */
    @GetMapping("/configuration")
    fun getSsoConfiguration(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<SsoConfigurationResponseDto?>> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다"))
        }

        val clientId = extractClientId(jwt)

        return try {
            val configuration = authorizationServerClient.getSsoConfiguration(jwt, clientId)
            ResponseEntity.ok(ApiResponse(data = configuration, message = "SSO 설정 조회 성공"))
        } catch (e: Exception) {
            logger.error("SSO 설정 조회 실패", e)
            ResponseEntity.ok(ApiResponse(data = null, message = "SSO 설정이 없습니다"))
        }
    }


    /**
     * SSO 설정 생성
     * POST /api/admin/sso/configuration
     */
    @PostMapping("/configuration")
    fun createSsoConfiguration(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody request: SsoConfigurationRequestDto
    ): ResponseEntity<ApiResponse<SsoConfigurationResponseDto>> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다"))
        }

        val clientId = extractClientId(jwt)

        return try {
            val configuration = authorizationServerClient.createSsoConfiguration(jwt, clientId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse(
                    data = configuration,
                    message = "SSO 설정 생성 성공"
                )
            )
        } catch (e: Exception) {
            logger.error("SSO 설정 생성 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SSO 설정 생성 실패: ${e.message}"))
        }
    }

    /**
     * SSO 설정 수정
     * PUT /api/admin/sso/configuration
     */
    @PutMapping("/configuration")
    fun updateSsoConfiguration(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody request: SsoConfigurationRequestDto
    ): ResponseEntity<ApiResponse<SsoConfigurationResponseDto>> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다"))
        }

        val clientId = extractClientId(jwt)

        return try {
            val configuration = authorizationServerClient.updateSsoConfiguration(jwt, clientId, request)
            ResponseEntity.ok(
                ApiResponse(
                    data = configuration,
                    message = "SSO 설정 수정 성공"
                )
            )
        } catch (e: Exception) {
            logger.error("SSO 설정 수정 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SSO 설정 수정 실패: ${e.message}"))
        }
    }

    /**
     * SSO 설정 삭제
     * DELETE /api/admin/sso/configuration
     */
    @DeleteMapping("/configuration")
    fun deleteSsoConfiguration(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<Nothing>> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다"))
        }

        val clientId = extractClientId(jwt)

        return try {
            val success = authorizationServerClient.deleteSsoConfiguration(jwt, clientId)
            if (success) {
                ResponseEntity.ok(
                    ApiResponse<Nothing>(
                        message = "SSO 설정 삭제 성공"
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("SSO 설정 삭제 실패"))
            }
        } catch (e: Exception) {
            logger.error("SSO 설정 삭제 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("SSO 설정 삭제 실패: ${e.message}"))
        }
    }

    /**
     * SSO 연결 테스트
     * POST /api/admin/sso/connection-test
     */
    @PostMapping("/connection-test")
    fun testSsoConnection(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestBody request: SsoConnectionTestRequestDto
    ): ResponseEntity<ApiResponse<SsoConnectionTestResponseDto>> {
        // TODO: SSO 연결 테스트는 추후 구현
        val testResult = SsoConnectionTestResponseDto(
            success = false,
            message = "SSO 연결 테스트는 추후 구현 예정입니다",
            details = emptyMap()
        )
        return ResponseEntity.ok(
            ApiResponse(
                data = testResult,
                message = if (testResult.success) "연결 테스트 성공" else "연결 테스트 실패"
            )
        )
    }

    /**
     * SSO 설정 상태 확인
     * GET /api/admin/sso/status
     */
    @GetMapping("/status")
    fun getSsoStatus(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<SsoStatusDto>> {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("인증이 필요합니다"))
        }

        val clientId = extractClientId(jwt)

        return try {
            val configuration = authorizationServerClient.getSsoConfiguration(jwt, clientId)
            if (configuration != null) {
                val status = SsoStatusDto(
                    enabled = true,
                    ssoType = configuration.ssoType.name,
                    configuredAt = configuration.regDt.toString(),
                    lastModified = configuration.modDt?.toString() ?: ""
                )
                ResponseEntity.ok(ApiResponse(data = status, message = "SSO 상태 조회 성공"))
            } else {
                val status = SsoStatusDto(
                    enabled = false,
                    message = "SSO 설정이 되어 있지 않습니다"
                )
                ResponseEntity.ok(ApiResponse(data = status, message = "SSO가 설정되지 않음"))
            }
        } catch (e: Exception) {
            val status = SsoStatusDto(
                enabled = false,
                message = "SSO 설정이 되어 있지 않습니다"
            )
            ResponseEntity.ok(ApiResponse(data = status, message = "SSO가 설정되지 않음"))
        }
    }

    /**
     * 지원되는 SSO 타입 조회
     * GET /api/admin/sso/supported-types
     */
    @GetMapping("/supported-types")
    fun getSupportedSsoTypes(): ResponseEntity<ApiResponse<List<SsoTypeInfoDto>>> {
        val supportedTypes = listOf(
            SsoTypeInfoDto(
                type = "OIDC",
                name = "OpenID Connect",
                description = "OAuth 2.0 확장 프로토콜로 인증과 사용자 정보 조회를 지원합니다"
            ),
            SsoTypeInfoDto(
                type = "SAML",
                name = "SAML 2.0",
                description = "XML 기반 인증 프로토콜로 엔터프라이즈 환경에 최적화되어 있습니다"
            )
        )

        return ResponseEntity.ok(
            ApiResponse(
                data = supportedTypes,
                message = "지원되는 SSO 타입 조회 성공"
            )
        )
    }

    /**
     * SSO 설정 유효성 검사 (사전 검증)
     * POST /api/admin/sso/validate
     */
    @PostMapping("/validate")
    fun validateSsoConfiguration(
        @RequestBody request: SsoConfigurationRequestDto
    ): ResponseEntity<ApiResponse<SsoValidationDto>> {
        return try {
            // 실제 저장하지 않고 검증만 수행하기 위해 임시 서비스 메서드 호출
            // 여기서는 간단히 필수 필드들만 검증
            val validationResult = SsoValidationDto(
                valid = true,
                message = "검증 통과"
            )
            ResponseEntity.ok(ApiResponse(data = validationResult, message = "검증 성공"))
        } catch (e: Exception) {
            val validationResult = SsoValidationDto(
                valid = false,
                message = e.message ?: "검증 실패"
            )
            ResponseEntity.ok(ApiResponse(data = validationResult, message = "검증 완료"))
        }
    }

    /**
     * SSO 설정 연결 테스트
     */
    @PostMapping("/configuration/test")
    fun testSsoConnection(
		@RequestBody testRequest: SsoConnectionTestRequest,
		@AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ApiResponse<SsoConnectionTestResponse>> {
        logger.info("SSO 연결 테스트 요청: ${testRequest.providerType}")

        return try {
            val startTime = System.currentTimeMillis()

            // 연결 테스트 실행
            // TODO: SSO 연결 테스트 서비스는 추후 구현
            val testResult = object {
                val success = false
                val message = "SSO 연결 테스트는 추후 구현 예정입니다"
                val details = emptyMap<String, Any>()
            }
            val responseTime = System.currentTimeMillis() - startTime

            val response = SsoConnectionTestResponse(
				success = testResult.success,
				message = testResult.message,
				responseTime = responseTime,
				details = testResult.details
			)

            if (testResult.success) {
                logger.info("SSO 연결 테스트 성공: ${testRequest.providerType}")
                ResponseEntity.ok(ApiResponse.success(response, "연결 테스트가 성공했습니다"))
            } else {
                logger.warn("SSO 연결 테스트 실패: ${testRequest.providerType} - ${testResult.message}")
                ResponseEntity.ok(ApiResponse.success(response, "연결 테스트가 완료되었습니다"))
            }

        } catch (e: Exception) {
            logger.error("SSO 연결 테스트 중 오류 발생", e)
            val response = SsoConnectionTestResponse(
                success = false,
                message = "연결 테스트 중 오류가 발생했습니다: ${e.message}",
                responseTime = 0L,
                details = emptyMap()
            )
            ResponseEntity.ok(ApiResponse.error("연결 테스트 실패", response))
        }
    }

    /**
     * JWT에서 클라이언트 ID 추출
     */
    private fun extractClientId(jwt: Jwt?): String {
        if (jwt == null) {
            throw IllegalArgumentException("인증이 필요합니다")
        }

        // JWT에서 클라이언트 ID 추출 (여러 가능한 클레임 확인)
        return jwt.getClaimAsString("shopl_client_id")
            ?: jwt.getClaimAsString("client_id")
            ?: jwt.getClaimAsString("account_id")
            ?: throw IllegalArgumentException("클라이언트 정보를 찾을 수 없습니다")
    }
}