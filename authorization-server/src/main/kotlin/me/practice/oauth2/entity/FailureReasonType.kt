package me.practice.oauth2.entity

enum class FailureReasonType {
    INVALID_CREDENTIALS,        // 잘못된 자격증명
    ACCOUNT_LOCKED,            // 계정 잠금
    ACCOUNT_EXPIRED,           // 계정 만료
    PASSWORD_EXPIRED,          // 비밀번호 만료
    ACCOUNT_DISABLED,          // 계정 비활성화
    TOO_MANY_ATTEMPTS,         // 로그인 시도 횟수 초과
    INVALID_CLIENT,            // 잘못된 클라이언트
    UNSUPPORTED_GRANT_TYPE,    // 지원하지 않는 Grant Type
    INVALID_SCOPE,             // 잘못된 Scope
    ACCESS_DENIED,             // 액세스 거부 (사용자 취소, 권한 거부)
    RATE_LIMIT_EXCEEDED,       // 요청 한도 초과 (429)
    SSO_ERROR,                 // SSO 연동 오류 (일반적인 오류)
    SSO_PROVIDER_UNAVAILABLE,  // SSO 제공자 서버 오류
    SSO_TOKEN_EXCHANGE_FAILED, // 토큰 교환 실패
    EXTERNAL_PROVIDER_ERROR,   // 외부 제공자 오류
    NETWORK_ERROR,             // 네트워크 오류
    SYSTEM_ERROR,              // 시스템 오류
    UNKNOWN                    // 알 수 없는 오류
}