package me.practice.oauth2.client.api.dto

import me.practice.oauth2.client.entity.SamlBinding
import me.practice.oauth2.client.entity.SsoType
import java.time.LocalDateTime

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

    // OAuth2 설정
    val oauth2ClientId: String? = null,
    val oauth2ClientSecret: String? = null,
    val oauth2AuthorizationUri: String? = null,
    val oauth2TokenUri: String? = null,
    val oauth2UserInfoUri: String? = null,
    val oauth2Scopes: String? = null,
    val oauth2UserNameAttribute: String? = null,

    // 공통 설정
    val redirectUris: List<String>? = null,
    val autoProvision: Boolean = true,
    val defaultRole: String? = null
)

data class SsoConfigurationResponseDto(
    val id: String,
    val clientId: String,
    val ssoType: SsoType,

    // OIDC 설정
    val oidcClientId: String? = null,
    val oidcIssuer: String? = null,
    val oidcScopes: String? = null,
    val oidcResponseType: String? = null,
    val oidcResponseMode: String? = null,
    val oidcClaimsMapping: Map<String, String>? = null,

    // SAML 설정
    val samlEntityId: String? = null,
    val samlSsoUrl: String? = null,
    val samlSloUrl: String? = null,
    val samlNameIdFormat: String? = null,
    val samlBindingSso: SamlBinding? = null,
    val samlBindingSlo: SamlBinding? = null,
    val samlWantAssertionsSigned: Boolean? = null,
    val samlWantResponseSigned: Boolean? = null,
    val samlSignatureAlgorithm: String? = null,
    val samlDigestAlgorithm: String? = null,
    val samlAttributeMapping: Map<String, String>? = null,

    // OAuth2 설정
    val oauth2ClientId: String? = null,
    val oauth2AuthorizationUri: String? = null,
    val oauth2TokenUri: String? = null,
    val oauth2UserInfoUri: String? = null,
    val oauth2Scopes: String? = null,
    val oauth2UserNameAttribute: String? = null,

    // 공통 설정
    val redirectUris: List<String>? = null,
    val autoProvision: Boolean = true,
    val defaultRole: String? = null,

    // 메타데이터
    val regDt: LocalDateTime,
    val modDt: LocalDateTime? = null
)

data class SsoConfigurationSummaryDto(
    val id: String,
    val clientId: String,
    val ssoType: SsoType,
    val providerName: String, // 표시용 이름 (예: "Google Workspace", "Microsoft Azure AD")
    val isActive: Boolean,
    val regDt: LocalDateTime,
    val modDt: LocalDateTime? = null
)

data class SsoConnectionTestRequestDto(
    val ssoType: SsoType,
    val testUrl: String? = null,
    val credentials: Map<String, String>? = null
)

data class SsoConnectionTestResponseDto(
    val success: Boolean,
    val message: String,
    val details: Map<String, Any>? = null,
    val testedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * SSO 연결 테스트 요청 DTO (웹에서 사용)
 */
data class SsoConnectionTestRequest(
    val providerType: String,
    val configName: String? = null,
    val companyDomain: String? = null,

    // SAML 필드
    val samlMetadataUrl: String? = null,
    val samlEntityId: String? = null,

    // OIDC 필드
    val oidcClientId: String? = null,
    val oidcClientSecret: String? = null,
    val oidcDiscoveryUrl: String? = null,
    val oidcScope: String? = null,

    // OAuth2 필드
    val oauth2ClientId: String? = null,
    val oauth2ClientSecret: String? = null,
    val oauth2AuthUrl: String? = null,
    val oauth2TokenUrl: String? = null,
    val oauth2UserInfoUrl: String? = null
)

/**
 * SSO 연결 테스트 응답 DTO (웹에서 사용)
 */
data class SsoConnectionTestResponse(
    val success: Boolean,
    val message: String,
    val responseTime: Long,
    val details: Map<String, Any> = emptyMap()
)