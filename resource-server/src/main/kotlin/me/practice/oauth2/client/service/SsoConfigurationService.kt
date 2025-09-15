package me.practice.oauth2.client.service

import me.practice.oauth2.client.api.dto.SsoConfigurationRequestDto
import me.practice.oauth2.client.api.dto.SsoConfigurationResponseDto
import me.practice.oauth2.client.api.dto.SsoConfigurationSummaryDto
import me.practice.oauth2.client.api.dto.SsoConnectionTestRequestDto
import me.practice.oauth2.client.api.dto.SsoConnectionTestResponseDto
import me.practice.oauth2.client.entity.IoClientSsoSetting
import me.practice.oauth2.client.entity.SsoType
import me.practice.oauth2.client.exception.*
import me.practice.oauth2.client.repository.IoClientSsoSettingRepository
import me.practice.oauth2.client.util.JsonMappingUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class SsoConfigurationService(
    private val ssoSettingRepository: IoClientSsoSettingRepository,
    private val jsonMappingUtil: JsonMappingUtil,
    private val connectionTestService: SsoConnectionTestService,
    private val authServerSyncService: AuthorizationServerSyncService
) {

    private val logger = LoggerFactory.getLogger(SsoConfigurationService::class.java)

    /**
     * 클라이언트의 SSO 설정 조회
     */
    @Transactional(readOnly = true)
    fun getSsoConfiguration(clientId: String): SsoConfigurationResponseDto {
        val setting = ssoSettingRepository.findByClientIdAndDelDtIsNull(clientId)
            ?: throw SsoConfigurationNotFoundException(clientId)

        return convertToResponseDto(setting)
    }

    /**
     * 모든 활성 SSO 설정 목록 조회 (요약 정보)
     */
    @Transactional(readOnly = true)
    fun getAllSsoConfigurations(): List<SsoConfigurationSummaryDto> {
        return ssoSettingRepository.findAllActiveSettings()
            .map { convertToSummaryDto(it) }
    }

    /**
     * SSO 설정 생성
     */
    fun createSsoConfiguration(clientId: String, request: SsoConfigurationRequestDto): SsoConfigurationResponseDto {
        // 기존 설정 존재 여부 확인
        if (ssoSettingRepository.existsByClientIdAndDelDtIsNull(clientId)) {
            throw DuplicateSsoConfigurationException("clientId", clientId)
        }

        // 요청 데이터 검증
        validateSsoConfigurationRequest(request)

        // 중복 확인 (프로토콜별 고유 필드)
        checkDuplicateConfiguration(request)

        // 엔티티 생성 및 저장
        val entity = convertToEntity(clientId, request)
        val savedEntity = ssoSettingRepository.save(entity)
        val responseDto = convertToResponseDto(savedEntity)

        // 인증 서버에 동기화 (비동기적으로 처리하되 오류 시 로그만 기록)
        try {
            authServerSyncService.syncToAuthorizationServer(responseDto)
        } catch (e: Exception) {
            // 동기화 실패해도 생성은 성공으로 처리
            logger.warn("SSO 설정 생성 후 인증 서버 동기화 실패: ${e.message}")
        }

        return responseDto
    }

    /**
     * SSO 설정 수정
     */
    fun updateSsoConfiguration(clientId: String, request: SsoConfigurationRequestDto): SsoConfigurationResponseDto {
        val existingSetting = ssoSettingRepository.findByClientIdAndDelDtIsNull(clientId)
            ?: throw SsoConfigurationNotFoundException(clientId)

        // 요청 데이터 검증
        validateSsoConfigurationRequest(request)

        // 중복 확인 (자신 제외)
        checkDuplicateConfiguration(request, existingSetting.id)

        // 엔티티 업데이트
        val updatedEntity = existingSetting.copy(
            ssoType = request.ssoType,

            // OIDC 필드 업데이트
            oidcClientId = request.oidcClientId,
            oidcClientSecret = request.oidcClientSecret,
            oidcIssuer = request.oidcIssuer,
            oidcScopes = request.oidcScopes,
            oidcResponseType = request.oidcResponseType,
            oidcResponseMode = request.oidcResponseMode,
            oidcClaimsMapping = jsonMappingUtil.mapToJson(request.oidcClaimsMapping),

            // SAML 필드 업데이트
            samlEntityId = request.samlEntityId,
            samlSsoUrl = request.samlSsoUrl,
            samlSloUrl = request.samlSloUrl,
            samlX509Cert = request.samlX509Cert,
            samlNameIdFormat = request.samlNameIdFormat,
            samlBindingSso = request.samlBindingSso,
            samlBindingSlo = request.samlBindingSlo,
            samlWantAssertionsSigned = request.samlWantAssertionsSigned,
            samlWantResponseSigned = request.samlWantResponseSigned,
            samlSignatureAlgorithm = request.samlSignatureAlgorithm,
            samlDigestAlgorithm = request.samlDigestAlgorithm,
            samlAttributeMapping = jsonMappingUtil.mapToJson(request.samlAttributeMapping),

            // OAuth2 필드 업데이트
            oauth2ClientId = request.oauth2ClientId,
            oauth2ClientSecret = request.oauth2ClientSecret,
            oauth2AuthorizationUri = request.oauth2AuthorizationUri,
            oauth2TokenUri = request.oauth2TokenUri,
            oauth2UserInfoUri = request.oauth2UserInfoUri,
            oauth2Scopes = request.oauth2Scopes,
            oauth2UserNameAttribute = request.oauth2UserNameAttribute,

            // 공통 필드 업데이트
            redirectUris = jsonMappingUtil.listToJson(request.redirectUris),
            autoProvision = request.autoProvision,
            defaultRole = request.defaultRole,
            modDt = LocalDateTime.now()
        )

        val savedEntity = ssoSettingRepository.save(updatedEntity)
        return convertToResponseDto(savedEntity)
    }

    /**
     * SSO 설정 삭제 (소프트 삭제)
     */
    fun deleteSsoConfiguration(clientId: String) {
        val existingSetting = ssoSettingRepository.findByClientIdAndDelDtIsNull(clientId)
            ?: throw SsoConfigurationNotFoundException(clientId)

        ssoSettingRepository.softDeleteById(existingSetting.id)

        // 인증 서버에 삭제 동기화 (비동기적으로 처리하되 오류 시 로그만 기록)
        try {
            authServerSyncService.syncDeletionToAuthorizationServer(clientId)
        } catch (e: Exception) {
            // 동기화 실패해도 삭제는 성공으로 처리
            logger.warn("SSO 설정 삭제 후 인증 서버 동기화 실패: ${e.message}")
        }
    }

    /**
     * SSO 연결 테스트
     */
    fun testSsoConnection(clientId: String, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        val setting = ssoSettingRepository.findByClientIdAndDelDtIsNull(clientId)
            ?: throw SsoConfigurationNotFoundException(clientId)

        return when (setting.ssoType) {
            SsoType.OIDC -> testOidcConnection(setting, request)
            SsoType.SAML -> testSamlConnection(setting, request)
            SsoType.OAUTH2 -> testOauth2Connection(setting, request)
        }
    }

    /**
     * SSO 설정 요청 검증
     */
    private fun validateSsoConfigurationRequest(request: SsoConfigurationRequestDto) {
        when (request.ssoType) {
            SsoType.OIDC -> validateOidcConfiguration(request)
            SsoType.SAML -> validateSamlConfiguration(request)
            SsoType.OAUTH2 -> validateOauth2Configuration(request)
        }
    }

    /**
     * OIDC 설정 검증
     */
    private fun validateOidcConfiguration(request: SsoConfigurationRequestDto) {
        // 필수 필드 검증
        if (request.oidcClientId.isNullOrBlank()) {
            throw SsoValidationException("oidcClientId", "OIDC Client ID는 필수입니다")
        }
        if (request.oidcClientSecret.isNullOrBlank()) {
            throw SsoValidationException("oidcClientSecret", "OIDC Client Secret은 필수입니다")
        }
        if (request.oidcIssuer.isNullOrBlank()) {
            throw SsoValidationException("oidcIssuer", "OIDC Issuer URL은 필수입니다")
        }

        // URL 형식 검증
        if (!isValidUrl(request.oidcIssuer)) {
            throw SsoValidationException("oidcIssuer", "유효하지 않은 Issuer URL 형식입니다")
        }

        // Scopes 검증
        val scopes = request.oidcScopes?.split(" ") ?: emptyList()
        if (!scopes.contains("openid")) {
            throw SsoValidationException("oidcScopes", "OIDC는 openid scope가 필수입니다")
        }

        // Response Type 검증
        val validResponseTypes = listOf("code", "id_token", "token", "code id_token", "code token", "id_token token", "code id_token token")
        if (request.oidcResponseType != null && !validResponseTypes.contains(request.oidcResponseType)) {
            throw SsoValidationException("oidcResponseType", "유효하지 않은 Response Type입니다")
        }
    }

    /**
     * SAML 설정 검증
     */
    private fun validateSamlConfiguration(request: SsoConfigurationRequestDto) {
        // 필수 필드 검증
        if (request.samlEntityId.isNullOrBlank()) {
            throw SsoValidationException("samlEntityId", "SAML Entity ID는 필수입니다")
        }
        if (request.samlSsoUrl.isNullOrBlank()) {
            throw SsoValidationException("samlSsoUrl", "SAML SSO URL은 필수입니다")
        }
        if (request.samlX509Cert.isNullOrBlank()) {
            throw SsoValidationException("samlX509Cert", "SAML X.509 인증서는 필수입니다")
        }

        // URL 형식 검증
        if (!isValidUrl(request.samlSsoUrl)) {
            throw SsoValidationException("samlSsoUrl", "유효하지 않은 SSO URL 형식입니다")
        }

        if (request.samlSloUrl != null && !isValidUrl(request.samlSloUrl)) {
            throw SsoValidationException("samlSloUrl", "유효하지 않은 SLO URL 형식입니다")
        }

        // 인증서 형식 검증 (간단한 검증)
        if (!request.samlX509Cert.contains("BEGIN CERTIFICATE") || !request.samlX509Cert.contains("END CERTIFICATE")) {
            throw SsoValidationException("samlX509Cert", "올바른 X.509 인증서 형식이 아닙니다")
        }

        // Name ID Format 검증
        val validNameIdFormats = listOf(
            "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent",
            "urn:oasis:names:tc:SAML:2.0:nameid-format:transient",
            "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
            "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"
        )
        if (request.samlNameIdFormat != null && !validNameIdFormats.contains(request.samlNameIdFormat)) {
            throw SsoValidationException("samlNameIdFormat", "유효하지 않은 Name ID Format입니다")
        }
    }

    /**
     * OAuth2 설정 검증
     */
    private fun validateOauth2Configuration(request: SsoConfigurationRequestDto) {
        // 필수 필드 검증
        if (request.oauth2ClientId.isNullOrBlank()) {
            throw SsoValidationException("oauth2ClientId", "OAuth2 Client ID는 필수입니다")
        }
        if (request.oauth2ClientSecret.isNullOrBlank()) {
            throw SsoValidationException("oauth2ClientSecret", "OAuth2 Client Secret은 필수입니다")
        }
        if (request.oauth2AuthorizationUri.isNullOrBlank()) {
            throw SsoValidationException("oauth2AuthorizationUri", "OAuth2 Authorization URI는 필수입니다")
        }
        if (request.oauth2TokenUri.isNullOrBlank()) {
            throw SsoValidationException("oauth2TokenUri", "OAuth2 Token URI는 필수입니다")
        }
        if (request.oauth2UserInfoUri.isNullOrBlank()) {
            throw SsoValidationException("oauth2UserInfoUri", "OAuth2 User Info URI는 필수입니다")
        }

        // URL 형식 검증
        if (!isValidUrl(request.oauth2AuthorizationUri)) {
            throw SsoValidationException("oauth2AuthorizationUri", "유효하지 않은 Authorization URI 형식입니다")
        }
        if (!isValidUrl(request.oauth2TokenUri)) {
            throw SsoValidationException("oauth2TokenUri", "유효하지 않은 Token URI 형식입니다")
        }
        if (!isValidUrl(request.oauth2UserInfoUri)) {
            throw SsoValidationException("oauth2UserInfoUri", "유효하지 않은 User Info URI 형식입니다")
        }
    }

    /**
     * 중복 설정 확인
     */
    private fun checkDuplicateConfiguration(request: SsoConfigurationRequestDto, excludeId: String? = null) {
        when (request.ssoType) {
            SsoType.OIDC -> {
                request.oidcClientId?.let { clientId ->
                    val existing = ssoSettingRepository.findByOidcClientIdAndDelDtIsNull(clientId)
                    if (existing != null && existing.id != excludeId) {
                        throw DuplicateSsoConfigurationException("OIDC Client ID", clientId)
                    }
                }
            }
            SsoType.SAML -> {
                request.samlEntityId?.let { entityId ->
                    val existing = ssoSettingRepository.findBySamlEntityIdAndDelDtIsNull(entityId)
                    if (existing != null && existing.id != excludeId) {
                        throw DuplicateSsoConfigurationException("SAML Entity ID", entityId)
                    }
                }
            }
            SsoType.OAUTH2 -> {
                request.oauth2ClientId?.let { clientId ->
                    val existing = ssoSettingRepository.findByOauth2ClientIdAndDelDtIsNull(clientId)
                    if (existing != null && existing.id != excludeId) {
                        throw DuplicateSsoConfigurationException("OAuth2 Client ID", clientId)
                    }
                }
            }
        }
    }

    /**
     * URL 유효성 검증
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlPattern = "^https?://[^\\s/$.?#].[^\\s]*$".toRegex()
            urlPattern.matches(url)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 요청 DTO를 엔티티로 변환
     */
    private fun convertToEntity(clientId: String, request: SsoConfigurationRequestDto): IoClientSsoSetting {
        return IoClientSsoSetting(
            id = UUID.randomUUID().toString().replace("-", ""),
            clientId = clientId,
            ssoType = request.ssoType,

            // OIDC 필드
            oidcClientId = request.oidcClientId,
            oidcClientSecret = request.oidcClientSecret,
            oidcIssuer = request.oidcIssuer,
            oidcScopes = request.oidcScopes,
            oidcResponseType = request.oidcResponseType,
            oidcResponseMode = request.oidcResponseMode,
            oidcClaimsMapping = jsonMappingUtil.mapToJson(request.oidcClaimsMapping),

            // SAML 필드
            samlEntityId = request.samlEntityId,
            samlSsoUrl = request.samlSsoUrl,
            samlSloUrl = request.samlSloUrl,
            samlX509Cert = request.samlX509Cert,
            samlNameIdFormat = request.samlNameIdFormat,
            samlBindingSso = request.samlBindingSso,
            samlBindingSlo = request.samlBindingSlo,
            samlWantAssertionsSigned = request.samlWantAssertionsSigned,
            samlWantResponseSigned = request.samlWantResponseSigned,
            samlSignatureAlgorithm = request.samlSignatureAlgorithm,
            samlDigestAlgorithm = request.samlDigestAlgorithm,
            samlAttributeMapping = jsonMappingUtil.mapToJson(request.samlAttributeMapping),

            // OAuth2 필드
            oauth2ClientId = request.oauth2ClientId,
            oauth2ClientSecret = request.oauth2ClientSecret,
            oauth2AuthorizationUri = request.oauth2AuthorizationUri,
            oauth2TokenUri = request.oauth2TokenUri,
            oauth2UserInfoUri = request.oauth2UserInfoUri,
            oauth2Scopes = request.oauth2Scopes,
            oauth2UserNameAttribute = request.oauth2UserNameAttribute,

            // 공통 필드
            redirectUris = jsonMappingUtil.listToJson(request.redirectUris),
            autoProvision = request.autoProvision,
            defaultRole = request.defaultRole,
            regDt = LocalDateTime.now()
        )
    }

    /**
     * 엔티티를 응답 DTO로 변환
     */
    private fun convertToResponseDto(entity: IoClientSsoSetting): SsoConfigurationResponseDto {
        return SsoConfigurationResponseDto(
			id = entity.id,
			clientId = entity.clientId,
			ssoType = entity.ssoType,

			// OIDC 필드 (Secret 제외)
			oidcClientId = entity.oidcClientId,
			oidcIssuer = entity.oidcIssuer,
			oidcScopes = entity.oidcScopes,
			oidcResponseType = entity.oidcResponseType,
			oidcResponseMode = entity.oidcResponseMode,
			oidcClaimsMapping = jsonMappingUtil.jsonToMap(entity.oidcClaimsMapping),

			// SAML 필드
			samlEntityId = entity.samlEntityId,
			samlSsoUrl = entity.samlSsoUrl,
			samlSloUrl = entity.samlSloUrl,
			samlNameIdFormat = entity.samlNameIdFormat,
			samlBindingSso = entity.samlBindingSso,
			samlBindingSlo = entity.samlBindingSlo,
			samlWantAssertionsSigned = entity.samlWantAssertionsSigned,
			samlWantResponseSigned = entity.samlWantResponseSigned,
			samlSignatureAlgorithm = entity.samlSignatureAlgorithm,
			samlDigestAlgorithm = entity.samlDigestAlgorithm,
			samlAttributeMapping = jsonMappingUtil.jsonToMap(entity.samlAttributeMapping),

			// OAuth2 필드 (Secret 제외)
			oauth2ClientId = entity.oauth2ClientId,
			oauth2AuthorizationUri = entity.oauth2AuthorizationUri,
			oauth2TokenUri = entity.oauth2TokenUri,
			oauth2UserInfoUri = entity.oauth2UserInfoUri,
			oauth2Scopes = entity.oauth2Scopes,
			oauth2UserNameAttribute = entity.oauth2UserNameAttribute,

			// 공통 필드
			redirectUris = jsonMappingUtil.jsonToList(entity.redirectUris),
			autoProvision = entity.autoProvision,
			defaultRole = entity.defaultRole,
			regDt = entity.regDt,
			modDt = entity.modDt
		)
    }

    /**
     * 엔티티를 요약 DTO로 변환
     */
    private fun convertToSummaryDto(entity: IoClientSsoSetting): SsoConfigurationSummaryDto {
        val providerName = when (entity.ssoType) {
            SsoType.OIDC -> "OIDC Provider (${entity.oidcIssuer?.substringAfter("://")?.substringBefore("/") ?: "Unknown"})"
            SsoType.SAML -> "SAML Provider (${entity.samlEntityId ?: "Unknown"})"
            SsoType.OAUTH2 -> "OAuth2 Provider (${entity.oauth2AuthorizationUri?.substringAfter("://")?.substringBefore("/") ?: "Unknown"})"
        }

        return SsoConfigurationSummaryDto(
			id = entity.id,
			clientId = entity.clientId,
			ssoType = entity.ssoType,
			providerName = providerName,
			isActive = entity.delDt == null,
			regDt = entity.regDt,
			modDt = entity.modDt
		)
    }

    /**
     * OIDC 연결 테스트 (실제 구현)
     */
    private fun testOidcConnection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return connectionTestService.testOidcConnection(setting, request)
    }

    /**
     * SAML 연결 테스트 (실제 구현)
     */
    private fun testSamlConnection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return connectionTestService.testSamlConnection(setting, request)
    }

    /**
     * OAuth2 연결 테스트 (실제 구현)
     */
    private fun testOauth2Connection(setting: IoClientSsoSetting, request: SsoConnectionTestRequestDto): SsoConnectionTestResponseDto {
        return connectionTestService.testOauth2Connection(setting, request)
    }
}