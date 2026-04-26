package com.taomic.agent.skill

import com.taomic.agent.skill.dsl.SkillSpec

/**
 * Skill 执行入口。
 *
 * 接口签名按 V0.2 LLM `tool_call` 契约设计：
 *   { "name": "run_skill", "arguments": { "skill_id": "...", "inputs": {...} } }
 * 直接对应 `runById(skillId, inputs)`；接 LLM 时零适配。
 *
 * V0.1 仅 [run] 一种入口（直接传 SkillSpec）。`runById` 由后续 SkillRegistry 引入。
 */
interface SkillRunner {
    suspend fun run(spec: SkillSpec, inputs: Map<String, Any?> = emptyMap()): SkillResult
}

data class SkillResult(
    val ok: Boolean,
    val stepsExecuted: Int,
    val totalSteps: Int,
    val durationMs: Long,
    val error: SkillError? = null,
    val log: List<String> = emptyList(),
)

sealed class SkillError {
    data class StepFailed(val stepIndex: Int, val action: String, val reason: String) : SkillError()
    data class Timeout(val stepIndex: Int, val waitedMs: Long) : SkillError()
    data class MissingInput(val name: String) : SkillError()
    data class TemplateError(val expr: String, val reason: String) : SkillError()
    data object Cancelled : SkillError()
}
