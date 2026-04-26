# ADR 0003 — Skill 形态采用声明式 DSL (YAML) + AccessibilityEvent 录制

- **状态**：Accepted
- **日期**：2026-04-26
- **决策人**：铲屎官 + Claude

## 背景

用户场景常重复（点外卖、打车、看视频、购物），将其录成"固定步骤"可大幅降低 LLM token 消耗。Skill 的存储与执行形态候选：

1. 声明式 DSL（YAML/JSON）+ AccessibilityEvent 录制（语义节点）
2. Intent / Deep Link 优先 + DSL 兜底
3. 纯坐标录制
4. 基于 LLM 的动态 plan + 缓存

## 决策

**主形态：声明式 DSL（YAML）+ 节点级录制**；Deep Link 作为优先级最高的 step 类型嵌入 DSL。

## 理由

- DSL 可读、可编辑、可版本化、跨设备稳定
- 节点级录制（resource-id / text / class_name / desc）抵抗 UI 改版能力远强于纯坐标
- YAML 适合嵌套步骤；后续若需更高表达力可平滑过渡到 Lua / KotlinScript（保留扩展点）
- AccessibilityEvent 流可作为"录制"的天然来源

## 取舍

- ⚠️ DSL 需迭代（首版可能漏某些原语，预留 `custom` 扩展槽）
- ⚠️ 用户录制时的"无关步骤"需自动剔除（heuristic + 用户审阅）
- ✅ 失败诊断容易：DSL 步骤可单步重放
- ✅ token 节省路径清晰：Skill 命中即可绕过 LLM Planner

## 影响

- `skill` 模块以 YAML schema 为契约，所有内部表示从 YAML 还原
- 录制器（V0.4）输出的中间数据必须能反序列化回 DSL
- 若 V0.6 出现复杂分支逻辑，启动条件变量与 if/else 扩展评审

## DSL 草稿

```yaml
id: tencent_video_play
name: 在腾讯视频播放指定剧
version: 1
inputs:
  - { name: title, type: string, required: true }
steps:
  - { action: launch_app, package: com.tencent.qqlive }
  - { action: wait_node, text: 搜索, timeout_ms: 3000 }
  - { action: click_node, text: 搜索 }
  - { action: input_text, target: { class_name: android.widget.EditText }, text: ${title} }
  - { action: press_key, key: ENTER }
  - { action: wait_node, contains_text: ${title}, timeout_ms: 5000 }
  - { action: click_node, contains_text: ${title}, index: 0 }
recovery:
  on_node_not_found: fallback_to_llm
```

详细规范见 `docs/03-design/skill-dsl-spec.md`。
