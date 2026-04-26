package com.taomic.agent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.taomic.agent.a11y.A11yActionContext
import com.taomic.agent.skill.DefaultSkillRunner
import com.taomic.agent.skill.SkillResult
import com.taomic.agent.skill.SkillRunner
import com.taomic.agent.skill.dsl.SkillParser
import com.taomic.agent.skill.dsl.SkillSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * V0.1 装配点：进程启动时初始化 SkillRunner + ActionContext + 内置 Skill 注册表，
 * 监听 `RUN_SKILL` 广播以驱动端到端烟雾测试：
 *
 *   adb shell am broadcast -a com.taomic.agent.RUN_SKILL \
 *     --es skill_id settings_open_internet
 *
 * V0.4 起，AgentForegroundService 接管这些职责（Application 仅做最小初始化）。
 * 当前阶段把广播订阅放 Application 是为了让 V0.1 端到端最早可演示，不阻塞 T-004。
 */
class AgentApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var actionContext: A11yActionContext
    private lateinit var skillRunner: SkillRunner
    private val skillRegistry = mutableMapOf<String, SkillSpec>()

    private val runSkillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_RUN_SKILL) return
            val skillId = intent.getStringExtra(EXTRA_SKILL_ID).orEmpty()
            val title = intent.getStringExtra(EXTRA_INPUT_TITLE)
            val inputs: Map<String, Any?> = buildMap {
                if (title != null) put("title", title)
            }
            val spec = skillRegistry[skillId]
            if (spec == null) {
                Log.w(TAG, "RUN_SKILL: unknown skill_id=\"$skillId\"; available=${skillRegistry.keys}")
                return
            }
            Log.i(TAG, "RUN_SKILL: \"$skillId\" inputs=$inputs")
            appScope.launch {
                val result = skillRunner.run(spec, inputs)
                logResult(skillId, result)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        actionContext = A11yActionContext(applicationContext)
        skillRunner = DefaultSkillRunner(actionContext)
        loadBuiltinSkills()
        registerRunReceiver()
        Log.i(TAG, "AgentApp onCreate; registered ${skillRegistry.size} skill(s): ${skillRegistry.keys}")
    }

    private fun loadBuiltinSkills() {
        // :skill 模块的 src/main/resources/skills/*.yaml 会打包进 APK 的 java-resources
        val builtins = listOf(
            "skills/settings_open_internet.yaml",
            "skills/tencent_video_play.yaml",
        )
        for (path in builtins) {
            try {
                val stream = javaClass.classLoader?.getResourceAsStream(path)
                if (stream == null) {
                    Log.w(TAG, "builtin skill not found on classpath: $path")
                    continue
                }
                val yaml = stream.bufferedReader().use { it.readText() }
                val spec = SkillParser.parse(yaml)
                skillRegistry[spec.id] = spec
                Log.i(TAG, "loaded skill: ${spec.id} (${spec.steps.size} steps)")
            } catch (e: Throwable) {
                Log.e(TAG, "failed to load $path: ${e.message}", e)
            }
        }
    }

    private fun registerRunReceiver() {
        val filter = IntentFilter(ACTION_RUN_SKILL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(runSkillReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(runSkillReceiver, filter)
        }
    }

    private fun logResult(skillId: String, r: SkillResult) {
        val tag = if (r.ok) "SUCCESS" else "FAIL"
        Log.i(TAG, "RUN_SKILL[$tag] \"$skillId\" steps=${r.stepsExecuted}/${r.totalSteps} dur=${r.durationMs}ms err=${r.error}")
        for (line in r.log) Log.i(TAG, "  $line")
    }

    companion object {
        const val TAG: String = "AgentApp"
        const val ACTION_RUN_SKILL: String = "com.taomic.agent.RUN_SKILL"
        const val EXTRA_SKILL_ID: String = "skill_id"
        const val EXTRA_INPUT_TITLE: String = "input_title"
    }
}
