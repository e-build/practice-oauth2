# SSO 웹 인터페이스 개발 체크리스트

## 프로젝트 개요
고객사 사용자가 리소스 서버에서 직접 SSO 설정을 할 수 있는 웹 인터페이스를 개발하고, 인증 서버와 연동하여 동적으로 저장된 SSO 설정으로 로그인 기능을 구현

## 아키텍처 전제사항
- **인증 서버**: 이미 SSO 설정 엔티티가 존재함
- **리소스 서버**: 웹 인터페이스를 통해 SSO 설정을 관리
- **데이터베이스**: 로컬 개발시 같은 MySQL 인스턴스를 스키마로 구분
  - `shopl_authorization` 스키마: 인증 서버용
  - `shopl` 스키마: 리소스 서버용 (필요시)
- **서버간 통신**: REST API를 통한 SSO 설정 동기화

## 현재 resource-server 구조 분석
- ✅ **프레임워크**: Spring Boot + Kotlin
- ✅ **프론트엔드**: Thymeleaf 템플릿 엔진 사용 중
- ✅ **의존성**: Web, JPA, MySQL, OAuth2 Resource Server 설정 완료
- ✅ **기본 구조**: JWT 기반 인증 및 대시보드 페이지 이미 존재
- ✅ **데이터베이스**: MySQL 연결 설정 완료 (`mydb` 스키마)

## Phase 1: SSO 관리 웹 인터페이스 개발 (resource-server)

### 1. 관리자 페이지 라우팅 및 보안 설정
- [x] 관리자 전용 SSO 관리 페이지 접근 권한 설정
- [x] 기존 `/dashboard` 외 추가 관리 페이지 라우팅
- [x] 관리자 역할(role) 기반 접근 제어 구현

### 2. SSO 관리 컨트롤러 개발
- [x] 기존 `HomeController`를 참고해서 `SsoAdminController` 생성
- [x] Thymeleaf 템플릿 방식으로 SSO 관리 페이지 컨트롤러 구현
- [x] 관리자 권한 검증 로직 추가

### 3. SSO 설정 관리 페이지 템플릿 생성
- [x] 기존 `dashboard.html` 스타일을 참고한 일관된 디자인
- [x] SSO 관리 메인 페이지 (`/admin/sso`) - `templates/admin/sso-main.html`
- [x] SSO 설정 목록 페이지 (`/admin/sso/configurations`) - `templates/admin/sso-list.html`
- [x] SSO 설정 등록/수정 페이지 (`/admin/sso/configurations/form`) - `templates/admin/sso-form.html`

### 4. SSO 제공자별 설정 폼 개발 (JavaScript + Thymeleaf)
- [ ] 기존 dashboard.html의 JavaScript 패턴을 따라 동적 폼 구현
- [ ] SSO 제공자 타입 선택 시 동적 필드 표시
- [ ] SAML 설정 폼
  - [ ] 메타데이터 URL 입력 필드
  - [ ] 엔티티 ID 입력 필드
  - [ ] 사용자 속성 매핑 설정 (이메일, 이름, 부서 등)
  - [ ] 서명 인증서 업로드 기능
- [ ] OIDC 설정 폼
  - [ ] 클라이언트 ID/Secret 입력 필드
  - [ ] Discovery URL 설정
  - [ ] 스코프 설정 (다중 선택)
  - [ ] 클레임 매핑 설정

### 5. 프론트엔드 유효성 검증 및 UX
- [ ] 기존 dashboard.html의 JavaScript 패턴을 활용한 클라이언트 사이드 검증
- [ ] 필수 필드 실시간 검증
- [ ] URL 형식 검증 (메타데이터 URL, Discovery URL)
- [ ] 중복 설정 방지 (동일 회사/도메인)
- [ ] SSO 연결 테스트 기능
- [ ] 기존 Shopl 브랜딩과 일관된 디자인

## Phase 2: 리소스 서버 백엔드 API

### 6. 리소스 서버 API 컨트롤러 개발
- [ ] 기존 `HomeController` 패턴을 따라 `SsoConfigurationController` 생성
- [ ] SSO 설정 등록 API (`POST /api/admin/sso/configurations`)
- [ ] SSO 설정 목록 조회 API (`GET /api/admin/sso/configurations`)
- [ ] SSO 설정 상세 조회 API (`GET /api/admin/sso/configurations/{id}`)
- [ ] SSO 설정 수정 API (`PUT /api/admin/sso/configurations/{id}`)
- [ ] SSO 설정 삭제 API (`DELETE /api/admin/sso/configurations/{id}`)
- [ ] SSO 연결 테스트 API (`POST /api/admin/sso/configurations/test`)

### 7. 인증 서버와의 연동 설정
- [ ] 기존 JWT 설정을 참고해서 인증 서버 연동 설정
- [ ] `application.yml`에 인증 서버 API 엔드포인트 설정 추가
- [ ] 인증 서버 기존 SSO 엔티티 및 API 구조 파악
- [ ] RestTemplate 또는 WebClient 설정 (기존 OAuth2 설정과 연계)

### 8. SSO 설정 서비스 레이어 개발
- [ ] `SsoConfigurationService` 생성 (기존 서비스 패턴 따름)
- [ ] SSO 설정을 인증 서버로 동기화하는 `SsoSyncService`
- [ ] 인증 서버 API 호출 실패 시 재시도 로직
- [ ] 인증 서버 응답 검증 및 에러 핸들링

### 9. DTO 및 검증 로직
- [ ] 기존 entity 패키지 구조를 참고한 DTO 패키지 생성
- [ ] SSO 설정 DTO 클래스들 (`SsoConfigurationDto`, `SamlConfigDto`, `OidcConfigDto`)
- [ ] Bean Validation 어노테이션 적용
- [ ] 커스텀 검증 로직 구현

## Phase 3: 인증 서버 백엔드 확장

### 10. 기존 SSO 설정 엔티티 확인 및 확장 (authorization-server)
- [ ] authorization-server 모듈의 기존 SSO 관련 엔티티 구조 파악
- [ ] 웹 인터페이스에서 필요한 필드 확인 및 추가
- [ ] 회사 정보 연동을 위한 관계 설정 확인
- [ ] 필요시 데이터베이스 마이그레이션 스크립트 작성

### 11. 인증 서버 SSO 관리 API 확장
- [ ] 기존 authorization-server의 SSO 관련 API 구조 확인
- [ ] 리소스 서버로부터 SSO 설정을 받는 내부 API 추가
  - [ ] `POST /internal/api/sso/configurations`
  - [ ] `GET /internal/api/sso/configurations/company/{companyId}`
  - [ ] `PUT /internal/api/sso/configurations/{id}`
  - [ ] `DELETE /internal/api/sso/configurations/{id}`
- [ ] 내부 API 보안 설정 (서버간 인증)

### 12. 동적 SSO 로그인 플로우 구현
- [ ] 기존 OAuth2 로그인 플로우와 연계한 SSO 로그인 구현
- [ ] 로그인 시 회사 식별 로직 (도메인/이메일 기반)
- [ ] 회사별 SSO 제공자 동적 조회
- [ ] SAML 제공자 동적 등록 및 설정
- [ ] OIDC 제공자 동적 등록 및 설정
- [ ] SSO 콜백 처리 로직 (회사별 분기)

### 13. 사용자 매핑 및 계정 연동
- [ ] 기존 사용자 엔티티와 SSO 사용자 정보 매핑
- [ ] 신규 사용자 자동 생성 로직
- [ ] 사용자 속성 업데이트 로직
- [ ] 권한 및 역할 매핑 (기존 role 시스템과 연계)

## Phase 4: 보안 및 검증

### 14. 보안 강화
- [ ] 기존 JWT 인증 시스템과 연계한 SSO 관리 페이지 보안
- [ ] SSO 설정 민감 정보 암호화 (Client Secret 등)
- [ ] 리소스 서버와 인증 서버 간 API 인증 (JWT 토큰 또는 API Key)
- [ ] 기존 Spring Security 설정에 SSO 관리 권한 추가
- [ ] 관리자 권한 검증 (기존 role 시스템 활용)

### 15. 설정 검증 로직
- [ ] SAML 메타데이터 URL 접근 및 파싱 검증
- [ ] OIDC Discovery 문서 검증
- [ ] 인증서 유효성 검증
- [ ] 설정값 중복 검사 (회사별 SSO 설정 중복 방지)
- [ ] 필수 속성 매핑 검증

## Phase 5: 통합 테스트 및 완성

### 16. 데이터베이스 스키마 설정
- [ ] 현재 `mydb` 스키마 활용 또는 별도 스키마 생성 결정
- [ ] 리소스 서버의 `application.yml` 데이터소스 설정 확인/수정
- [ ] 스키마 분리 시 권한 설정 및 연동 방법 결정

### 17. 통합 테스트 및 검증
- [ ] resource-server → authorization-server API 연동 테스트
- [ ] 전체 SSO 설정 플로우 테스트 (웹 UI → API → 인증서버 저장 → 로그인)
- [ ] 기존 대시보드 인증과 SSO 관리 권한 연동 테스트
- [ ] 다중 회사 SSO 설정 시나리오 테스트
- [ ] 실제 SSO 제공자와의 연동 테스트
- [ ] 오류 상황 처리 테스트

### 18. 기존 로그인 플로우와 SSO 연동
- [ ] 기존 OAuth2 인증 플로우에 SSO 옵션 추가
- [ ] 회사/도메인 기반 SSO 제공자 자동 감지
- [ ] 기존 JWT 토큰과 SSO 사용자 정보 연계
- [ ] 대시보드에서 SSO 로그인 사용자 구분 표시

### 19. 모니터링 및 로깅 강화
- [ ] 기존 로깅 패턴을 따른 SSO 관련 로그 추가
- [ ] MDCFilter에 SSO 관련 컨텍스트 추가
- [ ] SSO 설정 변경 이력 추적
- [ ] API 호출 성능 모니터링

### 20. 문서화
- [ ] 기존 프로젝트 구조에 맞는 SSO 설정 가이드 작성
- [ ] 관리자용 SSO 설정 매뉴얼
- [ ] 개발자용 API 문서
- [ ] 배포 및 설정 가이드

---

## 개발 우선순위 (웹 인터페이스 우선)
1. **Phase 1: 웹 인터페이스 개발** (1-2주)
2. **Phase 2: 리소스 서버 백엔드 API** (1주)
3. **Phase 3: 인증 서버 백엔드 개발** (2주)
4. **Phase 4: 보안 및 검증** (1주)
5. **Phase 5: 통합 테스트 및 완성** (1주)

## 예상 소요 시간
- **총 예상 기간: 6-7주**
- **웹 인터페이스 완성: 2-3주**

### 6. SSO 설정 관리 페이지
- [ ] SSO 설정 목록 페이지
- [ ] SSO 설정 등록 폼 페이지
- [ ] SSO 설정 수정 폼 페이지
- [ ] SSO 제공자별 설정 필드 동적 생성

### 7. SAML 설정 인터페이스
- [ ] SAML 메타데이터 URL 입력 필드
- [ ] 엔티티 ID 설정 필드
- [ ] 사용자 속성 매핑 설정 (이메일, 이름 등)
- [ ] 서명 인증서 업로드 기능

### 8. OIDC 설정 인터페이스
- [ ] 클라이언트 ID/Secret 입력 필드
- [ ] Discovery URL 설정
- [ ] 스코프 설정
- [ ] 클레임 매핑 설정

### 9. 설정 검증 및 테스트 기능
- [ ] SSO 연결 테스트 버튼
- [ ] 메타데이터 유효성 실시간 검증
- [ ] 설정 저장 전 검증 단계
- [ ] 테스트 로그인 플로우

## 통합 및 테스트

### 10. SSO 로그인 플로우 통합
- [ ] 로그인 페이지에서 회사 식별 (도메인 입력)
- [ ] 회사별 SSO 제공자 자동 선택
- [ ] SSO 로그인 버튼 동적 생성
- [ ] 로그인 성공 후 사용자 계정 연동

### 11. 데이터베이스 마이그레이션
- [ ] SSO 설정 테이블 생성 스크립트
- [ ] 샘플 데이터 생성
- [ ] 기존 데이터와 호환성 확인

### 12. 테스트
- [ ] SSO 설정 API 단위 테스트
- [ ] SSO 로그인 플로우 통합 테스트
- [ ] 다중 회사 SSO 시나리오 테스트
- [ ] 보안 취약점 테스트

## 배포 및 문서화

### 13. 배포 준비
- [ ] 환경별 설정 분리 (dev, staging, prod)
- [ ] SSO 설정 백업/복구 방안
- [ ] 모니터링 및 로깅 설정

### 14. 사용자 가이드 작성
- [ ] SSO 설정 가이드 문서
- [ ] 각 SSO 제공자별 설정 방법
- [ ] 트러블슈팅 가이드
- [ ] FAQ 작성

## 추가 고려사항

### 15. 고급 기능 (선택사항)
- [ ] SSO 설정 템플릿 기능
- [ ] 대량 사용자 프로비저닝
- [ ] SSO 사용량 통계 및 모니터링
- [ ] 다중 SSO 제공자 지원 (회사당 여러 제공자)

---

## 개발 우선순위
1. 데이터 모델링 및 기본 API 개발
2. 동적 SSO 로그인 플로우 구현
3. 웹 인터페이스 개발
4. 통합 테스트 및 검증
5. 배포 및 문서화

## 예상 소요 시간
- 백엔드 개발: 2-3주
- 프론트엔드 개발: 2주
- 통합 및 테스트: 1주
- **총 예상 기간: 5-6주**