package com.taomic.agent.core.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCacheRouterTest {

    private fun createRouter(
        events: List<HabitEventEntry>,
        skillNames: Map<String, String> = emptyMap(),
    ): HabitCacheRouter = HabitCacheRouter(
        getRecentEvents = { events },
        skillNameMap = skillNames,
    )

    @Test
    fun `exact match returns hit`() = suspendTest {
        val events = listOf(
            HabitEventEntry("看三体", "tencent_video_play"),
        )
        val router = createRouter(events)
        val result = router.route("看三体")
        assertTrue(result is RouteResult.Hit)
        assertEquals("tencent_video_play", (result as RouteResult.Hit).skillId)
    }

    @Test
    fun `case insensitive exact match`() = suspendTest {
        val events = listOf(
            HabitEventEntry("看三体", "skill_a"),
        )
        val router = createRouter(events)
        val result = router.route("看三体")
        assertTrue(result is RouteResult.Hit)
    }

    @Test
    fun `skill name keyword match`() = suspendTest {
        val events = emptyList<HabitEventEntry>()
        val router = createRouter(events, mapOf("video_play" to "视频播放"))
        val result = router.route("视频播放")
        assertTrue(result is RouteResult.Hit)
        assertEquals("video_play", (result as RouteResult.Hit).skillId)
    }

    @Test
    fun `no match returns miss`() = suspendTest {
        val router = createRouter(emptyList(), emptyMap())
        val result = router.route("随便说说")
        assertTrue(result is RouteResult.Miss)
    }

    @Test
    fun `keyword overlap match`() = suspendTest {
        val events = listOf(
            HabitEventEntry("我想看三体电视剧", "video_play"),
        )
        val router = createRouter(events, mapOf("video_play" to "视频"))
        val result = router.route("我想看三体最新集")
        // "三体" is 2 chars, should match via overlap
        assertTrue(result is RouteResult.Hit)
    }

    private fun suspendTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
