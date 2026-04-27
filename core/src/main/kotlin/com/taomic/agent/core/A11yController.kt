package com.taomic.agent.core

import com.taomic.agent.core.action.ActionOutcome
import com.taomic.agent.core.action.NodeQuery

/**
 * Core ↔ a11y 模块的完整契约。
 *
 * a11y 模块负责实现，core / llm / skill 模块通过此接口访问 a11y 能力，
 * 避免直接依赖 Android API 或 AgentAccessibilityService.instance()。
 *
 * V0.2 扩充：增加 findNode / clickNode / inputText / dumpScreen 等方法，
 * LLM 需要通过 dumpScreen 获取屏幕语义摘要。
 */
interface A11yController {
    /** 暴露当前是否已就绪（系统设置中已开启 AccessibilityService）。 */
    val isReady: Boolean

    /** 在当前活动窗口查找匹配 [query] 的节点。 */
    fun findNode(query: NodeQuery): Any?

    /** 点击匹配 [query] 的节点（找最近 clickable 祖先）。 */
    fun clickNode(query: NodeQuery): Boolean

    /** 在匹配 [target] 的节点上输入 [text]；[clearFirst] 时先清空。 */
    fun inputText(target: NodeQuery, text: String, clearFirst: Boolean): Boolean

    /** 获取当前活动窗口包名。 */
    fun activeWindowPackage(): String?

    /** 输出当前屏幕节点树的精简文本摘要，供 LLM 消费。 */
    fun dumpScreen(): String
}
