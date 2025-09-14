#!/bin/bash

# Claude Code 설정 복사 스크립트 - 회사 PC용
# 집 PC에서 설정한 Claude Code 전역 설정을 회사 PC에 적용

set -e

echo "🚀 Claude Code 설정 복사 스크립트 시작..."

# 색깔 출력을 위한 함수
print_green() {
    echo -e "\033[32m$1\033[0m"
}

print_yellow() {
    echo -e "\033[33m$1\033[0m"
}

print_red() {
    echo -e "\033[31m$1\033[0m"
}

# Claude Code 설치 확인
if ! command -v claude &> /dev/null; then
    print_red "❌ Claude Code가 설치되어 있지 않습니다."
    echo "먼저 Claude Code를 설치해주세요: https://claude.ai/code"
    exit 1
fi

print_green "✅ Claude Code가 설치되어 있습니다."

# 현재 설정 백업
if [ -f "$HOME/.claude.json" ]; then
    print_yellow "⚠️  기존 설정을 백업합니다..."
    cp "$HOME/.claude.json" "$HOME/.claude.json.backup.$(date +%Y%m%d_%H%M%S)"
    print_green "✅ 백업 완료"
fi

# MCP 서버 설정 적용
print_yellow "📝 MCP 서버 설정을 적용합니다..."

# ~/.claude.json 파일이 없으면 기본 구조 생성
if [ ! -f "$HOME/.claude.json" ]; then
    echo '{}' > "$HOME/.claude.json"
fi

# Python을 사용해서 JSON 파일 수정 (jq가 없을 수도 있으니)
python3 << 'EOF'
import json
import os
from pathlib import Path

claude_json_path = Path.home() / '.claude.json'

# 기존 설정 로드
try:
    with open(claude_json_path, 'r', encoding='utf-8') as f:
        config = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
    config = {}

# MCP 서버 설정 추가/업데이트
if 'mcpServers' not in config:
    config['mcpServers'] = {}

# 새로운 MCP 서버들 추가
new_servers = {
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

# 기존 서버 설정 유지하면서 새 서버 추가
for server_name, server_config in new_servers.items():
    config['mcpServers'][server_name] = server_config
    print(f"✅ {server_name} MCP 서버 설정 추가")

# 설정 저장
with open(claude_json_path, 'w', encoding='utf-8') as f:
    json.dump(config, f, indent=2, ensure_ascii=False)

print("✅ MCP 서버 설정 완료")
EOF

if [ $? -eq 0 ]; then
    print_green "✅ MCP 서버 설정이 완료되었습니다!"
else
    print_red "❌ MCP 서버 설정 중 오류가 발생했습니다."
    exit 1
fi

# Node.js 설치 확인
print_yellow "🔍 Node.js 설치 확인 중..."
if ! command -v node &> /dev/null; then
    print_red "❌ Node.js가 설치되어 있지 않습니다."
    echo "Node.js를 설치해주세요: https://nodejs.org/"
    exit 1
else
    NODE_VERSION=$(node --version)
    print_green "✅ Node.js $NODE_VERSION 설치됨"
fi

# NPM 패키지 캐시 확인 (선택사항)
print_yellow "📦 MCP 서버 패키지 사전 다운로드 중..."
echo "필요시 패키지가 자동으로 설치됩니다..."

# Claude Code 권한 설정 안내
print_yellow "⚙️  권한 설정 안내:"
echo "1. Claude Code를 다시 시작해주세요"
echo "2. 대화 입력창 우측 하단에 MCP 서버 표시기가 나타나는지 확인하세요"
echo "3. MCP 서버 사용 시 권한 요청이 나오면 승인해주세요"

# 설정 확인
print_yellow "🔍 현재 MCP 서버 설정 확인:"
python3 << 'EOF'
import json
from pathlib import Path

claude_json_path = Path.home() / '.claude.json'
try:
    with open(claude_json_path, 'r', encoding='utf-8') as f:
        config = json.load(f)

    if 'mcpServers' in config:
        print("설치된 MCP 서버들:")
        for server_name in config['mcpServers'].keys():
            print(f"  - {server_name}")
    else:
        print("MCP 서버 설정이 없습니다.")

except Exception as e:
    print(f"설정 파일 읽기 오류: {e}")
EOF

print_green "🎉 Claude Code 설정 복사가 완료되었습니다!"
print_yellow "📋 다음 단계:"
echo "1. Claude Code를 완전히 재시작하세요"
echo "2. 새 대화를 시작하여 MCP 도구들이 사용 가능한지 확인하세요"
echo "3. 프로젝트별 MySQL MCP는 각 프로젝트에서 별도 설정 필요"

echo ""
print_green "✨ 설정 완료! 이제 브라우저 자동화, HTTP 클라이언트, Git 도구를 사용할 수 있습니다."