# 流程：意图理解

## 触发

用户在浮窗输入文字 / 完成语音识别。

## 步骤

1. UI 收到完整输入串 → 发往 AgentCore
2. **IntentRouter**：
   - 取最近 5 个 habit_events 做上下文
   - 用 embedding 检索 skill 候选 top-3（V0.5）
   - 阈值（cos > 0.78）+ 参数完整性 → 命中
3. 命中：
   - 抽取参数（用 LLM 小调用，~50 token）或规则
   - 直接走 `flow-skill-execute`
4. 不命中：
   - 走 **Planner**
   - 构造 system prompt（含 tools + 偏好摘要 + 屏幕语义）
   - 调 LLM
   - 返回 tool_calls 或文字答复
5. 解析 tool_calls：
   - `run_skill` → SkillResolver → `flow-skill-execute`
   - `launch_app` → 直接 Intent
   - `answer_user` → UI 显示文字
   - `ask_user` → UI 反问，等用户输入下一轮

## 上下文

- 屏幕语义摘要（< 500 token）
- 偏好摘要（top 5 相关，< 200 token）
- 最近 6 轮对话
- 工具列表（按场景裁剪后 < 500 token）

## 错误

- LLM 网络错误 → 退避重试 3 次 → 失败提示
- LLM 返回非 JSON → 重试 1 次 → 提示
- 超时 30s → 取消并提示

## 验收

- "我想看三体" 命中 `tencent_video_play`
- "今天天气怎么样" 命中 `search_weather` 或 LLM 兜底
- 完整 trace（包括是否命中、token 消耗）写入 habit_events
