# 测试矩阵

## 模块 × 用例

### core

- IntentRouter
  - 命中已存 skill：返回正确 SkillSpec
  - 不命中：返回 null（走 LLM）
  - 阈值边界（cos = 0.78）
- Planner
  - tool_calls 解析
  - 文字答复解析
  - LLM 错误抛出 AgentError
- ActionExecutor
  - 串行执行
  - 失败回调 recovery
  - 用户取消立即停止

### skill

- DSL parse
  - 合法 YAML → SkillSpec
  - 缺必填 → 抛错
  - 未知 step type → 抛错
  - 输入参数引用未声明 → 抛错
- SkillRunner
  - 顺序执行 step
  - 变量替换
  - 超时触发 recovery

### llm

- OpenAiCompatClient
  - 正常请求 / 响应
  - tools 协议序列化
  - 5xx 重试
  - 超时取消
  - 流式响应

### a11y（Instrumentation）

- findByText / findByResId
- clickNode / longClickNode
- inputText / scroll / press_key
- 节点 recycle

### data（Instrumentation）

- Room migration
- SkillStore CRUD
- HabitStore 写入 / 查询
- PreferenceStore 加密
- 数据清空

### uikit（Compose Test）

- 浮窗拖拽
- 输入面板状态
- Skill 卡片渲染

### app（E2E）

- 冷启动引导流
- 浮窗呼出
- 完整"三体"场景
- LLM 兜底场景
- 不可逆守卫

## 端到端场景矩阵（V0.6 起）

| 场景 | 触发语 | 期待路径 | 验收 |
|---|---|---|---|
| 视频 | 我想看三体 | tencent_video_play | 拉起腾讯视频 + 搜索 |
| 购物 | 买一件刘亦菲同款 | jd_search_buy | 京东商品页停在付款 |
| 外卖 | 中午老地方那家 | meituan_reorder | 进店 + 加购 + 停付款 |
| 打车 | 回家 | didi_call | 滴滴叫车确认页 |
| 搜索 | 查天气 | search_weather | 搜索结果页 |

## 失败注入

- mock 网络断
- mock LLM 5xx
- mock 节点不存在
- mock 用户取消
- mock 屏幕变化（付款弹窗）

每种都断言：不崩溃、有清晰提示、日志可追溯。
