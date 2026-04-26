package com.taomic.agent.uikit.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 浮窗 host。负责：
 *  - 把 ComposeView 加到 WindowManager（TYPE_APPLICATION_OVERLAY）
 *  - 接收 [BubbleContent] 通过 Compose pointerInput 报告的手势：
 *      onDragDelta(dx, dy)：实时累加到 LayoutParams.x/y 并 updateViewLayout
 *      onDragEnd()：吸附到屏幕左/右边缘
 *      onIntent(text)：用户在 expanded 卡片上点 chip 触发的意图文本，转发给业务层
 *
 * 单例语义：一次只能有一个浮窗实例。重复 [show] 是 no-op。
 *
 * 使用 application context；浮窗生命周期独立于 Activity。T-004 后由
 * AgentForegroundService 接管 [show] / [hide]。
 *
 * 调用方负责 SYSTEM_ALERT_WINDOW 权限检查；权限缺失时 [WindowManager.addView]
 * 抛异常，本类捕获并记日志，[isShown] 仍返回 false。
 */
class FloatingBubble(
    private val appContext: Context,
    private val onIntent: (text: String) -> Unit,
) {

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private val owner = FloatingWindowOwner()
    private var params: WindowManager.LayoutParams? = null

    fun isShown(): Boolean = composeView != null

    fun show() {
        if (composeView != null) return

        val p = buildLayoutParams()
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                BubbleContent(
                    onDragDelta = { dx, dy -> applyDrag(dx, dy) },
                    onDragEnd = { snapToEdge() },
                    onIntent = { text ->
                        Log.d(TAG, "intent text=\"$text\"")
                        onIntent(text)
                    },
                )
            }
        }

        try {
            owner.onAttached()
            wm.addView(view, p)
            composeView = view
            params = p
            Log.i(TAG, "FloatingBubble shown at (${p.x}, ${p.y})")
        } catch (e: Throwable) {
            owner.onDetached()
            Log.e(TAG, "addView failed (overlay permission?): ${e.message}", e)
        }
    }

    fun hide() {
        val v = composeView ?: return
        runCatching { wm.removeView(v) }
        owner.onDetached()
        composeView = null
        params = null
        Log.i(TAG, "FloatingBubble hidden")
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            // 接收 touch（不带 NOT_TOUCHABLE）；不抢焦点；允许铺满屏幕计算坐标
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = INITIAL_Y_PX
        }

    private fun applyDrag(dx: Float, dy: Float) {
        val view = composeView ?: return
        val p = params ?: return
        p.x += dx.toInt()
        p.y += dy.toInt()
        runCatching { wm.updateViewLayout(view, p) }
    }

    private fun snapToEdge() {
        val view = composeView ?: return
        val p = params ?: return
        val width = screenWidthPx()
        val centerLine = width / 2
        p.x = if (p.x + view.width / 2 < centerLine) 0 else width - view.width
        runCatching { wm.updateViewLayout(view, p) }
        Log.d(TAG, "snapped to (${p.x}, ${p.y}) screenWidth=$width viewW=${view.width}")
    }

    private fun screenWidthPx(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }.widthPixels
        }

    companion object {
        const val TAG: String = "FloatingBubble"
        private const val INITIAL_Y_PX = 600
    }
}
