package com.taomic.agent.llm

/**
 * V0.1 占位：LLM 适配器入口。
 *
 * 默认协议：OpenAI 兼容 (`/v1/chat/completions`)
 * 出厂默认厂商：火山引擎方舟 (Volcengine Ark) 上的 Doubao 模型
 *
 * 用户可在设置中改：base_url / api_key / model / temperature / max_tokens / timeout
 *
 * 后续在此实现：
 *  - LlmClient 接口（声明在 :core）
 *  - OpenAiCompatClient（OkHttp 实现，本模块）
 *  - AnthropicClient（可选）
 *  - LocalLlamaClient（V0.8 引入）
 */
object LlmAdapter {
    const val DEFAULT_PROTOCOL: String = "openai_compat"

    /** 火山方舟 OpenAI 兼容入口；用户可在设置覆盖。 */
    const val DEFAULT_BASE_URL: String = "https://ark.cn-beijing.volces.com/api/v3"

    /** 默认模型：Doubao 1.5 Pro 32k；首次使用前用户须在方舟控制台开通并填 api_key。 */
    const val DEFAULT_MODEL: String = "doubao-1-5-pro-32k"

    const val DEFAULT_TEMPERATURE: Double = 0.3
    const val DEFAULT_MAX_TOKENS: Int = 2048
    const val DEFAULT_TIMEOUT_SEC: Int = 30
}
