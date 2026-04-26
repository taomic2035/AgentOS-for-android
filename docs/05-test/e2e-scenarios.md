# E2E 场景脚本

## 通用前置

```bash
# 1. 启动模拟器
emulator -avd pixel_6_arm64 -no-snapshot-load -netdelay none -netspeed full &
adb wait-for-device

# 2. 装包
./gradlew :app:installDebug

# 3. 启用 AccessibilityService（需要预先在 secure settings 写入）
adb shell settings put secure enabled_accessibility_services \
  com.taomic.agent/com.taomic.agent.a11y.AgentAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 4. 启用 OVERLAY 权限
adb shell appops set com.taomic.agent SYSTEM_ALERT_WINDOW allow

# 5. 启动 App
adb shell am start -n com.taomic.agent/.ui.MainActivity
```

## 场景 1：视频

```bash
./scripts/e2e-tencent-video.sh
```

步骤：

1. 启动 App → 浮窗出现
2. 通过 ADB 注入文字 "三体" 到浮窗输入框
3. 等待包名 = `com.tencent.qqlive`
4. 等待屏幕里出现"三体" 文本节点
5. 截图归档到 `scripts/test-artifacts/<ts>/tencent-video.png`
6. 断言：包名匹配 + 文本存在 = pass

## 场景 2：购物（V0.6 起）

```bash
./scripts/e2e-jd-shopping.sh "刘亦菲同款"
```

期望：拉起京东 → 搜索 → 商品页 → 停在付款页（断言 RiskGuard 触发）

## 场景 3：外卖（V0.6）

```bash
./scripts/e2e-meituan-reorder.sh
```

## 场景 4：打车（V0.6）

```bash
./scripts/e2e-didi-call.sh "公司"
```

## 场景 5：搜索（V0.6）

```bash
./scripts/e2e-search-weather.sh
```

## 守卫场景（V0.7 起）

```bash
./scripts/e2e-payment-guard.sh
```

期望：执行到付款页时浮窗弹确认，倒计时 10 秒后自动取消。

## 失败用例

- 模拟无网络：`adb shell svc wifi disable && adb shell svc data disable`
- 模拟 LLM 超时：mock server 延迟 60s
- 模拟节点缺失：替换 stub App 的 layout

## 报告产出

- 每次跑完输出 `scripts/test-artifacts/<ts>/report.html`
- 含每个场景：耗时 / 截图 / 日志 / pass/fail
