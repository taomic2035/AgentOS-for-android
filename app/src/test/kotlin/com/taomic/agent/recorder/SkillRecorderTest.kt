package com.taomic.agent.recorder

import android.view.accessibility.AccessibilityEvent
import com.taomic.agent.skill.dsl.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SkillRecorder 单元测试。
 *
 * 因为 AccessibilityEvent.obtain() 在纯 JVM 下不可用（需要 Android framework），
 * 这里测试 postProcess 逻辑通过暴露内部方法或用反射来验证。
 * 实际 Android 环境下的事件录制由仪器测试覆盖。
 *
 * V0.4 阶段仅验证状态管理和 API 调用流程；事件转换逻辑在仪器测试中验证。
 */
class SkillRecorderTest {

    @Test
    fun `initial state is not recording`() {
        val recorder = SkillRecorder()
        assertFalse(recorder.isRecording)
        assertEquals(0, recorder.stepCount)
    }

    @Test
    fun `startRecording sets recording state`() {
        val recorder = SkillRecorder()
        recorder.startRecording()
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `stopRecording returns result and resets state`() {
        val recorder = SkillRecorder()
        recorder.startRecording()
        val result = recorder.stopRecording()
        assertFalse(recorder.isRecording)
        assertEquals(0, result.steps.size)
    }

    @Test
    fun `cancel resets state without result`() {
        val recorder = SkillRecorder()
        recorder.startRecording()
        recorder.cancel()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `stopRecording on non-recording returns empty result`() {
        val recorder = SkillRecorder()
        val result = recorder.stopRecording()
        assertFalse(recorder.isRecording)
        assertEquals(0, result.steps.size)
    }

    @Test
    fun `toSkillSpec creates valid spec`() {
        val result = SkillRecorder.RecordingResult(
            steps = listOf(
                Step.LaunchApp(packageName = "com.example.app"),
            ),
        )
        val spec = result.toSkillSpec(id = "test_skill", name = "Test Skill", description = "A test")
        assertEquals("test_skill", spec.id)
        assertEquals("Test Skill", spec.name)
        assertEquals("A test", spec.description)
        assertEquals(1, spec.steps.size)
    }
}
