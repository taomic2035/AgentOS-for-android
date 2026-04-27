package com.taomic.agent.recorder

import android.view.accessibility.AccessibilityEvent
import com.taomic.agent.core.action.NodeQuery
import com.taomic.agent.skill.dsl.SkillSpec
import com.taomic.agent.skill.dsl.Step

/**
 * 将 AccessibilityEvent 流转换为 [Step] 序列，供 V0.4 录制功能使用。
 *
 * 录制流程：
 * 1. [startRecording] → 清空缓冲区，开始接收事件
 * 2. [onAccessibilityEvent] → 由外部（AgentAccessibilityService.eventCallback）逐条喂入
 * 3. [stopRecording] → 后处理合并，输出 [RecordingResult]（steps 已填充，id/name 需调用方补全）
 *
 * 事件映射规则：
 * - TYPE_WINDOW_STATE_CHANGED + 包名切换 → [Step.LaunchApp]
 * - TYPE_VIEW_CLICKED → [Step.ClickNode]
 * - TYPE_VIEW_TEXT_CHANGED → [Step.InputText]
 * - TYPE_VIEW_FOCUSED + EditText → [Step.WaitNode]
 */
class SkillRecorder {

    private val rawEvents = mutableListOf<AccessibilityEvent>()
    private var recording = false

    val isRecording: Boolean get() = recording
    val stepCount: Int get() = rawEvents.size

    fun startRecording() {
        rawEvents.clear()
        recording = true
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!recording) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                rawEvents.add(AccessibilityEvent.obtain(event))
            }
        }
    }

    fun stopRecording(): RecordingResult {
        recording = false
        val steps = postProcess(rawEvents)
        rawEvents.forEach { it.recycle() }
        rawEvents.clear()
        return RecordingResult(steps = steps)
    }

    fun cancel() {
        recording = false
        rawEvents.forEach { it.recycle() }
        rawEvents.clear()
    }

    private fun postProcess(events: List<AccessibilityEvent>): List<Step> {
        val steps = mutableListOf<Step>()
        var lastPkg: String? = null
        var lastFocusedQuery: NodeQuery? = null

        for (event in events) {
            val pkg = event.packageName?.toString().orEmpty()

            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (pkg.isNotBlank() && pkg != lastPkg && !isSystemUi(pkg)) {
                    steps += Step.LaunchApp(packageName = pkg)
                    lastPkg = pkg
                }
                continue
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
                val resId = event.source?.viewIdResourceName?.substringAfterLast('/')
                val cls = event.source?.className?.toString()?.substringAfterLast('.')
                lastFocusedQuery = NodeQuery(
                    resourceId = resId?.takeIf { it.isNotBlank() },
                    className = if (resId.isNullOrBlank()) cls?.takeIf { it.isNotBlank() } else null,
                )
                // EditText 获焦 → WaitNode
                val fullCls = event.source?.className?.toString().orEmpty()
                if (fullCls.contains("EditText", ignoreCase = true) || fullCls.contains("Editor", ignoreCase = true)) {
                    if (!lastFocusedQuery.isEmpty) {
                        steps += Step.WaitNode(
                            resourceId = lastFocusedQuery.resourceId,
                            className = lastFocusedQuery.className,
                            timeoutMs = 3000,
                        )
                    }
                }
                continue
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (isSystemUi(pkg)) continue
                val text = event.text?.firstOrNull()?.toString()
                val contentDesc = event.contentDescription?.toString()
                val resId = event.source?.viewIdResourceName?.substringAfterLast('/')
                val cls = event.source?.className?.toString()?.substringAfterLast('.')

                val clickStep = when {
                    !resId.isNullOrBlank() -> Step.ClickNode(resourceId = resId, className = cls)
                    !text.isNullOrBlank() -> Step.ClickNode(text = text, className = cls)
                    !contentDesc.isNullOrBlank() -> Step.ClickNode(desc = contentDesc, className = cls)
                    else -> null
                }
                if (clickStep != null) {
                    val last = steps.lastOrNull()
                    if (last !is Step.ClickNode || last != clickStep) {
                        steps += clickStep
                    }
                }
                continue
            }

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                val text = event.text?.firstOrNull()?.toString().orEmpty()
                if (text.isBlank()) continue
                val target = lastFocusedQuery?.takeIf { !it.isEmpty }
                    ?: NodeQuery(className = "EditText")
                val inputStep = Step.InputText(target = target, text = text)
                // 相同 target 的连续 InputText 只保留最后一个
                val last = steps.lastOrNull()
                if (last is Step.InputText && last.target == inputStep.target) {
                    steps[steps.lastIndex] = inputStep
                } else {
                    steps += inputStep
                }
            }
        }

        return steps
    }

    private fun isSystemUi(pkg: String): Boolean =
        pkg.startsWith("com.android.systemui") ||
            pkg.startsWith("com.android.launcher") ||
            pkg == "android"

    data class RecordingResult(
        val steps: List<Step>,
    ) {
        fun toSkillSpec(id: String, name: String, description: String? = null): SkillSpec =
            SkillSpec(id = id, name = name, description = description, steps = steps)
    }
}
