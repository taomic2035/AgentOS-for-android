package com.taomic.agent.core

/**
 * Agent Core 入口（占位）。
 *
 * V0.1 阶段：仅暴露公共类型与接口骨架，便于其他模块引用编译。
 * 后续在此引入：
 *  - IntentRouter
 *  - Planner（LLM tool-use 编排）
 *  - SkillResolver
 *  - ActionExecutor
 *  - Memory / Habit / Preference 接口契约
 */
object AgentCore {
    const val VERSION: String = "0.3.0"
}
