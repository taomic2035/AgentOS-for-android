package com.taomic.agent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.taomic.agent.a11y.A11yActionContext
import com.taomic.agent.a11y.AgentAccessibilityService
import com.taomic.agent.core.intent.ChainedIntentRouter
import com.taomic.agent.core.intent.IntentRouter
import com.taomic.agent.core.intent.KeywordIntentRouter
import com.taomic.agent.core.intent.LlmIntentRouter
import com.taomic.agent.core.intent.SkillDescriptor
import com.taomic.agent.orchestrator.AgentOrchestrator
import com.taomic.agent.skill.DefaultSkillRunner
import com.taomic.agent.skill.dsl.SkillSpec
import com.taomic.agent.ui.LlmConfigStore
import com.taomic.agent.uikit.floating.FloatingBubble

/**
 * 应用入口：进程启动时装配 [AgentOrchestrator] + [FloatingBubble]，
 * 注册 `RUN_SKILL` 广播驱动 adb 烟雾测试。
 *
 * V0.2：路由逻辑根据 LLM 配置动态选择——
 * - 未配 api_key → KeywordIntentRouter（仅关键词路由）
 * - 已配 api_key → ChainedIntentRouter(Keyword → LLM)
 */
class AgentApp : Application() {

    private lateinit var orchestrator: AgentOrchestrator
    private lateinit var llmConfigStore: LlmConfigStore
    private var bubble: FloatingBubble? = null

    private val runSkillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_RUN_SKILL) return
            val skillId = intent.getStringExtra(EXTRA_SKILL_ID).orEmpty()
            val title = intent.getStringExtra(EXTRA_INPUT_TITLE)
            val inputs: Map<String, Any?> = buildMap {
                if (title != null) put("title", title)
            }
            orchestrator.runSkillById(skillId, inputs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        llmConfigStore = LlmConfigStore(this)
        val actionContext = A11yActionContext(applicationContext)
        orchestrator = AgentOrchestrator(
            skillRunner = DefaultSkillRunner(actionContext),
            intentRouter = KeywordIntentRouter(),
            a11yController = AgentAccessibilityService.instance(),
        )
        orchestrator.loadBuiltinSkills(javaClass.classLoader!!)
        rebuildRouter()
        registerRunReceiver()
        Log.i(TAG, "AgentApp onCreate; skills=${orchestrator.skillIds} llm=${llmConfigStore.isConfigured}")
    }

    /** 根据配置重建路由器：有 LLM key 时用 ChainedRouter，否则仅关键词。 */
    fun rebuildRouter() {
        val keywordRouter = KeywordIntentRouter()
        if (!llmConfigStore.isConfigured) {
            Log.i(TAG, "LLM not configured; using KeywordIntentRouter only")
            orchestrator.intentRouter = keywordRouter
            return
        }
        val client = llmConfigStore.createClient()
        val descriptors = orchestrator.skillIds.map { SkillDescriptor(it, it) }
        val llmRouter = LlmIntentRouter(client, descriptors)
        orchestrator.intentRouter = ChainedIntentRouter(listOf(keywordRouter, llmRouter))
        Log.i(TAG, "LLM configured; using ChainedIntentRouter(Keyword → LLM)")
    }

    fun orchestrator(): AgentOrchestrator = orchestrator
    fun llmConfigStore(): LlmConfigStore = llmConfigStore

    fun showBubble() {
        if (bubble == null) {
            bubble = FloatingBubble(
                appContext = applicationContext,
                onIntent = { text ->
                    Log.i(TAG, "bubble intent → \"$text\"")
                    orchestrator.handleIntent(text)
                },
            )
        }
        bubble?.show()
    }

    fun hideBubble() {
        bubble?.hide()
    }

    fun isBubbleShown(): Boolean = bubble?.isShown() == true

    private fun registerRunReceiver() {
        val filter = IntentFilter(ACTION_RUN_SKILL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(runSkillReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(runSkillReceiver, filter)
        }
    }

    companion object {
        const val TAG: String = "AgentApp"
        const val ACTION_RUN_SKILL: String = "com.taomic.agent.RUN_SKILL"
        const val EXTRA_SKILL_ID: String = "skill_id"
        const val EXTRA_INPUT_TITLE: String = "input_title"
    }
}
