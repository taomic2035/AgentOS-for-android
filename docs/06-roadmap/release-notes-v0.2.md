# V0.2 Release Notes

> **状态**：✅ 验收通过（2026-04-27）
> **GitHub**：[`taomic2035/AgentOS-for-android`](https://github.com/taomic2035/AgentOS-for-android)

## 一句话

V0.2 接入了 LLM 智能层：用户输入任意自然语言（如"今天天气怎么样"），先走关键词命中（零 token），未命中则由 LLM 通过 tool_call 选择 Skill 执行。浮窗设置页新增"测试连接"按钮，可验证 LLM 配置可用性。

## 关键能力

| 能力 | 实现 | Verified |
|---|---|---|
| LLM 客户端（OpenAI 兼容协议） | `OpenAiCompatClient`（OkHttp + `/v1/chat/completions` + tools/function_calling） | ✅ |
| LLM 意图路由 | `LlmIntentRouter`：LLM tool_call → RouteResult.Hit | ✅ |
| 链式路由 | `ChainedIntentRouter`（Keyword → LLM 兜底） | ✅ |
| IntentRouter suspend 化 | `suspend fun route()` 支持异步 LLM 调用 | ✅ |
| Skill Recovery | `fallback_to_llm` / `prompt_user` / `retry` / `abort` 策略框架 | ✅ |
| A11yController 扩充 | `findNode/clickNode/inputText/activeWindowPackage/dumpScreen` | ✅ |
| 屏幕语义摘要 | `dumpScreen()` 输出精简节点树文本供 LLM 消费 | ✅ |
| AgentOrchestrator | 从 AgentApp 拆出 routing + execution + recovery | ✅ |
| LLM 连接测试 | 设置页"测试连接"按钮，验证 url/key/model 可用 | ✅ |
| 动态路由切换 | LLM 配置变更后自动 rebuild ChainedRouter | ✅ |

## 测试

| 层 | 数量 | 通过率 |
|---|---|---|
| Unit (`:core`/`:skill`/`:llm`) | **29** (新增 6: LlmClient 9 + LlmIntentRouter 5 + ChainedRouter 3 - 共享 3) | 29/29 |
| E2E | 2 | 2/2（回归通过） |
| Instrumented | 0 | 推迟到 V0.6 |

## 架构变更

### 新增

- `core/llm/LlmClient.kt` — LLM 客户端契约（`chat`/`chatWithTools`/`appendToolResult`/`ping`）
- `core/llm/ChatMessage/ChatRole/ToolDefinition/ToolCall/ChatCompletion/Usage` — 数据模型
- `core/intent/LlmIntentRouter.kt` — LLM 驱动意图路由
- `core/intent/ChainedIntentRouter.kt` — 链式路由
- `core/intent/SkillDescriptor.kt` — Skill 摘要信息
- `llm/OpenAiCompatClient.kt` — OkHttp 实现
- `app/orchestrator/AgentOrchestrator.kt` — 编排核心

### 重构

- `IntentRouter.route()` → `suspend fun route()`
- `AgentApp` 职责拆分：routing/execution/recovery 移至 `AgentOrchestrator`
- `A11yController` 接口扩充：从仅 `isReady` 扩展为完整 a11y 契约
- `A11yActionContext` 通过 `A11yController` 接口调用，减少 `instance()` 直接依赖

### 依赖变更

- `:core` 新增 `kotlinx-coroutines-test`（testImplementation）
- `:llm` 新增 `okhttp-mockwebserver` + `kotlinx-coroutines-test`（testImplementation）
- `:app` 新增 `kotlin-serialization` 插件 + `kotlinx-serialization-json`
- `gradle.properties` 添加 `-Djava.net.useSystemProxies=false`

## 已知限制

| 项 | 解决版本 |
|---|---|
| LLM 流式响应 | V0.3（与文字输入面板一起做） |
| 独立 SettingsActivity | V0.3 |
| Skill 条件/分支能力 | V0.4 |
| Recovery `fallback_to_llm` 完整实现 | V0.3（需 UI 反馈通道） |
| API key 加密存储 | V0.9 |

## 下一步（V0.3）

- 语音 + 文字双输入闭环
- SpeechRecognizer 集成
- 流式 LLM 响应
- 独立设置页 Activity

详见 `docs/06-roadmap/roadmap.md`。
