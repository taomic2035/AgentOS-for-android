package com.taomic.agent.core.intent

/**
 * V0.1b 关键词路由：硬编码两个内置 skill 的触发词。
 *
 * 这是 V0.2 IntentRouter 的雏形 —— V0.2 会引入 [LlmIntentRouter]，并通过
 * ChainedIntentRouter 把本类放在前面：先 token-free 命中，未命中再走 LLM。
 *
 * 命中规则（不区分大小写，子串匹配）：
 *  - 含 "网络" / "internet" / "wifi" / "wi-fi" → `settings_open_internet`
 *  - 含 "三体" / "视频" / "看 X" → `stub_video_play` with title=（"三体" 或 X）
 *  - 其他 → [RouteResult.Miss]
 *
 * V0.6 上真机时把"看 X" 的目标改为 `tencent_video_play`（或链式路由先尝试腾讯视频
 * 未装时降级到 stub）。V0.1 阶段统一指向 `stub_video_play` 让 e2e 不依赖三方 App。
 */
class KeywordIntentRouter : IntentRouter {
    override suspend fun route(text: String): RouteResult {
        val normalized = text.trim()
        if (normalized.isEmpty()) return RouteResult.Miss(text)

        val lower = normalized.lowercase()
        if (NETWORK_KEYWORDS.any { it in lower } || NETWORK_CN.any { it in normalized }) {
            return RouteResult.Hit("settings_open_internet")
        }
        if (VIDEO_CN.any { it in normalized } ||
            lower.contains("video") ||
            WATCH_PATTERN.containsMatchIn(normalized)
        ) {
            val title = extractTitle(normalized)
            return RouteResult.Hit("stub_video_play", mapOf("title" to title))
        }
        return RouteResult.Miss(text)
    }

    private fun extractTitle(text: String): String {
        // 命中"三体"则直接用，避免误把"看三体最新一集"切成"三体最新一集"
        if ("三体" in text) return "三体"
        // "看 X" / "看X"：取"看"之后的剩余字符作 title
        val matched = WATCH_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!matched.isNullOrEmpty()) return matched
        // 否则原文兜底
        return text
    }

    companion object {
        private val NETWORK_KEYWORDS = setOf("internet", "wifi", "wi-fi")
        private val NETWORK_CN = setOf("网络", "联网", "上网")
        private val VIDEO_CN = setOf("三体", "视频", "看剧", "电视剧")
        private val WATCH_PATTERN = Regex("""看[\s]*(.+)""")
    }
}
