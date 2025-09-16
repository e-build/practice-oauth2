# 로그인 실패 사유 저장 코드 완성 계획

## 현재 상황 분석

### 기존 구현 현황

#### 1. FailureReasonType Enum (16가지 정의됨, 11가지 신규 구현 대상)
```kotlin
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
    SSO_ERROR,                 // SSO 연동 오류
    EXTERNAL_PROVIDER_ERROR,   // 외부 제공자 오류
    SUSPICIOUS_LOCATION,       // 의심스러운 위치에서의 접근
    BRUTE_FORCE_DETECTED,      // 무차별 대입 공격 탐지
    NETWORK_ERROR,             // 네트워크 오류
    SYSTEM_ERROR,              // 시스템 오류
    UNKNOWN                    // 알 수 없는 오류
}
```

#### 2. 현재 구현된 실패 사유 저장 위치
1. **BasicAuthenticationProvider** - `INVALID_CREDENTIALS` (기본 로그인 실패)
2. **OAuth2AuthenticationFailureHandler** - SSO 관련 실패 사유들:
   - `INVALID_CLIENT`
   - `INVALID_SCOPE`
   - `NETWORK_ERROR`
   - `SSO_ERROR`

#### 3. 구현되지 않은 실패 사유들 (11개 누락)
- `ACCOUNT_LOCKED` - 계정 잠금
- `ACCOUNT_EXPIRED` - 계정 만료
- `PASSWORD_EXPIRED` - 비밀번호 만료
- `ACCOUNT_DISABLED` - 계정 비활성화
- `TOO_MANY_ATTEMPTS` - 로그인 시도 횟수 초과
- `UNSUPPORTED_GRANT_TYPE` - 지원하지 않는 Grant Type
- `SSO_PROVIDER_UNAVAILABLE` - SSO 제공자 서버 오류
- `SSO_TOKEN_EXCHANGE_FAILED` - 토큰 교환 실패
- `EXTERNAL_PROVIDER_ERROR` - 외부 제공자 오류
- `SYSTEM_ERROR` - 시스템 오류
- `UNKNOWN` - 알 수 없는 오류

#### 4. 제외된 실패 사유들 (구현하지 않음)
- ~~`SUSPICIOUS_LOCATION`~~ - 의심스러운 위치에서의 접근 (제외: 구현 복잡도 높음)
- ~~`BRUTE_FORCE_DETECTED`~~ - 무차별 대입 공격 탐지 (제외: 구현 복잡도 높음)
- ~~`SSO_ACCESS_DENIED`~~ - 사용자가 SSO 제공자에서 권한 거부 (제외: 사용자 의도적 행동)

## 구현 계획

### 1단계: 계정 상태 관련 실패 사유 구현
**위치**: `BasicAuthenticationProvider`, `CustomUserDetails`, `AccountValidator`

#### 구현 대상
1. **ACCOUNT_LOCKED**: 계정 잠금 상태 확인
2. **ACCOUNT_EXPIRED**: 계정 만료 확인
3. **ACCOUNT_DISABLED**: 계정 비활성화 상태 확인
4. **PASSWORD_EXPIRED**: 비밀번호 만료 확인

#### 구현 방법
- `CustomUserDetails`의 `isAccountNonLocked()`, `isAccountNonExpired()`, `isCredentialsNonExpired()`, `isEnabled()` 메서드에 실제 로직 구현
- `AccountValidator`에서 각 상태별로 구체적인 예외 생성
- `BasicAuthenticationProvider`에서 예외별 적절한 `FailureReasonType` 매핑

### 2단계: 보안 관련 실패 사유 구현
**위치**: `LoginHistoryService`와 새로운 보안 검증 서비스

#### 구현 대상
1. **TOO_MANY_ATTEMPTS**: 기존 통계 기능 활용하여 시도 횟수 초과 감지

#### 구현 방법
- `LoginSecurityValidator` 서비스 신규 생성
- 로그인 전 보안 검증 단계 추가
- 기존 `LoginHistoryStatisticsService.getRecentFailedLoginAttempts()` 활용

### 3단계: OAuth2/시스템 관련 실패 사유 구현
**위치**: OAuth2 관련 핸들러들과 전역 예외 처리

#### 구현 대상
1. **UNSUPPORTED_GRANT_TYPE**: OAuth2 Grant Type 검증
2. **SSO_PROVIDER_UNAVAILABLE**: SSO 제공자 서버 오류 (HTTP 5xx 등)
3. **SSO_TOKEN_EXCHANGE_FAILED**: OAuth2 토큰 교환 실패
4. **EXTERNAL_PROVIDER_ERROR**: 외부 소셜 로그인 제공자 오류
5. **SYSTEM_ERROR**: 시스템 레벨 오류
6. **UNKNOWN**: 모든 예외의 기본 fallback

#### 구현 방법
- `OAuth2AuthenticationFailureHandler` 확장 - SSO 오류 세분화
- 전역 예외 처리기 추가
- 외부 제공자별 오류 매핑

### 4단계: 통합 및 테스트
1. 모든 인증 경로에서 적절한 실패 사유 매핑 확인
2. 실패 사유별 테스트 케이스 작성
3. 로그 및 모니터링 강화

## 구현 체크리스트

### ✅ 1단계: 계정 상태 관련 실패 사유 구현

#### 1.1 CustomUserDetails 계정 상태 로직 구현
- [x] `isAccountNonExpired()` - 계정 삭제 날짜 기반 만료 확인
- [x] `isAccountNonLocked()` - 계정 상태가 'BLOCKED'인지 확인
- [x] `isCredentialsNonExpired()` - 비밀번호 업데이트 날짜 기반 만료 확인 (90일)
- [x] `isEnabled()` - 기존 로직 유지 (ACTIVE 상태 + 삭제되지 않음)

#### 1.2 계정 상태별 커스텀 예외 클래스 생성
- [x] `AccountExpiredException` 생성
- [x] `PasswordExpiredException` 생성

#### 1.3 AccountValidator 확장
- [x] `AccountExpiredException` 처리 추가
- [x] `CredentialsExpiredException` 처리 추가
- [x] 각 예외별 명확한 메시지 추가

#### 1.4 BasicAuthenticationProvider 수정
- [x] `AccountExpiredException` → `ACCOUNT_EXPIRED` 매핑
- [x] `LockedException` → `ACCOUNT_LOCKED` 매핑
- [x] `DisabledException` → `ACCOUNT_DISABLED` 매핑
- [x] `CredentialsExpiredException` → `PASSWORD_EXPIRED` 매핑
- [x] 예외별 로그인 실패 기록 로직 수정

### ✅ 2단계: 보안 관련 실패 사유 구현 (1개)

#### 2.1 LoginSecurityValidator 서비스 생성
- [x] `LoginSecurityValidator` 클래스 생성
- [x] `validateLoginAttempts()` 메서드 - 시도 횟수 검증

#### 2.2 LoginSecurityValidator 통합
- [x] `BasicAuthenticationProvider`에 보안 검증 단계 추가
- [x] `TOO_MANY_ATTEMPTS` 실패 사유 적용

#### 2.3 보안 검증 예외 클래스 생성
- [x] `TooManyAttemptsException` 생성

### ✅ 3단계: OAuth2/시스템 관련 실패 사유 구현 (6개)

#### 3.1 FailureReasonType Enum 확장
- [x] `SSO_PROVIDER_UNAVAILABLE` 추가
- [x] `SSO_TOKEN_EXCHANGE_FAILED` 추가

#### 3.2 OAuth2AuthenticationFailureHandler 확장
- [x] `UNSUPPORTED_GRANT_TYPE` 감지 로직 추가
- [x] `SSO_PROVIDER_UNAVAILABLE` 매핑 (HTTP 5xx 오류)
- [x] `SSO_TOKEN_EXCHANGE_FAILED` 매핑 (토큰 교환 실패)
- [x] `EXTERNAL_PROVIDER_ERROR` 세분화
- [x] 제공자별 오류 매핑 세분화

#### 3.3 전역 예외 처리기 생성
- [x] `GlobalAuthenticationExceptionHandler` 생성
- [x] `SYSTEM_ERROR` 처리 로직
- [x] `UNKNOWN` 기본 fallback 처리
- [x] 예외별 로그인 실패 이력 기록

#### 3.4 시스템 오류 감지
- [x] 데이터베이스 연결 오류 → `SYSTEM_ERROR`
- [x] 외부 서비스 연결 오류 → `EXTERNAL_PROVIDER_ERROR`
- [x] 알 수 없는 오류 → `UNKNOWN`

### ✅ 4단계: 통합 및 테스트

#### 4.1 인증 흐름 통합 테스트
- [x] 기본 로그인 실패 시나리오별 테스트
- [x] SSO 로그인 실패 시나리오별 테스트
- [x] 소셜 로그인 실패 시나리오별 테스트

#### 4.2 실패 사유별 단위 테스트
- [x] 계정 상태 관련 실패 사유 테스트 (4개)
- [x] 보안 관련 실패 사유 테스트 (1개)
- [x] OAuth2/시스템 관련 실패 사유 테스트 (6개)

#### 4.3 로그인 이력 확인
- [x] 각 실패 사유가 올바르게 데이터베이스에 저장되는지 확인
- [x] 로그인 통계가 올바르게 집계되는지 확인

#### 4.4 문서화
- [x] 각 실패 사유의 발생 조건 문서화
- [x] API 응답에서 실패 사유 확인 방법 문서화
- [x] 트러블슈팅 가이드 작성

## 구현 순서

1. **1단계 완료** → 계정 상태 관련 4개 실패 사유 완성
2. **2단계 완료** → 보안 관련 1개 실패 사유 완성
3. **3단계 완료** → OAuth2/시스템 관련 6개 실패 사유 완성
4. **4단계 완료** → 전체 테스트 및 문서화

**목표**: 실용적인 11개 `FailureReasonType`을 실제 코드에서 사용할 수 있도록 구현 완성

**제외된 사유**: 3개 (SUSPICIOUS_LOCATION, BRUTE_FORCE_DETECTED, SSO_ACCESS_DENIED)
**기존 구현된 사유**: 5개 (INVALID_CREDENTIALS, INVALID_CLIENT, INVALID_SCOPE, NETWORK_ERROR, SSO_ERROR)
**신규 구현 대상**: 11개