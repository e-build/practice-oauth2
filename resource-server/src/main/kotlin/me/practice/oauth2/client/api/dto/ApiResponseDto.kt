package me.practice.oauth2.client.api.dto

import java.time.LocalDateTime

// Common API Response DTOs

data class ErrorResponseDto(
    val error: String,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// AdminApiController DTOs
data class UserInfoResponseDto(
    val username: String?,
    val email: String?,
    val name: String?,
    val role: String?,
    val accountId: String?,
    val shoplClientId: String?,
    val shoplUserId: String?,
    val userId: String?,
    val sub: String?,
    val iss: String?,
    val aud: List<String>?,
    val exp: Long?,
    val iat: Long?,
    val scope: String?,
    val authenticated: Boolean,
    val allClaims: Map<String, Any>? = null
)

data class AuthenticationStatusDto(
    val authenticated: Boolean
)

data class PermissionCheckResponseDto(
    val hasPermission: Boolean,
    val role: String? = null,
    val message: String? = null
)

data class SsoConfigurationSummaryResponseDto(
    val id: String,
    val name: String,
    val providerType: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val details: SsoConfigurationDetailsDto,
    val autoProvision: Boolean,
    val defaultRole: String?
)

data class SsoConfigurationDetailsDto(
    val clientId: String? = null,
    val issuer: String? = null,
    val scopes: String? = null,
    val responseType: String? = null,
    val responseMode: String? = null,
    val hasClaimsMapping: Boolean? = null,
    val hasRedirectUris: Boolean? = null,
    val entityId: String? = null,
    val ssoUrl: String? = null,
    val sloUrl: String? = null,
    val nameIdFormat: String? = null,
    val bindingSso: String? = null,
    val bindingSlo: String? = null,
    val wantAssertionsSigned: Boolean? = null,
    val wantResponseSigned: Boolean? = null,
    val hasCertificate: Boolean? = null,
    val hasAttributeMapping: Boolean? = null
)

// ConfigurationController DTOs
data class ServerUrlsResponseDto(
    val authorizationServerBaseUrl: String,
    val resourceServerBaseUrl: String
)

// SsoConfigurationController DTOs
data class SsoOperationStatusDto(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any>? = null
)

data class SsoValidationResultDto(
    val isValid: Boolean,
    val message: String,
    val errors: List<String>? = null
)

data class SsoStatusDto(
    val enabled: Boolean,
    val ssoType: String? = null,
    val configuredAt: String? = null,
    val lastModified: String? = null,
    val message: String? = null
)

data class SsoTypeInfoDto(
    val type: String,
    val name: String,
    val description: String
)

data class SsoValidationDto(
    val valid: Boolean,
    val message: String
)

// Union response types for AdminApiController
sealed class UserInfoResponse {
    data class Authenticated(val userInfo: UserInfoResponseDto) : UserInfoResponse()
    data class Unauthenticated(val status: AuthenticationStatusDto) : UserInfoResponse()
}

sealed class SsoConfigurationsResponse {
    data class Success(val configurations: List<SsoConfigurationSummaryResponseDto>) : SsoConfigurationsResponse()
    data class Error(val error: ErrorResponseDto) : SsoConfigurationsResponse()
}