# API 엔드포인트 문서

이 문서는 OAuth2 Authorization Server가 제공하는 모든 API 엔드포인트에 대해 설명합니다.

## 📋 목차

- [개요](#개요)
- [표준 OAuth2 엔드포인트](#표준-oauth2-엔드포인트)
- [OpenID Connect 엔드포인트](#openid-connect-엔드포인트)
- [SSO 관리 엔드포인트](#sso-관리-엔드포인트)
- [관리자 API](#관리자-api)
- [에러 응답](#에러-응답)
- [예제 코드](#예제-코드)

## 🎯 개요

이 서버는 다음과 같은 표준을 준수합니다:

- **OAuth 2.1** - 최신 OAuth2 보안 모범 사례
- **OpenID Connect 1.0** - 인증 계층
- **RFC 7636** - PKCE (Proof Key for Code Exchange)
- **RFC 7662** - Token Introspection
- **RFC 8414** - Authorization Server Metadata

## 🔐 표준 OAuth2 엔드포인트

### 1. Authorization Endpoint

사용자 인증 및 인가 코드 발급

```http
GET /oauth2/authorize
```

**파라미터:**

| 파라미터 | 필수 | 설명 | 예제 |
|----------|------|------|------|
| `response_type` | ✅ | 응답 타입 | `code` |
| `client_id` | ✅ | 클라이언트 ID | `messaging-client` |
| `redirect_uri` | ✅ | 리다이렉트 URI | `http://localhost:8080/callback` |
| `scope` | ✅ | 요청 스코프 | `openid profile email` |
| `state` | ⚠️ | CSRF 보호용 랜덤 문자열 | `random-state-123` |
| `code_challenge` | ⚠️ | PKCE 코드 챌린지 | `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` |
| `code_challenge_method` | ⚠️ | PKCE 메서드 | `S256` |

**요청 예제:**
```http
GET /oauth2/authorize?response_type=code&client_id=messaging-client&scope=openid%20profile%20email&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback&code_challenge=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk&code_challenge_method=S256&state=abc123
```

**응답:**
```http
HTTP/1.1 302 Found
Location: http://localhost:8080/callback?code=SplxlOBeZQQYbYS6WxSbIA&state=abc123
```

### 2. Token Endpoint

인가 코드를 액세스 토큰으로 교환

```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>
```

#### Authorization Code Grant

**파라미터:**

| 파라미터 | 필수 | 설명 | 예제 |
|----------|------|------|------|
| `grant_type` | ✅ | 그랜트 타입 | `authorization_code` |
| `code` | ✅ | 인가 코드 | `SplxlOBeZQQYbYS6WxSbIA` |
| `redirect_uri` | ✅ | 리다이렉트 URI | `http://localhost:8080/callback` |
| `code_verifier` | ⚠️ | PKCE 코드 검증자 | `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk` |

**요청 예제:**
```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic bWVzc2FnaW5nLWNsaWVudDpzZWNyZXQ=

grant_type=authorization_code&code=SplxlOBeZQQYbYS6WxSbIA&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

**응답:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiYXVkIjpbIm1lc3NhZ2luZy1jbGllbnQiXSwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwIiwiZXhwIjoxNjQwOTk1MjAwLCJpYXQiOjE2NDA5OTE2MDB9...",
  "refresh_token": "FYS1OgUBLWiWYf2BrAVhITn5zJ8KwFo",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyMTIzIiwiYXVkIjoibWVzc2FnaW5nLWNsaWVudCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY0MDk5NTIwMCwiaWF0IjoxNjQwOTkxNjAwLCJuYW1lIjoiSm9obiBEb2UiLCJlbWFpbCI6ImpvaG4uZG9lQGV4YW1wbGUuY29tIn0...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "openid profile email"
}
```

#### Refresh Token Grant

**파라미터:**

| 파라미터 | 필수 | 설명 | 예제 |
|----------|------|------|------|
| `grant_type` | ✅ | 그랜트 타입 | `refresh_token` |
| `refresh_token` | ✅ | 리프레시 토큰 | `FYS1OgUBLWiWYf2BrAVhITn5zJ8KwFo` |

**요청 예제:**
```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic bWVzc2FnaW5nLWNsaWVudDpzZWNyZXQ=

grant_type=refresh_token&refresh_token=FYS1OgUBLWiWYf2BrAVhITn5zJ8KwFo
```

### 3. Token Introspection Endpoint

토큰의 유효성과 메타데이터 조회

```http
POST /oauth2/introspect
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>
```

**파라미터:**

| 파라미터 | 필수 | 설명 | 예제 |
|----------|------|------|------|
| `token` | ✅ | 검증할 토큰 | `eyJhbGciOiJSUzI1NiI...` |
| `token_type_hint` | ❌ | 토큰 타입 힌트 | `access_token`, `refresh_token` |

**요청 예제:**
```http
POST /oauth2/introspect
Content-Type: application/x-www-form-urlencoded
Authorization: Basic bWVzc2FnaW5nLWNsaWVudDpzZWNyZXQ=

token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...&token_type_hint=access_token
```

**응답 (활성 토큰):**
```json
{
  "active": true,
  "sub": "user123",
  "aud": ["messaging-client"],
  "iss": "http://localhost:9000",
  "exp": 1640995200,
  "iat": 1640991600,
  "scope": "openid profile email",
  "client_id": "messaging-client",
  "username": "john.doe@example.com",
  "token_type": "Bearer"
}
```

**응답 (비활성 토큰):**
```json
{
  "active": false
}
```

### 4. Token Revocation Endpoint

토큰 무효화

```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <base64(client_id:client_secret)>
```

**파라미터:**

| 파라미터 | 필수 | 설명 | 예제 |
|----------|------|------|------|
| `token` | ✅ | 무효화할 토큰 | `FYS1OgUBLWiWYf2BrAVhITn5zJ8KwFo` |
| `token_type_hint` | ❌ | 토큰 타입 힌트 | `refresh_token` |

**요청 예제:**
```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded
Authorization: Basic bWVzc2FnaW5nLWNsaWVudDpzZWNyZXQ=

token=FYS1OgUBLWiWYf2BrAVhITn5zJ8KwFo&token_type_hint=refresh_token
```

**응답:**
```http
HTTP/1.1 200 OK
```

## 🆔 OpenID Connect 엔드포인트

### 1. UserInfo Endpoint

사용자 정보 조회

```http
GET /userinfo
Authorization: Bearer <access_token>
```

**요청 예제:**
```http
GET /userinfo
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**응답:**
```json
{
  "sub": "user123",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "email": "john.doe@example.com",
  "email_verified": true,
  "picture": "https://example.com/avatar/john.jpg",
  "locale": "ko-KR",
  "updated_at": 1640991600
}
```

### 2. JWK Set Endpoint

JWT 서명 검증용 공개 키 조회

```http
GET /oauth2/jwks
```

**응답:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "rsa-key-1",
      "use": "sig",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e": "AQAB",
      "x5c": [
        "MIICnTCCAYUCBgGJxdHW3jANBgkqhkiG9w0BAQsFADA..."
      ],
      "x5t": "rsa-key-1",
      "x5t#S256": "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs"
    }
  ]
}
```

### 3. Discovery Endpoint

서버 메타데이터 조회

```http
GET /.well-known/openid-configuration
```

**응답:**
```json
{
  "issuer": "http://localhost:9000",
  "authorization_endpoint": "http://localhost:9000/oauth2/authorize",
  "token_endpoint": "http://localhost:9000/oauth2/token",
  "token_endpoint_auth_methods_supported": [
    "client_secret_basic",
    "client_secret_post"
  ],
  "jwks_uri": "http://localhost:9000/oauth2/jwks",
  "userinfo_endpoint": "http://localhost:9000/userinfo",
  "response_types_supported": [
    "code"
  ],
  "grant_types_supported": [
    "authorization_code",
    "refresh_token"
  ],
  "revocation_endpoint": "http://localhost:9000/oauth2/revoke",
  "revocation_endpoint_auth_methods_supported": [
    "client_secret_basic",
    "client_secret_post"
  ],
  "introspection_endpoint": "http://localhost:9000/oauth2/introspect",
  "introspection_endpoint_auth_methods_supported": [
    "client_secret_basic",
    "client_secret_post"
  ],
  "subject_types_supported": [
    "public"
  ],
  "id_token_signing_alg_values_supported": [
    "RS256"
  ],
  "scopes_supported": [
    "openid",
    "profile",
    "email"
  ]
}
```

## 🔗 SSO 관리 엔드포인트

### 1. SSO 제공자 목록 조회

특정 클라이언트에서 사용 가능한 SSO 제공자 목록

```http
GET /sso/providers/{shoplClientId}
```

**파라미터:**

| 파라미터 | 설명 | 예제 |
|----------|------|------|
| `shoplClientId` | 클라이언트 ID | `CLIENT001` |

**요청 예제:**
```http
GET /sso/providers/CLIENT001
```

**응답:**
```json
[
  {
    "providerId": "keycloak",
    "providerName": "Keycloak SSO",
    "authorizationUrl": "/oauth2/authorization/keycloak-client001",
    "iconUrl": "/images/sso/keycloak.png"
  },
  {
    "providerId": "google",
    "providerName": "Google",
    "authorizationUrl": "/oauth2/authorization/google-client001",
    "iconUrl": "/images/sso/google.png"
  }
]
```

### 2. SSO 로그인 시작

특정 SSO 제공자로 로그인 시작

```http
GET /oauth2/authorization/{registrationId}
```

**파라미터:**

| 파라미터 | 설명 | 예제 |
|----------|------|------|
| `registrationId` | SSO 등록 ID | `keycloak-client001` |

**요청 예제:**
```http
GET /oauth2/authorization/keycloak-client001?client_id=CLIENT001&redirect_uri=http://localhost:8080/callback&state=abc123
```

**응답:**
```http
HTTP/1.1 302 Found
Location: http://keycloak:8081/realms/shopl-sandbox/protocol/openid-connect/auth?response_type=code&client_id=as-broker&scope=openid%20profile%20email&state=...&redirect_uri=...
```

### 3. SSO 콜백 처리

SSO 제공자에서 돌아오는 콜백 처리

```http
GET /login/oauth2/code/{registrationId}
```

**파라미터:**

| 파라미터 | 설명 | 예제 |
|----------|------|------|
| `registrationId` | SSO 등록 ID | `keycloak-client001` |
| `code` | 인가 코드 | `ey123...` |
| `state` | 상태 값 | `abc123` |

**요청 예제:**
```http
GET /login/oauth2/code/keycloak-client001?code=ey123...&state=abc123
```

**내부 처리:**
1. 외부 IdP와 토큰 교환
2. 사용자 정보 조회
3. 계정 프로비저닝 (생성/연결)
4. 내부 OAuth2 인가 코드 생성
5. 클라이언트로 리다이렉트

## 🛠️ 관리자 API

### 1. 클라이언트 등록

새로운 OAuth2 클라이언트 등록

```http
POST /admin/clients
Content-Type: application/json
Authorization: Bearer <admin_token>
```

**요청 본문:**
```json
{
  "clientId": "new-client",
  "clientName": "New Client Application",
  "clientSecret": "new-client-secret",
  "redirectUris": [
    "http://localhost:8080/callback",
    "http://localhost:8080/silent-callback"
  ],
  "scopes": ["openid", "profile", "email"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "tokenSettings": {
    "accessTokenTimeToLive": "PT30M",
    "refreshTokenTimeToLive": "PT8H"
  }
}
```

**응답:**
```json
{
  "id": "client-uuid-123",
  "clientId": "new-client",
  "clientName": "New Client Application",
  "createdAt": "2025-09-11T12:00:00Z",
  "status": "ACTIVE"
}
```

### 2. SSO 설정 관리

클라이언트의 SSO 설정 추가/수정

```http
POST /admin/clients/{shoplClientId}/sso-settings
Content-Type: application/json
Authorization: Bearer <admin_token>
```

**요청 본문:**
```json
{
  "providerType": "OIDC",
  "providerName": "Corporate Keycloak",
  "clientId": "corp-client",
  "clientSecret": "corp-secret",
  "authorizationUri": "https://keycloak.corp.com/realms/employees/protocol/openid-connect/auth",
  "tokenUri": "https://keycloak.corp.com/realms/employees/protocol/openid-connect/token",
  "userInfoUri": "https://keycloak.corp.com/realms/employees/protocol/openid-connect/userinfo",
  "jwkSetUri": "https://keycloak.corp.com/realms/employees/protocol/openid-connect/certs",
  "issuerUri": "https://keycloak.corp.com/realms/employees",
  "autoProvision": true,
  "isEnabled": true
}
```

**응답:**
```json
{
  "id": "sso-setting-uuid-456",
  "shoplClientId": "CLIENT001",
  "providerType": "OIDC",
  "providerName": "Corporate Keycloak",
  "isEnabled": true,
  "createdAt": "2025-09-11T12:00:00Z"
}
```

### 3. 사용자 관리

사용자 계정 조회 및 관리

```http
GET /admin/users
Authorization: Bearer <admin_token>
```

**쿼리 파라미터:**

| 파라미터 | 설명 | 예제 |
|----------|------|------|
| `shoplClientId` | 클라이언트 ID 필터 | `CLIENT001` |
| `email` | 이메일 검색 | `john@example.com` |
| `page` | 페이지 번호 | `0` |
| `size` | 페이지 크기 | `20` |

**응답:**
```json
{
  "content": [
    {
      "id": "user123",
      "shoplClientId": "CLIENT001",
      "email": "john.doe@example.com",
      "name": "John Doe",
      "status": "ACTIVE",
      "createdAt": "2025-09-11T12:00:00Z",
      "oauthLinks": [
        {
          "providerType": "OIDC",
          "providerUserId": "keycloak-user-456",
          "emailAtProvider": "john.doe@company.com"
        }
      ]
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1
}
```

## ❌ 에러 응답

### OAuth2 표준 에러

```json
{
  "error": "invalid_request",
  "error_description": "The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed.",
  "error_uri": "https://tools.ietf.org/html/rfc6749#section-4.1.2.1"
}
```

### 일반적인 에러 코드

| 에러 코드 | 설명 | HTTP 상태 |
|-----------|------|-----------|
| `invalid_request` | 잘못된 요청 형식 | 400 |
| `invalid_client` | 클라이언트 인증 실패 | 401 |
| `invalid_grant` | 잘못된 인가 코드/토큰 | 400 |
| `unsupported_grant_type` | 지원하지 않는 그랜트 타입 | 400 |
| `invalid_scope` | 잘못된 스코프 | 400 |
| `access_denied` | 사용자가 인가 거부 | 400 |
| `server_error` | 서버 내부 오류 | 500 |

### SSO 관련 에러

```json
{
  "error": "sso_not_enabled",
  "error_description": "SSO is not enabled for this client",
  "client_id": "CLIENT001"
}
```

```json
{
  "error": "auto_provision_disabled",
  "error_description": "Automatic user provisioning is disabled",
  "provider_type": "OIDC"
}
```

## 💻 예제 코드

### JavaScript/Node.js

#### OAuth2 Authorization Code Flow
```javascript
const crypto = require('crypto');
const axios = require('axios');

class OAuth2Client {
    constructor(config) {
        this.config = config;
    }
    
    // 1. Authorization URL 생성
    getAuthorizationUrl(state, codeChallenge) {
        const params = new URLSearchParams({
            response_type: 'code',
            client_id: this.config.clientId,
            redirect_uri: this.config.redirectUri,
            scope: this.config.scope,
            state: state,
            code_challenge: codeChallenge,
            code_challenge_method: 'S256'
        });
        
        return `${this.config.authorizationEndpoint}?${params.toString()}`;
    }
    
    // 2. 토큰 교환
    async exchangeCodeForTokens(code, codeVerifier) {
        const params = new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: this.config.redirectUri,
            code_verifier: codeVerifier
        });
        
        const response = await axios.post(this.config.tokenEndpoint, params, {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Authorization': `Basic ${Buffer.from(`${this.config.clientId}:${this.config.clientSecret}`).toString('base64')}`
            }
        });
        
        return response.data;
    }
    
    // 3. 토큰 갱신
    async refreshTokens(refreshToken) {
        const params = new URLSearchParams({
            grant_type: 'refresh_token',
            refresh_token: refreshToken
        });
        
        const response = await axios.post(this.config.tokenEndpoint, params, {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Authorization': `Basic ${Buffer.from(`${this.config.clientId}:${this.config.clientSecret}`).toString('base64')}`
            }
        });
        
        return response.data;
    }
    
    // 4. 사용자 정보 조회
    async getUserInfo(accessToken) {
        const response = await axios.get(this.config.userInfoEndpoint, {
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });
        
        return response.data;
    }
}

// PKCE 헬퍼 함수들
function generateCodeVerifier() {
    return crypto.randomBytes(32).toString('base64url');
}

function generateCodeChallenge(codeVerifier) {
    return crypto.createHash('sha256').update(codeVerifier).digest('base64url');
}

// 사용 예제
const client = new OAuth2Client({
    clientId: 'messaging-client',
    clientSecret: 'secret',
    authorizationEndpoint: 'http://localhost:9000/oauth2/authorize',
    tokenEndpoint: 'http://localhost:9000/oauth2/token',
    userInfoEndpoint: 'http://localhost:9000/userinfo',
    redirectUri: 'http://localhost:8080/callback',
    scope: 'openid profile email'
});

// 인증 플로우 시작
const state = crypto.randomBytes(16).toString('hex');
const codeVerifier = generateCodeVerifier();
const codeChallenge = generateCodeChallenge(codeVerifier);

const authUrl = client.getAuthorizationUrl(state, codeChallenge);
console.log('Visit:', authUrl);
```

#### SSO 제공자 목록 조회
```javascript
async function loadSsoProviders(clientId) {
    try {
        const response = await axios.get(`http://localhost:9000/sso/providers/${clientId}`);
        return response.data;
    } catch (error) {
        console.error('Failed to load SSO providers:', error.response?.data || error.message);
        throw error;
    }
}

// 사용 예제
loadSsoProviders('CLIENT001').then(providers => {
    providers.forEach(provider => {
        console.log(`${provider.providerName}: ${provider.authorizationUrl}`);
    });
});
```

### Java/Spring

#### OAuth2 클라이언트 설정
```java
@Configuration
public class OAuth2ClientConfig {
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("messaging-client")
                .clientId("messaging-client")
                .clientSecret("secret")
                .authorizationUri("http://localhost:9000/oauth2/authorize")
                .tokenUri("http://localhost:9000/oauth2/token")
                .userInfoUri("http://localhost:9000/userinfo")
                .jwkSetUri("http://localhost:9000/oauth2/jwks")
                .scope("openid", "profile", "email")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build()
        );
    }
    
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .build();
        
        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        
        return authorizedClientManager;
    }
}
```

#### API 호출 예제
```java
@RestController
public class ApiController {
    
    @Autowired
    private OAuth2AuthorizedClientManager authorizedClientManager;
    
    @GetMapping("/api/userinfo")
    public ResponseEntity<String> getUserInfo(Authentication authentication) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("messaging-client")
                .principal(authentication)
                .build();
        
        OAuth2AuthorizedClient authorizedClient = 
                authorizedClientManager.authorize(authorizeRequest);
        
        if (authorizedClient != null) {
            String accessToken = authorizedClient.getAccessToken().getTokenValue();
            
            // UserInfo 엔드포인트 호출
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:9000/userinfo",
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            
            return response;
        }
        
        return ResponseEntity.status(401).body("Not authenticated");
    }
}
```

### Python

#### OAuth2 클라이언트
```python
import requests
import secrets
import hashlib
import base64
from urllib.parse import urlencode, parse_qs, urlparse

class OAuth2Client:
    def __init__(self, config):
        self.config = config
    
    def get_authorization_url(self, state, code_challenge):
        params = {
            'response_type': 'code',
            'client_id': self.config['client_id'],
            'redirect_uri': self.config['redirect_uri'],
            'scope': self.config['scope'],
            'state': state,
            'code_challenge': code_challenge,
            'code_challenge_method': 'S256'
        }
        
        return f"{self.config['authorization_endpoint']}?{urlencode(params)}"
    
    def exchange_code_for_tokens(self, code, code_verifier):
        data = {
            'grant_type': 'authorization_code',
            'code': code,
            'redirect_uri': self.config['redirect_uri'],
            'code_verifier': code_verifier
        }
        
        auth = (self.config['client_id'], self.config['client_secret'])
        
        response = requests.post(
            self.config['token_endpoint'],
            data=data,
            auth=auth,
            headers={'Content-Type': 'application/x-www-form-urlencoded'}
        )
        
        return response.json()
    
    def get_user_info(self, access_token):
        headers = {'Authorization': f'Bearer {access_token}'}
        
        response = requests.get(
            self.config['userinfo_endpoint'],
            headers=headers
        )
        
        return response.json()

def generate_code_verifier():
    return base64.urlsafe_b64encode(secrets.token_bytes(32)).decode('utf-8').rstrip('=')

def generate_code_challenge(code_verifier):
    digest = hashlib.sha256(code_verifier.encode('utf-8')).digest()
    return base64.urlsafe_b64encode(digest).decode('utf-8').rstrip('=')

# 사용 예제
config = {
    'client_id': 'messaging-client',
    'client_secret': 'secret',
    'authorization_endpoint': 'http://localhost:9000/oauth2/authorize',
    'token_endpoint': 'http://localhost:9000/oauth2/token',
    'userinfo_endpoint': 'http://localhost:9000/userinfo',
    'redirect_uri': 'http://localhost:8080/callback',
    'scope': 'openid profile email'
}

client = OAuth2Client(config)

# PKCE 파라미터 생성
state = secrets.token_urlsafe(16)
code_verifier = generate_code_verifier()
code_challenge = generate_code_challenge(code_verifier)

# 인증 URL 생성
auth_url = client.get_authorization_url(state, code_challenge)
print(f"Visit: {auth_url}")
```

---

다음: [설정 가이드](./configuration.md)