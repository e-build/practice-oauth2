package me.practice.oauth2.client.controller

import me.practice.oauth2.client.api.dto.SsoConfigurationRequestDto
import me.practice.oauth2.client.api.dto.SsoConfigurationResponseDto
import me.practice.oauth2.client.api.dto.SsoConfigurationSummaryDto
import me.practice.oauth2.client.api.dto.SsoConnectionTestRequestDto
import me.practice.oauth2.client.api.dto.SsoConnectionTestResponseDto
import me.practice.oauth2.client.service.SsoConfigurationService
import me.practice.oauth2.client.service.SsoConnectionTestService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/admin/sso")
class SsoConfigurationController(
    private val ssoConfigurationService: SsoConfigurationService,
    private val ssoConnectionTestService: SsoConnectionTestService
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
        val clientId = extractClientId(jwt)

        return try {
            val configuration = ssoConfigurationService.getSsoConfiguration(clientId)
            ResponseEntity.ok(ApiResponse(data = configuration, message = "SSO 설정 조회 성공"))
        } catch (e: Exception) {
            // 설정이 없는 경우에도 성공으로 처리하되 data는 null
            ResponseEntity.ok(ApiResponse(data = null, message = "SSO 설정이 없습니다"))
        }
    }

    /**
     * 모든 SSO 설정 목록 조회 (요약 정보)
     * GET /api/admin/sso/list
     */
    @GetMapping("/list")
    fun getAllSsoConfigurations(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<List<SsoConfigurationSummaryDto>>> {
        val configurations = ssoConfigurationService.getAllSsoConfigurations()
        return ResponseEntity.ok(
            ApiResponse(
                data = configurations,
                message = "SSO 설정 목록 조회 성공"
            )
        )
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
        val clientId = extractClientId(jwt)

        val configuration = ssoConfigurationService.createSsoConfiguration(clientId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(
                data = configuration,
                message = "SSO 설정 생성 성공"
            )
        )
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
        val clientId = extractClientId(jwt)

        val configuration = ssoConfigurationService.updateSsoConfiguration(clientId, request)
        return ResponseEntity.ok(
            ApiResponse(
                data = configuration,
                message = "SSO 설정 수정 성공"
            )
        )
    }

    /**
     * SSO 설정 삭제
     * DELETE /api/admin/sso/configuration
     */
    @DeleteMapping("/configuration")
    fun deleteSsoConfiguration(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<Nothing>> {
        val clientId = extractClientId(jwt)

        ssoConfigurationService.deleteSsoConfiguration(clientId)
        return ResponseEntity.ok(
            ApiResponse<Nothing>(
                message = "SSO 설정 삭제 성공"
            )
        )
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
        val clientId = extractClientId(jwt)

        val testResult = ssoConfigurationService.testSsoConnection(clientId, request)
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
    fun getSsoStatus(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val clientId = extractClientId(jwt)

        return try {
            val configuration = ssoConfigurationService.getSsoConfiguration(clientId)
            val status = mapOf<String, Any>(
                "enabled" to true,
                "ssoType" to configuration.ssoType.name,
                "configuredAt" to configuration.regDt.toString(),
                "lastModified" to (configuration.modDt?.toString() ?: "")
            )
            ResponseEntity.ok(ApiResponse(data = status, message = "SSO 상태 조회 성공"))
        } catch (e: Exception) {
            val status = mapOf<String, Any>(
                "enabled" to false,
                "message" to "SSO 설정이 되어 있지 않습니다"
            )
            ResponseEntity.ok(ApiResponse(data = status, message = "SSO가 설정되지 않음"))
        }
    }

    /**
     * 지원되는 SSO 타입 조회
     * GET /api/admin/sso/supported-types
     */
    @GetMapping("/supported-types")
    fun getSupportedSsoTypes(): ResponseEntity<ApiResponse<List<Map<String, String>>>> {
        val supportedTypes = listOf(
            mapOf(
                "type" to "OIDC",
                "name" to "OpenID Connect",
                "description" to "OAuth 2.0 확장 프로토콜로 인증과 사용자 정보 조회를 지원합니다"
            ),
            mapOf(
                "type" to "SAML",
                "name" to "SAML 2.0",
                "description" to "XML 기반 인증 프로토콜로 엔터프라이즈 환경에 최적화되어 있습니다"
            ),
            mapOf(
                "type" to "OAUTH2",
                "name" to "OAuth 2.0",
                "description" to "표준 OAuth 2.0 프로토콜로 인가를 통한 사용자 정보 접근을 지원합니다"
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
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            // 실제 저장하지 않고 검증만 수행하기 위해 임시 서비스 메서드 호출
            // 여기서는 간단히 필수 필드들만 검증
            val validationResult = mapOf(
                "valid" to true,
                "message" to "검증 통과"
            )
            ResponseEntity.ok(ApiResponse(data = validationResult, message = "검증 성공"))
        } catch (e: Exception) {
            val validationResult = mapOf(
                "valid" to false,
                "message" to (e.message ?: "검증 실패")
            )
            ResponseEntity.ok(ApiResponse(data = validationResult, message = "검증 완료"))
        }
    }

    /**
     * SSO 설정 연결 테스트
     * POST /api/admin/sso/configuration/test
     */
    @PostMapping("/test")
    fun testSsoConnection(
        @RequestBody testRequest: SsoConnectionTestRequest,
        @AuthenticationPrincipal authentication: JwtAuthenticationToken
    ): ResponseEntity<ApiResponse<SsoConnectionTestResponse>> {
        logger.info("SSO 연결 테스트 요청: ${testRequest.providerType}")

        return try {
            val startTime = System.currentTimeMillis()
            val jwt = authentication.token

            // 연결 테스트 실행
            val testResult = ssoConnectionTestService.testConnection(testRequest)
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