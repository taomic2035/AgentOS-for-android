package com.taomic.agent.core.action

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 屏幕节点查询条件。
 *
 * 字段间是 AND 关系；至少必须填一项有意义的查询条件（[isEmpty] 为 true 时调用方应拒绝）。
 *
 * 优先级建议（强 → 弱）：
 *   resourceId > desc > text 精确 > containsText > className+index
 */
@Serializable
data class NodeQuery(
    val text: String? = null,
    @SerialName("contains_text") val containsText: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    val desc: String? = null,
    @SerialName("class_name") val className: String? = null,
    val index: Int = 0,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() &&
            containsText.isNullOrBlank() &&
            resourceId.isNullOrBlank() &&
            desc.isNullOrBlank() &&
            className.isNullOrBlank()
}

/**
 * 全局键。
 *
 * 前 4 个对应 AccessibilityService.performGlobalAction；ENTER 需要 IME 通路（V0.1 占位，
 * 运行层返回 [ActionOutcome.NotImplemented]）。
 */
@Serializable
enum class GlobalKey {
    @SerialName("BACK") BACK,
    @SerialName("HOME") HOME,
    @SerialName("RECENTS") RECENTS,
    @SerialName("NOTIFICATIONS") NOTIFICATIONS,
    @SerialName("ENTER") ENTER,
}
