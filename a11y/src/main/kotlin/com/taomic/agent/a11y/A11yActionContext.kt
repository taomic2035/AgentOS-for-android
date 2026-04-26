package com.taomic.agent.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.taomic.agent.core.action.ActionContext
import com.taomic.agent.core.action.ActionOutcome
import com.taomic.agent.core.action.GlobalKey
import com.taomic.agent.core.action.NodeQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ActionContext] 的 Android 实现：把 SkillRunner 的请求转化为
 *  - [Context.startActivity]（launchApp）
 *  - [AgentAccessibilityService] 原语（waitNode / clickNode / inputText）
 *  - [AccessibilityService.performGlobalAction]（pressKey）
 *
 * Service 引用通过 [serviceProvider] 注入，便于在 Service 未连接时返回 Failed。
 */
class A11yActionContext(
    private val appContext: Context,
    private val serviceProvider: () -> AgentAccessibilityService? = { AgentAccessibilityService.instance() },
) : ActionContext {

    override suspend fun launchApp(packageName: String, uri: String?): ActionOutcome {
        val pm = appContext.packageManager
        val intent: Intent = if (uri.isNullOrBlank()) {
            pm.getLaunchIntentForPackage(packageName)
                ?: return ActionOutcome.Failed("no launch intent for package: $packageName")
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { setPackage(packageName) }
        }
        // CLEAR_TASK 确保每次 skill 执行时目标 App 都从 launcher Activity 开始；
        // 不带这个 flag 时若 App 已有任务栈（例如停在子页），launchIntent 会恢复到栈顶
        // Activity，导致后续 wait_node / click_node 找的是错误的页面节点。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return runCatching { appContext.startActivity(intent) }
            .fold(
                onSuccess = { ActionOutcome.Success("launched $packageName") },
                onFailure = { ActionOutcome.Failed("startActivity failed: ${it.message}") },
            )
    }

    override suspend fun waitNode(query: NodeQuery, timeoutMs: Long): ActionOutcome {
        if (query.isEmpty) return ActionOutcome.Failed("empty query")
        val started = System.currentTimeMillis()
        val hit: Boolean? = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val service = serviceProvider()
                    ?: return@withTimeoutOrNull false // 区分：service 未连（非超时）
                if (service.findNode(query) != null) return@withTimeoutOrNull true
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        return when (hit) {
            true -> ActionOutcome.Success("found in ${System.currentTimeMillis() - started}ms")
            false -> ActionOutcome.Failed("a11y service not connected")
            null -> ActionOutcome.Timeout(timeoutMs)
        }
    }

    override suspend fun clickNode(query: NodeQuery): ActionOutcome {
        val service = serviceProvider() ?: return ActionOutcome.Failed("a11y service not connected")
        return if (service.clickNode(query)) ActionOutcome.Success() else ActionOutcome.Failed("no clickable match for $query")
    }

    override suspend fun inputText(target: NodeQuery, text: String, clearFirst: Boolean): ActionOutcome {
        val service = serviceProvider() ?: return ActionOutcome.Failed("a11y service not connected")
        return if (service.inputText(target, text, clearFirst)) ActionOutcome.Success() else ActionOutcome.Failed("input failed for $target")
    }

    override suspend fun pressKey(key: GlobalKey): ActionOutcome {
        if (key == GlobalKey.ENTER) {
            return ActionOutcome.NotImplemented("ENTER on V0.1 (no IME injection yet)")
        }
        val service = serviceProvider() ?: return ActionOutcome.Failed("a11y service not connected")
        val globalAction = when (key) {
            GlobalKey.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalKey.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalKey.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalKey.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalKey.ENTER -> error("handled above")
        }
        return if (service.performGlobalAction(globalAction)) ActionOutcome.Success() else ActionOutcome.Failed("performGlobalAction failed: $key")
    }

    override suspend fun sleep(ms: Long): ActionOutcome {
        delay(ms)
        return ActionOutcome.Success()
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 150
    }
}
