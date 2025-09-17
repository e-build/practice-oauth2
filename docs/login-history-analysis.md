# 로그인 이력 저장 케이스 분석 문서

## 개요

이 문서는 Practice OAuth2 프로젝트에서 로그인 이력이 저장되는 모든 케이스들을 분석한 결과입니다. 로그인 성공/실패 시나리오별로 어떤 클래스에서 어떤 메서드가 호출되는지, 저장되는 데이터는 무엇인지 상세히 정리했습니다.

## 로그인 이력 저장 아키텍처

### 핵심 컴포넌트
- **LoginHistoryService**: 로그인 이력 저장의 중앙 서비스
- **IoIdpUserLoginHistory**: 로그인 이력 엔티티
- **FailureReasonType**: 실패 사유 분류 (22가지)
- **LoginType**: 로그인 타입 (BASIC, SOCIAL, SSO)

### 저장되는 데이터 항목
```kotlin
data class IoIdpUserLoginHistory(
    val shoplClientId: String,        // 클라이언트 ID
    val shoplUserId: String,          // 사용자 ID
    val platform: IdpClient.Platform, // 플랫폼 (DASHBOARD, APP)
    val loginType: LoginType,         // 로그인 타입 (BASIC, SOCIAL, SSO)
    val provider: String?,            // 제공자 (google, kakao 등)
    val result: LoginResult,          // 결과 (SUCCESS, FAIL)
    val failureReason: FailureReasonType?, // 실패 사유
    val ipAddress: String?,           // IP 주소
    val userAgent: String?,           // User-Agent
    val sessionId: String,            // 세션 ID
    val regDt: LocalDateTime          // 등록 시간
)
```

---

## 1. 로그인 성공 케이스

### 1.1 기본 로그인 성공 (BasicAuthenticationProvider)

**호출 위치**: `BasicAuthenticationProvider.handleAuthenticationSuccess()`

**저장되는 데이터**:
```kotlin
loginHistoryService.recordSuccessfulLogin(
    shoplClientId = account.shoplClientId,
    shoplUserId = account.shoplUserId,
    platform = IdpClient.Platform.DASHBOARD,
    loginType = LoginType.BASIC,
    provider = null,
    sessionId = sessionId,
    request = request
)
```

**특징**:
- Provider는 null (기본 로그인이므로)
- Platform은 항상 DASHBOARD
- 로그인 성공 후 호출됨

### 1.2 OAuth2/소셜 로그인 성공 (SsoAuthenticationSuccessHandler)

**호출 위치**: `SsoAuthenticationSuccessHandler.recordSsoLoginHistory()`

**저장되는 데이터**:
```kotlin
loginHistoryService.recordSuccessfulLogin(
    shoplClientId = account.shoplClientId,
    shoplUserId = account.shoplUserId,
    platform = IdpClient.Platform.DASHBOARD,
    loginType = loginType, // SOCIAL 또는 SSO
    provider = providerType.name, // google, kakao 등
    sessionId = sessionId,
    request = request
)
```

**LoginType 결정 로직**:
```kotlin
val loginType = when (providerType) {
    ProviderType.GOOGLE, ProviderType.KAKAO, ProviderType.NAVER,
    ProviderType.APPLE, ProviderType.MICROSOFT, ProviderType.GITHUB -> LoginType.SOCIAL
    ProviderType.SAML, ProviderType.OIDC -> LoginType.SSO
    else -> LoginType.SSO
}
```

**특징**:
- Provider에 실제 제공자명 저장 (GOOGLE, KAKAO 등)
- OAuth2/OIDC 인증 성공 후 사용자 프로비저닝과 함께 호출됨
- 로그인 이력 기록 실패가 SSO 인증을 방해하지 않도록 예외 처리

---

## 2. 로그인 실패 케이스

### 2.1 BasicAuthenticationProvider의 6개 실패 사유

**호출 위치**: `BasicAuthenticationProvider.handleAuthenticationFailure()`

| 실패 사유 | Exception | FailureReasonType |
|----------|-----------|-------------------|
| 로그인 시도 횟수 초과 | TooManyAttemptsException | TOO_MANY_ATTEMPTS |
| 계정 만료 | AccountExpiredException | ACCOUNT_EXPIRED |
| 계정 잠금 | LockedException | ACCOUNT_LOCKED |
| 계정 비활성화 | DisabledException | ACCOUNT_DISABLED |
| 비밀번호 만료 | PasswordExpiredException | PASSWORD_EXPIRED |
| 잘못된 자격증명 | BadCredentialsException | INVALID_CREDENTIALS |

**저장되는 데이터**:
```kotlin
loginHistoryService.recordFailedLogin(
    shoplClientId = account.shoplClientId,
    shoplUserId = account.shoplUserId,
    platform = IdpClient.Platform.DASHBOARD,
    loginType = LoginType.BASIC,
    failureReason = failureReason,
    sessionId = sessionId,
    request = request
)
```

### 2.2 OAuth2AuthenticationFailureHandler의 6개 주요 실패 사유

**호출 위치**: `OAuth2AuthenticationFailureHandler.recordOAuth2AuthenticationFailure()`

| 실패 사유 | 매핑 조건 | FailureReasonType |
|----------|-----------|-------------------|
| 액세스 거부 | access_denied, 403 Forbidden | ACCESS_DENIED |
| 잘못된 클라이언트 | invalid_client, 400 Bad Request | INVALID_CLIENT |
| 잘못된 스코프 | invalid_scope, scope 관련 오류 | INVALID_SCOPE |
| 토큰 교환 실패 | 401 Unauthorized, token 관련 오류 | SSO_TOKEN_EXCHANGE_FAILED |
| 제공자 서버 오류 | 5xx 서버 오류 | SSO_PROVIDER_UNAVAILABLE |
| 네트워크 오류 | ConnectException, SocketTimeoutException | NETWORK_ERROR |

**복잡한 매핑 로직**:
```kotlin
private fun mapOAuth2ExceptionToFailureReason(
    exception: AuthenticationException,
    providerType: ProviderType
): FailureReasonType {
    return when (exception) {
        is OAuth2AuthenticationException -> {
            // 1. 제공자별 매퍼를 통한 정확한 매핑 시도
            oAuth2FailureMappers
                .filter { it.canHandle(providerType) }
                .firstNotNullOfOrNull { it.mapException(exception, providerType) }
                ?: mapByHttpStatusAndGeneral(exception)
        }
        // 2. 네트워크 관련 예외
        is java.net.ConnectException,
        is java.net.SocketTimeoutException,
        is java.net.UnknownHostException -> FailureReasonType.NETWORK_ERROR
        // ... 기타 매핑
    }
}
```

**특별한 기능**:
- **사용자 식별 복구**: OAuth2UserRecoveryService를 통해 실패 상황에서도 사용자 ID 추출 시도
- **Chain of Responsibility 패턴**: 제공자별 오류 매퍼를 통한 정확한 분류

### 2.3 GlobalAuthenticationExceptionHandler의 4개 시스템 오류

**호출 위치**: `GlobalAuthenticationExceptionHandler.handleSystemException()`

| 실패 사유 | Exception | FailureReasonType |
|----------|-----------|-------------------|
| 데이터베이스 오류 | DataAccessException, SQLException | SYSTEM_ERROR |
| 네트워크 오류 | ConnectException, SocketTimeoutException | NETWORK_ERROR |
| 외부 서비스 오류 | RestClientException | EXTERNAL_PROVIDER_ERROR |
| 알 수 없는 오류 | 기타 모든 예외 | UNKNOWN |

**호출되는 상황**:
- BasicAuthenticationProvider에서 DataAccessException, Exception 발생 시
- 시스템 레벨의 인증 관련 예외 발생 시

**저장되는 데이터**:
```kotlin
loginHistoryService.recordFailedLogin(
    shoplClientId = shoplClientId, // 기본값: "UNKNOWN"
    shoplUserId = shoplUserId,     // 기본값: "unknown"
    platform = IdpClient.Platform.DASHBOARD,
    loginType = determineLoginType(request), // URI 기반 결정
    failureReason = failureReason,
    sessionId = sessionId,
    request = request
)
```

**LoginType 결정 로직**:
```kotlin
private fun determineLoginType(request: HttpServletRequest?): LoginType {
    if (request == null) return LoginType.BASIC

    val requestUri = request.requestURI
    return when {
        requestUri.contains("/oauth2/") -> LoginType.SOCIAL
        requestUri.contains("/sso/") -> LoginType.SSO
        else -> LoginType.BASIC
    }
}
```

---

## 3. 전체 실패 사유 분류 (22가지)

### 3.1 인증 관련 실패 (6가지)
- `INVALID_CREDENTIALS`: 잘못된 자격증명
- `ACCOUNT_LOCKED`: 계정 잠금
- `ACCOUNT_EXPIRED`: 계정 만료
- `PASSWORD_EXPIRED`: 비밀번호 만료
- `ACCOUNT_DISABLED`: 계정 비활성화
- `TOO_MANY_ATTEMPTS`: 로그인 시도 횟수 초과

### 3.2 OAuth2/SSO 관련 실패 (10가지)
- `INVALID_CLIENT`: 잘못된 클라이언트
- `UNSUPPORTED_GRANT_TYPE`: 지원하지 않는 Grant Type
- `INVALID_SCOPE`: 잘못된 Scope
- `ACCESS_DENIED`: 액세스 거부
- `RATE_LIMIT_EXCEEDED`: 요청 한도 초과
- `SSO_ERROR`: SSO 연동 오류 (일반적인 오류)
- `SSO_PROVIDER_UNAVAILABLE`: SSO 제공자 서버 오류
- `SSO_TOKEN_EXCHANGE_FAILED`: 토큰 교환 실패
- `EXTERNAL_PROVIDER_ERROR`: 외부 제공자 오류
- `NETWORK_ERROR`: 네트워크 오류

### 3.3 시스템 관련 실패 (2가지)
- `SYSTEM_ERROR`: 시스템 오류
- `UNKNOWN`: 알 수 없는 오류

---

## 4. 특별한 로직과 조건

### 4.1 예외 처리 정책
모든 로그인 이력 기록은 "Best Effort" 방식으로 동작:
- 로그인 이력 기록 실패가 실제 인증 과정을 방해하지 않음
- 예외 발생 시 로그만 남기고 계속 진행

### 4.2 사용자 식별 전략
1. **기본 로그인**: 항상 정확한 사용자 ID 확보
2. **OAuth2 성공**: 프로비저닝된 계정에서 정확한 사용자 ID 확보
3. **OAuth2 실패**: OAuth2UserRecoveryService를 통한 4단계 복구 전략
4. **시스템 오류**: 기본값 "unknown" 사용

### 4.3 클라이언트 ID 추출 우선순위
1. 요청 파라미터 (`client_id`)
2. 세션 속성 (`shopl_client_id`)
3. Referer 헤더에서 추출
4. 기본값 "CLIENT001" (개발용)

### 4.4 플랫폼 결정
현재 모든 로그인이 `IdpClient.Platform.DASHBOARD`로 저장됨
- 향후 모바일 앱 등 다른 플랫폼 지원 시 확장 가능

---

## 5. 통계 및 분석 기능

### 5.1 제공되는 통계
- 최근 실패한 로그인 시도 횟수
- 클라이언트별 로그인 통계
- 로그인 타입별 통계
- IP 주소별 로그인 시도 횟수

### 5.2 조회 기능
- 사용자별 로그인 이력 조회
- 날짜 범위별 조회
- 마지막 성공 로그인 조회

---

## 6. 보안 고려사항

### 6.1 민감 정보 보호
- 비밀번호는 로그에 기록되지 않음
- 사용자 ID는 부분적으로만 로그에 표시 (`${shoplUserId.take(3)}***`)

### 6.2 로그인 보안 검증
- LoginSecurityValidator를 통한 시도 횟수 제한
- IP 기반 모니터링
- 실패 패턴 분석 가능

---

## 7. 확장성 및 개선 방향

### 7.1 현재 확장 포인트
- OAuth2FailureMapper: 제공자별 오류 매핑 확장 가능
- ProviderType: 새로운 소셜/SSO 제공자 추가 가능
- FailureReasonType: 새로운 실패 사유 추가 가능

### 7.2 향후 개선 가능 영역
- 모바일 앱 플랫폼 지원
- 더 정교한 실패 사유 분류
- 실시간 보안 모니터링 연동
- 사용자 행동 패턴 분석 기능

이 분석을 통해 현재 시스템이 로그인 이력을 어떻게 체계적으로 관리하고 있는지, 그리고 향후 어떤 방향으로 확장할 수 있는지 파악할 수 있습니다.