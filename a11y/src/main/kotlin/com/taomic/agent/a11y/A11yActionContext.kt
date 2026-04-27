package com.taomic.agent.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.taomic.agent.core.A11yController
import com.taomic.agent.core.action.ActionContext
import com.taomic.agent.core.action.ActionOutcome
import com.taomic.agent.core.action.GlobalKey
import com.taomic.agent.core.action.NodeQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ActionContext] 的 Android 实现：把 SkillRunner 的请求转化为
 *  - [Context.startActivity]（launchApp）
 *  - [A11yController] 原语（waitNode / clickNode / inputText）
 *  - [AccessibilityService.performGlobalAction]（pressKey）
 *
 * 通过 [controllerProvider] 注入 [A11yController]，便于测试与服务未连接时返回 Failed。
 */
class A11yActionContext(
    private val appContext: Context,
    private val controllerProvider: () -> A11yController? = { AgentAccessibilityService.instance() },
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
                val controller = controllerProvider()
                    ?: return@withTimeoutOrNull false
                if (controller.findNode(query) != null) return@withTimeoutOrNull true
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
        val controller = controllerProvider() ?: return ActionOutcome.Failed("a11y service not connected")
        return if (controller.clickNode(query)) ActionOutcome.Success() else ActionOutcome.Failed("no clickable match for $query")
    }

    override suspend fun inputText(target: NodeQuery, text: String, clearFirst: Boolean): ActionOutcome {
        val controller = controllerProvider() ?: return ActionOutcome.Failed("a11y service not connected")
        return if (controller.inputText(target, text, clearFirst)) ActionOutcome.Success() else ActionOutcome.Failed("input failed for $target")
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
