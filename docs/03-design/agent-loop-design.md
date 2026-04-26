# Agent 主循环设计

## 一次完整调用

```
User Input
   │
   ▼
[1] IntentRouter
   │ └─ 缓存命中? ──yes──> [4]
   ▼ no
[2] Planner (LLM)
   │ ├─ 构造 system prompt（含可用 tools 列表 + 用户偏好摘要）
   │ ├─ 构造 user message（用户原话 + 屏幕摘要）
   │ ├─ 调 LLM，期待返回 tool_calls
   │ └─ 没工具调用 → 直接返回文字答复
   ▼
[3] SkillResolver
   │ └─ 把 tool_calls 翻译成 SkillSpec 序列
   ▼
[4] ActionExecutor (顺序执行)
   │ ├─ 每步前 RiskGuard 检查
   │ ├─ 调用 A11yController 完成节点级动作
   │ ├─ 失败 → recovery 策略（重试 / 回退 LLM / 提示用户）
   │ └─ 完成后写 habit_events
   ▼
Result → UI
```

## Tools 协议

LLM 看到的 tools 列表（OpenAI 兼容）：

```json
[
  {
    "type": "function",
    "function": {
      "name": "run_skill",
      "description": "Run a recorded skill by id",
      "parameters": {
        "type": "object",
        "properties": {
          "skill_id": {"type": "string"},
          "inputs": {"type": "object"}
        },
        "required": ["skill_id"]
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "launch_app",
      "description": "Launch an Android app by package name",
      "parameters": {"type": "object", "properties": {"package": {"type": "string"}}}
    }
  },
  {
    "type": "function",
    "function": {
      "name": "answer_user",
      "description": "Reply to user without taking action",
      "parameters": {"type": "object", "properties": {"text": {"type": "string"}}}
    }
  },
  {
    "type": "function",
    "function": {
      "name": "ask_user",
      "description": "Ask user for clarification",
      "parameters": {"type": "object", "properties": {"question": {"type": "string"}}}
    }
  }
]
```

V0.6 起追加：
- `record_skill`（用户说"记下来"）
- `update_preference`
- `confirm_with_user`（用于不可逆操作）

## 上下文压缩策略

- 屏幕快照转语义摘要（节点树 → key path 序列），平均 < 500 token
- 历史对话保留最近 6 条
- 偏好摘要（top 5 最相关）每次重新提取
- 上下文 token > 4096 触发压缩

## 重试与降级

| 失败类型 | 策略 |
|---|---|
| LLM 网络错误 | 退避重试 3 次，仍失败 → 降级本地小模型（V0.8） / 回退到上次成功 plan |
| Skill 步骤节点找不到 | recovery: `fallback_to_llm` 或 `prompt_user` |
| LLM 返回非 JSON | 重试一次，仍失败 → 提示用户 |
| 超时 | 取消整个协程链，UI 提示 |

## 性能目标

- Skill 命中场景：< 3s
- LLM 规划场景：< 8s（不含网络）

## 验收

- IntentRouter 命中率埋点（目标 V1.0 ≥ 60%）
- 平均 token 消耗（目标 V1.0 比 V0.5 下降 ≥ 50%）
