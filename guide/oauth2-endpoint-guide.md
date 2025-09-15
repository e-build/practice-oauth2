# Spring OAuth2 엔드포인트 가이드

## 주요 엔드포인트 개요

| 엔드포인트 | 용도 | 사용 시점 |
|-----------|------|----------|
| `/oauth2/authorize` | Authorization Code 요청 | 사용자 인증 시작 |
| `/oauth2/token` | Access Token 발급/갱신 | 토큰 교환 시 |
| `/oauth2/authorization/{client_id}` | OAuth2 클라이언트별 인증 시작 | 특정 클라이언트 로그인 |
| `/login/oauth2/code/{client_id}` | Authorization Code 콜백 처리 | 외부 OAuth2 Provider 콜백 |

## 시나리오별 시퀀스 다이어그램

### 1. Authorization Code Flow (서버가 Authorization Server 역할)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant Client as 클라이언트 앱
    participant AuthServer as Authorization Server
    participant ResourceServer as Resource Server

    User->>Client: 1. 로그인 요청
    Client->>AuthServer: 2. GET /oauth2/authorize?response_type=code&client_id=...
    AuthServer->>User: 3. 로그인 페이지 표시
    User->>AuthServer: 4. 로그인 정보 입력
    AuthServer->>Client: 5. 302 redirect with authorization code
    Client->>AuthServer: 6. POST /oauth2/token (code 교환)
    AuthServer->>Client: 7. Access Token + Refresh Token 반환
    Client->>ResourceServer: 8. API 호출 (Bearer Token)
    ResourceServer->>Client: 9. 리소스 반환
```

### 2. Client Credentials Flow

```mermaid
sequenceDiagram
    participant Client as 클라이언트 앱
    participant AuthServer as Authorization Server
    participant ResourceServer as Resource Server

    Client->>AuthServer: 1. POST /oauth2/token<br/>grant_type=client_credentials
    AuthServer->>Client: 2. Access Token 반환
    Client->>ResourceServer: 3. API 호출 (Bearer Token)
    ResourceServer->>Client: 4. 리소스 반환
```

### 3. Federated Identity (서버가 OAuth2 Client 역할)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant App as 우리 앱
    participant AuthServer as 우리 Auth Server
    participant Provider as 외부 Provider<br/>(Google, Kakao 등)

    User->>App: 1. "Google로 로그인" 클릭
    App->>AuthServer: 2. GET /oauth2/authorization/google
    AuthServer->>Provider: 3. 302 redirect to Google OAuth
    User->>Provider: 4. Google 로그인
    Provider->>AuthServer: 5. GET /login/oauth2/code/google?code=...
    AuthServer->>Provider: 6. POST /token (code 교환)
    Provider->>AuthServer: 7. Access Token 반환
    AuthServer->>Provider: 8. GET /userinfo (사용자 정보)
    Provider->>AuthServer: 9. 사용자 정보 반환
    AuthServer->>App: 10. 로그인 완료 (세션/JWT)
```

## 엔드포인트 상세 설명

### `/oauth2/authorize`
- **목적**: Authorization Code Grant의 첫 번째 단계
- **HTTP Method**: GET
- **주요 파라미터**:
    - `response_type=code`
    - `client_id`: 클라이언트 식별자
    - `redirect_uri`: 콜백 URL
    - `scope`: 요청 권한 범위
    - `state`: CSRF 방지용 랜덤 값
- **응답**: 사용자 로그인 페이지 또는 authorization code와 함께 redirect

### `/oauth2/token`
- **목적**: 토큰 발급 및 갱신
- **HTTP Method**: POST
- **Grant Types**:
    - `authorization_code`: Authorization Code를 Access Token으로 교환
    - `refresh_token`: Refresh Token으로 새 Access Token 발급
    - `client_credentials`: 클라이언트 자격 증명으로 토큰 발급
- **응답**: Access Token, Refresh Token, 만료 시간 등

### `/oauth2/authorization/{client_id}`
- **목적**: 특정 OAuth2 Provider로의 로그인 시작점
- **HTTP Method**: GET
- **사용 사례**:
    - "Google로 로그인", "Kakao로 로그인" 등의 소셜 로그인
    - Federated Identity 구현 시 사용
- **동작**: 해당 Provider의 OAuth2 authorization URL로 redirect

### `/login/oauth2/code/{client_id}`
- **목적**: 외부 OAuth2 Provider로부터의 콜백 처리
- **HTTP Method**: GET
- **파라미터**:
    - `code`: Authorization Code
    - `state`: CSRF 방지용 값
- **동작**:
    1. Authorization Code를 Access Token으로 교환
    2. 사용자 정보 조회
    3. 내부 사용자 계정과 연동
    4. 세션 생성 또는 JWT 발급

## 구성 예시

### application.yml 설정
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/kakao"
        provider:
          kakao:
            authorization-uri: https://kauth.kakao.com/oauth/authorize
            token-uri: https://kauth.kakao.com/oauth/token
            user-info-uri: https://kapi.kakao.com/v2/user/me
```

## 보안 고려사항

1. **CSRF 공격 방지**: `state` 파라미터 사용
2. **Authorization Code 탈취 방지**: PKCE (Proof Key for Code Exchange) 적용
3. **Redirect URI 검증**: 등록된 URI만 허용
4. **HTTPS 강제**: 모든 OAuth2 통신은 HTTPS 사용
5. **토큰 만료 시간**: 적절한 Access Token 및 Refresh Token 만료 시간 설정