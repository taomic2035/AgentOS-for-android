package com.taomic.agent.skill.dsl

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.SerializationException

/**
 * Skill DSL 解析与序列化。基于 kaml + kotlinx.serialization。
 *
 * - YAML → SkillSpec：[parse]
 * - SkillSpec → YAML：[encode]（V0.4 录制器导出用）
 *
 * 失败抛 [SkillParseException]，由调用方决定是回退还是提示用户。
 */
object SkillParser {

    private val yaml: Yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false, // 允许未知字段，便于 DSL 向后兼容
            polymorphismStyle = PolymorphismStyle.Property,
            polymorphismPropertyName = "action",
        ),
    )

    fun parse(source: String): SkillSpec = try {
        yaml.decodeFromString(SkillSpec.serializer(), source)
    } catch (e: YamlException) {
        throw SkillParseException("YAML parse error: ${e.message} @ ${e.path}", cause = e)
    } catch (e: SerializationException) {
        throw SkillParseException("DSL schema error: ${e.message}", cause = e)
    }

    fun encode(spec: SkillSpec): String =
        yaml.encodeToString(SkillSpec.serializer(), spec)
}

class SkillParseException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
