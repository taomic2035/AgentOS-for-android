# 模块设计

## 总则

- 每个模块单一职责
- 模块间通过 `core` 中的接口通信
- 业务核心（`core`）与 Android 解耦，便于单测

## app 模块

**职责**

- 应用入口（MainActivity = 首屏引导）
- Service 注册与启动
- 主题 / 资源 / 国际化
- 设置面板（Compose）

**关键类**

- `MainActivity`（引导：开 OVERLAY / 开 A11y / 设置 LLM）
- `AgentOSApplication`（DI 容器初始化）
- `SettingsActivity`（V0.2）

**依赖**：所有模块

## core 模块

**职责**

- 业务核心算法与编排
- 模块间接口契约

**关键类**

- `AgentCore`（单例入口）
- `IntentRouter`（先 Skill 命中，再 LLM）
- `Planner`（LLM tool-use 编排）
- `SkillResolver`（高级意图 → 具体 Skill）
- `ActionExecutor`（统一动作出口）
- `A11yController`（接口，由 a11y 模块实现）
- `LlmClient`（接口，由 llm 模块实现）
- `SkillStore` / `HabitStore` / `PreferenceStore`（接口，由 data 模块实现）

**依赖**：仅 kotlinx 标准库 / 序列化 / 协程

## a11y 模块

**职责**

- AccessibilityService 实现
- 屏幕语义快照
- 节点查找与操作原语
- AccessibilityEvent 订阅（用于 Skill 录制）

**关键类**

- `AgentAccessibilityService`
- `A11yControllerImpl`（实现 core.A11yController）
- `NodeQuery` / `ClickStrategy` / `InputStrategy`
- `EventRecorder`（V0.4）

## skill 模块

**职责**

- DSL 模型 / 解析 / 验证
- 解释执行（步骤分发到 ActionExecutor）
- 内置 skill 包加载（`resources/skills/*.yaml`）

**关键类**

- `SkillSpec`（DSL data class）
- `YamlSkillParser`
- `SkillRunner`
- `BuiltinSkills`

## llm 模块

**职责**

- LLM 客户端实现
- 协议适配
- 重试 / 超时 / 流式

**关键类**

- `LlmClient`（接口，定义在 core）
- `OpenAiCompatClient`
- `AnthropicClient`（V0.x）
- `LocalLlamaClient`（V0.8）

## data 模块

**职责**

- Room 数据库
- 各 Store 实现（Skill / Habit / Preference / ChatHistory / RuntimeLogs）
- 加密存储（API key 走 KeyStore，DB 走 SQLCipher）

**关键类**

- `AppDatabase`
- `SkillDao` / `HabitDao` / ...
- `EncryptedKeyStore`

## uikit 模块

**职责**

- 浮窗（FloatingBubble）
- Compose 组件库（输入面板、设置项、Skill 卡片等）

**关键类**

- `FloatingBubble`（WindowManager + ComposeView）
- `InputPanel`
- `AgentTheme`

## 跨模块通信

- 同进程：直接调用接口
- Service ↔ UI：StateFlow / LiveData
- Service 内部：Coroutines + Channel
