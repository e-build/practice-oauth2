package me.practice.oauth2.controller

import com.fasterxml.jackson.databind.ObjectMapper
import me.practice.oauth2.entity.IoIdpShoplClientSsoSetting
import me.practice.oauth2.entity.SsoType
import me.practice.oauth2.entity.SamlBinding
import me.practice.oauth2.service.SsoConfigurationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

/**
 * 내부 SSO API 컨트롤러
 * 리소스 서버와의 SSO 설정 동기화를 위한 내부 API
 */
@RestController
@RequestMapping("/api/sso")
class InternalSsoApiController(
    private val ssoConfigurationService: SsoConfigurationService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(InternalSsoApiController::class.java)

    data class ApiResponse<T>(
        val success: Boolean = true,
        val data: T? = null,
        val message: String? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
    )

    data class ResourceServerSsoSettingDto(
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


        // 공통 필드
        val redirectUris: String? = null,
        val autoProvision: Boolean = true,
        val defaultRole: String? = null
    )

    /**
     * 클라이언트의 SSO 설정 조회 - Resource Server 호환
     * GET /api/sso/configuration?shoplClientId={id}
     */
    @GetMapping("/configuration")
    fun getSsoConfiguration(
        @RequestParam shoplClientId: String
    ): ResponseEntity<SsoConfigurationResponseDto?> {

        logger.info("SSO 설정 조회 요청 (Resource Server): shoplClientId=$shoplClientId")

        return try {
            val settings = ssoConfigurationService.getSsoSettings(shoplClientId)
            if (settings != null) {
                val responseDto = convertToSsoConfigurationResponseDto(settings)
                ResponseEntity.ok(responseDto)
            } else {
                ResponseEntity.ok(null)
            }
        } catch (e: Exception) {
            logger.error("SSO 설정 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }

    /**
     * SSO 설정 생성 - Resource Server 호환
     * POST /api/sso/configuration?shoplClientId={id}
     */
    @PostMapping("/configuration")
    fun createSsoConfiguration(
        @RequestParam shoplClientId: String,
        @RequestBody request: SsoConfigurationRequestDto
    ): ResponseEntity<SsoConfigurationResponseDto> {

        logger.info("SSO 설정 생성 요청 (Resource Server): shoplClientId=$shoplClientId")

        return try {
            val newSettings = createEntityFromSsoConfigurationDto(shoplClientId, request)
            val savedSettings = ssoConfigurationService.createSsoSettings(newSettings)
            val responseDto = convertToSsoConfigurationResponseDto(savedSettings)

            logger.info("SSO 설정 생성 성공: id=${savedSettings.id}")
            ResponseEntity.ok(responseDto)
        } catch (e: Exception) {
            logger.error("SSO 설정 생성 실패", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
        }
    }

    /**
     * SSO 설정 수정 - Resource Server 호환
     * PUT /api/sso/configuration?shoplClientId={id}
     */
    @PutMapping("/configuration")
    fun updateSsoConfiguration(
        @RequestParam shoplClientId: String,
        @RequestBody request: SsoConfigurationRequestDto
    ): ResponseEntity<SsoConfigurationResponseDto> {

        logger.info("SSO 설정 수정 요청 (Resource Server): shoplClientId=$shoplClientId")

        return try {
            val existingSettings = ssoConfigurationService.getSsoSettings(shoplClientId)
            if (existingSettings != null) {
                val updatedSettings = updateEntityFromSsoConfigurationDto(existingSettings, request)
                val savedSettings = ssoConfigurationService.updateSsoSettings(updatedSettings)
                val responseDto = convertToSsoConfigurationResponseDto(savedSettings)

                logger.info("SSO 설정 수정 성공: id=${savedSettings.id}")
                ResponseEntity.ok(responseDto)
            } else {
                // 없으면 새로 생성
                val newSettings = createEntityFromSsoConfigurationDto(shoplClientId, request)
                val savedSettings = ssoConfigurationService.createSsoSettings(newSettings)
                val responseDto = convertToSsoConfigurationResponseDto(savedSettings)
                ResponseEntity.ok(responseDto)
            }
        } catch (e: Exception) {
            logger.error("SSO 설정 수정 실패", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
        }
    }

    /**
     * SSO 설정 삭제 - Resource Server 호환
     * DELETE /api/sso/configuration?shoplClientId={id}
     */
    @DeleteMapping("/configuration")
    fun deleteSsoConfiguration(
        @RequestParam shoplClientId: String
    ): ResponseEntity<Void> {

        logger.info("SSO 설정 삭제 요청 (Resource Server): shoplClientId=$shoplClientId")

        return try {
            ssoConfigurationService.deleteSsoSettings(shoplClientId)
            logger.info("SSO 설정 삭제 완료: shoplClientId=$shoplClientId")
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            logger.error("SSO 설정 삭제 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * 클라이언트의 SSO 설정 조회 (기존 방식 유지)
     * GET /api/sso/settings/{shoplClientId}
     */
    @GetMapping("/settings/{shoplClientId}")
    fun getSsoSettings(
        @PathVariable shoplClientId: String,
        @RequestHeader(value = "X-Source", required = false) source: String?
    ): ResponseEntity<ApiResponse<IoIdpShoplClientSsoSetting?>> {

        logger.info("SSO 설정 조회 요청: shoplClientId=$shoplClientId, source=$source")

        return try {
            val settings = ssoConfigurationService.getSsoSettings(shoplClientId)
            ResponseEntity.ok(
                ApiResponse(
                    data = settings,
                    message = if (settings != null) "SSO 설정 조회 성공" else "SSO 설정이 없습니다"
                )
            )
        } catch (e: Exception) {
            logger.error("SSO 설정 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse<IoIdpShoplClientSsoSetting?>(
                    success = false,
                    message = "SSO 설정 조회 중 오류 발생: ${e.message}"
                )
            )
        }
    }

    /**
     * SSO 설정 생성/수정 (리소스 서버에서 동기화)
     * PUT /api/sso/settings
     */
    @PutMapping("/settings")
    fun syncSsoSettings(
        @RequestBody request: ResourceServerSsoSettingDto,
        @RequestHeader(value = "X-Source", required = false) source: String?
    ): ResponseEntity<ApiResponse<IoIdpShoplClientSsoSetting>> {

        logger.info("SSO 설정 동기화 요청: shoplClientId=${request.shoplClientId}, ssoType=${request.ssoType}, source=$source")

        return try {
            // 기존 설정 확인
            val existingSettings = ssoConfigurationService.getSsoSettings(request.shoplClientId)

            val savedSettings = if (existingSettings != null) {
                // 기존 설정 업데이트
                val updatedSettings = updateEntityFromDto(existingSettings, request)
                ssoConfigurationService.updateSsoSettings(updatedSettings)
            } else {
                // 새 설정 생성
                val newSettings = createEntityFromDto(request)
                ssoConfigurationService.createSsoSettings(newSettings)
            }

            logger.info("SSO 설정 동기화 성공: id=${savedSettings.id}")

            ResponseEntity.ok(
                ApiResponse(
                    data = savedSettings,
                    message = if (existingSettings != null) "SSO 설정 수정 완료" else "SSO 설정 생성 완료"
                )
            )

        } catch (e: Exception) {
            logger.error("SSO 설정 동기화 실패", e)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse<IoIdpShoplClientSsoSetting>(
                    success = false,
                    message = "SSO 설정 동기화 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * SSO 설정 삭제
     * DELETE /api/sso/settings/{shoplClientId}
     */
    @DeleteMapping("/settings/{shoplClientId}")
    fun deleteSsoSettings(
        @PathVariable shoplClientId: String,
        @RequestHeader(value = "X-Source", required = false) source: String?
    ): ResponseEntity<ApiResponse<Nothing>> {

        logger.info("SSO 설정 삭제 요청: shoplClientId=$shoplClientId, source=$source")

        return try {
            val existingSettings = ssoConfigurationService.getSsoSettings(shoplClientId)

            if (existingSettings != null) {
                ssoConfigurationService.deleteSsoSettings(shoplClientId)
                logger.info("SSO 설정 삭제 완료: shoplClientId=$shoplClientId")

                ResponseEntity.ok(
                    ApiResponse(
                        message = "SSO 설정 삭제 완료"
                    )
                )
            } else {
                ResponseEntity.ok(
                    ApiResponse(
                        message = "삭제할 SSO 설정이 없습니다"
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("SSO 설정 삭제 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "SSO 설정 삭제 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * 모든 SSO 설정 목록 조회
     * GET /api/sso/settings
     */
    @GetMapping("/settings")
    fun getAllSsoSettings(
        @RequestHeader(value = "X-Source", required = false) source: String?
    ): ResponseEntity<ApiResponse<List<IoIdpShoplClientSsoSetting>>> {

        logger.info("모든 SSO 설정 조회 요청: source=$source")

        return try {
            val allSettings = ssoConfigurationService.getAllSsoSettings()
            ResponseEntity.ok(
                ApiResponse(
                    data = allSettings,
                    message = "모든 SSO 설정 조회 성공"
                )
            )
        } catch (e: Exception) {
            logger.error("모든 SSO 설정 조회 실패", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse<List<IoIdpShoplClientSsoSetting>>(
                    success = false,
                    message = "SSO 설정 목록 조회 실패: ${e.message}"
                )
            )
        }
    }

    /**
     * DTO를 기존 엔티티에 업데이트
     */
    private fun updateEntityFromDto(
        existing: IoIdpShoplClientSsoSetting,
        dto: ResourceServerSsoSettingDto
    ): IoIdpShoplClientSsoSetting {
        return existing.copy(
            ssoType = SsoType.valueOf(dto.ssoType),

            // OIDC 필드
            oidcClientId = dto.oidcClientId,
            oidcClientSecret = dto.oidcClientSecret,
            oidcIssuer = dto.oidcIssuer,
            oidcScopes = dto.oidcScopes,
            oidcResponseType = dto.oidcResponseType,
            oidcResponseMode = dto.oidcResponseMode,
            oidcClaimsMapping = dto.oidcClaimsMapping,

            // SAML 필드
            samlEntityId = dto.samlEntityId,
            samlSsoUrl = dto.samlSsoUrl,
            samlSloUrl = dto.samlSloUrl,
            samlX509Cert = dto.samlX509Cert,
            samlNameIdFormat = dto.samlNameIdFormat,
            samlBindingSso = dto.samlBindingSso?.let { SamlBinding.valueOf(it) },
            samlBindingSlo = dto.samlBindingSlo?.let { SamlBinding.valueOf(it) },
            samlWantAssertionsSigned = dto.samlWantAssertionsSigned,
            samlWantResponseSigned = dto.samlWantResponseSigned,
            samlSignatureAlgorithm = dto.samlSignatureAlgorithm,
            samlDigestAlgorithm = dto.samlDigestAlgorithm,
            samlAttributeMapping = dto.samlAttributeMapping,

            // 공통 필드
            redirectUris = dto.redirectUris,
            autoProvision = dto.autoProvision,
            defaultRole = dto.defaultRole,
            modDt = LocalDateTime.now()
        )
    }

    /**
     * DTO에서 새 엔티티 생성
     */
    private fun createEntityFromDto(dto: ResourceServerSsoSettingDto): IoIdpShoplClientSsoSetting {
        return IoIdpShoplClientSsoSetting(
            id = dto.id,
            shoplClientId = dto.shoplClientId,
            ssoType = SsoType.valueOf(dto.ssoType),

            // OIDC 필드
            oidcClientId = dto.oidcClientId,
            oidcClientSecret = dto.oidcClientSecret,
            oidcIssuer = dto.oidcIssuer,
            oidcScopes = dto.oidcScopes,
            oidcResponseType = dto.oidcResponseType,
            oidcResponseMode = dto.oidcResponseMode,
            oidcClaimsMapping = dto.oidcClaimsMapping,

            // SAML 필드
            samlEntityId = dto.samlEntityId,
            samlSsoUrl = dto.samlSsoUrl,
            samlSloUrl = dto.samlSloUrl,
            samlX509Cert = dto.samlX509Cert,
            samlNameIdFormat = dto.samlNameIdFormat,
            samlBindingSso = dto.samlBindingSso?.let { SamlBinding.valueOf(it) },
            samlBindingSlo = dto.samlBindingSlo?.let { SamlBinding.valueOf(it) },
            samlWantAssertionsSigned = dto.samlWantAssertionsSigned,
            samlWantResponseSigned = dto.samlWantResponseSigned,
            samlSignatureAlgorithm = dto.samlSignatureAlgorithm,
            samlDigestAlgorithm = dto.samlDigestAlgorithm,
            samlAttributeMapping = dto.samlAttributeMapping,

            // 공통 필드
            redirectUris = dto.redirectUris,
            autoProvision = dto.autoProvision,
            defaultRole = dto.defaultRole,
            regDt = LocalDateTime.now()
        )
    }

    // Resource Server 호환 DTO들
    data class SsoConfigurationRequestDto(
        val ssoType: SsoType,

        // OIDC 설정
        val oidcClientId: String? = null,
        val oidcClientSecret: String? = null,
        val oidcIssuer: String? = null,
        val oidcScopes: String? = null,
        val oidcResponseType: String? = null,
        val oidcResponseMode: String? = null,
        val oidcClaimsMapping: Map<String, String>? = null,

        // SAML 설정
        val samlEntityId: String? = null,
        val samlSsoUrl: String? = null,
        val samlSloUrl: String? = null,
        val samlX509Cert: String? = null,
        val samlNameIdFormat: String? = null,
        val samlBindingSso: SamlBinding? = null,
        val samlBindingSlo: SamlBinding? = null,
        val samlWantAssertionsSigned: Boolean? = null,
        val samlWantResponseSigned: Boolean? = null,
        val samlSignatureAlgorithm: String? = null,
        val samlDigestAlgorithm: String? = null,
        val samlAttributeMapping: Map<String, String>? = null,

        // 공통 설정
        val redirectUris: List<String>? = null,
        val autoProvision: Boolean = true,
        val defaultRole: String? = null
    )

    data class SsoConfigurationResponseDto(
        val id: String,
        val shoplClientId: String,
        val ssoType: SsoType,

        // OIDC 설정
        val oidcClientId: String? = null,
        val oidcClientSecret: String? = null,
        val oidcIssuer: String? = null,
        val oidcScopes: String? = null,
        val oidcResponseType: String? = null,
        val oidcResponseMode: String? = null,
        val oidcClaimsMapping: Map<String, String>? = null,

        // SAML 설정
        val samlEntityId: String? = null,
        val samlSsoUrl: String? = null,
        val samlSloUrl: String? = null,
        val samlX509Cert: String? = null,
        val samlNameIdFormat: String? = null,
        val samlBindingSso: SamlBinding? = null,
        val samlBindingSlo: SamlBinding? = null,
        val samlWantAssertionsSigned: Boolean? = null,
        val samlWantResponseSigned: Boolean? = null,
        val samlSignatureAlgorithm: String? = null,
        val samlDigestAlgorithm: String? = null,
        val samlAttributeMapping: Map<String, String>? = null,

        // 공통 설정
        val redirectUris: List<String>? = null,
        val autoProvision: Boolean = true,
        val defaultRole: String? = null,

        // 메타데이터
        val regDt: LocalDateTime,
        val modDt: LocalDateTime? = null,
        val delDt: LocalDateTime? = null
    )

    /**
     * Entity를 SsoConfigurationResponseDto로 변환
     */
    private fun convertToSsoConfigurationResponseDto(entity: IoIdpShoplClientSsoSetting): SsoConfigurationResponseDto {
        return SsoConfigurationResponseDto(
            id = entity.id,
            shoplClientId = entity.shoplClientId,
            ssoType = entity.ssoType,

            // OIDC 필드
            oidcClientId = entity.oidcClientId,
            oidcClientSecret = entity.oidcClientSecret,
            oidcIssuer = entity.oidcIssuer,
            oidcScopes = entity.oidcScopes,
            oidcResponseType = entity.oidcResponseType,
            oidcResponseMode = entity.oidcResponseMode,
            oidcClaimsMapping = entity.oidcClaimsMapping?.let {
                // String을 Map으로 변환 (JSON 파싱 필요시)
                emptyMap()
            },

            // SAML 필드
            samlEntityId = entity.samlEntityId,
            samlSsoUrl = entity.samlSsoUrl,
            samlSloUrl = entity.samlSloUrl,
            samlX509Cert = entity.samlX509Cert,
            samlNameIdFormat = entity.samlNameIdFormat,
            samlBindingSso = entity.samlBindingSso,
            samlBindingSlo = entity.samlBindingSlo,
            samlWantAssertionsSigned = entity.samlWantAssertionsSigned,
            samlWantResponseSigned = entity.samlWantResponseSigned,
            samlSignatureAlgorithm = entity.samlSignatureAlgorithm,
            samlDigestAlgorithm = entity.samlDigestAlgorithm,
            samlAttributeMapping = entity.samlAttributeMapping?.let {
                // String을 Map으로 변환 (JSON 파싱 필요시)
                emptyMap()
            },

            // 공통 필드
            redirectUris = entity.redirectUris?.split(",")?.map { it.trim() },
            autoProvision = entity.autoProvision,
            defaultRole = entity.defaultRole,

            // 메타데이터
            regDt = entity.regDt,
            modDt = entity.modDt,
            delDt = entity.delDt
        )
    }

    /**
     * SsoConfigurationRequestDto에서 새 엔티티 생성
     */
    private fun createEntityFromSsoConfigurationDto(shoplClientId: String, dto: SsoConfigurationRequestDto): IoIdpShoplClientSsoSetting {
        return IoIdpShoplClientSsoSetting(
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 20),
            shoplClientId = shoplClientId,
            ssoType = dto.ssoType,

            // OIDC 필드
            oidcClientId = dto.oidcClientId,
            oidcClientSecret = dto.oidcClientSecret,
            oidcIssuer = dto.oidcIssuer,
            oidcScopes = dto.oidcScopes,
            oidcResponseType = dto.oidcResponseType,
            oidcResponseMode = dto.oidcResponseMode,
            oidcClaimsMapping = dto.oidcClaimsMapping?.let { objectMapper.writeValueAsString(it) }, // Map을 JSON으로 변환

            // SAML 필드
            samlEntityId = dto.samlEntityId,
            samlSsoUrl = dto.samlSsoUrl,
            samlSloUrl = dto.samlSloUrl,
            samlX509Cert = dto.samlX509Cert,
            samlNameIdFormat = dto.samlNameIdFormat,
            samlBindingSso = dto.samlBindingSso,
            samlBindingSlo = dto.samlBindingSlo,
            samlWantAssertionsSigned = dto.samlWantAssertionsSigned,
            samlWantResponseSigned = dto.samlWantResponseSigned,
            samlSignatureAlgorithm = dto.samlSignatureAlgorithm,
            samlDigestAlgorithm = dto.samlDigestAlgorithm,
            samlAttributeMapping = dto.samlAttributeMapping?.let { objectMapper.writeValueAsString(it) }, // Map을 JSON으로 변환

            // 공통 필드
            redirectUris = dto.redirectUris?.joinToString(","),
            autoProvision = dto.autoProvision,
            defaultRole = dto.defaultRole,
            regDt = LocalDateTime.now()
        )
    }

    /**
     * SsoConfigurationRequestDto로 기존 엔티티 업데이트
     */
    private fun updateEntityFromSsoConfigurationDto(existing: IoIdpShoplClientSsoSetting, dto: SsoConfigurationRequestDto): IoIdpShoplClientSsoSetting {
        return existing.copy(
            ssoType = dto.ssoType,

            // OIDC 필드
            oidcClientId = dto.oidcClientId,
            oidcClientSecret = dto.oidcClientSecret,
            oidcIssuer = dto.oidcIssuer,
            oidcScopes = dto.oidcScopes,
            oidcResponseType = dto.oidcResponseType,
            oidcResponseMode = dto.oidcResponseMode,
            oidcClaimsMapping = dto.oidcClaimsMapping?.let { objectMapper.writeValueAsString(it) },

            // SAML 필드
            samlEntityId = dto.samlEntityId,
            samlSsoUrl = dto.samlSsoUrl,
            samlSloUrl = dto.samlSloUrl,
            samlX509Cert = dto.samlX509Cert,
            samlNameIdFormat = dto.samlNameIdFormat,
            samlBindingSso = dto.samlBindingSso,
            samlBindingSlo = dto.samlBindingSlo,
            samlWantAssertionsSigned = dto.samlWantAssertionsSigned,
            samlWantResponseSigned = dto.samlWantResponseSigned,
            samlSignatureAlgorithm = dto.samlSignatureAlgorithm,
            samlDigestAlgorithm = dto.samlDigestAlgorithm,
            samlAttributeMapping = dto.samlAttributeMapping?.let { objectMapper.writeValueAsString(it) },

            // 공통 필드
            redirectUris = dto.redirectUris?.joinToString(","),
            autoProvision = dto.autoProvision,
            defaultRole = dto.defaultRole,
            modDt = LocalDateTime.now()
        )
    }
}