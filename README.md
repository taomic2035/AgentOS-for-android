# AgentOS — Android 智能助手

> 一句话：常驻系统、可随时呼出的 Android 智能助理，理解你说的话，动手帮你做事。
> "我想看三体" → 自动拉起腾讯视频，搜剧，从上次进度续看。
> "我也想买刘亦菲同款" → 自动比价，跳到京东商品页，停在付款页等你输密码。

完整愿景见 [`docs/00-vision/product-vision.md`](docs/00-vision/product-vision.md)。
完整文档索引见 [`docs/README.md`](docs/README.md)。

---

## 当前进度

**V0.2 ✅ 验收通过** (2026-04-27) —— LLM 智能层接入，ChainedRouter（关键词 → LLM 兜底），OpenAI 兼容客户端，设置页连接测试。

详见 [`docs/06-roadmap/release-notes-v0.2.md`](docs/06-roadmap/release-notes-v0.2.md)。

下一步：V0.3 语音 + 文字双输入。

## 架构概览

```
app  →  uikit  a11y  data  skill  llm
                                 ↓
                                core  (纯 Kotlin / Java，业务核心)
```

详见 [`docs/02-architecture/architecture-overview.md`](docs/02-architecture/architecture-overview.md)。

## 五分钟跑通

### 1. 环境准备

需要 Android 开发环境（详见 `~/Android/env.md`）：

- Android SDK 35 (Android 15)
- NDK r27c
- AVD `pixel_6_arm64`（Apple Silicon Mac 推荐）
- JDK 17

### 2. 加载环境变量

```bash
source ~/.zshrc
```

### 3. 验证 Gradle

```bash
cd /Users/taomic/vibecoding/aosp_agent
./gradlew help
./gradlew projects
```

应看到 7 个子模块：`app / core / a11y / skill / llm / data / uikit`。

### 4. 启动模拟器

```bash
emulator -avd pixel_6_arm64 -no-snapshot-load &
adb wait-for-device
```

### 5. 编译并安装（V0.1 完成后可用）

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.taomic.agent/.ui.MainActivity
```

## 模块清单

| 模块 | 类型 | 主要职责 |
|---|---|---|
| `app` | Android Application | 入口 / Service 装配 / 主题 |
| `core` | Kotlin JVM Library | 业务核心 + 接口契约 |
| `a11y` | Android Library | AccessibilityService 与操作原语 |
| `skill` | Kotlin JVM Library | DSL / Runner / 内置 Skill 包 |
| `llm` | Kotlin JVM Library | LLM 适配（OpenAI 兼容 / Anthropic / Local） |
| `data` | Android Library | Room DB / 各 Store |
| `uikit` | Android Library | 浮窗 / Compose 组件 |

## 语言策略

Java + Kotlin 混合，详见 [ADR 0005](docs/adr/0005-mixed-java-kotlin.md)。
- 业务核心 / UI / Service：默认 Kotlin
- POJO / 第三方 SDK 桥接 / 纯算法：Java 可选
- 同一类内不混合两种语言

## 关键决策（Accepted）

| ADR | 主题 |
|---|---|
| [0001](docs/adr/0001-control-path-accessibility.md) | 控制路径：AccessibilityService 主路径 |
| [0002](docs/adr/0002-llm-openai-compat.md) | 默认 LLM 接入：OpenAI 兼容协议 |
| [0003](docs/adr/0003-skill-dsl-yaml.md) | Skill 形态：声明式 DSL (YAML) + 录制 |
| [0004](docs/adr/0004-asr-system-default.md) | 语音识别：Android SpeechRecognizer 起步 |
| [0005](docs/adr/0005-mixed-java-kotlin.md) | 语言策略：Java + Kotlin 混合 |

## 版本路线图

```
V0.1 骨架 → V0.2 LLM → V0.3 输入 → V0.4 录制 → V0.5 习惯
→ V0.6 五大场景 → V0.7 守卫 → V0.8 本地兜底 → V0.9 合规 → V1.0 RC
```

详见 [`docs/06-roadmap/roadmap.md`](docs/06-roadmap/roadmap.md)。

## 测试

V0.1 已建立三层测试架构（仪器测试推迟到 V0.6+）。

### 一键 CI（推荐）

```bash
./scripts/ci.sh             # build + unit + e2e 全跑
./scripts/ci.sh unit        # 只跑单元测试
./scripts/ci.sh e2e         # 只跑 e2e（需要 emulator-5554 在线）
SKIP_BUILD=1 ./scripts/ci.sh   # 跳过 gradle 重新打包
```

V0.1 实测：23 个单元测试 + 2 个 e2e 全过。

### 单元测试（纯 JVM，秒级）

```bash
./gradlew :core:test :skill:test :llm:test
```

覆盖 IntentRouter、Skill DSL parser、SkillRunner 解释器（用 FakeActionContext mock 出 a11y 行为）。

### E2E（emulator-5554 在线）

```bash
./scripts/e2e-stub-video.sh    # 浮窗 chip "看三体" → stub-video PlayerActivity (~1.1s)
./scripts/e2e-settings.sh      # 浮窗 chip "打开网络" → Settings/SubSettings (~1.3s)
```

每次跑会在 `scripts/test-artifacts/e2e-{name}-YYYYMMDD-HHMMSS/` 归档：

- `01-*.png` — 最终焦点截图
- `logcat.txt` — AgentApp/AgentA11y/FloatingBubble 关键日志

E2E 脚本通过 `am broadcast com.taomic.agent.RUN_SKILL` 触发，与浮窗 chip 走同一条业务路径，但不依赖坐标 tap，CI 稳定。

### 仪器测试（V0.6+）

UiAutomator 仪器测试在 V0.6（场景多了真值得写）才加。当前 V0.1 的 a11y 原语稳定性靠 e2e 端到端覆盖。

测试策略详见 [`docs/05-test/test-strategy.md`](docs/05-test/test-strategy.md)。
V0.1 release notes 见 [`docs/06-roadmap/release-notes-v0.1.md`](docs/06-roadmap/release-notes-v0.1.md)。

## 协作约定

- 每完成一个 V0.x 输出 release-notes / Demo 录屏 / 文档变更清单
- 任何方案级 ADR 改动先开 issue 讨论再落地
- 破坏性操作必须显式确认
- Token / 费用 / 隐私层面默认值变更先告知再执行

## License

待定（V0.9 合规阶段确认）。
