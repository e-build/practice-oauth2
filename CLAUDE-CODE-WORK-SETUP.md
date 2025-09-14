# Claude Code 회사 PC 설정 가이드

## 🏠➡️🏢 집에서 회사로 설정 이전하기

집에서 설정한 Claude Code MCP 도구들을 회사 PC에 그대로 적용하는 방법입니다.

## 📋 준비물

1. **Claude Code 설치** - https://claude.ai/code
2. **Node.js 설치** - https://nodejs.org/
3. **Python3** (대부분 시스템에 기본 설치됨)

## 🚀 사용 방법

### 1. 스크립트 파일 복사
```bash
# 이 프로젝트의 스크립트를 회사 PC에 복사
scp setup-claude-code-work.sh 회사PC:/임시폴더/
```

또는 GitHub/이메일 등을 통해 `setup-claude-code-work.sh` 파일을 회사 PC로 이전

### 2. 스크립트 실행
```bash
# 회사 PC에서 실행
chmod +x setup-claude-code-work.sh
./setup-claude-code-work.sh
```

### 3. Claude Code 재시작
완전히 종료 후 재시작

## ✅ 설치되는 MCP 도구들

### 전역 MCP 서버 (모든 프로젝트에서 사용 가능)
- **🎭 Playwright**: 브라우저 자동화
  - 웹페이지 자동 조작
  - 스크린샷 촬영
  - OAuth2 로그인 플로우 테스트

- **🌐 Fetch**: HTTP 클라이언트
  - API 요청/응답 테스트
  - REST 엔드포인트 호출
  - 헤더, 파라미터 설정

- **📁 Git**: Git 저장소 관리
  - 커밋, 브랜치 관리
  - 파일 상태 확인
  - 현재 디렉토리 기준 동작

## 🔒 안전한 설정

### 자동 백업
- 기존 `~/.claude.json` 설정은 자동으로 백업됩니다
- 백업 파일: `~/.claude.json.backup.YYYYMMDD_HHMMSS`

### 기존 설정 보존
- 기존 MCP 서버 설정은 그대로 유지
- 새로운 서버만 추가
- Context7 등 기존 도구 영향 없음

## 🛠️ 문제 해결

### 스크립트 실행 오류
```bash
# Python3 설치 확인
python3 --version

# Node.js 설치 확인
node --version

# Claude Code 설치 확인
claude --version
```

### MCP 서버가 보이지 않는 경우
1. Claude Code 완전 재시작
2. 새 대화 시작
3. 입력창 우측 하단에 MCP 표시기 확인
4. 권한 요청 시 승인

### 설정 확인
```bash
# 현재 MCP 서버 목록 확인
claude mcp list

# 설정 파일 직접 확인
cat ~/.claude.json | grep -A 20 mcpServers
```

## 📝 프로젝트별 MySQL 설정 (선택사항)

특정 프로젝트에서 MySQL MCP가 필요한 경우:

```bash
# 프로젝트 루트에서
cat > .mcp.json << EOF
{
  "name": "project-mcp",
  "servers": [
    {
      "name": "mysql",
      "command": "npx",
      "args": ["-y", "@benborla29/mcp-server-mysql"],
      "env": {
        "MYSQL_HOST": "127.0.0.1",
        "MYSQL_PORT": "3306",
        "MYSQL_USER": "your_user",
        "MYSQL_PASS": "your_password",
        "MYSQL_DB": "your_database"
      }
    }
  ]
}
EOF
```

## 🎯 사용 예시

설치 후 Claude Code에서 다음과 같이 사용 가능:

```
브라우저에서 localhost:9001/admin/auth-dashboard에 접속해서 스크린샷 찍어줘

API 엔드포인트 http://localhost:9000/oauth2/authorize 상태 확인해줘

현재 프로젝트의 git 상태와 최근 커밋 보여줘
```

---

**🎉 이제 집과 회사에서 동일한 Claude Code 환경을 사용할 수 있습니다!**