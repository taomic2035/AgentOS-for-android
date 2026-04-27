package com.taomic.agent.core.intent

import com.taomic.agent.core.llm.ChatCompletion
import com.taomic.agent.core.llm.ChatMessage
import com.taomic.agent.core.llm.ChatRole
import com.taomic.agent.core.llm.LlmClient
import com.taomic.agent.core.llm.ToolCall
import com.taomic.agent.core.llm.Usage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmIntentRouterTest {

    @Test
    fun `route returns Hit when LLM returns tool_call`() = runTest {
        val client = StubLlmClient(
            completion = ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ToolCall(
                        id = "call_1",
                        name = "run_skill",
                        arguments = """{"skill_id":"stub_video_play","inputs":{"title":"三体"}}""",
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )
        val router = LlmIntentRouter(
            client = client,
            skillDescriptors = listOf(SkillDescriptor("stub_video_play", "播放视频")),
        )
        val result = router.route("我想看三体") as RouteResult.Hit
        assertEquals("stub_video_play", result.skillId)
        assertEquals("三体", result.inputs["title"])
    }

    @Test
    fun `route returns Miss when LLM returns text only`() = runTest {
        val client = StubLlmClient(
            completion = ChatCompletion(
                content = "今天天气不错！",
                toolCalls = emptyList(),
                finishReason = "stop",
            ),
        )
        val router = LlmIntentRouter(client, emptyList())
        val result = router.route("今天天气怎么样")
        assertTrue(result is RouteResult.Miss)
    }

    @Test
    fun `route returns Miss when LLM throws`() = runTest {
        val client = StubLlmClient(shouldThrow = true)
        val router = LlmIntentRouter(client, emptyList())
        val result = router.route("测试")
        assertTrue(result is RouteResult.Miss)
    }

    @Test
    fun `route returns Miss when tool_call name is not run_skill`() = runTest {
        val client = StubLlmClient(
            completion = ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ToolCall(id = "c1", name = "other_func", arguments = "{}"),
                ),
                finishReason = "tool_calls",
            ),
        )
        val router = LlmIntentRouter(client, emptyList())
        val result = router.route("测试")
        assertTrue(result is RouteResult.Miss)
    }

    @Test
    fun `ChainedRouter falls through to LLM on keyword miss`() = runTest {
        val llmClient = StubLlmClient(
            completion = ChatCompletion(
                content = null,
                toolCalls = listOf(
                    ToolCall(
                        id = "c1",
                        name = "run_skill",
                        arguments = """{"skill_id":"weather_query","inputs":{}}""",
                    ),
                ),
                finishReason = "tool_calls",
            ),
        )
        val chain = ChainedIntentRouter(listOf(
            KeywordIntentRouter(),
            LlmIntentRouter(llmClient, listOf(SkillDescriptor("weather_query", "查询天气"))),
        ))
        val result = chain.route("今天天气怎么样") as RouteResult.Hit
        assertEquals("weather_query", result.skillId)
    }

    private class StubLlmClient(
        private val completion: ChatCompletion = ChatCompletion("", emptyList(), "stop"),
        private val shouldThrow: Boolean = false,
    ) : LlmClient {
        override suspend fun chat(messages: List<ChatMessage>): ChatCompletion {
            if (shouldThrow) throw RuntimeException("LLM error")
            return completion
        }
        override suspend fun chatWithTools(messages: List<ChatMessage>, tools: List<com.taomic.agent.core.llm.ToolDefinition>): ChatCompletion {
            if (shouldThrow) throw RuntimeException("LLM error")
            return completion
        }
        override suspend fun appendToolResult(messages: List<ChatMessage>, toolCallId: String, result: String): ChatCompletion {
            return completion
        }
        override suspend fun ping(): Boolean = !shouldThrow
    }
}
