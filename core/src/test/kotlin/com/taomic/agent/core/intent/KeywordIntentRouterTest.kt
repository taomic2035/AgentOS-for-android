package com.taomic.agent.core.intent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordIntentRouterTest {

    private val router = KeywordIntentRouter()

    @Test
    fun `network keywords map to settings_open_internet`() = runTest {
        listOf("打开网络", "网络设置", "wifi 不通", "Wi-Fi", "internet status", "联网", "上网")
            .forEach { input ->
                val r = router.route(input) as RouteResult.Hit
                assertEquals("settings_open_internet for \"$input\"", "settings_open_internet", r.skillId)
                assertTrue(r.inputs.isEmpty())
            }
    }

    @Test
    fun `santi keyword maps to stub_video_play with title=三体`() = runTest {
        listOf("三体", "看三体", "我想看三体最新一集", "三体最终战")
            .forEach { input ->
                val r = router.route(input) as RouteResult.Hit
                assertEquals("stub_video_play", r.skillId)
                assertEquals("三体" /* for input "$input" */, r.inputs["title"])
            }
    }

    @Test
    fun `watch pattern extracts title for non-santi videos`() = runTest {
        val r = router.route("看红楼梦") as RouteResult.Hit
        assertEquals("stub_video_play", r.skillId)
        assertEquals("红楼梦", r.inputs["title"])
    }

    @Test
    fun `chinese video synonyms route to stub_video_play`() = runTest {
        listOf("放视频", "电视剧推荐", "看剧").forEach { input ->
            val r = router.route(input)
            assertTrue("input=\"$input\" → $r", r is RouteResult.Hit)
            assertEquals("stub_video_play", (r as RouteResult.Hit).skillId)
        }
    }

    @Test
    fun `unmatched text returns Miss`() = runTest {
        listOf("天气怎么样", "讲个笑话", "abc xyz").forEach { input ->
            val r = router.route(input)
            assertTrue("input=\"$input\" should miss but got $r", r is RouteResult.Miss)
            assertEquals(input, (r as RouteResult.Miss).text)
        }
    }

    @Test
    fun `empty or blank text returns Miss`() = runTest {
        listOf("", "   ", "\t\n").forEach { input ->
            assertTrue(router.route(input) is RouteResult.Miss)
        }
    }

    @Test
    fun `keyword priority — network beats video when both present`() = runTest {
        val r = router.route("看网络视频") as RouteResult.Hit
        assertEquals("settings_open_internet", r.skillId)
    }

    // --- ChainedIntentRouter ---

    @Test
    fun `chained router returns first hit`() = runTest {
        val chain = ChainedIntentRouter(listOf(
            KeywordIntentRouter(),
            StubRouter(RouteResult.Miss("test")),
        ))
        val r = chain.route("打开网络") as RouteResult.Hit
        assertEquals("settings_open_internet", r.skillId)
    }

    @Test
    fun `chained router falls through on miss`() = runTest {
        val chain = ChainedIntentRouter(listOf(
            KeywordIntentRouter(),
            StubRouter(RouteResult.Hit("llm_skill", mapOf("q" to "test"))),
        ))
        val r = chain.route("今天天气") as RouteResult.Hit
        assertEquals("llm_skill", r.skillId)
    }

    @Test
    fun `chained router returns miss when all miss`() = runTest {
        val chain = ChainedIntentRouter(listOf(
            KeywordIntentRouter(),
        ))
        val r = chain.route("讲个笑话")
        assertTrue(r is RouteResult.Miss)
    }

    private class StubRouter(private val result: RouteResult) : IntentRouter {
        override suspend fun route(text: String): RouteResult = result
    }
}
