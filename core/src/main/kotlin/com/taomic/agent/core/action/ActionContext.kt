package com.taomic.agent.core.action

/**
 * SkillRunner 执行 Step 时使用的运行时能力。
 *
 * 实现者：[com.taomic.agent.a11y.A11yActionContext]（基于 AgentAccessibilityService）。
 * 单元测试：用 mock 实现替换。
 *
 * 所有方法都是 suspend，便于在协程作用域内串行执行 + 支持取消。
 */
interface ActionContext {

    /** 通过包名（可选 deep link uri）拉起目标 App。 */
    suspend fun launchApp(packageName: String, uri: String? = null): ActionOutcome

    /** 轮询直到 [query] 在当前活动窗口里命中或超时。 */
    suspend fun waitNode(query: NodeQuery, timeoutMs: Long): ActionOutcome

    /** 点击命中的节点；若节点本身不可点击，沿祖先向上找最近 clickable。 */
    suspend fun clickNode(query: NodeQuery): ActionOutcome

    /** 在 [target] 命中的输入框中写文本。[clearFirst]=true 时先清空。 */
    suspend fun inputText(target: NodeQuery, text: String, clearFirst: Boolean = true): ActionOutcome

    /** 全局按键。ENTER 在 V0.1 返回 [ActionOutcome.NotImplemented]。 */
    suspend fun pressKey(key: GlobalKey): ActionOutcome

    /** 等待固定时长（用于不得不等动画的场景，业务上应少用）。 */
    suspend fun sleep(ms: Long): ActionOutcome
}

/** 单步动作结果。 */
sealed class ActionOutcome {
    /** 成功执行；可能附带一段诊断信息。 */
    data class Success(val detail: String = "") : ActionOutcome()

    /** 业务失败（例如节点找不到、点击未生效）。 */
    data class Failed(val reason: String) : ActionOutcome()

    /** 超时（waitNode / launchApp 等带 timeout 的动作）。 */
    data class Timeout(val waitedMs: Long) : ActionOutcome()

    /** V0.1 未实现的能力（例如 ENTER 键）。 */
    data class NotImplemented(val what: String) : ActionOutcome()

    val ok: Boolean get() = this is Success
}
