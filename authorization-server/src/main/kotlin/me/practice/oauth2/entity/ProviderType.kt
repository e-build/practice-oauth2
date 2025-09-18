package me.practice.oauth2.entity

/**
 * 로그인 제공자 타입 enum
 *
 * OAuth2 인증 서버에서 지원하는 모든 인증 제공자 타입을 정의합니다.
 * IoIdpUserLoginHistory 엔티티에서 타입 안전성을 보장하기 위해 사용됩니다.
 */
enum class ProviderType {
    // 소셜 로그인 제공자
    GOOGLE,     // Google OAuth2
    NAVER,      // Naver OAuth2
    KAKAO,      // Kakao OAuth2
    APPLE,      // Apple OAuth2
    MICROSOFT,  // Microsoft OAuth2
    GITHUB,     // GitHub OAuth2

    // SSO 프로토콜
    SAML,       // SAML 2.0
    OIDC,       // OpenID Connect
    OAUTH2,     // 순수 OAuth2

    // 기본 인증
    BASIC       // 기본 로그인 (ID/패스워드)
}