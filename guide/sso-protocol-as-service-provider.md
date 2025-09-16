
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
