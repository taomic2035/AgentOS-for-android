package com.taomic.agent.skill.dsl

import com.taomic.agent.core.action.GlobalKey
import com.taomic.agent.core.action.NodeQuery
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * 单步动作。`action` 字段作 polymorphic discriminator。
 *
 * V0.1 必须的 6 种：launch_app / wait_node / click_node / input_text / press_key / sleep
 *
 * NodeQuery 与 GlobalKey 来自 :core (com.taomic.agent.core.action)，
 * 这样 :a11y 模块实现 ActionContext 时无需依赖 :skill。
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
