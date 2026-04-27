# AgentOS 文档索引

按"愿景 → 需求 → 架构 → 设计 → 流程 → 测试 → 路线图"顺序阅读，可在 30 分钟内对项目形成完整心智模型。

## 00. 愿景

- [产品愿景](00-vision/product-vision.md) — 一句话定义 / 北极星指标 / 道德边界

## 01. 需求 (PRD)

- [总览](01-prd/prd-overview.md)
- [功能清单](01-prd/prd-feature-list.md) — 每行一项可独立验收的功能
- [五大场景脚本](01-prd/prd-scenarios.md) — 视频 / 购物 / 外卖 / 打车 / 搜索
- [非功能需求](01-prd/prd-non-functional.md) — 性能 / 隐私 / 兼容 / 合规

## 02. 架构

- [架构总览](02-architecture/architecture-overview.md)
- [模块设计](02-architecture/module-design.md)
- [数据模型](02-architecture/data-model.md)
- [权限模型](02-architecture/permission-model.md)
- [线程模型](02-architecture/threading-model.md)

## 03. 设计

- [浮窗 UI 设计](03-design/floating-ui-design.md)
- [Agent 主循环](03-design/agent-loop-design.md)
- [Skill DSL 规范](03-design/skill-dsl-spec.md)
- [LLM 适配器](03-design/llm-adapter-design.md)
- [记忆与习惯](03-design/memory-and-habit-design.md)
- [无障碍策略](03-design/accessibility-strategy.md)

## 04. 关键流程

- [冷启动](04-flows/flow-cold-start.md)
- [意图理解](04-flows/flow-intent-resolution.md)
- [Skill 录制](04-flows/flow-skill-record.md)
- [Skill 执行](04-flows/flow-skill-execute.md)
- [付款 / 不可逆守卫](04-flows/flow-payment-confirm.md)
- [错误恢复](04-flows/flow-error-recovery.md)

## 05. 测试

- [测试策略](05-test/test-strategy.md)
- [测试矩阵](05-test/test-matrix.md)
- [E2E 场景脚本](05-test/e2e-scenarios.md)
- [CI 设计](05-test/ci-design.md)

## 06. 路线图

- [版本路线图](06-roadmap/roadmap.md) — V0.1 → V1.0
- [V0.1 任务计划](06-roadmap/v0.1-task-plan.md) — 风险前置垂直切片（来自迭代前反思）
- [V0.1 Release Notes](06-roadmap/release-notes-v0.1.md) — ✅ 验收通过 2026-04-26
- [V0.2 Release Notes](06-roadmap/release-notes-v0.2.md) — ✅ 验收通过 2026-04-27
- [V0.3 Release Notes](06-roadmap/release-notes-v0.3.md) — ✅ 验收通过 2026-04-27
- [发布检查清单](06-roadmap/release-checklist.md)

## ADR — 架构决策记录

- [0001 控制路径选用 AccessibilityService](adr/0001-control-path-accessibility.md)
- [0002 默认 LLM 接入采用 OpenAI 兼容协议](adr/0002-llm-openai-compat.md)
- [0003 Skill 形态采用声明式 DSL (YAML)](adr/0003-skill-dsl-yaml.md)
- [0004 语音识别采用 Android SpeechRecognizer 起步](adr/0004-asr-system-default.md)
- [0005 语言策略：Java + Kotlin 混合](adr/0005-mixed-java-kotlin.md)

## 文档约定

- 每份文档统一模板：**目的 / 范围 / 决策 / 接口 / 风险 / 验收**
- 每条 ADR 不可改，只能追加新的 ADR 取代
- 重大方案变更先开 issue 讨论再改文档
