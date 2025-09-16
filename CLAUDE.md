# Practice OAuth2 프로젝트 가이드

## 프롬프트 가이드

- 절대 기존 코드를 무시하지 말것!
- 질문과 관련한 주제에 관하여 재사용가능한 것을 먼저 탐색하고 답변할 것
- 작업 과정에서 playwright 로 브라우저 구동을 헀다면 작업이 완료된 후 종료한다.

## 프로젝트 개요

- 이 프로젝트는 Kotlin, Spring boot 사용하는 REST API 서버입니다.
- 데이터베이스는 MySQL 8을 사용하며, ORM은 JPA를 사용합니다.
- OAuth 2.0 기반 인증/인가를 제공하는 서버 개발을 학습하기 위한 프로젝트입니다.
- OAuth 2.0이 Spring 에서 구현되고 사용할 수 있는 지가 중점
- 모든 코드에 대해 테스트 코드를 작성할 필요는 없습니다. 개발자가 지시할 떄만 테스트 코드를 작성하면 됩니다.
- 현재 서비스를 'shopl' 이라고하는 B2B SaaS 로 가정하고 고객사를 정의하는 용어를 client 로 통칭하고 있음. Spring Security OAuth 에서도 client 라는 개념이 있는데,
  OAuth의 client과, 서비스의 client를 명확하게 구분할 것

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

### 인증/인가 데이터 흐름

1. 사용자가 페이지 접근 → HTML 템플릿 반환 (권한 검증 없음)
2. 프론트엔드 JavaScript가 localStorage에서 JWT 토큰 확인
3. 토큰이 있으면 REST API 호출하여 데이터 로드
4. 토큰이 없거나 만료되면 로그인 페이지로 리다이렉트

### HTTP API Request 검증 전략

- **Bean Validation 사용 금지**: Bean Validation 어노테이션(@Valid, @NotNull 등) 사용하지 않음
- **서비스 레이어 직접 검증**: 모든 유효성 검증을 서비스 레이어에서 조건문(if/else)으로 직접 구현
- **검증 로직 명시성**: 검증 로직이 명확하게 보이도록 코드로 표현

### Spring 프로퍼티 관리 전략

- **@Value 어노테이션 사용 금지**: @Value("${property.name}") 어노테이션 사용하지 않음
- **@ConfigurationProperties 사용**: 타입 안전한 프로퍼티 바인딩을 위해 data class 사용
- **app prefix 통일**: 모든 커스텀 프로퍼티는 `app` prefix로 시작
- **계층적 구조**: 관련 프로퍼티들을 nested data class로 그룹화
- **예시**:
  ```kotlin
  @ConfigurationProperties(prefix = "app")
  data class AppProperties(
      val authorizationServer: AuthorizationServer,
      val database: Database
  ) {
      data class AuthorizationServer(
          val baseUrl: String
      )
      data class Database(
          val host: String,
          val port: Int
      )
  }
  ```

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

## 핵심 명령어

- 서버 구동
    - 기본 구동:
        - ./gradlew :authorization-server:bootRun
        - ./gradlew :resource-server:bootRun
    - 포트 변경하여 구동:
        - ./gradlew :authorization-server:bootRun --args="--server.port=9000"
        - ./gradlew :resource-server:bootRun --args="--server.port=9001"
    - 프로필 지정(예: local):
        - ./gradlew :authorization-server:bootRun --args="--spring.profiles.active=local"
        - ./gradlew :resource-server:bootRun --args="--spring.profiles.active=local"
    - 환경변수로 포트 지정 예시:
        - SERVER_PORT=9000 ./gradlew :authorization-server:bootRun
        - SERVER_PORT=9001 ./gradlew :resource-server:bootRun
- 테스트 실행
    - 전체 테스트:
        - ./gradlew :authorization-server:test
        - ./gradlew :resource-server:test
    - 특정 프로필로 테스트(예: test):
        - ./gradlew :authorization-server:test -Dspring.profiles.active=test
        - ./gradlew :resource-server:test -Dspring.profiles.active=test
    - 특정 테스트 클래스만:
        - ./gradlew :authorization-server:test --tests "패키지경로.클래스명"
        - ./gradlew :resource-server:test --tests "패키지경로.클래스명"
    - 특정 테스트 메서드만:
        - ./gradlew :authorization-server:test --tests "패키지경로.클래스명.메소드명"
        - ./gradlew :resource-server:test --tests "패키지경로.클래스명.메소드명"

## 테스트 계정 정보

- **테스트 로그인 계정**: test@example.com / password123

## 활용 주요 도구

- Claude code MCP
    - linear
    - context7
    - playwright