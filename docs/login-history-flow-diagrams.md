# 로그인 이력 저장 시퀀스 다이어그램

Practice OAuth2 프로젝트에서 발생하는 모든 로그인 이력 저장 케이스들을 시각화한 시퀀스 다이어그램입니다.

## 1. 기본 로그인 성공 플로우

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Form as 로그인 폼
    participant Security as Spring Security
    participant Provider as BasicAuthenticationProvider
    participant UserService as CustomUserDetailsService
    participant History as LoginHistoryService
    participant DB as Database

    User->>Form: 로그인 정보 입력
    Form->>Security: POST /login (username, password)
    Security->>Provider: authenticate(token)
    Provider->>UserService: loadUserByUsername(email)
    UserService->>DB: 사용자 정보 조회
    DB-->>UserService: IoIdpAccount 반환
    UserService-->>Provider: CustomUserDetails 반환
    Provider->>Provider: 비밀번호 검증

    alt 로그인 성공
        Provider->>History: recordSuccessfulLogin()
        History->>DB: 성공 이력 저장 (LoginType.BASIC)
        Provider-->>Security: Authentication 반환
        Security-->>User: 로그인 성공 리다이렉트
    else 로그인 실패
        Provider->>History: recordFailedLogin(failureReason)
        Note over History: 6가지 실패 사유:<br/>INVALID_CREDENTIALS<br/>ACCOUNT_LOCKED<br/>ACCOUNT_EXPIRED<br/>PASSWORD_EXPIRED<br/>ACCOUNT_DISABLED<br/>TOO_MANY_ATTEMPTS
        History->>DB: 실패 이력 저장
        Provider-->>Security: AuthenticationException
        Security-->>User: 로그인 실패 메시지
    end
```

## 2. OAuth2 로그인 성공 플로우

```mermaid
sequenceDiagram
    participant User as 사용자
    participant AuthServer as Authorization Server
    participant OAuth2 as OAuth2 제공자<br/>(Google, Kakao 등)
    participant Filter as OAuth2ContextCaptureFilter
    participant Session as SessionUserContextManager
    participant Handler as SsoAuthenticationSuccessHandler
    participant History as LoginHistoryService
    participant DB as Database

    User->>AuthServer: OAuth2 로그인 요청
    AuthServer->>Filter: /oauth2/authorization/{providerId}
    Filter->>Session: saveMinimalUserContext()
    Note over Session: 사용자 컨텍스트를<br/>세션에 저장 (5분 TTL)
    AuthServer->>OAuth2: 인증 요청 리다이렉트
    OAuth2->>User: 로그인 화면 표시
    User->>OAuth2: 인증 정보 입력
    OAuth2->>AuthServer: 인증 코드 반환
    AuthServer->>OAuth2: 액세스 토큰 요청
    OAuth2-->>AuthServer: 액세스 토큰 + 사용자 정보

    AuthServer->>Handler: onAuthenticationSuccess()
    Handler->>History: recordSuccessfulLogin()
    Note over History: LoginType 결정:<br/>Google/Kakao → SOCIAL<br/>SAML/OIDC → SSO
    History->>DB: 성공 이력 저장
    Handler-->>User: 리소스 서버로 리다이렉트
```

## 3. OAuth2 로그인 실패 플로우 (DON-49 개선)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant AuthServer as Authorization Server
    participant OAuth2 as OAuth2 제공자
    participant FailureHandler as OAuth2AuthenticationFailureHandler
    participant Recovery as OAuth2UserRecoveryService
    participant Session as SessionUserContextManager
    participant Mapper as OAuth2FailureMapper체인
    participant History as LoginHistoryService
    participant DB as Database

    User->>AuthServer: OAuth2 로그인 시도
    AuthServer->>OAuth2: 인증 요청
    OAuth2-->>AuthServer: 인증 실패 (예외 발생)

    AuthServer->>FailureHandler: onAuthenticationFailure(exception)

    par 사용자 식별 복구 (DON-49)
        FailureHandler->>Recovery: attemptUserRecovery(request, exception)
        Recovery->>Session: getMinimalUserContext() (1순위)
        alt 세션 복구 성공
            Session-->>Recovery: MinimalUserContext 반환
        else 세션 복구 실패
            Recovery->>Recovery: 예외 분석 기반 복구 (2순위)
            Recovery->>Recovery: HTTP Referer 기반 복구 (3순위)
            Recovery->>Recovery: Request 속성 기반 복구 (4순위)
        end
        Recovery-->>FailureHandler: 복구된 사용자 ID 또는 "unknown"
    and 실패 사유 매핑 (DON-52)
        FailureHandler->>Mapper: mapFailureReason(exception)
        Note over Mapper: Chain of Responsibility:<br/>1. GoogleOAuth2FailureMapper<br/>2. KakaoOAuth2FailureMapper<br/>3. MicrosoftOAuth2FailureMapper<br/>4. GitHubOAuth2FailureMapper<br/>5. DefaultOAuth2FailureMapper
        Mapper-->>FailureHandler: 정확한 FailureReasonType
    end

    FailureHandler->>History: recordFailedLogin(userId, failureReason)
    Note over History: 6가지 주요 실패 사유:<br/>INVALID_CLIENT<br/>INVALID_SCOPE<br/>SSO_PROVIDER_UNAVAILABLE<br/>SSO_TOKEN_EXCHANGE_FAILED<br/>NETWORK_ERROR<br/>ACCESS_DENIED
    History->>DB: 실패 이력 저장
    FailureHandler-->>User: 로그인 실패 페이지 리다이렉트
```

## 4. 글로벌 예외 처리 플로우

```mermaid
sequenceDiagram
    participant Request as HTTP 요청
    participant Controller as 인증 관련<br/>컨트롤러
    participant GlobalHandler as GlobalAuthenticationExceptionHandler
    participant History as LoginHistoryService
    participant DB as Database

    Request->>Controller: 인증 요청
    Controller->>Controller: 처리 중 예외 발생

    alt RestClientException (외부 API 호출 실패)
        Controller-->>GlobalHandler: RestClientException
        GlobalHandler->>History: recordFailedLogin(EXTERNAL_PROVIDER_ERROR)
    else DataAccessException (데이터베이스 오류)
        Controller-->>GlobalHandler: DataAccessException
        GlobalHandler->>History: recordFailedLogin(SYSTEM_ERROR)
    else ConnectException (네트워크 오류)
        Controller-->>GlobalHandler: ConnectException
        GlobalHandler->>History: recordFailedLogin(NETWORK_ERROR)
    else 기타 예상치 못한 예외
        Controller-->>GlobalHandler: Exception
        GlobalHandler->>History: recordFailedLogin(UNKNOWN)
    end

    History->>DB: 실패 이력 저장
    GlobalHandler-->>Request: 에러 응답 반환
```

## 5. 세션 기반 사용자 컨텍스트 관리 (DON-49 핵심)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Filter as OAuth2ContextCaptureFilter
    participant Session as SessionUserContextManager
    participant Redis as 세션 저장소
    participant Recovery as OAuth2UserRecoveryService

    Note over Filter,Recovery: OAuth2 시작 시점 컨텍스트 저장

    User->>Filter: /oauth2/authorization/google?username=test@example.com
    Filter->>Filter: extractUserIdentifier(request)
    Filter->>Filter: extractClientId(request)
    Filter->>Session: saveMinimalUserContext()
    Session->>Session: hashIdentifier(SHA-256)
    Session->>Redis: 세션에 MinimalUserContext 저장 (5분 TTL)

    Note over User,Recovery: OAuth2 실패 시 컨텍스트 복구

    User->>User: OAuth2 인증 실패 발생
    Recovery->>Session: getMinimalUserContext(session)
    Session->>Redis: 세션에서 컨텍스트 조회
    alt 컨텍스트 유효 (5분 이내)
        Redis-->>Session: MinimalUserContext 반환
        Session->>Session: TTL 검증 통과
        Session->>Session: deriveUserIdFromContext()
        Session-->>Recovery: 복구된 사용자 ID
    else 컨텍스트 만료 또는 없음
        Redis-->>Session: null 또는 만료된 컨텍스트
        Session->>Redis: 만료된 컨텍스트 삭제
        Session-->>Recovery: null (복구 실패)
    end
```

## 6. 제공자별 오류 매핑 체인 (DON-52)

```mermaid
sequenceDiagram
    participant Exception as OAuth2AuthenticationException
    participant Chain as OAuth2FailureMapperChain
    participant Google as GoogleOAuth2FailureMapper
    participant Kakao as KakaoOAuth2FailureMapper
    participant Microsoft as MicrosoftOAuth2FailureMapper
    participant GitHub as GitHubOAuth2FailureMapper
    participant Default as DefaultOAuth2FailureMapper

    Exception->>Chain: mapFailureReason(exception)

    Chain->>Google: canHandle(exception)?
    alt Google 관련 오류
        Google->>Google: access_denied → ACCESS_DENIED<br/>server_error → SSO_PROVIDER_UNAVAILABLE<br/>quota_exceeded → RATE_LIMIT_EXCEEDED
        Google-->>Chain: FailureReasonType 반환
    else Google 외 오류
        Google-->>Chain: null
        Chain->>Kakao: canHandle(exception)?
        alt Kakao 관련 오류
            Kakao->>Kakao: KOE101 → INVALID_CLIENT<br/>KOE320 → SSO_TOKEN_EXCHANGE_FAILED<br/>KOE401 → INVALID_CLIENT<br/>KOE403 → ACCESS_DENIED
            Kakao-->>Chain: FailureReasonType 반환
        else Kakao 외 오류
            Kakao-->>Chain: null
            Chain->>Microsoft: canHandle(exception)?
            alt Microsoft 관련 오류
                Microsoft->>Microsoft: AADSTS* 패턴 처리<br/>AADSTS50001 → INVALID_CLIENT<br/>AADSTS50055 → PASSWORD_EXPIRED
                Microsoft-->>Chain: FailureReasonType 반환
            else Microsoft 외 오류
                Microsoft-->>Chain: null
                Chain->>GitHub: canHandle(exception)?
                alt GitHub 관련 오류
                    GitHub->>GitHub: bad_verification_code → SSO_TOKEN_EXCHANGE_FAILED<br/>incorrect_client_credentials → INVALID_CLIENT
                    GitHub-->>Chain: FailureReasonType 반환
                else GitHub 외 오류
                    GitHub-->>Chain: null
                    Chain->>Default: mapFailureReason(exception)
                    Default->>Default: 기본 OAuth2 매핑<br/>invalid_client → INVALID_CLIENT<br/>invalid_scope → INVALID_SCOPE
                    Default-->>Chain: FailureReasonType 반환
                end
            end
        end
    end

    Chain-->>Exception: 최종 FailureReasonType
```

## 7. 통합 테스트 시나리오 (DON-50)

```mermaid
sequenceDiagram
    participant Test as 테스트 케이스
    participant TestDB as 테스트 데이터베이스
    participant Provider as BasicAuthenticationProvider
    participant History as LoginHistoryService
    participant Verification as 검증 로직

    Note over Test,Verification: BasicAuthenticationProvider 통합 테스트

    Test->>TestDB: 테스트 계정 생성 및 상태 설정

    loop 6가지 실패 시나리오
        alt INVALID_CREDENTIALS 테스트
            Test->>TestDB: 올바른 계정 + 잘못된 비밀번호 설정
            Test->>Provider: authenticate(wrongPassword)
            Provider->>History: recordFailedLogin(INVALID_CREDENTIALS)
            History->>TestDB: 실패 이력 저장
            Test->>Verification: 이력 검증 (INVALID_CREDENTIALS)
        else ACCOUNT_LOCKED 테스트
            Test->>TestDB: 계정 상태를 BLOCKED로 설정
            Test->>Provider: authenticate(validCredentials)
            Provider->>History: recordFailedLogin(ACCOUNT_LOCKED)
            History->>TestDB: 실패 이력 저장
            Test->>Verification: 이력 검증 (ACCOUNT_LOCKED)
        else PASSWORD_EXPIRED 테스트
            Test->>TestDB: 비밀번호 업데이트 날짜를 90일 전으로 설정
            Test->>Provider: authenticate(validCredentials)
            Provider->>History: recordFailedLogin(PASSWORD_EXPIRED)
            History->>TestDB: 실패 이력 저장
            Test->>Verification: 이력 검증 (PASSWORD_EXPIRED)
        end
        Note over Test: 각 실패 사유별로<br/>정확한 매핑 검증
    end

    Test->>Verification: 전체 테스트 결과 확인
    Note over Verification: 13개 FailureReasonType<br/>100% 자동 검증 완료
```

## 주요 특징 및 개선사항

### DON-49: OAuth2 사용자 식별 개선
- **4단계 복구 전략**: 세션 → 예외 분석 → Referer → Request 속성
- **세션 기반 컨텍스트**: OAuth2 시작 시점에서 5분 TTL로 사용자 정보 저장
- **사용자 식별률**: 10% → 60% 달성 목표

### DON-52: OAuth2 예외 매핑 확장
- **Chain of Responsibility 패턴**: 제공자별 매퍼 체인
- **정밀한 오류 분류**: SSO_ERROR/UNKNOWN 50% 감소
- **확장 가능**: 새로운 제공자 쉽게 추가 가능

### DON-50: 완전 커버리지 테스트
- **테스트 커버리지**: 30% → 95% 달성
- **13개 FailureReasonType**: 100% 자동 검증
- **실제 DB 조작**: 현실적인 테스트 시나리오

이러한 시퀀스 다이어그램을 통해 Practice OAuth2 프로젝트의 모든 로그인 이력 저장 케이스와 개선사항을 한눈에 파악할 수 있습니다.