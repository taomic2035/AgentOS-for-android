package com.taomic.agent.recorder

import android.content.Context
import android.util.Log
import com.taomic.agent.skill.dsl.SkillParser
import com.taomic.agent.skill.dsl.SkillSpec
import java.io.File

/**
 * 用户录制 Skill 的文件存储。
 *
 * 存储位置：`filesDir/skills/<id>.yaml`
 * - [save]：SkillSpec → YAML → 写文件
 * - [load]：读文件 → YAML → SkillSpec
 * - [listAll]：扫描目录返回所有已保存的 SkillSpec
 * - [delete]：按 id 删除文件
 *
 * @param directory 存储目录；默认使用 Context.filesDir/skills
 */
class SkillStore(directory: File? = null, context: Context? = null) {

    private val skillsDir: File = directory
        ?: File(requireNotNull(context).filesDir, DIR_NAME).also { it.mkdirs() }

    init {
        skillsDir.mkdirs()
    }

    fun save(spec: SkillSpec): Boolean {
        return try {
            val yaml = SkillParser.encode(spec)
            val file = File(skillsDir, "${spec.id}$SUFFIX")
            file.writeText(yaml)
            Log.i(TAG, "saved skill: ${spec.id} → ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "failed to save skill ${spec.id}: ${e.message}", e)
            false
        }
    }

    fun load(id: String): SkillSpec? {
        val file = File(skillsDir, "$id$SUFFIX")
        if (!file.exists()) return null
        return try {
            val yaml = file.readText()
            SkillParser.parse(yaml)
        } catch (e: Exception) {
            Log.e(TAG, "failed to load skill $id: ${e.message}", e)
            null
        }
    }

    fun listAll(): List<SkillSpec> {
        if (!skillsDir.exists()) return emptyList()
        return skillsDir.listFiles()
            ?.filter { it.name.endsWith(SUFFIX) }
            ?.mapNotNull { file ->
                try {
                    SkillParser.parse(file.readText())
                } catch (e: Exception) {
                    Log.w(TAG, "skipping corrupt skill file: ${file.name}")
                    null
                }
            }
            ?: emptyList()
    }

    fun delete(id: String): Boolean {
        val file = File(skillsDir, "$id$SUFFIX")
        val deleted = file.delete()
        if (deleted) Log.i(TAG, "deleted skill: $id")
        return deleted
    }

    fun exists(id: String): Boolean = File(skillsDir, "$id$SUFFIX").exists()

    companion object {
        const val TAG: String = "SkillStore"
        const val DIR_NAME: String = "skills"
        const val SUFFIX: String = ".yaml"
    }
}
