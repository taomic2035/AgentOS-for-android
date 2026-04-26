package com.taomic.agent.a11y

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.taomic.agent.core.A11yController
import com.taomic.agent.core.action.NodeQuery

/**
 * V0.1 AccessibilityService 实现：
 *
 *  - onAccessibilityEvent 仅在窗口包名变化时打日志（噪音控制）
 *  - 提供广播触发入口（adb 自动化 / 内部模块均可用）：
 *      adb shell am broadcast -a com.taomic.agent.a11y.CLICK_BY_TEXT --es text "Settings"
 *      adb shell am broadcast -a com.taomic.agent.a11y.DUMP_WINDOW
 *  - 暴露 [findNode] / [clickNode] / [inputText] 给 [A11yActionContext]
 *  - 实现 [A11yController]，由静态 [instance] 暴露给业务层
 *
 * V0.6+ 将抽出独立的 NodeFinder / ClickStrategy / InputStrategy 类，本类仅做事件分发。
 */
class AgentAccessibilityService : AccessibilityService(), A11yController {

    @Volatile
    private var lastWindowPackage: String? = null

    private val triggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CLICK_BY_TEXT -> {
                    val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                    val ok = clickByText(text)
                    Log.i(TAG, "broadcast CLICK_BY_TEXT text=\"$text\" ok=$ok")
                }
                ACTION_DUMP_WINDOW -> {
                    Log.i(TAG, "broadcast DUMP_WINDOW pkg=${activeWindowPackage()}")
                    dumpActiveWindow()
                }
            }
        }
    }

    override val isReady: Boolean
        get() = serviceInfo != null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerTriggerReceiver()
        Log.i(TAG, "service connected; ready=$isReady")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != null && pkg != lastWindowPackage
        ) {
            lastWindowPackage = pkg
            Log.i(TAG, "window changed → $pkg")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "service interrupted")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(triggerReceiver)
        } catch (_: IllegalArgumentException) {
            // 未注册时忽略
        }
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun registerTriggerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_CLICK_BY_TEXT)
            addAction(ACTION_DUMP_WINDOW)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(triggerReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(triggerReceiver, filter)
        }
    }

    private fun activeWindowPackage(): String? = rootInActiveWindow?.packageName?.toString()

    // ---------------------------------------------------------------- 原语层

    /**
     * 在当前活动窗口里查找匹配 [query] 的第 `query.index` 个节点。
     *
     * 优先级：resourceId > text 精确 > containsText > desc/className BFS。
     * 调用方负责 [AccessibilityNodeInfo.recycle]（Android 13+ 已自动管理，但保持习惯）。
     */
    fun findNode(query: NodeQuery): AccessibilityNodeInfo? {
        if (query.isEmpty) return null
        val root = rootInActiveWindow ?: return null

        // 跨模块 public 属性禁止 smart-cast，用 local val 取出
        val resId = query.resourceId
        val exactText = query.text
        val containsText = query.containsText
        val desc = query.desc
        val className = query.className

        val candidates: List<AccessibilityNodeInfo> = when {
            !resId.isNullOrBlank() ->
                root.findAccessibilityNodeInfosByViewId(resId).orEmpty()

            !exactText.isNullOrBlank() ->
                root.findAccessibilityNodeInfosByText(exactText).orEmpty()
                    .filter { it.text?.toString() == exactText }

            !containsText.isNullOrBlank() ->
                root.findAccessibilityNodeInfosByText(containsText).orEmpty()

            !desc.isNullOrBlank() || !className.isNullOrBlank() ->
                bfsFilter(root) { node ->
                    (desc?.let { node.contentDescription?.toString()?.contains(it) } ?: true) &&
                        (className?.let { node.className?.toString() == it } ?: true)
                }

            else -> emptyList()
        }

        return candidates.getOrNull(query.index.coerceAtLeast(0))
    }

    /** 查找 → 找最近 clickable 祖先 → performAction(ACTION_CLICK)。 */
    fun clickNode(query: NodeQuery): Boolean {
        val match = findNode(query) ?: run {
            Log.w(TAG, "clickNode: no match for $query (pkg=${activeWindowPackage()})")
            return false
        }
        val target = nearestClickable(match) ?: run {
            Log.w(TAG, "clickNode: matched but no clickable ancestor for $query")
            return false
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "clickNode $query → performed=$ok")
        return ok
    }

    /** 在 [target] 命中节点上写 [text]；[clearFirst]=true 时先清空。 */
    fun inputText(target: NodeQuery, text: String, clearFirst: Boolean): Boolean {
        val node = findNode(target) ?: run {
            Log.w(TAG, "inputText: no match for $target")
            return false
        }
        if (clearFirst) {
            val clearArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.i(TAG, "inputText $target text=\"$text\" → $ok")
        return ok
    }

    /** 兼容入口：旧广播 CLICK_BY_TEXT 转发到 [clickNode]。 */
    fun clickByText(text: String): Boolean =
        if (text.isBlank()) false else clickNode(NodeQuery(containsText = text))

    private fun bfsFilter(
        root: AccessibilityNodeInfo,
        budget: Int = 300,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue += root
        var visited = 0
        while (queue.isNotEmpty() && visited < budget) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) out += node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue += it }
            }
        }
        return out
    }

    private fun nearestClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cursor: AccessibilityNodeInfo? = node
        var hops = 0
        while (cursor != null && hops < 4) {
            if (cursor.isClickable) return cursor
            cursor = cursor.parent
            hops++
        }
        return null
    }

    // ---------------------------------------------------------------- 调试 dump

    private fun dumpActiveWindow() {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "dump: no active window")
            return
        }
        val sb = StringBuilder()
        dumpNode(root, depth = 0, sb = sb, budget = 200)
        Log.i(TAG, "dump (depth-truncated):\n$sb")
    }

    private fun dumpNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        sb: StringBuilder,
        budget: Int,
    ): Int {
        if (budget <= 0) return 0
        val indent = "  ".repeat(depth.coerceAtMost(10))
        sb.append(indent)
            .append('<')
            .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { sb.append(" id=").append(it.substringAfterLast('/')) }
        node.text?.takeIf { it.isNotEmpty() }?.let { sb.append(" text=\"").append(it).append('"') }
        node.contentDescription?.takeIf { it.isNotEmpty() }?.let { sb.append(" desc=\"").append(it).append('"') }
        if (node.isClickable) sb.append(" clickable")
        sb.append(">\n")
        var consumed = 1
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            consumed += dumpNode(child, depth + 1, sb, budget - consumed)
            if (consumed >= budget) break
        }
        return consumed
    }

    companion object {
        const val TAG: String = "AgentA11y"

        const val ACTION_CLICK_BY_TEXT: String = "com.taomic.agent.a11y.CLICK_BY_TEXT"
        const val ACTION_DUMP_WINDOW: String = "com.taomic.agent.a11y.DUMP_WINDOW"
        const val EXTRA_TEXT: String = "text"

        @Volatile
        private var instance: AgentAccessibilityService? = null

        fun instance(): AgentAccessibilityService? = instance
    }
}
