# 로그인 실패 사유 저장 커버리지 분석 보고서

## 분석 목적
16개의 `FailureReasonType`이 실제 코드에서 올바르게 저장되는지 완전한 경로 매핑과 누락 케이스 분석

## 실패 사유별 저장 경로 매핑 현황

### ✅ 완전히 구현된 실패 사유 (11개)

#### 1. 계정 상태 관련 (4개) - BasicAuthenticationProvider
- **INVALID_CREDENTIALS**: `BasicAuthenticationProvider.kt:81` → `BadCredentialsException` catch
- **ACCOUNT_LOCKED**: `BasicAuthenticationProvider.kt:69` → `LockedException` catch
- **ACCOUNT_EXPIRED**: `BasicAuthenticationProvider.kt:65` → `AccountExpiredException` catch
- **PASSWORD_EXPIRED**: `BasicAuthenticationProvider.kt:77` → `PasswordExpiredException` catch
- **ACCOUNT_DISABLED**: `BasicAuthenticationProvider.kt:73` → `DisabledException` catch

#### 2. 보안 관련 (1개) - BasicAuthenticationProvider
- **TOO_MANY_ATTEMPTS**: `BasicAuthenticationProvider.kt:61` → `TooManyAttemptsException` catch

#### 3. OAuth2/SSO 관련 (6개) - OAuth2AuthenticationFailureHandler
- **INVALID_CLIENT**: `OAuth2AuthenticationFailureHandler.kt:90` → `"invalid_client"` error code
- **INVALID_SCOPE**: `OAuth2AuthenticationFailureHandler.kt:91` → `"invalid_scope"` error code
- **UNSUPPORTED_GRANT_TYPE**: `OAuth2AuthenticationFailureHandler.kt:92` → `"unsupported_grant_type"` error code
- **SSO_PROVIDER_UNAVAILABLE**: `OAuth2AuthenticationFailureHandler.kt:93,94,95,120` → server error codes
- **SSO_TOKEN_EXCHANGE_FAILED**: `OAuth2AuthenticationFailureHandler.kt:96` → `"invalid_grant"` error code
- **NETWORK_ERROR**: `OAuth2AuthenticationFailureHandler.kt:97,115` → network exceptions

#### 4. 시스템 오류 관련 (4개) - GlobalAuthenticationExceptionHandler
- **EXTERNAL_PROVIDER_ERROR**: `GlobalAuthenticationExceptionHandler.kt:71` → `RestClientException`
- **SYSTEM_ERROR**: `GlobalAuthenticationExceptionHandler.kt:63,75,78` → DB/리소스/보안 예외
- **UNKNOWN**: `GlobalAuthenticationExceptionHandler.kt:81` → 기타 모든 예외
- **SSO_ERROR**: `OAuth2AuthenticationFailureHandler.kt:107,126` → 기타 OAuth2 예외

### ❌ 구현되지 않은 실패 사유 (2개)

#### 1. 새로 추가된 타입들 (FailureReasonType에는 있지만 저장 로직 없음)
- **SSO_PROVIDER_UNAVAILABLE**: 이미 구현됨 ✅
- **SSO_TOKEN_EXCHANGE_FAILED**: 이미 구현됨 ✅

### ⚠️ 제외된 실패 사유 (3개) - 의도적으로 구현하지 않음
- **SUSPICIOUS_LOCATION**: 구현 복잡도가 높아 제외
- **BRUTE_FORCE_DETECTED**: 구현 복잡도가 높아 제외
- **SSO_ACCESS_DENIED**: 사용자 의도적 행동으로 제외

## 저장 로직 실행 흐름 분석

### 1. 기본 로그인 실패 흐름
```
로그인 시도 → BasicAuthenticationProvider.authenticate()
         ├─ 보안 검증 실패 → TooManyAttemptsException → TOO_MANY_ATTEMPTS 저장
         ├─ 계정 상태 실패 → Account*Exception → 해당 실패 사유 저장
         ├─ 비밀번호 실패 → BadCredentialsException → INVALID_CREDENTIALS 저장
         ├─ DB 오류 → DataAccessException → GlobalHandler → SYSTEM_ERROR 저장
         └─ 기타 오류 → Exception → GlobalHandler → UNKNOWN 저장
```

### 2. OAuth2/SSO 로그인 실패 흐름
```
OAuth2 로그인 시도 → OAuth2AuthenticationFailureHandler.onAuthenticationFailure()
                ├─ OAuth2AuthenticationException → 구체적 error code 매핑
                ├─ 네트워크 예외 → NETWORK_ERROR 저장
                ├─ HTTP 서버 오류 → SSO_PROVIDER_UNAVAILABLE 저장
                └─ 기타 OAuth2 오류 → SSO_ERROR 저장
```

### 3. 시스템 오류 처리 흐름
```
시스템 예외 발생 → GlobalAuthenticationExceptionHandler.handleSystemException()
               ├─ 데이터베이스 예외 → SYSTEM_ERROR
               ├─ 네트워크 예외 → NETWORK_ERROR
               ├─ 외부 서비스 예외 → EXTERNAL_PROVIDER_ERROR
               └─ 기타 예외 → UNKNOWN
```

## 잠재적 누락 케이스 및 위험성 분석

### 🚨 높은 위험도 - 즉시 조치 필요

1. **GlobalHandler 호출 누락 위험**
   - `BasicAuthenticationProvider`의 시스템 예외가 GlobalHandler로 위임되지만, 로그인 이력 저장은 Provider에서만 실행
   - **위험**: 시스템 오류 발생 시 이중 저장 또는 저장 누락 가능성

2. **OAuth2Handler의 사용자 식별 문제**
   - OAuth2 실패 시 `shoplUserId = "unknown"`으로 고정
   - **위험**: 실제 사용자 분석이 불가능한 이력 데이터 생성

### ⚠️ 중간 위험도 - 모니터링 필요

3. **예외 처리 체인의 누락**
   - OAuth2Handler에서 처리되지 않은 새로운 예외 타입
   - **위험**: 새로운 오류 패턴 발생 시 `SSO_ERROR`로만 분류되어 구체적 원인 파악 어려움

4. **요청 컨텍스트 정보 추출 실패**
   - `extractShoplClientId()`, `extractRegistrationId()` 메서드의 기본값 의존
   - **위험**: 잘못된 클라이언트나 제공자로 이력이 저장될 수 있음

### ✅ 낮은 위험도 - 현재 적절히 관리됨

5. **이력 저장 실패 예외 처리**
   - 모든 저장 로직에서 try-catch로 예외 처리되어 인증 프로세스 중단 방지
   - **현상태**: 로그만 남기고 인증 프로세스는 정상 진행

## 테스트 커버리지 검증 방안

### 1. 단위 테스트 완성도 확인
```kotlin
// 각 FailureReasonType별 저장 검증
@Test fun `INVALID_CREDENTIALS 저장 확인`()
@Test fun `ACCOUNT_LOCKED 저장 확인`()
@Test fun `TOO_MANY_ATTEMPTS 저장 확인`()
@Test fun `SSO_PROVIDER_UNAVAILABLE 저장 확인`()
// ... 총 15개 테스트 케이스
```

### 2. 통합 테스트 시나리오
```kotlin
// 실제 인증 흐름을 통한 이력 저장 검증
@Test fun `잘못된 비밀번호로 로그인 시 INVALID_CREDENTIALS 저장`()
@Test fun `OAuth2 네트워크 오류 시 NETWORK_ERROR 저장`()
@Test fun `DB 연결 오류 시 SYSTEM_ERROR 저장`()
```

### 3. 실시간 모니터링 방안
```sql
-- 실패 사유별 발생 빈도 모니터링
SELECT failure_reason, COUNT(*) as count,
       DATE(created_at) as date
FROM io_idp_user_login_history
WHERE login_result = 'FAILURE'
  AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY failure_reason, DATE(created_at)
ORDER BY date DESC, count DESC;

-- 'UNKNOWN' 실패 사유 상세 분석
SELECT * FROM io_idp_user_login_history
WHERE failure_reason = 'UNKNOWN'
  AND created_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY created_at DESC;
```

## 권장 액션 플랜

### 즉시 실행 (High Priority)
1. **이중 저장 방지 로직 검증**
   - `BasicAuthenticationProvider`와 `GlobalHandler` 간의 중복 호출 가능성 점검
   - 통합 테스트로 시스템 오류 시나리오 검증

2. **OAuth2 사용자 식별 개선**
   - OAuth2 실패 시에도 가능한 사용자 정보 추출 로직 추가
   - 최소한 이메일이나 제공자별 ID 추출 시도

### 단기 실행 (Medium Priority)
3. **예외 매핑 확장**
   - 새로운 OAuth2 오류 패턴 발견 시 구체적 매핑 추가
   - 주요 OAuth2 제공자별 오류 코드 사전 매핑

4. **모니터링 대시보드 구축**
   - 실패 사유별 실시간 모니터링 쿼리 구현
   - UNKNOWN 사유 발생 시 알림 시스템 구축

### 장기 실행 (Low Priority)
5. **컨텍스트 정보 추출 안정성 향상**
   - 더 정확한 클라이언트 ID 및 제공자 정보 추출 로직
   - 세션 기반 정보 보완

## 결론

✅ **전체 16개 FailureReasonType 중 13개가 실제 저장 로직과 연결됨**
⚠️ **3개는 의도적으로 제외됨 (구현 복잡도 또는 불필요)**
🚨 **주요 위험요소는 시스템 오류 처리 흐름과 OAuth2 사용자 식별 부분**

현재 구현은 대부분의 실패 케이스를 잘 커버하고 있으나, 몇 가지 개선점이 있습니다. 특히 이중 저장 방지와 OAuth2 사용자 식별 개선이 우선순위가 높습니다.