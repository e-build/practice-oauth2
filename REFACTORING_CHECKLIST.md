# Authorization Server 리팩토링 체크리스트

## 📋 개요
- **목표**: 중복 코드 제거 및 단일 책임 원칙 적용
- **대상**: 7개 대형 파일 (230~450라인)
- **전략**: 3단계 점진적 리팩토링 + 각 단계별 테스트

---

## 🎯 **1단계: 핵심 인프라 리팩토링 (RedisAuthorizationDTO 분리)**

### 구현 작업
- [ ] RedisAuthorizationDTO.kt 리팩토링 - 순수 DTO 클래스 분리
- [ ] OAuth2AuthorizationConverter.kt 생성 - 변환 로직 분리
- [ ] OAuth2AttributeCoercer.kt 생성 - 속성 복원 로직 분리
- [ ] SsoAccountReconstructor.kt 생성 - SSO 계정 재구성 로직 분리
- [ ] ProviderUserIdExtractor 인터페이스 및 구현체들 생성
  - [ ] GoogleUserIdExtractor
  - [ ] KakaoUserIdExtractor
  - [ ] NaverUserIdExtractor

### 테스트 작업
- [ ] **단위테스트**: OAuth2AuthorizationConverter 테스트
- [ ] **단위테스트**: OAuth2AttributeCoercer 테스트
- [ ] **단위테스트**: SsoAccountReconstructor 테스트
- [ ] **단위테스트**: ProviderUserIdExtractor 구현체들 테스트

### 통합 작업
- [ ] RedisOAuth2AuthorizationService 의존성 업데이트
- [ ] **회귀테스트**: 1단계 완료 후 기존 테스트 수행
  - [ ] `./gradlew :authorization-server:test`
  - [ ] Redis 관련 통합 테스트 확인

---

## 🎯 **2단계: 서비스 레이어 정리**

### LoginHistory 서비스 리팩토링
- [ ] HttpRequestInfoExtractor.kt 생성 - HTTP 요청 정보 추출 로직 분리
- [ ] LoginHistoryQuery 데이터 클래스 생성
- [ ] **단위테스트**: LoginHistoryQuery 테스트
- [ ] **단위테스트**: HttpRequestInfoExtractor 테스트
- [ ] LoginHistoryService.kt 메서드 통합 및 간소화
- [ ] LoginHistoryStatisticsService.kt 생성 - 통계 로직 분리
- [ ] **단위테스트**: 리팩토링된 LoginHistoryService 테스트
- [ ] **단위테스트**: LoginHistoryStatisticsService 테스트

### UserProvisioning 서비스 리팩토링
- [ ] OAuth2UserInfoExtractor 인터페이스 및 구현체들 생성
  - [ ] GoogleUserInfoExtractor
  - [ ] KakaoUserInfoExtractor
  - [ ] NaverUserInfoExtractor
- [ ] AccountFactory.kt 생성 - 계정 생성 로직 분리
- [ ] IdGeneratorService.kt 생성 - ID 생성 전략 통합
- [ ] **단위테스트**: OAuth2UserInfoExtractor 구현체들 테스트
- [ ] **단위테스트**: AccountFactory 테스트
- [ ] **단위테스트**: IdGeneratorService 테스트
- [ ] UserProvisioningService.kt 리팩토링 - 새로운 서비스들 활용
- [ ] **단위테스트**: 리팩토링된 UserProvisioningService 테스트 업데이트

### SSO 인증 핸들러 리팩토링
- [ ] ClientIdExtractor.kt 생성 - 클라이언트 ID 추출 로직 분리
- [ ] RedirectUrlBuilder.kt 생성 - 리다이렉트 URL 생성 로직 분리
- [ ] SsoSuccessHandler 체인 패턴 구현
  - [ ] SsoSuccessHandler 인터페이스 정의
  - [ ] UserProvisioningHandler 구현
  - [ ] SessionManagementHandler 구현
  - [ ] LoginHistoryHandler 구현
- [ ] **단위테스트**: ClientIdExtractor 테스트
- [ ] **단위테스트**: RedirectUrlBuilder 테스트
- [ ] **단위테스트**: SsoSuccessHandler 체인 테스트
- [ ] SsoAuthenticationSuccessHandler.kt 리팩토링
- [ ] **단위테스트**: 리팩토링된 SsoAuthenticationSuccessHandler 테스트 업데이트

### 2단계 검증
- [ ] **회귀테스트**: 전체 인증 플로우 통합 테스트
  - [ ] 기본 로그인 플로우 테스트
  - [ ] SSO 로그인 플로우 테스트
  - [ ] 소셜 로그인 플로우 테스트

---

## 🎯 **3단계: 설정 및 컨트롤러 최적화**

### 보안 설정 분리
- [ ] OAuth2AuthorizationServerConfig.kt 생성 - 인가 서버 설정 분리
- [ ] WebSecurityConfig.kt 생성 - 웹 보안 설정 분리
- [ ] JwtConfiguration.kt 생성 - JWT 설정 분리
- [ ] CorsConfiguration.kt 생성 - CORS 설정 분리
- [ ] **단위테스트**: OAuth2AuthorizationServerConfig 테스트
- [ ] **단위테스트**: WebSecurityConfig 테스트
- [ ] **단위테스트**: JwtConfiguration 테스트
- [ ] **단위테스트**: CorsConfiguration 테스트
- [ ] **회귀테스트**: 시스템 기동 테스트
  - [ ] `./gradlew :authorization-server:bootRun` 정상 기동 확인

### 클라이언트 관리 리팩토링
- [ ] OAuth2ClientSettingsParser.kt 생성 - 설정 파싱 로직 분리
- [ ] TokenSettingsParser.kt 생성 - 토큰 설정 파싱 분리
- [ ] IdpClientFactory.kt 생성 - 팩토리 패턴 적용
- [ ] **단위테스트**: OAuth2ClientSettingsParser 테스트
- [ ] **단위테스트**: TokenSettingsParser 테스트
- [ ] **단위테스트**: IdpClientFactory 테스트
- [ ] IdpClient.kt 리팩토링 - 순수 도메인 객체로 변경
- [ ] **단위테스트**: 리팩토링된 IdpClient 테스트 업데이트

### 컨트롤러 분리
- [ ] SsoAuthController.kt 생성 - 인증 플로우 컨트롤러 분리
- [ ] SsoProviderController.kt 생성 - 제공자 관리 컨트롤러 분리
- [ ] SsoProviderService.kt 생성 - 제공자 정보 관리 서비스
- [ ] SsoDisplayNameResolver.kt 생성 - 표시명 처리 로직 분리
- [ ] **단위테스트**: SsoAuthController 테스트
- [ ] **단위테스트**: SsoProviderController 테스트
- [ ] **단위테스트**: SsoProviderService 테스트
- [ ] **단위테스트**: SsoDisplayNameResolver 테스트
- [ ] 기존 SsoController.kt 정리 및 제거

### 3단계 검증
- [ ] **회귀테스트**: SSO 플로우 End-to-End 테스트
  - [ ] Google SSO 테스트
  - [ ] Kakao SSO 테스트
  - [ ] 커스텀 SSO 설정 테스트

---

## 🎯 **최종 검증 및 마무리**

### 전체 시스템 검증
- [ ] **통합테스트**: 전체 시스템 통합 테스트 수행
  - [ ] `./gradlew :authorization-server:test`
  - [ ] 모든 기존 테스트 통과 확인
- [ ] **성능테스트**: 리팩토링 전후 성능 비교
  - [ ] 메모리 사용량 확인
  - [ ] 응답 시간 측정
- [ ] **E2E 테스트**: 실제 브라우저 환경에서 전체 플로우 테스트

### 코드 품질 검증
- [ ] 코드 커버리지 확인 (80% 이상 유지)
- [ ] 정적 분석 도구 실행 (Ktlint, Detekt)
- [ ] 코드 리뷰 체크리스트 확인
  - [ ] SOLID 원칙 준수 확인
  - [ ] 의존성 순환 참조 없음 확인
  - [ ] 네이밍 컨벤션 준수 확인

### 문서화
- [ ] **아키텍처 문서 업데이트**
  - [ ] 새로운 클래스 다이어그램 작성
  - [ ] 의존성 관계도 업데이트
- [ ] **개발 가이드 업데이트**
  - [ ] 새로운 서비스 사용법 문서화
  - [ ] 확장 포인트 안내 추가
- [ ] **CLAUDE.md 업데이트**
  - [ ] 리팩토링된 구조 반영
  - [ ] 새로운 실행 방법 안내

---

## 🛠️ **진행 상태 체크**

### 진행률
- **1단계**: ⬜ 0% (0/8)
- **2단계**: ⬜ 0% (0/23)
- **3단계**: ⬜ 0% (0/20)
- **최종검증**: ⬜ 0% (0/10)

### 현재 작업
- [ ] 현재 진행 중인 작업을 여기에 체크

### 이슈 트래킹
- [ ] 발견된 이슈나 주의사항을 여기에 기록

---

## 📝 **메모**

### 주의사항
- 각 단계별로 반드시 테스트를 먼저 작성하고 구현할 것
- 기존 테스트가 깨지면 즉시 수정할 것
- 커밋은 작은 단위로 자주할 것
- 의존성 변경 시 다른 모듈에 미치는 영향 확인할 것

### 리팩토링 후 기대 효과
- 코드 가독성 향상 (평균 파일 크기 50% 감소)
- 단일 책임 원칙 준수로 유지보수성 개선
- 테스트 용이성 향상 (단위 테스트 커버리지 증가)
- 확장성 개선 (새로운 OAuth 제공자 추가 용이)