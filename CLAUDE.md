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

## 주요기능
- authorization-server
  - 기본 로그인
  - SSO
  - 소셜 로그인 - GOOGLE, KAKAO, ...
- resource-server
  - 페이지 제공
    - /admin/auth-dashboard - 인증된 토큰 정보 표시. SSO 설정 화면 라우팅 버튼 노출
    - 등등...

## 핵심 명령어

## 설치된 주요 도구