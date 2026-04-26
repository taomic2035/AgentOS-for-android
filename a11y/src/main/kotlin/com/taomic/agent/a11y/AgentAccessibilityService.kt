package com.taomic.agent.a11y

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.taomic.agent.core.A11yController

/**
 * V0.1 最小骨架：
 *  - onAccessibilityEvent 仅打日志（窗口包名 / 事件类型 / 顶部 Activity 切换）
 *  - 暴露广播入口供 adb / 内部模块触发节点点击：
 *      adb shell am broadcast -a com.taomic.agent.a11y.CLICK_BY_TEXT --es text "Network & internet"
 *  - 实现 [A11yController]，单例由 instance() 取出，便于后续 ActionExecutor 直接调用
 *
 * V0.6+ 将扩展为完整原语集（findByResId / inputText / scroll / press_key 等）。
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
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null && pkg != lastWindowPackage) {
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

    private fun dumpActiveWindow() {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "dump: no active window")
            return
        }
        val sb = StringBuilder()
        dumpNode(root, depth = 0, sb = sb, budget = 200)
        Log.i(TAG, "dump (depth-truncated):\n$sb")
        root.recycle()
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
            child.recycle()
            if (consumed >= budget) break
        }
        return consumed
    }

    /**
     * 查找首个文本匹配（精确或包含）的可点击节点并触发 ACTION_CLICK。
     * 命中后向上爬最多 3 级寻找 isClickable 的祖先（很多 ListView item 文本节点本身不可点击）。
     */
    fun clickByText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(text)
        if (matches.isNullOrEmpty()) {
            Log.w(TAG, "clickByText: no match for \"$text\" in pkg=${root.packageName}")
            root.recycle()
            return false
        }
        for (match in matches) {
            val target = nearestClickable(match) ?: continue
            val performed = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            target.recycle()
            matches.forEach { runCatching { it.recycle() } }
            root.recycle()
            Log.i(TAG, "clickByText \"$text\" → performed=$performed")
            return performed
        }
        matches.forEach { runCatching { it.recycle() } }
        root.recycle()
        Log.w(TAG, "clickByText: matched but no clickable ancestor for \"$text\"")
        return false
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
