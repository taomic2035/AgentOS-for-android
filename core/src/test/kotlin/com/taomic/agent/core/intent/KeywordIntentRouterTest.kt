package com.taomic.agent.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordIntentRouterTest {

    private val router = KeywordIntentRouter()

    @Test
    fun `network keywords map to settings_open_internet`() {
        listOf("打开网络", "网络设置", "wifi 不通", "Wi-Fi", "internet status", "联网", "上网")
            .forEach { input ->
                val r = router.route(input) as RouteResult.Hit
                assertEquals("settings_open_internet for \"$input\"", "settings_open_internet", r.skillId)
                assertTrue(r.inputs.isEmpty())
            }
    }

    @Test
    fun `santi keyword maps to tencent_video_play with title=三体`() {
        listOf("三体", "看三体", "我想看三体最新一集", "三体最终战")
            .forEach { input ->
                val r = router.route(input) as RouteResult.Hit
                assertEquals("tencent_video_play", r.skillId)
                assertEquals("三体" /* for input "$input" */, r.inputs["title"])
            }
    }

    @Test
    fun `watch pattern extracts title for non-santi videos`() {
        val r = router.route("看红楼梦") as RouteResult.Hit
        assertEquals("tencent_video_play", r.skillId)
        assertEquals("红楼梦", r.inputs["title"])
    }

    @Test
    fun `chinese video synonyms route to tencent`() {
        listOf("放视频", "电视剧推荐", "看剧").forEach { input ->
            val r = router.route(input)
            assertTrue("input=\"$input\" → $r", r is RouteResult.Hit)
            assertEquals("tencent_video_play", (r as RouteResult.Hit).skillId)
        }
    }

    @Test
    fun `unmatched text returns Miss`() {
        listOf("天气怎么样", "讲个笑话", "abc xyz").forEach { input ->
            val r = router.route(input)
            assertTrue("input=\"$input\" should miss but got $r", r is RouteResult.Miss)
            assertEquals(input, (r as RouteResult.Miss).text)
        }
    }

    @Test
    fun `empty or blank text returns Miss`() {
        listOf("", "   ", "\t\n").forEach { input ->
            assertTrue(router.route(input) is RouteResult.Miss)
        }
    }

    @Test
    fun `keyword priority — network beats video when both present`() {
        // V0.1b 简化策略：网络关键词优先（先 if）。这条 case 锁住当前行为，未来若改优先级需更新此测试
        val r = router.route("看网络视频") as RouteResult.Hit
        assertEquals("settings_open_internet", r.skillId)
    }
}
