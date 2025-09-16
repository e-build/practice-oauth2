package me.practice.oauth2.client.api

import me.practice.oauth2.client.api.dto.*
import me.practice.oauth2.client.client.AuthorizationServerClient
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 API 컨트롤러
 * 인증된 사용자에게 사용자 정보, 권한 확인, SSO 설정 관리 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/admin")
class AdminApiController(private val authorizationServerClient: AuthorizationServerClient) {

    /**
     * 현재 사용자 정보 조회
     * JWT 토큰이 있으면 상세 정보를, 없으면 미인증 상태를 반환합니다.
     */
    @GetMapping("/user-info")
    fun getUserInfo(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<UserInfoResponseDto> {
        return if (jwt != null) {
            val userInfo = createUserInfoFromJwt(jwt)
            ResponseEntity.ok(userInfo)
        } else {
            val unauthenticatedInfo = UserInfoResponseDto(
                username = null, email = null, name = null, role = null,
                accountId = null, shoplClientId = null, shoplUserId = null,
                userId = null, sub = null, iss = null, aud = null,
                exp = null, iat = null, scope = null,
                authenticated = false, allClaims = null
            )
            ResponseEntity.ok(unauthenticatedInfo)
        }
    }

    /**
     * 사용자 권한 확인
     * JWT 토큰 기반으로 권한 상태를 확인합니다.
     */
    @GetMapping("/check-permission")
    fun checkPermission(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<PermissionCheckResponseDto> {
        return if (jwt != null) {
            val response = PermissionCheckResponseDto(
                hasPermission = true,
                role = jwt.getClaimAsString("role")
            )
            ResponseEntity.ok(response)
        } else {
            val response = PermissionCheckResponseDto(
                hasPermission = false,
                message = "Authentication required"
            )
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
        }
    }

    /**
     * SSO 설정 목록 조회
     * 현재는 클라이언트당 하나의 SSO 설정만 지원하므로 단일 설정을 배열로 반환합니다.
     */
    @GetMapping("/sso/configurations")
    fun getSsoConfigurations(@AuthenticationPrincipal jwt: Jwt?): ResponseEntity<List<SsoConfigurationSummaryResponseDto>> {
        return when (val validationResult = validateJwtAndExtractClientId(jwt)) {
            is JwtValidationResult.Success -> {
                try {
                    val configurations = fetchSsoConfigurations(jwt!!, validationResult.clientId)
                    ResponseEntity.ok(configurations)
                } catch (e: Exception) {
                    // 에러 케이스는 빈 리스트로 반환 (프론트엔드 일관성)
                    ResponseEntity.ok(emptyList())
                }
            }
            is JwtValidationResult.Error -> {
                ResponseEntity.status(validationResult.status).body(emptyList())
            }
        }
    }

    // === Private Helper Methods ===

    /**
     * JWT에서 사용자 정보를 추출하여 UserInfoResponseDto를 생성합니다.
     */
    private fun createUserInfoFromJwt(jwt: Jwt): UserInfoResponseDto {
        val allClaims = jwt.claims
        return UserInfoResponseDto(
            username = jwt.getClaimAsString("username"),
            email = jwt.getClaimAsString("email"),
            name = jwt.getClaimAsString("name"),
            role = jwt.getClaimAsString("role"),
            accountId = jwt.getClaimAsString("account_id"),
            shoplClientId = jwt.getClaimAsString("shopl_client_id"),
            shoplUserId = jwt.getClaimAsString("shopl_user_id"),
            userId = jwt.getClaimAsString("user_id"),
            sub = jwt.getClaimAsString("sub"),
            iss = jwt.getClaimAsString("iss"),
            aud = jwt.audience,
            exp = jwt.getClaim<Long>("exp"),
            iat = jwt.getClaim<Long>("iat"),
            scope = jwt.getClaimAsString("scope"),
            authenticated = true,
            allClaims = allClaims
        )
    }

    /**
     * JWT 검증 및 클라이언트 ID 추출
     */
    private fun validateJwtAndExtractClientId(jwt: Jwt?): JwtValidationResult {
        if (jwt == null) {
            return JwtValidationResult.Error(HttpStatus.UNAUTHORIZED)
        }

        val shoplClientId = jwt.getClaimAsString("shopl_client_id")
        if (shoplClientId.isNullOrEmpty()) {
            return JwtValidationResult.Error(HttpStatus.BAD_REQUEST)
        }

        return JwtValidationResult.Success(shoplClientId)
    }

    /**
     * SSO 설정 목록을 가져옵니다.
     */
    private fun fetchSsoConfigurations(jwt: Jwt, clientId: String): List<SsoConfigurationSummaryResponseDto> {
        val ssoConfiguration = authorizationServerClient.getSsoConfiguration(jwt, clientId)

        return if (ssoConfiguration != null) {
            val configurationSummary = createSsoConfigurationSummary(ssoConfiguration)
            listOf(configurationSummary)
        } else {
            emptyList()
        }
    }

    /**
     * SSO 설정 요약 정보를 생성합니다.
     */
    private fun createSsoConfigurationSummary(config: SsoConfigurationResponseDto): SsoConfigurationSummaryResponseDto {
        val details = createSsoConfigurationDetails(config)

        return SsoConfigurationSummaryResponseDto(
            id = config.id,
            name = generateSsoName(config),
            providerType = config.ssoType.name,
            status = "ACTIVE",
            createdAt = config.regDt.toString(),
            updatedAt = (config.modDt?.toString() ?: config.regDt.toString()),
            details = details,
            autoProvision = config.autoProvision,
            defaultRole = config.defaultRole
        )
    }

    /**
     * SSO 타입별 세부 정보를 생성합니다.
     */
    private fun createSsoConfigurationDetails(config: SsoConfigurationResponseDto): SsoConfigurationDetailsDto {
        return when (config.ssoType) {
            SsoType.OIDC -> createOidcDetails(config)
            SsoType.SAML -> createSamlDetails(config)
        }
    }

    /**
     * OIDC 세부 정보를 생성합니다.
     */
    private fun createOidcDetails(config: SsoConfigurationResponseDto): SsoConfigurationDetailsDto {
        return SsoConfigurationDetailsDto(
            clientId = config.oidcClientId,
            issuer = config.oidcIssuer,
            scopes = config.oidcScopes,
            responseType = config.oidcResponseType,
            responseMode = config.oidcResponseMode,
            hasClaimsMapping = (config.oidcClaimsMapping != null),
            hasRedirectUris = (config.redirectUris != null)
        )
    }

    /**
     * SAML 세부 정보를 생성합니다.
     */
    private fun createSamlDetails(config: SsoConfigurationResponseDto): SsoConfigurationDetailsDto {
        return SsoConfigurationDetailsDto(
            entityId = config.samlEntityId,
            ssoUrl = config.samlSsoUrl,
            sloUrl = config.samlSloUrl,
            nameIdFormat = config.samlNameIdFormat,
            bindingSso = config.samlBindingSso?.name,
            bindingSlo = config.samlBindingSlo?.name,
            wantAssertionsSigned = config.samlWantAssertionsSigned,
            wantResponseSigned = config.samlWantResponseSigned,
            hasCertificate = (config.samlX509Cert != null),
            hasAttributeMapping = (config.samlAttributeMapping != null)
        )
    }

    /**
     * SSO 제공자별 표시 이름을 생성합니다.
     */
    private fun generateSsoName(config: SsoConfigurationResponseDto): String {
        return when (config.ssoType) {
            SsoType.OIDC -> generateOidcName(config)
            SsoType.SAML -> generateSamlName(config)
        }
    }

    /**
     * OIDC 제공자 이름을 생성합니다.
     */
    private fun generateOidcName(config: SsoConfigurationResponseDto): String {
        val issuer = config.oidcIssuer
        return when {
            issuer?.contains("google", ignoreCase = true) == true -> "Google SSO"
            issuer?.contains("microsoft", ignoreCase = true) == true ||
            issuer?.contains("azure", ignoreCase = true) == true -> "Microsoft SSO"
            issuer?.contains("okta", ignoreCase = true) == true -> "Okta SSO"
            issuer?.contains("auth0", ignoreCase = true) == true -> "Auth0 SSO"
            config.oidcClientId != null -> "${config.oidcClientId} OIDC"
            else -> "OIDC SSO"
        }
    }

    /**
     * SAML 제공자 이름을 생성합니다.
     */
    private fun generateSamlName(config: SsoConfigurationResponseDto): String {
        val entityId = config.samlEntityId
        return when {
            entityId?.contains("google", ignoreCase = true) == true -> "Google SAML"
            entityId?.contains("microsoft", ignoreCase = true) == true ||
            entityId?.contains("azure", ignoreCase = true) == true -> "Microsoft SAML"
            entityId?.contains("okta", ignoreCase = true) == true -> "Okta SAML"
            entityId != null -> "${entityId.split("/").last()} SAML"
            else -> "SAML SSO"
        }
    }

    // === Internal Data Classes ===

    /**
     * JWT 검증 결과를 나타내는 sealed class
     */
    private sealed class JwtValidationResult {
        data class Success(val clientId: String) : JwtValidationResult()
        data class Error(val status: HttpStatus) : JwtValidationResult()
    }
}