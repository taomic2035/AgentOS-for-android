package com.taomic.agent.skill.dsl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * 节点查询条件。
 *
 * 优先级（强 → 弱）：resourceId > desc > text 精确 > containsText > className+index。
 * 至少必须填一项；同时填多项时 AND 关系。
 */
@Serializable
data class NodeQuery(
    val text: String? = null,
    @SerialName("contains_text") val containsText: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    val desc: String? = null,
    @SerialName("class_name") val className: String? = null,
    val index: Int = 0,
)

/** 全局按键（performGlobalAction 支持的子集 + 占位 ENTER）。 */
@Serializable
enum class GlobalKey {
    @SerialName("BACK") BACK,
    @SerialName("HOME") HOME,
    @SerialName("RECENTS") RECENTS,
    @SerialName("NOTIFICATIONS") NOTIFICATIONS,

    /** ENTER 需绕过 globalAction（V0.1 占位，运行层返回 NotImplemented）。 */
    @SerialName("ENTER") ENTER,
}

/**
 * 单步动作。`action` 字段作 polymorphic discriminator。
 *
 * V0.1 必须的 6 种：launch_app / wait_node / click_node / input_text / press_key / sleep
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("action")
@Serializable
sealed interface Step {

    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(
        @SerialName("package") val packageName: String,
        val uri: String? = null,
    ) : Step

    @Serializable
    @SerialName("wait_node")
    data class WaitNode(
        val text: String? = null,
        @SerialName("contains_text") val containsText: String? = null,
        @SerialName("resource_id") val resourceId: String? = null,
        val desc: String? = null,
        @SerialName("class_name") val className: String? = null,
        @SerialName("timeout_ms") val timeoutMs: Long = 3000,
    ) : Step {
        fun toQuery(): NodeQuery = NodeQuery(
            text = text,
            containsText = containsText,
            resourceId = resourceId,
            desc = desc,
            className = className,
        )
    }

    @Serializable
    @SerialName("click_node")
    data class ClickNode(
        val text: String? = null,
        @SerialName("contains_text") val containsText: String? = null,
        @SerialName("resource_id") val resourceId: String? = null,
        val desc: String? = null,
        @SerialName("class_name") val className: String? = null,
        val index: Int = 0,
    ) : Step {
        fun toQuery(): NodeQuery = NodeQuery(
            text = text,
            containsText = containsText,
            resourceId = resourceId,
            desc = desc,
            className = className,
            index = index,
        )
    }

    @Serializable
    @SerialName("input_text")
    data class InputText(
        val target: NodeQuery,
        val text: String,
        @SerialName("clear_first") val clearFirst: Boolean = true,
    ) : Step

    @Serializable
    @SerialName("press_key")
    data class PressKey(
        val key: GlobalKey,
    ) : Step

    @Serializable
    @SerialName("sleep")
    data class Sleep(
        val ms: Long,
    ) : Step
}
