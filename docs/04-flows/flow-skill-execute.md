# 流程：Skill 执行

## 触发

- IntentRouter 命中
- Planner 返回 `run_skill` tool_call
- 用户在 Skill 列表页点击"运行"

## 步骤

1. SkillRunner 加载 `SkillSpec`
2. 校验 inputs（缺必填 → 提示用户补填）
3. **逐步执行**：
   - 每步前 RiskGuard 检查（关键词 / 屏幕变化）
   - 调用 ActionExecutor → A11yController → 系统执行
   - 步骤超时 → recovery
   - 步骤失败 → recovery
4. 全部成功 → 写 habit_events（outcome=success）
5. 中途失败：
   - `fallback_to_llm`：把当前屏幕状态 + 剩余步骤交给 LLM 续航
   - `prompt_user`：UI 提示"卡在 XX 步骤，需要你帮一把"
   - `abort`：直接终止

## 守卫点

- 任何 step 在执行前都过 RiskGuard
- 命中守卫 → 暂停 → 弹确认 UI → 用户决定继续 / 取消

## 取消

- 用户点浮窗"停止" → 取消整条协程
- 全场景 60s 超时

## 日志

- 每步：started / finished / latency
- 失败：含期望节点 / 实际节点树摘要
- 全程截图（V0.7 起，可选）

## 验收

- V0.1：硬编码"三体"场景全程成功率 ≥ 90%
- V0.6：5 大场景每个 ≥ 80%
- 任何失败都有可追溯日志
