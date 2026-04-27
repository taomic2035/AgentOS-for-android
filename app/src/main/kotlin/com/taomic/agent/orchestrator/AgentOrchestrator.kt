package com.taomic.agent.orchestrator

import android.util.Log
import com.taomic.agent.core.A11yController
import com.taomic.agent.core.intent.IntentRouter
import com.taomic.agent.core.intent.KeywordIntentRouter
import com.taomic.agent.core.intent.RouteResult
import com.taomic.agent.data.HabitEvent
import com.taomic.agent.data.HabitRepository
import com.taomic.agent.recorder.SkillRecorder
import com.taomic.agent.recorder.SkillStore
import com.taomic.agent.skill.SkillResult
import com.taomic.agent.skill.SkillRunner
import com.taomic.agent.skill.dsl.RecoveryAction
import com.taomic.agent.skill.dsl.SkillParser
import com.taomic.agent.skill.dsl.SkillSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Agent 编排核心：意图路由 → Skill 执行 → Recovery → LLM 交互。
 *
 * V0.2 从 AgentApp 拆出，职责清晰：
 * - SkillRegistry：管理已注册的 Skill
 * - [handleIntent]：用户输入 → 路由 → 执行
 * - [runSkillById]：直接按 ID 执行 Skill
 * - Recovery：Skill 失败时根据策略回退（fallback_to_llm / prompt_user / abort）
 * - 协程作用域与异常处理
 */
class AgentOrchestrator(
    private val skillRunner: SkillRunner,
    intentRouter: IntentRouter = KeywordIntentRouter(),
    private val a11yController: A11yController? = null,
    private val skillStore: SkillStore? = null,
    private val habitRepository: HabitRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onStateChanged: (state: OrchestratorState) -> Unit = {},
) {

    private val skillRegistry = mutableMapOf<String, SkillSpec>()
    private val recorder = SkillRecorder()

    enum class OrchestratorState { IDLE, THINKING, EXECUTING, DONE, ERROR, RECORDING }

    /** 意图路由器；LLM 配置变更后可替换。 */
    var intentRouter: IntentRouter = intentRouter

    /** 加载内置 Skill（从 classpath 的 skills 目录下的 YAML 文件）。 */
    fun loadBuiltinSkills(classLoader: ClassLoader) {
        loadBuiltinSkills(listOf(
            "skills/settings_open_internet.yaml",
            "skills/stub_video_play.yaml",
            "skills/tencent_video_play.yaml",
        ), classLoader)
    }

    /** 加载内置 Skill（自定义路径列表）。 */
    fun loadBuiltinSkills(paths: List<String>, classLoader: ClassLoader) {
        for (path in paths) {
            try {
                val stream = classLoader.getResourceAsStream(path)
                if (stream == null) {
                    Log.w(TAG, "builtin skill not found on classpath: $path")
                    continue
                }
                val yaml = stream.bufferedReader().use { it.readText() }
                val spec = SkillParser.parse(yaml)
                skillRegistry[spec.id] = spec
                Log.i(TAG, "loaded skill: ${spec.id} (${spec.steps.size} steps)")
            } catch (e: Throwable) {
                Log.e(TAG, "failed to load $path: ${e.message}", e)
            }
        }
    }

    /** 注册外部 Skill。 */
    fun registerSkill(spec: SkillSpec) {
        skillRegistry[spec.id] = spec
    }

    /** 查找已注册的 Skill。 */
    fun findSkill(id: String): SkillSpec? = skillRegistry[id]

    /** 已注册的 Skill ID 列表。 */
    val skillIds: Set<String> get() = skillRegistry.keys.toSet()

    /** 用户意图入口：路由 → Skill 执行。 */
    fun handleIntent(text: String) {
        scope.launch {
            onStateChanged(OrchestratorState.THINKING)
            when (val r = intentRouter.route(text)) {
                is RouteResult.Hit -> {
                    Log.i(TAG, "router hit: \"$text\" → skill=${r.skillId} inputs=${r.inputs}")
                    executeSkill(r.skillId, r.inputs, originalText = text)
                }
                is RouteResult.Miss -> {
                    Log.w(TAG, "router miss: \"$text\"")
                    onStateChanged(OrchestratorState.IDLE)
                }
            }
        }
    }

    /** 直接按 ID 执行 Skill。 */
    fun runSkillById(skillId: String, inputs: Map<String, Any?> = emptyMap()) {
        scope.launch {
            executeSkill(skillId, inputs)
        }
    }

    private suspend fun executeSkill(
        skillId: String,
        inputs: Map<String, Any?>,
        originalText: String? = null,
    ) {
        val spec = skillRegistry[skillId]
        if (spec == null) {
            Log.w(TAG, "unknown skill_id=\"$skillId\"; available=${skillRegistry.keys}")
            onStateChanged(OrchestratorState.ERROR)
            return
        }
        onStateChanged(OrchestratorState.EXECUTING)
        Log.i(TAG, "executing: \"$skillId\" inputs=$inputs")
        val result = runCatching { skillRunner.run(spec, inputs) }
            .fold(
                onSuccess = { it },
                onFailure = { e ->
                    Log.e(TAG, "skill execution threw: ${e.message}", e)
                    SkillResult(
                        ok = false,
                        stepsExecuted = 0,
                        totalSteps = spec.steps.size,
                        durationMs = 0,
                        error = com.taomic.agent.skill.SkillError.StepFailed(-1, "executeSkill", e.message ?: "unknown"),
                    )
                },
            )
        logResult(skillId, result)

        // V0.5 记录习惯事件
        habitRepository?.recordEvent(
            HabitEvent(
                timestamp = System.currentTimeMillis(),
                intentText = originalText ?: skillId,
                skillId = skillId,
                result = if (result.ok) "success" else "fail",
                durationMs = result.durationMs,
            )
        )

        if (result.ok) {
            onStateChanged(OrchestratorState.DONE)
        } else {
            onStateChanged(OrchestratorState.ERROR)
        }

        if (!result.ok) {
            handleRecovery(spec, result, originalText)
        }
    }

    private suspend fun handleRecovery(spec: SkillSpec, result: SkillResult, originalText: String?) {
        val recovery = spec.recovery ?: return
        val action = when {
            result.error is com.taomic.agent.skill.SkillError.StepFailed ->
                recovery.onNodeNotFound
            result.error is com.taomic.agent.skill.SkillError.Timeout ->
                recovery.onTimeout
            else -> return
        }

        when (action) {
            RecoveryAction.FALLBACK_TO_LLM -> {
                Log.i(TAG, "recovery: fallback_to_llm for skill=${spec.id}")
                // Recovery fallback 由外部通过更新 intentRouter 实现
                // 如果 originalText 有值且 ChainedRouter 中有 LLM 路由，重新路由会走 LLM 路径
                // V0.2 简化：仅打日志，LLM 路由已在 ChainedRouter 中自然兜底
            }
            RecoveryAction.PROMPT_USER -> {
                Log.i(TAG, "recovery: prompt_user for skill=${spec.id}")
                // V0.3 实现：通过 UI 向用户展示错误和选项
            }
            RecoveryAction.RETRY -> {
                Log.i(TAG, "recovery: retry for skill=${spec.id} (V0.4+)")
            }
            RecoveryAction.ABORT -> {
                Log.i(TAG, "recovery: abort for skill=${spec.id}")
            }
        }
    }

    private fun logResult(skillId: String, r: SkillResult) {
        val tag = if (r.ok) "SUCCESS" else "FAIL"
        Log.i(TAG, "RUN_SKILL[$tag] \"$skillId\" steps=${r.stepsExecuted}/${r.totalSteps} dur=${r.durationMs}ms err=${r.error}")
        for (line in r.log) Log.i(TAG, "  $line")
    }

    // ---------------------------------------------------------------- 录制

    val isRecording: Boolean get() = recorder.isRecording

    fun startRecording() {
        recorder.startRecording()
        onStateChanged(OrchestratorState.RECORDING)
        Log.i(TAG, "recording started")
    }

    fun onRecordEvent(event: android.view.accessibility.AccessibilityEvent) {
        recorder.onAccessibilityEvent(event)
    }

    fun stopRecording(): SkillRecorder.RecordingResult {
        val result = recorder.stopRecording()
        onStateChanged(OrchestratorState.IDLE)
        Log.i(TAG, "recording stopped; ${result.steps.size} steps captured")
        return result
    }

    fun cancelRecording() {
        recorder.cancel()
        onStateChanged(OrchestratorState.IDLE)
        Log.i(TAG, "recording cancelled")
    }

    /** 将录制结果保存为 Skill：注册到内存 + 持久化到 SkillStore。 */
    fun saveRecordedSkill(result: SkillRecorder.RecordingResult, id: String, name: String, description: String? = null): SkillSpec {
        val spec = result.toSkillSpec(id, name, description)
        registerSkill(spec)
        skillStore?.save(spec)
        Log.i(TAG, "recorded skill saved: $id (${spec.steps.size} steps)")
        return spec
    }

    /** 从 SkillStore 加载用户录制的 Skill 到内存注册表。 */
    fun loadUserSkills() {
        val store = skillStore ?: return
        val specs = store.listAll()
        for (spec in specs) {
            skillRegistry[spec.id] = spec
            Log.i(TAG, "loaded user skill: ${spec.id} (${spec.steps.size} steps)")
        }
        Log.i(TAG, "loaded ${specs.size} user skills from store")
    }

    /** 删除用户 Skill：从内存 + SkillStore。 */
    fun deleteUserSkill(id: String) {
        skillRegistry.remove(id)
        skillStore?.delete(id)
        Log.i(TAG, "deleted user skill: $id")
    }

    companion object {
        const val TAG: String = "AgentOrchestrator"
    }
}
