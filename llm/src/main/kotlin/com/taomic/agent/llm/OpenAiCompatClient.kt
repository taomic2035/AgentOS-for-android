package com.taomic.agent.llm

import com.taomic.agent.core.llm.ChatCompletion
import com.taomic.agent.core.llm.ChatMessage
import com.taomic.agent.core.llm.ChatRole
import com.taomic.agent.core.llm.LlmClient
import com.taomic.agent.core.llm.ToolCall
import com.taomic.agent.core.llm.ToolDefinition
import com.taomic.agent.core.llm.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容协议的 LLM 客户端实现（OkHttp）。
 *
 * 支持 `/v1/chat/completions` + tools/function_calling。
 * 默认厂商：火山引擎方舟 (Doubao)。
 */
class OpenAiCompatClient(
    private val baseUrl: String = LlmAdapter.DEFAULT_BASE_URL,
    private val apiKey: String,
    private val model: String = LlmAdapter.DEFAULT_MODEL,
    private val temperature: Double = LlmAdapter.DEFAULT_TEMPERATURE,
    private val maxTokens: Int = LlmAdapter.DEFAULT_MAX_TOKENS,
    timeoutSec: Int = LlmAdapter.DEFAULT_TIMEOUT_SEC,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LlmClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .build()

    override suspend fun chat(messages: List<ChatMessage>): ChatCompletion =
        doChat(messages, tools = null)

    override suspend fun chatWithTools(messages: List<ChatMessage>, tools: List<ToolDefinition>): ChatCompletion =
        doChat(messages, tools)

    override suspend fun appendToolResult(
        messages: List<ChatMessage>,
        toolCallId: String,
        result: String,
    ): ChatCompletion {
        val extended = messages + ChatMessage(role = ChatRole.TOOL, content = result, toolCallId = toolCallId)
        return doChat(extended, null)
    }

    override suspend fun ping(): Boolean {
        return try {
            val result = chat(listOf(ChatMessage(ChatRole.USER, "hi")))
            result.content != null || result.toolCalls.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun doChat(messages: List<ChatMessage>, tools: List<ToolDefinition>?): ChatCompletion =
        withContext(Dispatchers.IO) {
            val body = buildRequestJson(messages, tools)
            val request = Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                throw LlmException("Empty response body (HTTP ${response.code})")
            }

            if (!response.isSuccessful) {
                throw LlmException("HTTP ${response.code}: $responseBody")
            }

            parseCompletion(responseBody)
        }

    private fun buildRequestJson(messages: List<ChatMessage>, tools: List<ToolDefinition>?): JsonObject {
        val msgArray = buildJsonArray {
            for (msg in messages) {
                add(buildMessageObject(msg))
            }
        }
        val builder = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to JsonPrimitive(model),
            "messages" to msgArray,
            "temperature" to JsonPrimitive(temperature),
            "max_tokens" to JsonPrimitive(maxTokens),
        )
        if (tools != null && tools.isNotEmpty()) {
            builder["tools"] = buildJsonArray {
                for (tool in tools) {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                            put("parameters", json.parseToJsonElement(tool.parameters))
                        })
                    })
                }
            }
        }
        return JsonObject(builder)
    }

    private fun buildMessageObject(msg: ChatMessage): JsonObject {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "role" to JsonPrimitive(msg.role.value),
        )
        if (msg.content.isNotEmpty()) {
            map["content"] = JsonPrimitive(msg.content)
        }
        if (msg.toolCalls.isNotEmpty()) {
            map["tool_calls"] = buildJsonArray {
                for (tc in msg.toolCalls) {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(tc.id))
                        put("type", JsonPrimitive("function"))
                        put("function", buildJsonObject {
                            put("name", JsonPrimitive(tc.name))
                            put("arguments", JsonPrimitive(tc.arguments))
                        })
                    })
                }
            }
        }
        if (msg.toolCallId != null) {
            map["tool_call_id"] = JsonPrimitive(msg.toolCallId)
        }
        return JsonObject(map)
    }

    private fun parseCompletion(body: String): ChatCompletion {
        val root = json.parseToJsonElement(body).jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LlmException("No choices in response")

        val message = choice["message"]?.jsonObject ?: throw LlmException("No message in choice")
        val contentElement = message["content"]
        val content = if (contentElement == null || contentElement is kotlinx.serialization.json.JsonNull) {
            null
        } else {
            contentElement.jsonPrimitive.content
        }
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content ?: "unknown"

        val toolCalls = message["tool_calls"]?.jsonArray?.map { tc ->
            val func = tc.jsonObject["function"]?.jsonObject ?: throw LlmException("Malformed tool_call")
            ToolCall(
                id = tc.jsonObject["id"]?.jsonPrimitive?.content ?: "",
                name = func["name"]?.jsonPrimitive?.content ?: "",
                arguments = func["arguments"]?.jsonPrimitive?.content ?: "{}",
            )
        } ?: emptyList()

        val usage = root["usage"]?.jsonObject?.let {
            Usage(
                promptTokens = it["prompt_tokens"]?.jsonPrimitive?.int ?: 0,
                completionTokens = it["completion_tokens"]?.jsonPrimitive?.int ?: 0,
                totalTokens = it["total_tokens"]?.jsonPrimitive?.int ?: 0,
            )
        }

        return ChatCompletion(content = content, toolCalls = toolCalls, finishReason = finishReason, usage = usage)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal val ChatRole.value: String
    get() = when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
        ChatRole.TOOL -> "tool"
    }
