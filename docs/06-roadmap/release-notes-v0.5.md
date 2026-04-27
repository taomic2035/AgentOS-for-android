# V0.5 Release Notes — 习惯/记忆

**验收通过** 2026-04-27

## 目标

同一意图二次出现走 Skill 缓存命中，减少 LLM 调用；补齐 V0.4 遗留的录制审阅页。

## 交付清单

### T-501 录制审阅页（V0.4 遗留）
- 浮窗录制完成后显示步骤审阅界面
- 步骤列表展示（文本描述）
- Skill ID / 名称输入框
- 保存 / 丢弃按钮
- `FloatingBubble.updateRecordedSteps()` / `clearRecordedSteps()` 状态管理
- `AgentApp.stopRecording()` 自动将 Step 转为文本描述传给浮窗

### T-502 HabitEvent 数据模型 + Room 表
- Room 数据库 `AgentDatabase`（:data 模块）
- `habit_events` 表：id / timestamp / intent_text / skill_id / result / duration_ms / token_count / screen_summary
- `preference` 表：key / value / confidence / updated_at
- `HabitDao`：insertEvent / getRecentEvents / getEventsSince / getSkillUsageStats / upsertPreference / getAllPreferences
- `HabitRepository`：封装 DAO，提供 recordEvent / upsertPreference / clearAll
- :data 模块添加 KSP + Room 依赖，删除旧 DataPlaceholder

### T-503 HabitEvent 记录
- `AgentOrchestrator.executeSkill()` 完成后自动写入 HabitEvent
- 新增 `habitRepository` 构造参数
- AgentApp.onCreate() 中创建 HabitRepository 并传入 orchestrator

### T-505 Skill 缓存命中（HabitCacheRouter）
- `HabitCacheRouter`（:core 模块）实现 IntentRouter 接口
- 三级匹配策略：
  1. 精确匹配：历史意图文本完全一致
  2. 关键词匹配：输入包含 Skill 名称
  3. bigram 重叠匹配：中文友好分词，>=2 个 bigram 重叠即命中
- 多候选时按频率/重叠度排序
- `HabitEventEntry` 数据类解耦 :core 与 :data
- 路由链升级：Keyword → HabitCache → LLM（无 LLM 时 Keyword → HabitCache）
- 单元测试 5/5 通过

### T-506 集成测试
- 全量单元测试通过：core / skill / llm / app / data
- Debug APK 构建成功
- HabitCacheRouterTest：5/5
- SkillStoreTest：6/6
- SkillRecorderTest：6/6

## 技术决策

| 决策 | 选择 | 原因 |
|---|---|---|
| HabitCacheRouter 位置 | :core 模块 | 实现 IntentRouter 接口，与 KeywordIntentRouter 同层 |
| :core 与 :data 解耦 | HabitEventEntry 数据类 + 函数注入 | :core 不能依赖 :data（架构反方向） |
| 中文分词 | 字符 bigram + 空格分词混合 | 无需引入 NLP 库，2-gram 覆盖中文短句匹配 |
| 偏好抽取 | 推迟到 V0.6+ | 需要足够 habit_events 数据才有意义，当前频率匹配已够用 |
| Room 版本 | 2.6.1 + KSP | 最新稳定版，KSP 替代 kapt 提升编译速度 |

## 已知限制

- 偏好抽取（LLM 小批量调用）未实现，推迟到 V0.6+
- HabitCacheRouter 仅基于文本匹配，非语义 embedding（V0.8 集成本地模型后替换）
- Room 迁移策略未实现（V1.0 前无需）

## 下一步 V0.6

- 五大场景内置 Skill 包（视频 / 购物 / 外卖 / 打车 / 搜索）
- 场景 E2E 测试
- 偏好抽取（数据足够后）
