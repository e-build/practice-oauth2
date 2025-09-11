# 로그인 이력 관리 기능 개발 체크리스트

## 개요
OAuth2 인증 서버에서 사용자의 로그인 이력을 추적하고 관리하는 기능을 구현합니다.

## 테이블 스키마
```sql
CREATE TABLE io_idp_login_history
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shopl_client_id     VARCHAR(64) NOT NULL,
    shopl_user_id       VARCHAR(64) NOT NULL,
    platform            ENUM('DASHBOARD', 'APP') NOT NULL,

    login_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    login_type          ENUM('BASIC', 'SOCIAL', 'SSO') NOT NULL,
    provider            VARCHAR(64) NULL,

    result              ENUM('SUCCESS', 'FAIL') NOT NULL,
    failure_reason      VARCHAR(100) NULL,

    ip_address          VARCHAR(45) NULL,
    user_agent          TEXT NULL,
    location            VARCHAR(200) NULL,

    session_id          VARCHAR(128) NOT NULL,

    INDEX idx_user_client_time (shopl_user_id, shopl_client_id, login_time DESC),
    INDEX idx_result_time (result, login_time DESC),
    INDEX idx_client_time (shopl_client_id, login_time DESC)
);
```

## 개발 체크리스트

### 0. 테이블 피드백
- [ ] 너무 복잡해지지 않는 선에서 사용자에게 제공될 '로그인 이력' 조회 기능만 담기도록 테이블 설계 피드백
- [ ] 현재 OAuth 2.0 기반 서버 내 인증관련 이벤트들 중 중요한데 누락된 데이터가 있다면 테이블에 반영

### 1. Entity 및 Domain 모델
- [ ] IoIdpLoginHistory 엔티티 클래스 생성
- [ ] Platform enum 클래스 생성 (DASHBOARD, APP)
- [ ] Result enum 클래스 생성 (SUCCESS, FAIL)
- [ ] LoginType enum 클래스 생성 (BASIC, SOCIAL, SSO)
- [ ] FailureReasonType enum 클래스 생성
- [ ] ProviderType 기존 enum에 필요한 값 추가 확인

### 2. Repository 계층
- [ ] IoIdpLoginHistoryRepository 인터페이스 생성
- [ ] 기본 CRUD 메서드 정의
- [ ] 사용자별 로그인 이력 조회 메서드
- [ ] 기간별 로그인 통계 메서드
- [ ] 실패한 로그인 시도 조회 메서드

### 3. Service 계층
- [ ] LoginHistoryService 클래스 생성
- [ ] 로그인 이력 저장 기능 (성공/실패)
- [ ] Principal 해싱 로직 구현 (SHA-256 + Salt)
- [ ] Principal 마스킹 로직 구현 (힌트 생성)
- [ ] 로그인 이력 조회 기능
- [ ] 로그인 통계 기능
- [ ] Scope JSON 처리 로직

### 4. 유틸리티 클래스
- [ ] PrincipalHashingUtil 클래스 생성
- [ ] PrincipalMaskingUtil 클래스 생성
- [ ] ScopeJsonUtil 클래스 생성 (기존 JsonUtils 확장)

### 5. 기존 인증 플로우 통합
- [ ] CustomAuthenticationProvider에 로그인 이력 기록 추가
  - [ ] 인증 성공 시 이력 저장
  - [ ] 인증 실패 시 이력 저장 (실패 원인 포함)
- [ ] SsoAuthenticationSuccessHandler에 SSO 로그인 이력 기록 추가
- [ ] OAuth2 인증 실패 핸들러에 이력 기록 추가

### 6. API 엔드포인트
- [ ] LoginHistoryController 생성
- [ ] 사용자별 로그인 이력 조회 API
- [ ] 로그인 통계 API (성공률, 실패 원인 분석 등)
- [ ] 관리자용 전체 로그인 이력 조회 API
- [ ] 특정 기간 로그인 이력 조회 API

### 7. 보안 및 프라이버시
- [ ] 개인정보 해싱 및 마스킹 처리 확인
- [ ] 로그인 이력 데이터 보관 정책 구현
- [ ] 관리자 권한 확인 로직

### 8. 테스트
- [ ] LoginHistoryService 단위 테스트
- [ ] Repository 테스트
- [ ] 인증 플로우 통합 테스트
- [ ] API 엔드포인트 테스트

### 9. 문서화
- [ ] API 문서 업데이트 (Swagger)
- [ ] 코드 주석 추가
- [ ] 설정 가이드 작성

## 구현 우선순위
1. Entity 및 Repository 구현 (기본 데이터 모델)
2. Service 계층 구현 (핵심 비즈니스 로직)
3. 유틸리티 클래스 구현 (해싱, 마스킹)
4. 기존 인증 플로우에 통합
5. API 엔드포인트 구현
6. 테스트 및 문서화

## 주의사항
- 개인정보 보호를 위해 principal은 반드시 해싱하여 저장
- 실패 로그인 시도의 경우 보안을 위해 상세 정보는 제한적으로 저장
- JSON 스코프 데이터의 크기 제한 고려
- Redis OAuth2Authorization.id와의 연관 관계 유지