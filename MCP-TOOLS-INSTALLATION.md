# MCP (Model Context Protocol) 도구 설치 가이드

## 설치 완료 요약

### 사용자 전역 MCP 서버 (Claude Desktop)
- **Playwright**: 브라우저 자동화
- **Fetch**: HTTP 클라이언트
- **Git**: Git 저장소 관리

### 프로젝트별 MCP 서버
- **MySQL**: 데이터베이스 접근 (OAuth2 관련 데이터)

## 실제 사용한 설치 명령어들

### 1. 사용자 전역 설정 (~/.claude.json)

```bash
# Claude Desktop 전역 설정 파일 수정
vi ~/.claude.json
```

`mcpServers` 섹션에 추가:
```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@playwright/mcp@latest"]
    },
    "fetch": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-fetch"]
    },
    "git": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-git"]
    }
  }
}
```

### 2. 프로젝트별 설정 (.mcp.json)

```bash
# 프로젝트 루트에 MCP 설정 파일 생성
touch .mcp.json
```

`.mcp.json` 내용:
```json
{
  "name": "practice-oauth2-mcp",
  "servers": [
    {
      "name": "mysql",
      "command": "npx",
      "args": ["-y", "@benborla29/mcp-server-mysql"],
      "env": {
        "MYSQL_HOST": "127.0.0.1",
        "MYSQL_PORT": "3306",
        "MYSQL_USER": "root",
        "MYSQL_PASS": "1234qwer!",
        "MYSQL_DB": "shopl_authorization"
      },
      "description": "MySQL database access for OAuth2 data"
    }
  ]
}
```

### 3. 프로젝트별 Claude Code 설정

```bash
# 프로젝트의 모든 MCP 서버 자동 승인
echo '{"enableAllProjectMcpServers": true}' > .claude/settings.local.json
```

## 설치 후 확인 방법

### 1. Claude Desktop/Code 재시작
완전히 종료 후 재시작 필요

### 2. MCP 서버 상태 확인
```bash
claude mcp list
```

### 3. 도구 사용 테스트

#### Playwright (브라우저 자동화)
- 웹페이지 방문, 스크린샷 촬영
- OAuth2 로그인 플로우 자동화

#### Fetch (HTTP 클라이언트)
- API 요청/응답 테스트
- OAuth2 엔드포인트 호출

#### Git (저장소 관리)
- 커밋, 브랜치 관리
- 파일 상태 확인

#### MySQL (데이터베이스)
- OAuth2 클라이언트 설정 조회
- 사용자 데이터 확인

## 주요 특징

### 설치 방식
- **사용자 전역**: `~/.claude.json`에 설정, 모든 프로젝트에서 사용 가능
- **프로젝트별**: `.mcp.json`에 설정, 해당 프로젝트에서만 사용

### NPX 자동 설치
- `npx -y` 명령으로 필요시 자동 설치
- 별도의 글로벌 설치 불필요

### 보안 설정
- MySQL 접근은 프로젝트별로 제한
- 환경 변수로 민감한 정보 관리

## 문제 해결

### MCP 서버가 보이지 않는 경우
1. Claude Desktop 완전 재시작
2. JSON 문법 오류 확인
3. `claude mcp list` 명령으로 상태 확인

### 권한 오류
1. `.claude/settings.local.json`에서 `enableAllProjectMcpServers: true` 확인
2. 개별 서버 승인 필요시 대화창에서 승인

---

**설치 완료 일자**: 2024년 12월 15일
**설치된 MCP 서버**: 4개 (playwright, fetch, git, mysql)