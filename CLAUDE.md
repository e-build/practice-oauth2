# Practice OAuth2 프로젝트 가이드

## 프롬프트 가이드
- 절대 기존 코드를 무시하지 말것!
- 질문과 관련한 주제에 관하여 재사용가능한 것을 먼저 탐색하고 답변할 것

## 프로젝트 개요
- 이 프로젝트는 Kotlin, Spring boot 사용하는 REST API 서버입니다.
- 데이터베이스는 MySQL 8을 사용하며, ORM은 JPA를 사용합니다.
- OAuth 2.0 기반 인증/인가를 제공하는 서버 개발을 학습하기 위한 프로젝트입니다.
- OAuth 2.0이 Spring 에서 구현되고 사용할 수 있는 지가 중점
- 모든 코드에 대해 테스트 코드를 작성할 필요는 없습니다. 개발자가 지시할 떄만 테스트 코드를 작성하면 됩니다.
- 현재 서비스를 'shopl' 이라고하는 B2B SaaS 로 가정하고 고객사를 정의하는 용어를 client 로 통칭하고 있음. Spring Security OAuth 에서도 client 라는 개념이 있는데, OAuth의 client과, 서비스의 client를 명확하게 구분할 것

## 주요 구조
- 크게 두개의 모듈이 존재합니다.
- authorization-server 
  - 인증/인가 제공 서버
  - 테이블 설계 - resources/db/init.sql 
- resource-server 
  - 리소스 페이지, 리소스 API 를 제공하는 서버
  - 테이블 설계 - resources/db/init.sql

## 아키텍처 전략

### 인증/인가 분리 전략
- **프론트엔드에서 권한 검증**: 모든 페이지 접근 권한은 프론트엔드(JavaScript)에서 검증
- **백엔드는 순수 REST API 제공**: Spring 컨트롤러는 데이터만 제공, 권한 검증은 JWT 기반으로만 수행
- **Thymeleaf Model 사용 금지**: Spring Model 객체 사용을 최대한 지양하고 REST API 방식 사용

### 보안 설정
- resource-server Security 설정: `.requestMatchers("/admin/**").permitAll()` - 모든 admin 페이지는 프론트엔드에서 권한 검증
- JWT 토큰 기반 API 인증: Authorization Bearer 헤더 방식으로 API 보안 처리

### 데이터 흐름
1. 사용자가 페이지 접근 → HTML 템플릿 반환 (권한 검증 없음)
2. 프론트엔드 JavaScript가 localStorage에서 JWT 토큰 확인
3. 토큰이 있으면 REST API 호출하여 데이터 로드
4. 토큰이 없거나 만료되면 로그인 페이지로 리다이렉트

### 검증 전략
- **Bean Validation 사용 금지**: Bean Validation 어노테이션(@Valid, @NotNull 등) 사용하지 않음
- **서비스 레이어 직접 검증**: 모든 유효성 검증을 서비스 레이어에서 조건문(if/else)으로 직접 구현
- **검증 로직 명시성**: 검증 로직이 명확하게 보이도록 코드로 표현

## 주요기능
- authorization-server
  - 기본 로그인
  - SSO
  - 소셜 로그인 - GOOGLE, KAKAO, ...
- resource-server
  - **페이지 제공 (HTML 템플릿만)**
    - /admin/home - 관리자 홈 페이지 (템플릿만)
    - /admin/sso/configurations - SSO 설정 목록 페이지 (템플릿만)
    - /admin/sso/configurations/form - SSO 설정 폼 페이지 (템플릿만)
  - **REST API 제공**
    - GET /api/admin/user-info - 현재 사용자 정보 반환
    - GET /api/admin/check-permission - 권한 확인
    - GET /api/admin/sso/configurations - SSO 설정 목록 데이터

## SSO 프로토콜 지원 정책

### 지원해야 할 SSO 프로토콜과 그 이유

#### SAML 2.0
- **용도**: 기업용 SSO의 표준, 엔터프라이즈 환경
- **특징**: XML 기반, 복잡하지만 강력한 보안, 세밀한 권한 제어
- **주요 제공자**: Microsoft AD FS, Okta, OneLogin, Ping Identity
- **동작**: SP-initiated 또는 IdP-initiated 인증, SAML Assertion으로 사용자 정보 전달

#### OpenID Connect (OIDC)
- **용도**: 현대적인 웹/모바일 애플리케이션용 ID 레이어
- **특징**: OAuth 2.0 위에 구축, JSON 기반, 사용자 신원 정보 제공
- **주요 제공자**: Google Workspace, Microsoft Azure AD, Auth0
- **동작**: OAuth 2.0 인증 + ID 토큰으로 사용자 정보 획득
- **Discovery**: `/.well-known/openid-configuration` 엔드포인트로 자동 설정 가능

#### OAuth 2.0 (순수)
- **용도**: 권한 위임이 주목적이지만 인증용으로도 광범위 사용
- **특징**: 단순한 HTTP 기반, 높은 호환성, 커스터마이징 가능
- **주요 제공자**: GitHub, GitLab, 일부 레거시 시스템
- **동작**: 인증 코드 → 액세스 토큰 → 사용자 정보 API 호출
- **한계**: 표준화된 사용자 정보 엔드포인트 없음 (제공자별 상이)

### OIDC vs OAuth 2.0 차이점
| 구분 | OIDC | OAuth 2.0 |
|------|------|-----------|
| 목적 | 인증 (누구인가?) | 권한 위임 (무엇을 할 수 있나?) |
| 표준화 | 높음 (OpenID Foundation) | 중간 (RFC만 존재) |
| 사용자 정보 | ID 토큰으로 직접 제공 | UserInfo API 호출 필요 |
| 구현 복잡도 | 낮음 (표준 라이브러리) | 높음 (제공자별 커스텀) |
| Discovery | `/.well-known/openid-configuration` | 제공자별 상이 |
| 토큰 검증 | JWT ID 토큰 검증 | 액세스 토큰 + API 호출 |

### 왜 OAuth 2.0도 지원해야 하는가?
1. **기존 시스템 호환성**: 많은 기업이 OIDC 이전의 OAuth 2.0 시스템 사용 중
2. **제공자 다양성**: GitHub, GitLab 등 개발 도구들이 순수 OAuth 2.0 제공
3. **커스터마이징 필요성**: 표준화된 OIDC보다 유연한 설정이 필요한 경우
4. **점진적 마이그레이션**: OAuth 2.0에서 OIDC로의 점진적 전환 지원
5. **레거시 호환**: 기존 OAuth 2.0 구현체들과의 호환성 유지

## 핵심 명령어

## 설치된 주요 도구