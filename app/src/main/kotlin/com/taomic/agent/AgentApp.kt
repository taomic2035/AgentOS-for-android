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
import com.taomic.agent.core.intent.HabitCacheRouter
import com.taomic.agent.core.intent.HabitEventEntry
import com.taomic.agent.data.AgentDatabase
import com.taomic.agent.data.HabitRepository
import com.taomic.agent.recorder.SkillRecorder
import com.taomic.agent.recorder.SkillStore
import com.taomic.agent.core.intent.IntentRouter
import com.taomic.agent.core.intent.KeywordIntentRouter
import com.taomic.agent.core.intent.LlmIntentRouter
import com.taomic.agent.core.intent.SkillDescriptor
import com.taomic.agent.orchestrator.AgentOrchestrator
import com.taomic.agent.skill.DefaultSkillRunner
import com.taomic.agent.ui.LlmConfigStore
import com.taomic.agent.uikit.floating.AgentState
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
    private lateinit var skillStore: SkillStore
    private lateinit var habitRepository: HabitRepository
    private var bubble: FloatingBubble? = null
    private var lastRecordingResult: SkillRecorder.RecordingResult? = null

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
        skillStore = SkillStore(context = this)
        habitRepository = HabitRepository(AgentDatabase.getInstance(this).habitDao())
        val actionContext = A11yActionContext(applicationContext)
        orchestrator = AgentOrchestrator(
            skillRunner = DefaultSkillRunner(actionContext),
            intentRouter = KeywordIntentRouter(),
            a11yController = AgentAccessibilityService.instance(),
            skillStore = skillStore,
            habitRepository = habitRepository,
            onStateChanged = { state ->
                val bubbleState = when (state) {
                    AgentOrchestrator.OrchestratorState.IDLE -> AgentState.IDLE
                    AgentOrchestrator.OrchestratorState.THINKING -> AgentState.THINKING
                    AgentOrchestrator.OrchestratorState.EXECUTING -> AgentState.EXECUTING
                    AgentOrchestrator.OrchestratorState.RECORDING -> AgentState.RECORDING
                    AgentOrchestrator.OrchestratorState.DONE -> AgentState.DONE
                    AgentOrchestrator.OrchestratorState.ERROR -> AgentState.ERROR
                }
                bubble?.updateState(bubbleState)
            },
        )
        orchestrator.loadBuiltinSkills(javaClass.classLoader!!)
        orchestrator.loadUserSkills()
        rebuildRouter()
        registerRunReceiver()
        Log.i(TAG, "AgentApp onCreate; skills=${orchestrator.skillIds} llm=${llmConfigStore.isConfigured}")
    }

    /** 根据配置重建路由器：有 LLM key 时用 ChainedRouter，否则仅关键词。 */
    fun rebuildRouter() {
        val keywordRouter = KeywordIntentRouter()
        val habitRouter = HabitCacheRouter(
            getRecentEvents = {
                habitRepository.getRecentEvents(200)
                    .filter { it.result == "success" && it.skillId != null }
                    .map { HabitEventEntry(it.intentText, it.skillId!!) }
            },
            skillNameMap = orchestrator.skillIds.associateWith { id ->
                orchestrator.findSkill(id)?.name ?: id
            },
        )
        if (!llmConfigStore.isConfigured) {
            Log.i(TAG, "LLM not configured; using Keyword + HabitCache router")
            orchestrator.intentRouter = ChainedIntentRouter(listOf(keywordRouter, habitRouter))
            return
        }
        val client = llmConfigStore.createClient()
        val descriptors = orchestrator.skillIds.map { SkillDescriptor(it, it) }
        val llmRouter = LlmIntentRouter(client, descriptors)
        orchestrator.intentRouter = ChainedIntentRouter(listOf(keywordRouter, habitRouter, llmRouter))
        Log.i(TAG, "LLM configured; using ChainedRouter(Keyword → HabitCache → LLM)")
    }

    fun orchestrator(): AgentOrchestrator = orchestrator
    fun llmConfigStore(): LlmConfigStore = llmConfigStore

    /** 开始录制模式：设置 eventCallback 将事件转发给 orchestrator。 */
    fun startRecording() {
        AgentAccessibilityService.instance()?.eventCallback = { event ->
            orchestrator.onRecordEvent(event)
        }
        orchestrator.startRecording()
    }

    /** 停止录制：清除 eventCallback，返回录制结果。 */
    fun stopRecording(): SkillRecorder.RecordingResult {
        AgentAccessibilityService.instance()?.eventCallback = null
        val result = orchestrator.stopRecording()
        lastRecordingResult = result
        // 将步骤描述传给浮窗
        val stepDescriptions = result.steps.map { step ->
            when (step) {
                is com.taomic.agent.skill.dsl.Step.LaunchApp -> "启动 ${step.packageName}"
                is com.taomic.agent.skill.dsl.Step.ClickNode -> buildString {
                    append("点击 ")
                    append(step.text ?: step.resourceId ?: step.desc ?: "?")
                }
                is com.taomic.agent.skill.dsl.Step.InputText -> "输入 \"${step.text}\""
                is com.taomic.agent.skill.dsl.Step.WaitNode -> buildString {
                    append("等待 ")
                    append(step.text ?: step.resourceId ?: step.className ?: "?")
                }
                is com.taomic.agent.skill.dsl.Step.PressKey -> "按 ${step.key}"
                is com.taomic.agent.skill.dsl.Step.Sleep -> "等待 ${step.ms}ms"
            }
        }
        bubble?.updateRecordedSteps(stepDescriptions)
        return result
    }

    /** 取消录制。 */
    fun cancelRecording() {
        AgentAccessibilityService.instance()?.eventCallback = null
        orchestrator.cancelRecording()
    }

    fun showBubble() {
        if (bubble == null) {
            bubble = FloatingBubble(
                appContext = applicationContext,
                onIntent = { text ->
                    Log.i(TAG, "bubble intent → \"$text\"")
                    orchestrator.handleIntent(text)
                },
                onStartRecording = { startRecording() },
                onStopRecording = { stopRecording() },
                onCancelRecording = { cancelRecording() },
                onSaveRecordedSkill = { id, name, desc ->
                    val result = lastRecordingResult ?: return@FloatingBubble
                    orchestrator.saveRecordedSkill(result, id, name, desc)
                    lastRecordingResult = null
                    bubble?.clearRecordedSteps()
                },
                onDiscardRecording = {
                    lastRecordingResult = null
                    bubble?.clearRecordedSteps()
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
