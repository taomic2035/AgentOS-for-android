# LLM 适配器设计

## 接口

```kotlin
interface LlmClient {
    suspend fun chat(
        messages: List<Message>,
        tools: List<Tool> = emptyList(),
        config: LlmConfig,
    ): LlmResponse

    fun stream(
        messages: List<Message>,
        tools: List<Tool> = emptyList(),
        config: LlmConfig,
    ): Flow<LlmStreamEvent>
}

data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val timeoutSec: Int = 30,
)
```

## 实现

| Client | 协议 | 版本 |
|---|---|---|
| `OpenAiCompatClient` | `/v1/chat/completions`（Doubao / OpenAI / DeepSeek / Qwen / 智谱 / Moonshot） | V0.2 |
| `AnthropicClient` | `/v1/messages`（Claude） | V0.x（可选） |
| `LocalLlamaClient` | llama.cpp / MLC-LLM 端侧 | V0.8 |

## OpenAI 兼容请求骨架（以 Doubao 为例）

```http
POST https://ark.cn-beijing.volces.com/api/v3/chat/completions
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "model": "doubao-1-5-pro-32k",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "tools": [...],
  "tool_choice": "auto",
  "temperature": 0.3,
  "stream": false
}
```

## 出厂默认

- `base_url`：`https://ark.cn-beijing.volces.com/api/v3`（火山引擎方舟）
- `model`：`doubao-1-5-pro-32k`
- `api_key`：空（引导用户在方舟控制台开通并填入）
- `temperature`：0.3
- `max_tokens`：2048
- `timeout`：30s

## Capability 探测

不同厂商对 tools 的支持差异：

| 厂商 | tools | tool_choice | parallel_tool_calls |
|---|---|---|---|
| OpenAI | ✅ | ✅ | ✅ |
| Doubao（火山方舟） | ✅ | ✅ | △ 部分模型 |
| DeepSeek | ✅ | ✅ | ✅ |
| Qwen | ✅ | △ 部分 | ✕ |
| 智谱 | ✅ | △ | ✕ |

策略：

1. 用户首次配置后自动跑一次"capability 探测"
2. 探测结果缓存，作为 Planner 决策依据
3. 不支持 tools 的模型 → 改用 ReAct prompt fallback

## 重试与超时

- 网络层重试：3 次指数退避
- 业务层超时：30s
- 流式响应：客户端可中途取消

## 成本统计

- 每次请求记录 input_tokens / output_tokens / model
- 设置页显示当月累计 token
- 超过用户设置的预算阈值时提示

## 安全

- API key 走 KeyStore 加密
- 请求日志不打印 key
- HTTPS 强制

## 测试

- Mock server（`okhttp.mockwebserver`）模拟各种响应
- 真实 API 烟雾测试（CI 跑 1 次）
- tools 协议变更检测
