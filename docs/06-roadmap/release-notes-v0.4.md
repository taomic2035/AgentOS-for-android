# V0.4 Release Notes — Skill 录制

**验收通过** 2026-04-27

## 目标

用户能录制新 Skill 并复用：录制 A11y 操作 → 后处理为 Step 序列 → 序列化为 YAML 持久化 → 下次启动自动加载。

## 交付清单

### T-401 A11y 事件录制器
- `SkillRecorder`（`:app` 模块）接收 AccessibilityEvent，映射为 Step 序列
  - WINDOW_STATE_CHANGED → LaunchApp
  - VIEW_CLICKED → ClickNode
  - VIEW_TEXT_CHANGED → InputText
  - VIEW_FOCUSED + EditText → WaitNode
- 后处理：去重、剔除系统 UI、合并连续同 target InputText
- `AgentAccessibilityService.eventCallback` 接线：录制开始时设置，停止时清除

### T-402 Skill 序列化 + 文件存储
- `SkillStore`：`filesDir/skills/<id>.yaml` 文件存储
  - save / load / listAll / delete / exists
  - 复用 `SkillParser.encode()` 序列化、`SkillParser.parse()` 反序列化
  - 支持注入目录路径（便于单元测试）
- 单元测试：6/6 通过（临时目录方案，无 Android 依赖）

### T-403 Skill 动态加载
- `AgentOrchestrator.loadUserSkills()`：启动时从 SkillStore 加载用户录制的 Skill
- `AgentOrchestrator.saveRecordedSkill()`：录制结果 → SkillSpec → 注册 + 持久化
- `AgentOrchestrator.deleteUserSkill()`：从内存 + 文件同时删除
- `AgentApp.onCreate()` 中自动调用 `loadUserSkills()`

### T-404 录制模式 UI
- `AgentState.RECORDING` 新状态
- 浮窗 CollapsedBubble：录制时红色背景 + "●" 标识
- 浮窗 ExpandedCard：
  - 非录制态：新增「录制」AssistChip
  - 录制态：显示「停止录制」+「取消」按钮
- `FloatingBubble` 新增 `onStartRecording` / `onStopRecording` / `onCancelRecording` 回调
- `AgentApp` 中接线回调到 orchestrator

### T-405 设置页 Skill 管理
- SettingsActivity 新增「Skill 管理」卡片
  - 显示已注册 Skill 列表（名称 + 步数 + 描述）
  - 用户录制的 Skill 可删除
  - 刷新按钮
- 版本号更新为 V0.4

### T-406 集成测试
- 全量单元测试通过：core / skill / llm / app（含新增 SkillStoreTest + SkillRecorderTest）
- Debug APK 构建成功
- app `build.gradle.kts` 启用 `unitTests.isReturnDefaultValues = true`

## 技术决策

| 决策 | 选择 | 原因 |
|---|---|---|
| SkillRecorder 位置 | `:app` 模块 | 需要同时引用 `:a11y`（AccessibilityEvent）和 `:skill`（Step/SkillSpec），:app 是唯一同时依赖两者的模块 |
| eventCallback 模式 | AgentAccessibilityService 暴露 eventCallback | 解耦 a11y 与 skill 模块，a11y 不依赖 skill |
| SkillStore 存储格式 | YAML（复用 SkillParser） | 与内置 Skill 格式一致，便于调试 |
| 测试方案 | 临时目录注入 | 避免 Robolectric 依赖，纯 JVM 测试 |

## 已知限制

- 录制步骤审阅页（编辑/重排/参数化）尚未实现，V0.5 补齐
- 录制后 Skill 的 id/name 需要调用方指定（目前为占位），V0.5 通过 UI 输入
- AccessibilityEvent.obtain()/recycle() 在 Android 13+ 已自动管理，但仍标记 deprecated

## 下一步 V0.5

- 录制审阅页：步骤编辑、参数化、重排
- 习惯/记忆：HabitStore + embedding 检索
- 偏好抽取
