Git Worktree 병렬 작업 설정 가이드

# Git worktree란?
- 같은 레포지토리에 붙어 있는 여러 브랜치를 관리할 때 사용하는 도구입니다.
- 하나의 레포지토리에 연관된 worktree는 main worktree와 linked worktree로 분류되죠. main worktree는 git init, git clone을 통해 만들어집니다. 그에 반해 linked worktree는 main worktree와 linked worktree에서 만들어집니다.
- git clone과 달리 브랜치와 커밋 히스토리를 공유합니다.

# AI를 활용한 작업 장점
- git checkout 을 통해 여러 브런치를 관리할 때와 달리 IDE indexing을 매번 할 필요 없어짐
- AI Agent가 실행 가능한 코드를 작성할 수 있는 환경을 만드는데 큰 도움됨. 코드 작성을 AI Agent에 맡길 경우엔 컴파일 및 테스트를 실행할 수 있는 독립적인 환경을 쉽게 설정할 수 있는 것이 중요함
- AI가 자신이 작성한 코드를 쉽게 검증할 수 있죠. Git worktree는 브랜치간 환경을 독립적으로 분리 하여 AI Agent에게 코드 작업을 대리하는데 유리한 환경을 만듭니다.

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