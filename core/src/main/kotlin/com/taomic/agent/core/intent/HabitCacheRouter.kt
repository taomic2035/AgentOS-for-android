package com.taomic.agent.core.intent

/**
 * 基于习惯事件的 Skill 缓存命中路由。
 *
 * 原理：用户输入 → 从历史交互中查找相同/相似意图命中的 Skill → 直接执行。
 *
 * V0.5 实现策略（轻量，无需 embedding 模型）：
 * 1. 精确匹配：intent_text 完全一致 → 命中
 * 2. 关键词匹配：输入包含 Skill 名称/描述中的关键词 → 命中
 * 3. 频率优先：多个候选时按历史使用频率排序
 *
 * V0.8 集成端侧 embedding 后替换为语义相似度匹配。
 *
 * @param getRecentEvents 获取近期成功事件（解耦 :core 与 :data）
 * @param skillNameMap skillId → name 映射
 */
class HabitCacheRouter(
    private val getRecentEvents: suspend () -> List<HabitEventEntry>,
    private val skillNameMap: Map<String, String>,
) : IntentRouter {

    override suspend fun route(text: String): RouteResult {
        val events = getRecentEvents()

        // 1) 精确匹配
        val exactMatch = events.find { it.intentText.equals(text, ignoreCase = true) }
        if (exactMatch != null) {
            return RouteResult.Hit(skillId = exactMatch.skillId, inputs = emptyMap())
        }

        // 2) 关键词匹配：输入包含 Skill 名称
        val candidates = mutableMapOf<String, Int>()
        for ((skillId, name) in skillNameMap) {
            if (text.contains(name, ignoreCase = true) || name.contains(text, ignoreCase = true)) {
                val freq = events.count { it.skillId == skillId }
                candidates[skillId] = freq
            }
        }

        // 3) 历史意图关键词重叠
        val inputWords = tokenize(text)
        for (event in events) {
            val eventWords = tokenize(event.intentText)
            val overlap = inputWords.intersect(eventWords).size
            if (overlap >= 2) {
                candidates[event.skillId] = (candidates[event.skillId] ?: 0) + overlap
            }
        }

        if (candidates.isNotEmpty()) {
            val best = candidates.maxByOrNull { it.value }
            if (best != null) {
                return RouteResult.Hit(skillId = best.key, inputs = emptyMap())
            }
        }

        return RouteResult.Miss(text)
    }

    private fun tokenize(text: String): Set<String> {
        // 对中文：按 2-gram 滑动窗口分词；对英文：按空格分词
        val result = mutableSetOf<String>()
        // 空格分词（英文）
        text.split(Regex("[\\s，。！？、：；\"']"))
            .filter { it.length >= 2 }
            .forEach { result.add(it.lowercase()) }
        // 字符 bigram（中文友好）
        val chars = text.filter { !it.isWhitespace() }
        for (i in 0 until chars.length - 1) {
            result.add("${chars[i]}${chars[i + 1]}".lowercase())
        }
        return result
    }
}

/** 习惯事件精简数据，供 HabitCacheRouter 消费（解耦 :core 与 :data）。 */
data class HabitEventEntry(
    val intentText: String,
    val skillId: String,
)
