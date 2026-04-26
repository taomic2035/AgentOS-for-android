#!/usr/bin/env bash
# AgentOS V0.1 一键 CI 跑：build + unit + 两条 e2e（依赖 emulator-5554 在线）
#
# 用法：
#   ./scripts/ci.sh              # 全跑
#   ./scripts/ci.sh unit         # 只跑单元测试
#   ./scripts/ci.sh e2e          # 跳过 build + unit，只跑 e2e
#   SKIP_BUILD=1 ./scripts/ci.sh # build 后单测/e2e 跳过 gradle 重新打包
#
# 退出码：0 = 全过；非 0 = 任一阶段失败

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# shellcheck source=lib/e2e-common.sh
source "$SCRIPT_DIR/lib/e2e-common.sh"

stage="${1:-all}"

step() {
    echo ""
    echo "================================================================"
    echo ">>> $1"
    echo "================================================================"
}

if [[ "$stage" == "all" || "$stage" == "build" ]]; then
    step "BUILD: ./gradlew assembleDebug + :tools:stub-video:assembleDebug"
    require_env
    ./gradlew :app:assembleDebug :tools:stub-video:assembleDebug
fi

if [[ "$stage" == "all" || "$stage" == "unit" ]]; then
    step "UNIT TESTS: :core :skill :llm"
    ./gradlew :core:test :skill:test :llm:test --rerun-tasks

    echo ""
    echo "    test counts (suite | tests | failures):"
    find . -name 'TEST-*.xml' -path '*/build/test-results/*' 2>/dev/null \
        | xargs awk -F'"' '/<testsuite/ {printf "      %-50s %4d tests   %d failures\n", $2, $4, $8}' 2>/dev/null \
        || true
fi

if [[ "$stage" == "all" || "$stage" == "e2e" ]]; then
    step "E2E: settings_open_internet"
    SKIP_BUILD=1 "$SCRIPT_DIR/e2e-settings.sh"

    step "E2E: stub_video_play (看三体)"
    SKIP_BUILD=1 "$SCRIPT_DIR/e2e-stub-video.sh"
fi

echo ""
echo "================================================================"
echo ">>> CI: ALL GREEN"
echo "================================================================"
