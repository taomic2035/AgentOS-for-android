package com.taomic.agent.skill.dsl

import com.taomic.agent.core.action.GlobalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillParserTest {

    @Test
    fun `parse settings_open_internet yaml from resources`() {
        val source = readResource("/skills/settings_open_internet.yaml")
        val spec = SkillParser.parse(source)

        assertEquals("settings_open_internet", spec.id)
        assertEquals(1, spec.version)
        assertEquals(0, spec.inputs.size)
        assertEquals(4, spec.steps.size)

        val step0 = spec.steps[0] as Step.LaunchApp
        assertEquals("com.android.settings", step0.packageName)
        assertNull(step0.uri)

        val step1 = spec.steps[1] as Step.WaitNode
        assertEquals("Network & internet", step1.text)
        assertEquals(5000L, step1.timeoutMs)

        val step2 = spec.steps[2] as Step.ClickNode
        assertEquals("Network & internet", step2.text)
        assertEquals(0, step2.index)

        val step3 = spec.steps[3] as Step.WaitNode
        assertEquals("Internet", step3.containsText)
        assertEquals(3000L, step3.timeoutMs)

        assertEquals(RecoveryAction.PROMPT_USER, spec.recovery.onNodeNotFound)
        assertEquals(RecoveryAction.ABORT, spec.recovery.onTimeout)
    }

    @Test
    fun `parse tencent_video_play yaml with template variable`() {
        val source = readResource("/skills/tencent_video_play.yaml")
        val spec = SkillParser.parse(source)

        assertEquals("tencent_video_play", spec.id)
        assertEquals(1, spec.inputs.size)
        assertEquals("title", spec.inputs.first().name)
        assertTrue(spec.inputs.first().required)

        // input_text 步骤的 text 字段保留模板变量原文，由 SkillRunner 在执行时替换
        val inputStep = spec.steps.filterIsInstance<Step.InputText>().single()
        assertEquals("\${title}", inputStep.text)
        assertEquals("android.widget.EditText", inputStep.target.className)

        // press_key ENTER（V0.1 占位，运行层会标记 NotImplemented）
        val pressEnter = spec.steps.filterIsInstance<Step.PressKey>().single()
        assertEquals(GlobalKey.ENTER, pressEnter.key)

        assertEquals(RecoveryAction.FALLBACK_TO_LLM, spec.recovery.onNodeNotFound)
    }

    @Test
    fun `parse rejects missing required id`() {
        val source = """
            name: bad
            version: 1
            steps:
              - action: sleep
                ms: 100
        """.trimIndent()
        assertThrows(SkillParseException::class.java) {
            SkillParser.parse(source)
        }
    }

    @Test
    fun `parse rejects unknown action`() {
        val source = """
            id: test
            name: bad
            steps:
              - action: rocket_launch
                power: max
        """.trimIndent()
        assertThrows(SkillParseException::class.java) {
            SkillParser.parse(source)
        }
    }

    @Test
    fun `parse press_key all keys round-trip`() {
        val source = """
            id: keys
            name: keys
            steps:
              - { action: press_key, key: BACK }
              - { action: press_key, key: HOME }
              - { action: press_key, key: RECENTS }
              - { action: press_key, key: NOTIFICATIONS }
              - { action: press_key, key: ENTER }
        """.trimIndent()
        val spec = SkillParser.parse(source)
        val keys = spec.steps.filterIsInstance<Step.PressKey>().map { it.key }
        assertEquals(
            listOf(GlobalKey.BACK, GlobalKey.HOME, GlobalKey.RECENTS, GlobalKey.NOTIFICATIONS, GlobalKey.ENTER),
            keys,
        )
    }

    @Test
    fun `encode then parse round-trip preserves spec`() {
        val original = readResource("/skills/settings_open_internet.yaml")
        val once = SkillParser.parse(original)
        val encoded = SkillParser.encode(once)
        val twice = SkillParser.parse(encoded)
        assertEquals(once, twice)
    }

    @Test
    fun `parser tolerates unknown top-level fields (forward-compat)`() {
        val source = """
            id: future
            name: future
            future_capability: enabled
            steps:
              - { action: sleep, ms: 50 }
        """.trimIndent()
        val spec = SkillParser.parse(source)
        assertEquals("future", spec.id)
        assertEquals(1, spec.steps.size)
    }

    private fun readResource(path: String): String {
        val stream = checkNotNull(this::class.java.getResourceAsStream(path)) {
            "missing test resource: $path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
