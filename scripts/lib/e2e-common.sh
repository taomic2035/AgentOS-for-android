#!/usr/bin/env bash
# AgentOS V0.1 e2e 测试用的环境装载工具。被 e2e-*.sh 脚本 source。
#
# 必须在仓库根（aosp_agent/）执行。导出以下变量给调用方使用：
#   ADB                 — adb 可执行文件绝对路径
#   ANDROID_DEVICE      — 目标设备序列号（默认 emulator-5554）
#   AGENT_PKG           — com.taomic.agent
#   STUB_VIDEO_PKG      — com.taomic.agentos.stubvideo
#   AGENT_APK           — agent debug APK 相对路径
#   STUB_APK            — stub-video debug APK 相对路径
#   AGENT_A11Y_SVC      — a11y service 全名
#
# 提供以下函数：
#   require_env         — 检查 JAVA_HOME / adb / device 都就位
#   build_apks          — gradle 打 :app + :tools:stub-video debug APK
#   install_apks        — adb install -r 两个 APK
#   reset_a11y          — 关闭再启用 a11y service（force-stop 后必须做）
#   grant_overlay       — appops 授权 SYSTEM_ALERT_WINDOW
#   start_agent_service — am start MainActivity + tap 启动助手按钮
#                         （后续可替换为直接 startForegroundService 命令）
#   run_skill_id <id>   — am broadcast com.taomic.agent.RUN_SKILL --es skill_id <id>
#                         可选 --es input_title <title>
#   wait_for_focus <pkg/.cls> [timeout_s]
#   snapshot_screen <out.png>
#   dump_logcat_since <epoch_sec> <out.txt>

set -euo pipefail

ADB="${ADB:-$HOME/Android/sdk/platform-tools/adb}"
ANDROID_DEVICE="${ANDROID_DEVICE:-emulator-5554}"
AGENT_PKG="com.taomic.agent"
STUB_VIDEO_PKG="com.taomic.agentos.stubvideo"
AGENT_APK="app/build/outputs/apk/debug/app-debug.apk"
STUB_APK="tools/stub-video/build/outputs/apk/debug/stub-video-debug.apk"
AGENT_A11Y_SVC="${AGENT_PKG}/${AGENT_PKG}.a11y.AgentAccessibilityService"

export ADB ANDROID_DEVICE AGENT_PKG STUB_VIDEO_PKG AGENT_APK STUB_APK AGENT_A11Y_SVC

require_env() {
    if [[ -z "${JAVA_HOME:-}" ]]; then
        if [[ -d "$HOME/tools/jdk/amazon-corretto-17.jdk/Contents/Home" ]]; then
            export JAVA_HOME="$HOME/tools/jdk/amazon-corretto-17.jdk/Contents/Home"
        else
            echo "JAVA_HOME unset; please point it at a JDK 17 install" >&2
            return 1
        fi
    fi
    if [[ ! -x "$ADB" ]]; then
        echo "adb not found at $ADB" >&2
        return 1
    fi
    local devices
    devices=$("$ADB" devices | awk 'NR>1 && /device$/ {print $1}')
    if ! echo "$devices" | grep -qx "$ANDROID_DEVICE"; then
        echo "device $ANDROID_DEVICE not online; available:" >&2
        echo "$devices" >&2
        return 1
    fi
}

build_apks() {
    echo ">> gradle assembleDebug (agent + stub-video)"
    ./gradlew :app:assembleDebug :tools:stub-video:assembleDebug -q
}

install_apks() {
    echo ">> install agent + stub-video"
    "$ADB" -s "$ANDROID_DEVICE" install -r "$STUB_APK" >/dev/null
    "$ADB" -s "$ANDROID_DEVICE" install -r "$AGENT_APK" >/dev/null
}

reset_a11y() {
    echo ">> reset a11y service"
    "$ADB" -s "$ANDROID_DEVICE" shell settings put secure accessibility_enabled 0 || true
    sleep 1
    "$ADB" -s "$ANDROID_DEVICE" shell settings put secure enabled_accessibility_services "$AGENT_A11Y_SVC"
    "$ADB" -s "$ANDROID_DEVICE" shell settings put secure accessibility_enabled 1
    sleep 2
}

grant_overlay() {
    "$ADB" -s "$ANDROID_DEVICE" shell appops set "$AGENT_PKG" SYSTEM_ALERT_WINDOW allow
}

start_agent_service() {
    echo ">> launch MainActivity + service"
    "$ADB" -s "$ANDROID_DEVICE" shell am start -n "${AGENT_PKG}/.ui.MainActivity" >/dev/null
    sleep 2
    # 通过 explicit Activity 启动 fg service 比 input tap 坐标稳定
    "$ADB" -s "$ANDROID_DEVICE" shell am start-foreground-service -n "${AGENT_PKG}/.service.AgentForegroundService" >/dev/null 2>&1 || \
        "$ADB" -s "$ANDROID_DEVICE" shell am startservice -n "${AGENT_PKG}/.service.AgentForegroundService" >/dev/null 2>&1 || true
    sleep 2
}

# run_skill_id <id> [title]
run_skill_id() {
    local id="$1"
    local title="${2:-}"
    if [[ -n "$title" ]]; then
        "$ADB" -s "$ANDROID_DEVICE" shell "am broadcast -a ${AGENT_PKG}.RUN_SKILL --es skill_id $id --es input_title \"$title\"" >/dev/null
    else
        "$ADB" -s "$ANDROID_DEVICE" shell "am broadcast -a ${AGENT_PKG}.RUN_SKILL --es skill_id $id" >/dev/null
    fi
}

# wait_for_focus <匹配子串> [timeout_s=10]
#   匹配 dumpsys window mCurrentFocus 行的子串（fixed string），如
#   "PlayerActivity" 或 "SubSettings"。dumpsys 不同 Activity 输出
#   pkg/.cls 或 pkg/fqcn 格式不一，调用方传 simple class name 最稳。
wait_for_focus() {
    local target="$1"
    local timeout="${2:-10}"
    local i=0
    while (( i < timeout )); do
        local focus
        focus=$("$ADB" -s "$ANDROID_DEVICE" shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 || true)
        if echo "$focus" | grep -qF "$target"; then
            return 0
        fi
        sleep 1
        i=$((i + 1))
    done
    echo "wait_for_focus timeout: expected substring \"$target\", last focus:" >&2
    "$ADB" -s "$ANDROID_DEVICE" shell dumpsys window 2>&1 | grep mCurrentFocus | head -1 >&2
    return 1
}

snapshot_screen() {
    local out="$1"
    mkdir -p "$(dirname "$out")"
    "$ADB" -s "$ANDROID_DEVICE" exec-out screencap -p > "$out"
}

dump_logcat() {
    local out="$1"
    "$ADB" -s "$ANDROID_DEVICE" logcat -d -s 'AgentApp:I' 'AgentA11y:I' 'AgentFgSvc:I' 'FloatingBubble:I' > "$out"
}
