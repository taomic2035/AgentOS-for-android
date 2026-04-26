package com.taomic.agent.skill

import com.taomic.agent.core.action.ActionContext
import com.taomic.agent.core.action.ActionOutcome
import com.taomic.agent.core.action.GlobalKey
import com.taomic.agent.core.action.NodeQuery
import com.taomic.agent.skill.dsl.SkillParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSkillRunnerTest {

    /** Fake ActionContext — 记录所有调用并返回可配置的 outcome。 */
    private class FakeCtx : ActionContext {
        data class Call(val name: String, val args: List<Any?>)

        val calls = mutableListOf<Call>()
        val outcomes: MutableMap<String, ActionOutcome> = mutableMapOf()
        var defaultOutcome: ActionOutcome = ActionOutcome.Success()

        private fun record(name: String, vararg args: Any?): ActionOutcome {
            calls += Call(name, args.toList())
            return outcomes[name] ?: defaultOutcome
        }

        override suspend fun launchApp(packageName: String, uri: String?): ActionOutcome =
            record("launchApp", packageName, uri)

        override suspend fun waitNode(query: NodeQuery, timeoutMs: Long): ActionOutcome =
            record("waitNode", query, timeoutMs)

        override suspend fun clickNode(query: NodeQuery): ActionOutcome =
            record("clickNode", query)

        override suspend fun inputText(target: NodeQuery, text: String, clearFirst: Boolean): ActionOutcome =
            record("inputText", target, text, clearFirst)

        override suspend fun pressKey(key: GlobalKey): ActionOutcome =
            record("pressKey", key)

        override suspend fun sleep(ms: Long): ActionOutcome =
            record("sleep", ms)
    }

    @Test
    fun `runs settings_open_internet skill end-to-end with all-success ctx`() = runBlocking {
        val source = readResource("/skills/settings_open_internet.yaml")
        val spec = SkillParser.parse(source)
        val ctx = FakeCtx()

        val result = DefaultSkillRunner(ctx).run(spec)

        assertTrue("ok=${result.error}", result.ok)
        assertEquals(4, result.stepsExecuted)
        assertEquals(4, result.totalSteps)
        assertNull(result.error)
        assertEquals(4, ctx.calls.size)
        assertEquals("launchApp", ctx.calls[0].name)
        assertEquals("com.android.settings", ctx.calls[0].args[0])
        assertEquals("waitNode", ctx.calls[1].name)
        assertEquals("clickNode", ctx.calls[2].name)
        assertEquals("waitNode", ctx.calls[3].name)
    }

    @Test
    fun `template variable substitution flows into ctx args`() = runBlocking {
        val source = readResource("/skills/tencent_video_play.yaml")
        val spec = SkillParser.parse(source)
        val ctx = FakeCtx()

        val result = DefaultSkillRunner(ctx).run(spec, inputs = mapOf("title" to "三体"))

        // tencent_video_play 含 ENTER —— V0.1 将由实现层返回 NotImplemented。
        // 这里 fake ctx 默认 Success，所以会成功；实测验证一下 inputText 的文本被替换：
        val inputCall = ctx.calls.single { it.name == "inputText" }
        assertEquals("三体", inputCall.args[1] as String)
        // 同时 wait_node contains_text=${title} 也应替换：
        val waitWithTitle = ctx.calls
            .filter { it.name == "waitNode" }
            .map { it.args[0] as NodeQuery }
            .single { it.containsText == "三体" }
        assertNotNull(waitWithTitle)
        assertTrue(result.ok)
    }

    @Test
    fun `step failure halts and reports StepFailed with index`() = runBlocking {
        val source = readResource("/skills/settings_open_internet.yaml")
        val spec = SkillParser.parse(source)
        val ctx = FakeCtx().apply {
            outcomes["clickNode"] = ActionOutcome.Failed("simulated click miss")
        }

        val result = DefaultSkillRunner(ctx).run(spec)

        assertTrue(!result.ok)
        val err = result.error as SkillError.StepFailed
        assertEquals(2, err.stepIndex) // step[0]=launch, step[1]=wait, step[2]=click
        assertTrue(err.reason.contains("simulated click miss"))
        // 失败步骤之后不应再有 ctx 调用
        assertEquals(3, ctx.calls.size)
    }

    @Test
    fun `step timeout reports Timeout with waitedMs`() = runBlocking {
        val source = readResource("/skills/settings_open_internet.yaml")
        val spec = SkillParser.parse(source)
        val ctx = FakeCtx().apply {
            outcomes["waitNode"] = ActionOutcome.Timeout(waitedMs = 5000)
        }

        val result = DefaultSkillRunner(ctx).run(spec)

        val err = result.error as SkillError.Timeout
        assertEquals(1, err.stepIndex)
        assertEquals(5000, err.waitedMs)
    }

    @Test
    fun `NotImplemented ctx outcome maps to StepFailed with reason marker`() = runBlocking {
        val spec = SkillParser.parse(
            """
            id: enter
            name: enter only
            steps:
              - { action: press_key, key: ENTER }
            """.trimIndent(),
        )
        val ctx = FakeCtx().apply {
            outcomes["pressKey"] = ActionOutcome.NotImplemented("ENTER on V0.1")
        }

        val result = DefaultSkillRunner(ctx).run(spec)

        val err = result.error as SkillError.StepFailed
        assertTrue(err.reason.contains("not implemented"))
    }

    @Test
    fun `missing required input fails before any ctx call`() = runBlocking {
        val source = readResource("/skills/tencent_video_play.yaml")
        val spec = SkillParser.parse(source)
        val ctx = FakeCtx()

        val result = DefaultSkillRunner(ctx).run(spec, inputs = emptyMap())

        val err = result.error as SkillError.MissingInput
        assertEquals("title", err.name)
        assertEquals(0, ctx.calls.size)
    }

    @Test
    fun `unbound template variable produces TemplateError`() = runBlocking {
        val spec = SkillParser.parse(
            """
            id: bad-template
            name: bad-template
            steps:
              - { action: launch_app, package: "com.example.${'$'}{not_declared}" }
            """.trimIndent(),
        )
        val result = DefaultSkillRunner(FakeCtx()).run(spec)
        val err = result.error as SkillError.TemplateError
        assertTrue(err.reason.contains("unbound"))
    }

    @Test
    fun `default values fill missing optional inputs`() = runBlocking {
        val spec = SkillParser.parse(
            """
            id: with-default
            name: with-default
            inputs:
              - { name: greeting, type: string, required: false, default: hi }
            steps:
              - { action: launch_app, package: "com.example.${'$'}{greeting}" }
            """.trimIndent(),
        )
        val ctx = FakeCtx()
        val result = DefaultSkillRunner(ctx).run(spec)
        assertTrue(result.ok)
        assertEquals("com.example.hi", ctx.calls.single().args[0])
    }

    @Test
    fun `durationMs is non-negative even on instant fakes`() = runBlocking {
        val spec = SkillParser.parse(
            """
            id: instant
            name: instant
            steps:
              - { action: sleep, ms: 0 }
            """.trimIndent(),
        )
        var t = 1000L
        val runner = DefaultSkillRunner(FakeCtx(), nowMs = { t.also { t += 10 } })
        val result = runner.run(spec)
        assertTrue(result.durationMs >= 0)
    }

    private fun readResource(path: String): String =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "missing: $path" }
            .bufferedReader().use { it.readText() }
}
