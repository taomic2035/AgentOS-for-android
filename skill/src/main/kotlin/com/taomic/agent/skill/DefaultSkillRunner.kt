package com.taomic.agent.skill

import com.taomic.agent.core.action.ActionContext
import com.taomic.agent.core.action.ActionOutcome
import com.taomic.agent.core.action.NodeQuery
import com.taomic.agent.skill.dsl.SkillSpec
import com.taomic.agent.skill.dsl.Step

/**
 * 解释器实现：把 [SkillSpec] 中的 [Step] 序列依次派发到 [ctx]。
 *
 * 设计要点：
 * - 单线程串行（无并发分支）；调用方通过取消协程作用域来中止
 * - 模板替换在执行前对每个字符串字段做（`${name}` → inputs[name]）
 * - 任一 step 返回非 Success 立即终止并打包 [SkillResult]
 * - 不内置 recovery 策略（V0.1 简化；V0.2 接 LLM 时再加 fallback_to_llm 等）
 */
class DefaultSkillRunner(
    private val ctx: ActionContext,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : SkillRunner {

    override suspend fun run(spec: SkillSpec, inputs: Map<String, Any?>): SkillResult {
        val started = nowMs()
        val log = mutableListOf<String>()

        // 1) 校验 inputs（缺必填且无默认值即失败）
        spec.inputs.firstOrNull { it.required && inputs[it.name] == null && it.default == null }?.let {
            return fail(0, spec.steps.size, started, log, SkillError.MissingInput(it.name))
        }
        val resolved: Map<String, Any?> = buildMap {
            for (input in spec.inputs) {
                put(input.name, inputs[input.name] ?: input.default)
            }
            // 允许调用方传 spec 没声明的额外变量（不强制；便于 V0.4 录制器扩展）
            for ((k, v) in inputs) if (!containsKey(k)) put(k, v)
        }

        // 2) 顺序执行
        for ((index, step) in spec.steps.withIndex()) {
            val outcome = try {
                executeStep(step, resolved)
            } catch (e: TemplateException) {
                return fail(index, spec.steps.size, started, log, SkillError.TemplateError(e.expr, e.message ?: "?"))
            }
            log += "step[$index] ${step.javaClass.simpleName} → $outcome"
            when (outcome) {
                is ActionOutcome.Success -> Unit // continue
                is ActionOutcome.Timeout ->
                    return fail(index, spec.steps.size, started, log, SkillError.Timeout(index, outcome.waitedMs))
                is ActionOutcome.Failed ->
                    return fail(index, spec.steps.size, started, log, SkillError.StepFailed(index, step.javaClass.simpleName, outcome.reason))
                is ActionOutcome.NotImplemented ->
                    return fail(index, spec.steps.size, started, log, SkillError.StepFailed(index, step.javaClass.simpleName, "not implemented: ${outcome.what}"))
            }
        }

        return SkillResult(
            ok = true,
            stepsExecuted = spec.steps.size,
            totalSteps = spec.steps.size,
            durationMs = nowMs() - started,
            log = log,
        )
    }

    private suspend fun executeStep(step: Step, inputs: Map<String, Any?>): ActionOutcome = when (step) {
        is Step.LaunchApp -> ctx.launchApp(
            packageName = step.packageName.t(inputs),
            uri = step.uri?.t(inputs),
        )
        is Step.WaitNode -> ctx.waitNode(step.toQuery().t(inputs), step.timeoutMs)
        is Step.ClickNode -> ctx.clickNode(step.toQuery().t(inputs))
        is Step.InputText -> ctx.inputText(step.target.t(inputs), step.text.t(inputs), step.clearFirst)
        is Step.PressKey -> ctx.pressKey(step.key)
        is Step.Sleep -> ctx.sleep(step.ms)
    }

    private fun fail(
        stepsExecuted: Int,
        totalSteps: Int,
        started: Long,
        log: List<String>,
        error: SkillError,
    ): SkillResult = SkillResult(
        ok = false,
        stepsExecuted = stepsExecuted,
        totalSteps = totalSteps,
        durationMs = nowMs() - started,
        error = error,
        log = log,
    )

    private fun String.t(inputs: Map<String, Any?>): String =
        TEMPLATE_REGEX.replace(this) { match ->
            val name = match.groupValues[1]
            val value = inputs[name]
                ?: throw TemplateException(match.value, "unbound variable: $name")
            value.toString()
        }

    private fun NodeQuery.t(inputs: Map<String, Any?>): NodeQuery = copy(
        text = text?.t(inputs),
        containsText = containsText?.t(inputs),
        resourceId = resourceId?.t(inputs),
        desc = desc?.t(inputs),
        className = className?.t(inputs),
    )

    private class TemplateException(val expr: String, message: String) : RuntimeException(message)

    companion object {
        private val TEMPLATE_REGEX = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_]*)}""")
    }
}
