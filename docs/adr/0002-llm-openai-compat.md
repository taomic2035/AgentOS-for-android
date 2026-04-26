# ADR 0002 — 默认 LLM 接入采用 OpenAI 兼容协议

- **状态**：Accepted
- **日期**：2026-04-26
- **决策人**：铲屎官 + Claude

## 背景

需要选择 V0.x 默认 LLM 提供方与协议形态。候选：

1. OpenAI 兼容（DeepSeek / Qwen / 智谱 / Moonshot / OpenAI 等共用 `/v1/chat/completions`）
2. Anthropic 原生协议
3. 国内厂商私有协议（百度 / 讯飞）
4. 端侧本地模型为主

## 决策

**默认走 OpenAI 兼容协议**，出厂默认指向 **火山引擎方舟 (Volcengine Ark) 上的 Doubao 模型**。

- 默认 `base_url`：`https://ark.cn-beijing.volces.com/api/v3`
- 默认 `model`：`doubao-1-5-pro-32k`
- 默认 `api_key`：空（首次启动引导用户在方舟控制台开通后填入）
- 默认 `temperature`：0.3 / `max_tokens`：2048 / `timeout`：30s
- 用户可在设置面板修改任意配置项
- 抽象 `LlmClient` 接口，便于后续扩展 `AnthropicClient` 与 `LocalLlamaClient`（V0.8）

## 理由

- 一票服务通用：DeepSeek / Qwen / 智谱 / Doubao / OpenAI 等均兼容
- 工具调用（tools / function calling）协议成熟，Doubao 已支持
- 首版工程量最小，便于快速联调
- 国内访问稳定（方舟节点在北京，无需翻墙）

## 取舍

- ⚠️ 不同厂商对 tools 的支持度有差异，需在客户端做能力探测和回退
- ⚠️ Doubao 模型按量计费，须监控费用并在设置中显示
- ✅ 用户随时可换；厂商兼容协议不锁死

## 影响

- `llm` 模块必须设计 capability 探测与降级逻辑
- 设置页须暴露完整可配置项，并提供"恢复默认"
- token / 费用统计在协议层就要做（cost-model 需可插拔）
