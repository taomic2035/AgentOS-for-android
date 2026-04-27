package com.taomic.agent.llm

import com.taomic.agent.core.llm.ChatMessage
import com.taomic.agent.core.llm.ChatRole
import com.taomic.agent.core.llm.ToolDefinition
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatClient(
            baseUrl = server.url("").toString().trimEnd('/'),
            apiKey = "test-key",
            model = "test-model",
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `chat returns text content`() = runTest {
        server.enqueue(mockCompletion(content = "Hello! How can I help?"))
        val result = client.chat(listOf(ChatMessage(ChatRole.USER, "hi")))
        assertEquals("Hello! How can I help?", result.content)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals("stop", result.finishReason)
    }

    @Test
    fun `chatWithTools returns tool_calls`() = runTest {
        val toolArgsJson = """{"skill_id":"stub_video_play","inputs":{"title":"三体"}}"""
        val escapedArgs = toolArgsJson.replace("\"", "\\\"")
        server.enqueue(mockToolCallCompletion(
            toolCallId = "call_1",
            toolName = "run_skill",
            escapedToolArgs = escapedArgs,
        ))
        val tools = listOf(
            ToolDefinition(
                name = "run_skill",
                description = "Run a registered skill",
                parameters = """{"type":"object","properties":{"skill_id":{"type":"string"}}}""",
            ),
        )
        val result = client.chatWithTools(
            listOf(ChatMessage(ChatRole.USER, "我想看三体")),
            tools,
        )
        assertNull(result.content)
        assertEquals(1, result.toolCalls.size)
        assertEquals("call_1", result.toolCalls[0].id)
        assertEquals("run_skill", result.toolCalls[0].name)
        assertTrue(result.toolCalls[0].arguments.contains("stub_video_play"))
        assertEquals("tool_calls", result.finishReason)
    }

    @Test
    fun `chat parses usage tokens`() = runTest {
        server.enqueue(mockCompletion(content = "ok", promptTokens = 10, completionTokens = 5, totalTokens = 15))
        val result = client.chat(listOf(ChatMessage(ChatRole.USER, "hi")))
        assertNotNull(result.usage)
        assertEquals(10, result.usage!!.promptTokens)
        assertEquals(5, result.usage!!.completionTokens)
        assertEquals(15, result.usage!!.totalTokens)
    }

    @Test(expected = LlmException::class)
    fun `chat throws on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        client.chat(listOf(ChatMessage(ChatRole.USER, "hi")))
    }

    @Test(expected = LlmException::class)
    fun `chat throws on empty response body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        client.chat(listOf(ChatMessage(ChatRole.USER, "hi")))
    }

    @Test
    fun `chat sends authorization header`() = runTest {
        server.enqueue(mockCompletion(content = "ok"))
        client.chat(listOf(ChatMessage(ChatRole.USER, "hi")))
        val request = server.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun `ping returns true on success`() = runTest {
        server.enqueue(mockCompletion(content = "hi"))
        assertTrue(client.ping())
    }

    @Test
    fun `ping returns false on failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(false, client.ping())
    }

    @Test
    fun `chat sends correct model and messages`() = runTest {
        server.enqueue(mockCompletion(content = "ok"))
        client.chat(listOf(
            ChatMessage(ChatRole.SYSTEM, "You are a helper"),
            ChatMessage(ChatRole.USER, "hello"),
        ))
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("You are a helper"))
    }

    // --- helpers ---

    private fun mockCompletion(
        content: String = "",
        promptTokens: Int = 0,
        completionTokens: Int = 0,
        totalTokens: Int = 0,
    ): MockResponse = MockResponse().setBody("""
        {
          "id": "chatcmpl-test",
          "object": "chat.completion",
          "choices": [{
            "index": 0,
            "message": {"role": "assistant", "content": "$content"},
            "finish_reason": "stop"
          }],
          "usage": {"prompt_tokens": $promptTokens, "completion_tokens": $completionTokens, "total_tokens": $totalTokens}
        }
    """.trimIndent()).setResponseCode(200)
        .addHeader("Content-Type", "application/json")

    private fun mockToolCallCompletion(
        toolCallId: String,
        toolName: String,
        escapedToolArgs: String,
    ): MockResponse = MockResponse().setBody("""
        {
          "id": "chatcmpl-tool",
          "object": "chat.completion",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": null,
              "tool_calls": [{
                "id": "$toolCallId",
                "type": "function",
                "function": {"name": "$toolName", "arguments": "$escapedToolArgs"}
              }]
            },
            "finish_reason": "tool_calls"
          }],
          "usage": {"prompt_tokens": 50, "completion_tokens": 20, "total_tokens": 70}
        }
    """.trimIndent()).setResponseCode(200)
        .addHeader("Content-Type", "application/json")
}
