package com.taomic.agent.skill.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DSL 顶层结构。对应 docs/03-design/skill-dsl-spec.md。
 *
 * V0.1 子集：仅 launch_app / wait_node / click_node / input_text / press_key / sleep。
 * 高级特性（if / loop / assert / confirm_with_user / take_screenshot）在 V0.2+ 落地。
 */
@Serializable
data class SkillSpec(
    val id: String,
    val name: String,
    val version: Int = 1,
    val description: String? = null,
    val inputs: List<InputSpec> = emptyList(),
    val steps: List<Step>,
    val recovery: Recovery = Recovery(),
    val metadata: SkillMetadata? = null,
)

@Serializable
data class InputSpec(
    val name: String,
    val type: InputType = InputType.STRING,
    val required: Boolean = true,
    val default: String? = null,
)

@Serializable
enum class InputType {
    @SerialName("string") STRING,
    @SerialName("int") INT,
    @SerialName("bool") BOOL,
}

@Serializable
data class Recovery(
    @SerialName("on_node_not_found") val onNodeNotFound: RecoveryAction = RecoveryAction.PROMPT_USER,
    @SerialName("on_timeout") val onTimeout: RecoveryAction = RecoveryAction.ABORT,
    @SerialName("on_error") val onError: RecoveryAction = RecoveryAction.ABORT,
)

@Serializable
enum class RecoveryAction {
    @SerialName("fallback_to_llm") FALLBACK_TO_LLM,
    @SerialName("prompt_user") PROMPT_USER,
    @SerialName("retry") RETRY,
    @SerialName("abort") ABORT,
}

@Serializable
data class SkillMetadata(
    val author: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val tags: List<String> = emptyList(),
)
