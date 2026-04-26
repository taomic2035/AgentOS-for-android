package com.taomic.agent.core.intent

/**
 * 意图路由的契约。把用户输入文字（来自浮窗 / ASR / 命令行）映射为
 *   - [Hit]：命中已注册的 Skill，附带可填的 inputs；调用方直接交给 SkillRunner
 *   - [Miss]：没匹配到任何 Skill；调用方通常会展示提示，或交给 V0.2 的 LLM Planner
 *
 * 抽象成接口便于在 V0.2 用 LLM 的 router 替换或链式叠加：
 *   ChainedIntentRouter(KeywordIntentRouter(), LlmIntentRouter(...))
 *   先关键词命中（节省 token），不命中再走 LLM。
 */
interface IntentRouter {
    fun route(text: String): RouteResult
}

sealed class RouteResult {
    data class Hit(val skillId: String, val inputs: Map<String, Any?> = emptyMap()) : RouteResult()
    data class Miss(val text: String) : RouteResult()
}
