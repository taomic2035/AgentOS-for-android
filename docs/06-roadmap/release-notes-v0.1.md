# V0.1 Release Notes

> **状态**：✅ 验收通过（2026-04-26）
> **GitHub**：[`taomic2035/AgentOS-for-android`](https://github.com/taomic2035/AgentOS-for-android)

## 一句话

V0.1 把 AgentOS 的"骨架闭环"跑通了：用户在浮窗里点 chip "看三体" → 自动拉起 stub-video → 自动搜索 → 自动选剧 → 进播放页，**端到端 1.1 秒**，全程零键盘交互。这是产品愿景"我想看三体 → 自动播放"的最小可演示版本。

## 关键能力

| 能力 | 实现 | Verified |
|---|---|---|
| 浮窗呼出 | `FloatingBubble`（WindowManager + Compose） | ✅ |
| 浮窗拖拽 + 吸附 | Compose `pointerInput` + 屏幕中线吸附算法 | ✅ |
| 浮窗 collapsed ↔ expanded 卡片 | Compose remember + 状态切换 | ✅ |
| AccessibilityService 控制其他 App | `AgentAccessibilityService` + 节点查找 + click/inputText | ✅ |
| Skill DSL (YAML) | kaml + sealed `Step`：launch_app / wait_node / click_node / input_text / press_key / sleep | ✅ |
| Skill 执行引擎 | `DefaultSkillRunner`：模板替换 + 顺序执行 + 错误优雅退出 | ✅ |
| 关键词意图路由 | `KeywordIntentRouter`（V0.2 LLM 雏形：先关键词，未中再 LLM） | ✅ |
| 前台服务常驻 | `AgentForegroundService` (FOREGROUND_SERVICE_TYPE_SPECIAL_USE, START_STICKY) | ✅ |
| 三步引导页 | OVERLAY / A11y / LLM 配置 + 主"启动助手"按钮 | ✅ |
| Stub Video App（e2e 兜底） | `tools/stub-video/` Android Application | ✅ |
| 一键 CI（unit + e2e） | `scripts/ci.sh` | ✅ |

## 端到端实测数据（emulator-5554, pixel_6_arm64, API 35）

| Skill | 步骤 | 端到端耗时 | 最终焦点 |
|---|---|---|---|
| `settings_open_internet` | 4/4 | 1294 ms | Settings/SubSettings (Network & internet) |
| `stub_video_play` (title=三体) | 6/6 | 1154 ms | stubvideo/PlayerActivity |

## 测试

| 层 | 数量 | 通过率 |
|---|---|---|
| Unit (`:core`/`:skill`/`:llm`) | **23** | 23/23 |
| E2E (`scripts/e2e-*.sh`) | 2 | 2/2 |
| Instrumented (UiAutomator) | 0 | 推迟到 V0.6（场景多了再写） |

## 模块结构

```
app/         应用入口、AgentApp 装配、ForegroundService、引导页
core/        业务核心（无 Android 依赖）：ActionContext、IntentRouter、A11yController 接口
a11y/        AccessibilityService 实现 + A11yActionContext
skill/       DSL 解析 (kaml) + SkillRunner + 内置 yaml
llm/         LLM 适配器（V0.2 实装；V0.1 仅默认值常量）
data/        Room 占位（V0.4 启用）
uikit/       FloatingBubble + BubbleContent
tools/stub-video/  e2e 兜底假视频 App
docs/        愿景 / PRD / 架构 / 设计 / 流程 / 测试 / 路线图 / ADR
scripts/     ci.sh + e2e-*.sh + lib/
```

## V0.1 完成的 Task

按调整后的"风险前置垂直切片"顺序：

| Task | 内容 |
|---|---|
| T-000a | 模拟器 + V0.1 APK 烟雾测试 |
| T-000b/006a | 最小 AccessibilityService + 系统 App 验证（合并） |
| T-000c | 自建 stub-video App |
| T-007a/b/c/d | DSL 类型 + ActionContext 契约 + SkillRunner + a11y 装配 |
| T-006b | A11y 完整原语集（合并入 T-007d） |
| T-005a/b/c | 浮窗壳 + 输入卡片 + 关键词路由 |
| T-004 | AgentForegroundService（前台服务常驻） |
| T-008 | E2E shell 脚本 |
| T-003 | 完整三步引导页 |
| T-009 | CI 测试基线（ci.sh） |

## 重要工程决策（已固化为 ADR）

- **0001** 控制路径 = AccessibilityService 主路径
- **0002** 默认 LLM = OpenAI 兼容协议（火山引擎方舟 / Doubao）
- **0003** Skill DSL = YAML + 录制
- **0004** ASR = Android SpeechRecognizer 起步（V0.3 接入）
- **0005** 语言策略 = Java + Kotlin 混合

## 已知限制（V0.2-V0.7 解决）

| 项 | 解决版本 |
|---|---|
| Compose ComposeView 不向 setOnTouchListener 冒泡 touch | 已绕过用 `pointerInput`（永久方案） |
| 浮窗输入框 + 软键盘（IME）需要 flag toggle | V0.3（与 ASR 一起做） |
| LLM 真实接入 | V0.2 |
| 用户录制新 Skill | V0.4 |
| 偏好抽取 / Skill 命中节省 token | V0.5 |
| 五大场景（视频 / 购物 / 外卖 / 打车 / 搜索）真 App skill | V0.6 |
| 不可逆操作 RiskGuard | V0.7 |
| `api_key` 加密存储（KeyStore + EncryptedSharedPreferences） | V0.9 |
| 国内厂商白名单（MIUI / HarmonyOS / EMUI 自启动 / 关联启动） | V0.9 |
| `force-stop` 后 a11y service 自动重连 | V0.9（注释提示用户 toggle） |

## 调试心得（写给将来）

- **adb 自动化点 Compose Button 用坐标 input tap**（不要用 a11y CLICK_BY_TEXT — Compose mergeDescendants 会把 Text 子节点合并到父节点）
- **launchApp 必加 `FLAG_ACTIVITY_CLEAR_TASK`**，否则之前的 Activity 栈会带回错误的页面节点
- **a11y service 在 force-stop 后失活**，CI / e2e 脚本必须 `accessibility_enabled 0→1` toggle
- **Android ICU regex 比 JVM 严格**，KDoc 嵌套注释 `/skills/*.yaml` 会让 `:skill:compileKotlin` 失败 — 写文档示例避开 `*` 紧跟 `/` 的组合
- **WindowManager overlay 用 `applicationContext`** 拿到的 WM，浮窗才能脱离 Activity 生命周期独立存在

## 下一步（V0.2）

- LLM 真实接入：OpenAI 兼容 client（OkHttp）+ tools 协议 + 流式响应
- `LlmIntentRouter` + ChainedRouter（关键词 → LLM 兜底）
- 设置页扩展为独立 Activity（与引导页解耦）
- 第一个 LLM 驱动场景："今天天气怎么样"

详见 `docs/06-roadmap/roadmap.md`。

## 认证签名 / 商店

V0.1 仅 debug build。release 签名 + 商店审核材料在 V0.9 阶段准备。

---

*归档艺术品（截图 + logcat）：`scripts/test-artifacts/`*
