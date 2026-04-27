package com.taomic.agent.core.intent

/**
 * 意图路由的契约。把用户输入文字（来自浮窗 / ASR / 命令行）映射为
 *   - [Hit]：命中已注册的 Skill，附带可填的 inputs；调用方直接交给 SkillRunner
 *   - [Miss]：没匹配到任何 Skill；调用方通常会展示提示，或交给 LLM Planner
 *
 * V0.2 起 route 为 suspend —— LLM 路由需要网络调用。
 * [ChainedIntentRouter] 按顺序调用多个 router，第一个 Hit 即返回，
 * 典型链路：KeywordIntentRouter（零 token）→ LlmIntentRouter（LLM 兜底）。
 */
interface IntentRouter {
    suspend fun route(text: String): RouteResult
}

sealed class RouteResult {
    data class Hit(val skillId: String, val inputs: Map<String, Any?> = emptyMap()) : RouteResult()
    data class Miss(val text: String) : RouteResult()
}

/**
 * 链式路由：按 [routers] 顺序依次调用，第一个 Hit 即返回；
 * 全部 Miss 则返回最后一个 Miss。
 */
class ChainedIntentRouter(private val routers: List<IntentRouter>) : IntentRouter {
    override suspend fun route(text: String): RouteResult {
        var lastMiss: RouteResult.Miss? = null
        for (router in routers) {
            when (val result = router.route(text)) {
                is RouteResult.Hit -> return result
                is RouteResult.Miss -> { lastMiss = result }
            }
        }
        return lastMiss ?: RouteResult.Miss(text)
    }
}
