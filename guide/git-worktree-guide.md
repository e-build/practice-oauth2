Git Worktree 병렬 작업 설정 가이드

현재 상태: main 브랜치에 4개의 커밋이 origin보다 앞서 있고, 여러 개의 Gradle 서버가 백그라운드에서 실행 중입니다.

1. 기본 Worktree 생성

# 새로운 기능 브랜치용 worktree 생성
`git worktree add ../practice-oauth2-feature feature-branch`

# 버그 수정용 worktree 생성
`git worktree add ../practice-oauth2-hotfix hotfix-branch`

# 기존 브랜치를 기반으로 worktree 생성
`git worktree add ../practice-oauth2-main main`

2. 병렬 작업 시나리오별 예시

시나리오 A: 새로운 기능 개발과 버그 수정을 동시에
# 현재 디렉토리에서 새 기능 작업
`git checkout -b feature/sso-enhancement`

# 다른 worktree에서 버그 수정
`git worktree add ../oauth2-hotfix hotfix/login-issue`

시나리오 B: 서로 다른 버전에서 테스트
# 메인 브랜치용
`git worktree add ../oauth2-main main`

# 개발 브랜치용
`git worktree add ../oauth2-dev develop`

3. 각 Worktree에서 독립적인 서버 실행

각 worktree에서 별도의 포트로 서버를 실행할 수 있습니다:

# Worktree 1 (현재): 기본 포트 8080, 8081
./gradlew :authorization-server:bootRun
./gradlew :resource-server:bootRun

# Worktree 2: 다른 포트 8090, 8091
```bash
cd ../oauth2-feature
./gradlew :authorization-server:bootRun --args='--server.port=8090'
./gradlew :resource-server:bootRun --args='--server.port=8091'
```

4. Worktree 관리 명령어

# Worktree 목록 확인
`git worktree list`

# Worktree 제거
`git worktree remove ../oauth2-feature`

# Worktree 정리 (제거된 디렉토리 정리)
`git worktree prune`

⏺ 5. OAuth2 프로젝트에 특화된 병렬 작업 예시

예시 1: Authorization Server와 Resource Server 분리 개발
# Authorization Server 전용 worktree
`git worktree add ../oauth2-auth-dev auth-server-enhancement`

# Resource Server 전용 worktree
`git worktree add ../oauth2-resource-dev resource-server-enhancement`

예시 2: 소셜 로그인 기능별 병렬 개발
# Google OAuth 구현
`git worktree add ../oauth2-google feature/google-oauth`

# Kakao OAuth 구현
`git worktree add ../oauth2-kakao feature/kakao-oauth`

# 각각에서 다른 포트로 테스트
cd ../oauth2-google && ./gradlew bootRun --args='--server.port=8090'
cd ../oauth2-kakao && ./gradlew bootRun --args='--server.port=8100'

예시 3: 프로덕션 환경 시뮬레이션
# 현재: 개발 환경 (H2 DB)
# Worktree: 프로덕션 환경 시뮬레이션 (MySQL)
`git worktree add ../oauth2-prod production-test`

주의사항

- 각 worktree는 독립적인 working directory를 가지므로 설정 파일 충돌 주의
- 데이터베이스 포트나 스키마 이름을 다르게 설정하여 충돌 방지
- Git hooks는 모든 worktree에서 공유되므로 주의