#!/usr/bin/env bash
# V0.1 端到端：触发 settings_open_internet skill，验证 4 步全过 + 焦点落在
# Settings/SubSettings (Network & internet 子页)。
#
# 这是 V0.1 最稳的烟雾测试 —— 不依赖 stub-video 装机，只用系统 Settings。

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=lib/e2e-common.sh
source "$SCRIPT_DIR/lib/e2e-common.sh"

TS=$(date +%Y%m%d-%H%M%S)
ARTIFACTS="$REPO_ROOT/scripts/test-artifacts/e2e-settings-$TS"
mkdir -p "$ARTIFACTS"

require_env

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
    ./gradlew :app:assembleDebug -q
fi
"$ADB" -s "$ANDROID_DEVICE" install -r "$AGENT_APK" >/dev/null
reset_a11y
grant_overlay
start_agent_service

"$ADB" -s "$ANDROID_DEVICE" shell input keyevent KEYCODE_HOME
sleep 1
"$ADB" -s "$ANDROID_DEVICE" shell am force-stop com.android.settings || true
sleep 1
"$ADB" -s "$ANDROID_DEVICE" logcat -c

echo ">> RUN_SKILL settings_open_internet"
run_skill_id settings_open_internet

echo ">> wait for SubSettings focus"
wait_for_focus "SubSettings" 10

snapshot_screen "$ARTIFACTS/01-network-internet.png"
dump_logcat "$ARTIFACTS/logcat.txt"

if grep -q 'RUN_SKILL\[SUCCESS\] "settings_open_internet" steps=4/4' "$ARTIFACTS/logcat.txt"; then
    echo ">> RESULT: PASS"
    DUR=$(grep -oE 'dur=[0-9]+ms' "$ARTIFACTS/logcat.txt" | head -1 || echo "dur=?")
    echo "    duration: $DUR"
    echo "    artifacts: $ARTIFACTS"
    exit 0
else
    echo ">> RESULT: FAIL — see $ARTIFACTS/logcat.txt"
    grep RUN_SKILL "$ARTIFACTS/logcat.txt" || true
    exit 1
fi
