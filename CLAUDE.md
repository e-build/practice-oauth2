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

## 설치된 주요 도구