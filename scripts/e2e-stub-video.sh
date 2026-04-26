#!/usr/bin/env bash
# V0.1 端到端：浮窗触发"看三体"链路，验证 stub_video_play skill 6 步全过 +
# 焦点最终落在 stubvideo.PlayerActivity。截图 + logcat 自动归档。
#
# 与手动测试不同点：
#   - 不通过坐标 input tap 浮窗（坐标依赖分辨率，CI 不稳）
#   - 直接发送 RUN_SKILL 广播触发同一条业务路径
#   - 触发结果与浮窗 chip 一致（都走 AgentApp.runSkillById → SkillRunner）

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib/e2e-common.sh
source "$SCRIPT_DIR/lib/e2e-common.sh"

TS=$(date +%Y%m%d-%H%M%S)
ARTIFACTS="$REPO_ROOT/scripts/test-artifacts/e2e-stub-video-$TS"
mkdir -p "$ARTIFACTS"

require_env

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
    build_apks
fi
install_apks
reset_a11y
grant_overlay
start_agent_service

# 让 stub-video 是冷启动；agent service 已启动，可直接广播
"$ADB" -s "$ANDROID_DEVICE" shell input keyevent KEYCODE_HOME
sleep 1
"$ADB" -s "$ANDROID_DEVICE" shell am force-stop "$STUB_VIDEO_PKG"
sleep 1
"$ADB" -s "$ANDROID_DEVICE" logcat -c

echo ">> RUN_SKILL stub_video_play title=三体"
run_skill_id stub_video_play 三体

echo ">> wait for PlayerActivity focus"
wait_for_focus "PlayerActivity" 10

echo ">> snapshot + logcat"
snapshot_screen "$ARTIFACTS/01-player-activity.png"
dump_logcat "$ARTIFACTS/logcat.txt"

# 校验：从 logcat 提取 RUN_SKILL[SUCCESS] + step 数
if grep -q 'RUN_SKILL\[SUCCESS\] "stub_video_play" steps=6/6' "$ARTIFACTS/logcat.txt"; then
    echo ">> RESULT: PASS — 6/6 steps, focus on PlayerActivity"
    DUR=$(grep -oE 'dur=[0-9]+ms' "$ARTIFACTS/logcat.txt" | head -1 || echo "dur=?")
    echo "    duration: $DUR"
    echo "    artifacts: $ARTIFACTS"
    exit 0
else
    echo ">> RESULT: FAIL — see $ARTIFACTS/logcat.txt"
    grep RUN_SKILL "$ARTIFACTS/logcat.txt" || true
    exit 1
fi
