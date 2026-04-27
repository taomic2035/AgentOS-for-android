# V0.3 Release Notes

> **状态**：✅ 验收通过（2026-04-27）
> **GitHub**：[`taomic2035/AgentOS-for-android`](https://github.com/taomic2035/AgentOS-for-android)

## 一句话

V0.3 补齐了输入闭环：浮窗内嵌文字输入框 + 发送按钮 + IME 适配 + 麦克风按钮（SpeechRecognizer）+ LLM 流式响应（SSE）+ 输入状态机 UI + 独立设置页。用户现在可以在浮窗里打字或说话，AgentOS 实时反馈思考/执行/完成状态。

## 关键能力

| 能力 | 实现 | Verified |
|---|---|---|
| 浮窗文字输入框 | OutlinedTextField + 发送按钮 + IME flag 动态切换 | ✅ |
| 麦克风按钮 | SpeechRecognizerHelper + 麦克风图标按钮 | ✅ |
| LLM 流式响应 | `chatStream()` + SSE `stream: true` + `onToken` 回调 | ✅ |
| 输入状态机 UI | IDLE → THINKING → EXECUTING → DONE/ERROR | ✅ |
| 独立 SettingsActivity | LLM 配置 + 连接测试 + 关于 | ✅ |
| Recovery prompt_user | ERROR 状态展示"出错了，请重试"，用户可重新输入 | ✅ |
| 动态 IME 适配 | 展开输入框时 `FLAG_NOT_TOUCH_MODAL`，收起时恢复 `FLAG_NOT_FOCUSABLE` | ✅ |

## 测试

| 层 | 数量 | 通过率 |
|---|---|---|
| Unit (`:core`/`:skill`/`:llm`) | **29** | 29/29 |
| E2E | 2 | 2/2（回归通过） |
| Instrumented | 0 | 推迟到 V0.6 |

## 架构变更

### 新增

- `uikit/speech/SpeechRecognizerHelper.kt` — SpeechRecognizer 封装
- `uikit/floating/BubbleContent.AgentState` — Agent 执行状态枚举
- `core/llm/StreamCallback` — 流式回调接口
- `ui/SettingsActivity` — 独立设置页

### 重构

- `BubbleContent` 新增输入框 + 发送按钮 + 麦克风按钮 + 状态提示
- `FloatingBubble` 新增 `updateFocusable()` + `updateState()` + SpeechRecognizer 生命周期
- `AgentOrchestrator` 新增 `OrchestratorState` 枚举 + `onStateChanged` 回调
- `LlmClient` 新增 `chatStream()` 方法
- `OpenAiCompatClient` 实现 SSE 流式解析
- `MainActivity` 精简引导文案 + 新增"设置"入口按钮
- `AndroidManifest` 注册 `SettingsActivity` + `RECORD_AUDIO` 权限

### 依赖变更

- `:app` 新增 `kotlin-serialization` 插件 + `kotlinx-serialization-json`

## 已知限制

| 项 | 解决版本 |
|---|---|
| SpeechRecognizer 依赖 Google 服务 | V0.8 本地 ASR (sherpa-onnx) 兜底 |
| 流式响应仅文本，不支持流式 tool_call | V0.4+ |
| 设置页仅 LLM 配置，无 Skill 管理 | V0.4 录制器 |
| API key 加密存储 | V0.9 |

## 下一步（V0.4）

- Skill 录制：A11y 事件录制器 + DSL 序列化 + Skill 编辑页
- Skill 条件/分支能力
- 设置页 Skill 管理

详见 `docs/06-roadmap/roadmap.md`。
