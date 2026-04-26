package com.taomic.agent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.taomic.agent.a11y.A11yActionContext
import com.taomic.agent.core.intent.IntentRouter
import com.taomic.agent.core.intent.KeywordIntentRouter
import com.taomic.agent.core.intent.RouteResult
import com.taomic.agent.skill.DefaultSkillRunner
import com.taomic.agent.skill.SkillResult
import com.taomic.agent.skill.SkillRunner
import com.taomic.agent.skill.dsl.SkillParser
import com.taomic.agent.skill.dsl.SkillSpec
import com.taomic.agent.uikit.floating.FloatingBubble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * V0.1 装配点：进程启动时初始化 SkillRunner + ActionContext + 内置 Skill 注册表，
 * 暴露 [runSkillById] 给 UI 直接调用，并监听 `RUN_SKILL` 广播驱动 adb 烟雾测试：
 *
 *   adb shell am broadcast -a com.taomic.agent.RUN_SKILL \
 *     --es skill_id settings_open_internet
 *
 * 浮窗 [FloatingBubble] 由本类持有；T-005 期间通过 [showBubble] / [hideBubble] 由
 * MainActivity 触发。T-004 后 AgentForegroundService 接管整个生命周期，本类回归
 * 仅装配单例的角色。
 */
class AgentApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var actionContext: A11yActionContext
    private lateinit var skillRunner: SkillRunner
    private val intentRouter: IntentRouter = KeywordIntentRouter()
    private val skillRegistry = mutableMapOf<String, SkillSpec>()
    private var bubble: FloatingBubble? = null

    private val runSkillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_RUN_SKILL) return
            val skillId = intent.getStringExtra(EXTRA_SKILL_ID).orEmpty()
            val title = intent.getStringExtra(EXTRA_INPUT_TITLE)
            val inputs: Map<String, Any?> = buildMap {
                if (title != null) put("title", title)
            }
            runSkillById(skillId, inputs)
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

    // ---------------------------------------------------------------- 公共 facade

    /** 由 UI（浮窗 / 设置页 / 引导页）直接调用，避免广播开销。 */
    fun runSkillById(skillId: String, inputs: Map<String, Any?> = emptyMap()) {
        val spec = skillRegistry[skillId]
        if (spec == null) {
            Log.w(TAG, "runSkillById: unknown skill_id=\"$skillId\"; available=${skillRegistry.keys}")
            return
        }
        Log.i(TAG, "runSkillById: \"$skillId\" inputs=$inputs")
        appScope.launch {
            val result = skillRunner.run(spec, inputs)
            logResult(skillId, result)
        }
    }

    fun showBubble() {
        if (bubble == null) {
            bubble = FloatingBubble(
                appContext = applicationContext,
                onIntent = { text ->
                    Log.i(TAG, "bubble intent → \"$text\"")
                    handleIntent(text)
                },
            )
        }
        bubble?.show()
    }

    fun hideBubble() {
        bubble?.hide()
    }

    fun isBubbleShown(): Boolean = bubble?.isShown() == true

    /**
     * V0.1b 入口：浮窗 chip 或 adb 都可调；通过 [intentRouter] 决定 skillId+inputs。
     * V0.2 接 LLM 时把 [intentRouter] 替换为 ChainedIntentRouter(Keyword, Llm)。
     */
    fun handleIntent(text: String) {
        when (val r = intentRouter.route(text)) {
            is RouteResult.Hit -> {
                Log.i(TAG, "router hit: \"$text\" → skill=${r.skillId} inputs=${r.inputs}")
                runSkillById(r.skillId, r.inputs)
            }
            is RouteResult.Miss -> {
                Log.w(TAG, "router miss: \"$text\" (V0.1b 仅支持网络 / 三体相关意图；V0.2 接 LLM 后兜底)")
            }
        }
    }

    // ---------------------------------------------------------------- 私有装配

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
