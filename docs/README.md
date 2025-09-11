# OAuth2 Authorization Server

Spring Authorization Server 기반의 OAuth2 인증 서버로, 일반 OAuth2 로그인과 SSO(Single Sign-On) 로그인을 모두 지원합니다.

## 📋 목차

- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [문서 가이드](#문서-가이드)

## 🎯 프로젝트 개요

이 프로젝트는 다음과 같은 인증 방식을 지원하는 OAuth2 Authorization Server입니다:

1. **기본 OAuth2 로그인**: 자체 사용자 DB를 이용한 인증
2. **SSO 로그인**: 외부 IdP(Keycloak, Google, 등)를 통한 Single Sign-On

## ✨ 주요 기능

### 🔐 인증 및 권한
- OAuth2.1 / OpenID Connect 표준 준수
- JWT 기반 액세스 토큰 및 ID 토큰
- 리프레시 토큰을 통한 토큰 갱신
- PKCE (Proof Key for Code Exchange) 지원

### 👥 사용자 관리
- 자체 사용자 계정 관리
- SSO를 통한 사용자 자동 프로비저닝
- 멀티 테넌트 지원 (클라이언트별 사용자 관리)

### 🏢 SSO 통합
- Keycloak, Google, Microsoft, GitHub 등 지원
- 동적 SSO 설정 관리
- 사용자 계정 연결 및 매핑

### 📊 세션 및 저장소
- Redis 기반 OAuth2Authorization 저장
- 확장 가능한 세션 관리
- 고성능 토큰 검색 및 관리

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │    │  Authorization  │    │   Resource      │
│                 │    │     Server      │    │    Server       │
│                 │    │                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │   React     │ │◄──►│ │  OAuth2     │ │    │ │   REST      │ │
│ │   Vue.js    │ │    │ │  Endpoints  │ │    │ │   API       │ │
│ │   Mobile    │ │    │ │             │ │    │ │             │ │
│ └─────────────┘ │    │ └─────────────┘ │    │ └─────────────┘ │
└─────────────────┘    │                 │    └─────────────────┘
                       │ ┌─────────────┐ │
                       │ │    SSO      │ │    ┌─────────────────┐
                       │ │ Integration │ │◄──►│   External      │
                       │ │             │ │    │     IdP         │
                       │ └─────────────┘ │    │ (Keycloak, etc) │
                       └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │     Redis       │
                       │  (Session &     │
                       │   Token Store)  │
                       └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │     MySQL       │
                       │  (User & SSO    │
                       │   Config Data)  │
                       └─────────────────┘
```

## 🛠️ 기술 스택

### Backend
- **Kotlin** - 주 개발 언어
- **Spring Boot 3.x** - 애플리케이션 프레임워크
- **Spring Authorization Server** - OAuth2/OpenID Connect 구현
- **Spring Security** - 보안 및 인증
- **Spring Data JPA** - 데이터 액세스

### Database & Cache
- **MySQL** - 메인 데이터베이스 (사용자, 클라이언트, SSO 설정)
- **Redis** - 세션 저장소 및 토큰 캐시

### External Integration
- **Keycloak** - 주요 SSO 제공자
- **OAuth2/OpenID Connect** - 표준 프로토콜

## 📁 프로젝트 구조

```
authorization-server/
├── src/main/kotlin/me/practice/oauth2/
│   ├── configuration/          # 설정 클래스들
│   │   ├── AuthSecurityConfiguration.kt    # Spring Security 설정
│   │   ├── OAuth2ClientConfiguration.kt    # OAuth2 클라이언트 설정
│   │   ├── CustomUserDetails*.kt          # 사용자 세부정보 구현
│   │   └── RedisAuthorizationDTO.kt        # Redis 직렬화 DTO
│   │
│   ├── controller/             # REST 컨트롤러
│   │   └── SsoController.kt               # SSO 관련 엔드포인트
│   │
│   ├── entity/                 # JPA 엔티티
│   │   ├── IoIdpAccount.kt                # 사용자 계정 엔티티
│   │   ├── IoIdpAccountOauthLink.kt       # OAuth 연결 엔티티
│   │   └── IoIdpShoplClientSsoSetting.kt  # SSO 설정 엔티티
│   │
│   ├── handler/                # 인증 핸들러
│   │   └── SsoAuthenticationSuccessHandler.kt  # SSO 성공 핸들러
│   │
│   ├── infrastructure/         # 인프라스트럭처
│   │   └── redis/             
│   │       ├── AuthorizationJsonCodec.kt       # JSON 직렬화
│   │       ├── RedisOAuth2AuthorizationService.kt  # Redis 서비스
│   │       └── RedisAuthorizationDTO.kt        # DTO 및 변환기
│   │
│   ├── service/                # 비즈니스 로직
│   │   ├── UserProvisioningService.kt      # 사용자 프로비저닝
│   │   ├── SsoConfigurationService.kt      # SSO 설정 관리
│   │   ├── DynamicClientRegistrationService.kt  # 동적 클라이언트 등록
│   │   └── CompositeClientRegistrationRepository.kt # 클라이언트 저장소
│   │
│   └── utils/                  # 유틸리티
│       └── JsonUtils.kt                   # JSON 처리 유틸
│
├── src/main/resources/
│   ├── application.yml         # 메인 설정 파일
│   ├── templates/             # Thymeleaf 템플릿
│   │   ├── login-custom.html  # 커스텀 로그인 페이지
│   │   └── sso/              # SSO 관련 페이지
│   └── static/               # 정적 리소스
│
└── docs/                      # 프로젝트 문서
    ├── README.md             # 프로젝트 개요 (현재 파일)
    ├── oauth2-basic-login.md # 기본 OAuth2 로그인 가이드
    ├── sso-integration.md    # SSO 통합 가이드
    ├── api-endpoints.md      # API 엔드포인트 문서
    └── configuration.md      # 설정 가이드
```

## 📚 문서 가이드

### 🔗 상세 문서

- **[기본 OAuth2 로그인](./oauth2-basic-login.md)** - 표준 OAuth2 인증 플로우
- **[SSO 통합 가이드](./sso-integration.md)** - 외부 IdP와의 SSO 연동
- **[API 엔드포인트](./api-endpoints.md)** - REST API 문서
- **[설정 가이드](./configuration.md)** - 환경 설정 및 배포

### 📖 읽는 순서

1. **프로젝트 처음 시작**: [설정 가이드](./configuration.md) → [기본 OAuth2 로그인](./oauth2-basic-login.md)
2. **SSO 추가 구현**: [SSO 통합 가이드](./sso-integration.md)
3. **API 연동 개발**: [API 엔드포인트](./api-endpoints.md)

## 🚀 빠른 시작

1. **설정 파일 구성**
   ```bash
   cp src/main/resources/application.yml.example src/main/resources/application.yml
   # application.yml에서 데이터베이스 및 Redis 설정 수정
   ```

2. **데이터베이스 준비**
   ```bash
   # MySQL 스키마 생성
   mysql -u root -p < schema.sql
   ```

3. **애플리케이션 실행**
   ```bash
   ./gradlew bootRun
   ```

4. **테스트**
   ```bash
   # 기본 OAuth2 플로우 테스트
   curl http://localhost:9000/oauth2/authorize?response_type=code&client_id=messaging-client&scope=openid&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/messaging-client-oidc
   ```

## 🤝 기여

프로젝트 개선을 위한 기여를 환영합니다! 

1. 이슈 등록 또는 기능 제안
2. 포크 후 기능 브랜치 생성
3. 커밋 및 풀 리퀘스트 생성

## 📝 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다.

---

> 💡 **팁**: 각 문서는 독립적으로 읽을 수 있도록 구성되어 있지만, 전체적인 이해를 위해서는 위의 순서대로 읽는 것을 권장합니다.