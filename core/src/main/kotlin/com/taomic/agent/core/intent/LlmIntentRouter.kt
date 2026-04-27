package com.taomic.agent.core.intent

import com.taomic.agent.core.llm.ChatCompletion
import com.taomic.agent.core.llm.ChatMessage
import com.taomic.agent.core.llm.ChatRole
import com.taomic.agent.core.llm.LlmClient
import com.taomic.agent.core.llm.ToolCall
import com.taomic.agent.core.llm.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LLM 驱动的意图路由：通过 LLM tool_call 选择要执行的 Skill。
 *
 * 工作流程：
 * 1. 将用户输入 + 可用 Skill 列表发给 LLM
 * 2. LLM 返回 tool_call（function name = skill_id, arguments = inputs）
 * 3. 解析 tool_call 为 RouteResult.Hit
 *
 * 如果 LLM 不返回 tool_call（纯文字回复），返回 RouteResult.Miss。
 */
class LlmIntentRouter(
    private val client: LlmClient,
    private val skillDescriptors: List<SkillDescriptor>,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
) : IntentRouter {

    override suspend fun route(text: String): RouteResult {
        val tools = buildToolDefinitions()
        val messages = buildMessages(text)

        val completion: ChatCompletion = try {
            client.chatWithTools(messages, tools)
        } catch (_: Exception) {
            return RouteResult.Miss(text)
        }

        val toolCall = completion.toolCalls.firstOrNull() ?: return RouteResult.Miss(text)
        return parseToolCall(toolCall, text)
    }

    private fun buildToolDefinitions(): List<ToolDefinition> {
        val properties = """{"type":"object","properties":{"skill_id":{"type":"string"},"inputs":{"type":"object"}},"required":["skill_id"]}"""
        return listOf(
            ToolDefinition(
                name = "run_skill",
                description = skillDescriptors.joinToString("; ") { "${it.id}: ${it.description}" },
                parameters = properties,
            ),
        )
    }

    private fun buildMessages(text: String): List<ChatMessage> = listOf(
        ChatMessage(ChatRole.SYSTEM, systemPrompt),
        ChatMessage(ChatRole.USER, text),
    )

    private fun parseToolCall(toolCall: ToolCall, originalText: String): RouteResult {
        if (toolCall.name != "run_skill") return RouteResult.Miss(originalText)
        return try {
            val args = json.parseToJsonElement(toolCall.arguments).jsonObject
            val skillId = args["skill_id"]?.jsonPrimitive?.content ?: return RouteResult.Miss(originalText)
            val inputsObj = args["inputs"]?.jsonObject
            val inputs = inputsObj?.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    is JsonObject -> v.toString()
                    is JsonArray -> v.toString()
                    else -> v.toString()
                }
            } ?: emptyMap()
            RouteResult.Hit(skillId, inputs)
        } catch (_: Exception) {
            RouteResult.Miss(originalText)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val DEFAULT_SYSTEM_PROMPT: String = """
You are an Android assistant. Based on the user's request, select the most appropriate skill to execute.
If no skill matches, respond with plain text instead of a tool call.
Always respond in the same language as the user's input.
""".trimIndent()
    }
}

/** Skill 的摘要信息，供 LLM 选择时参考。 */
data class SkillDescriptor(
    val id: String,
    val description: String,
)
