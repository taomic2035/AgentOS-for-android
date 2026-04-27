package com.taomic.agent.core.llm

/**
 * LLM 客户端契约（定义在 :core，实现在 :llm）。
 *
 * 支持三种交互模式：
 * 1. **纯对话**：[chat] 返回文本回复
 * 2. **Tool-use**：[chatWithTools] 返回可能包含 tool_call 的回复
 * 3. **流式对话**：[chatStream] 逐 token 回调
 */
interface LlmClient {

    /** 纯对话：发送消息，返回助手文本回复。 */
    suspend fun chat(messages: List<ChatMessage>): ChatCompletion

    /** 带 tools 的对话：发送消息 + 可用工具定义，返回可能包含 tool_call 的回复。 */
    suspend fun chatWithTools(messages: List<ChatMessage>, tools: List<ToolDefinition>): ChatCompletion

    /** 追加 tool 执行结果并继续对话（多轮 tool-use 循环）。 */
    suspend fun appendToolResult(messages: List<ChatMessage>, toolCallId: String, result: String): ChatCompletion

    /** 验证连接可用性（发一条短请求测试 url/key/model）。 */
    suspend fun ping(): Boolean

    /** 流式对话：逐 token 回调 [onToken]，完成后返回完整 ChatCompletion。 */
    suspend fun chatStream(messages: List<ChatMessage>, onToken: (token: String) -> Unit): ChatCompletion
}

/** 流式回调接口，供 UI 层消费。 */
interface StreamCallback {
    /** 收到一个 token。 */
    fun onToken(token: String)
    /** 流式完成。 */
    fun onComplete(completion: ChatCompletion)
    /** 流式出错。 */
    fun onError(error: Throwable)
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

enum class ChatRole {
    SYSTEM, USER, ASSISTANT, TOOL,
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String, // JSON Schema string
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String, // JSON string
)

data class ChatCompletion(
    val content: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String,
    val usage: Usage? = null,
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)
