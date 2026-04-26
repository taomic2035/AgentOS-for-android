# 测试策略

## 测试金字塔

```
           E2E (UI Automator + AVD)
          ──────────────────────
        Instrumentation (Espresso + UiAutomator)
       ──────────────────────────
     Unit (JUnit + Mockk + Truth)
    ──────────────────────────────
```

## 三层职责

| 层 | 跑在哪 | 内容 | 速度 |
|---|---|---|---|
| Unit | JVM（jvm-toolchain 17） | core / skill / llm 纯逻辑 | <1s/case |
| Instrumentation | AVD / 真机 | a11y / data / uikit Android API 行为 | 5-30s/case |
| E2E | AVD pixel_6_arm64 | 完整场景脚本 | 30-120s/case |

## 模块覆盖目标

| 模块 | Unit 覆盖率 | Instrumentation | E2E |
|---|---|---|---|
| core | ≥ 80% | — | 间接 |
| skill | ≥ 80% | — | 间接 |
| llm | ≥ 70% | — | 间接 |
| a11y | ≥ 50% | ✅ | 直接 |
| data | ≥ 60% | ✅ | 间接 |
| uikit | — | ✅（Compose Test） | 间接 |
| app | — | ✅ | 直接 |

## 工具栈

- JUnit 4 / Mockk
- Robolectric（部分需要 Android 上下文的纯逻辑）
- AndroidX Test + Espresso + UiAutomator（仪器）
- Compose UI Test（uikit）
- 自建 stub Activity（用于 a11y 操控验证）

## CI 触发

- 提交 PR：跑 Unit
- 合并 main：跑 Unit + Instrumentation
- 每晚 nightly：跑 Unit + Instrumentation + E2E

## 测试数据

- 内置 mock LLM server（OkHttp MockWebServer）
- 内置 stub App（仿腾讯视频、京东等的最小 Activity）
- 录屏 / 截图归档到 `scripts/test-artifacts/<version>/`

## 性能 / 稳定性

- E2E 每晚跑 5 次，统计成功率
- 失败用例自动重跑 1 次

## 验收

- CI 全绿
- 每个 V0.x 都有自身的 release-test 报告
