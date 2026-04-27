package com.taomic.agent.uikit.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.taomic.agent.uikit.speech.SpeechRecognizerHelper

/**
 * 浮窗 host。负责：
 *  - 把 ComposeView 加到 WindowManager（TYPE_APPLICATION_OVERLAY）
 *  - 接收 [BubbleContent] 通过 Compose pointerInput 报告的手势
 *  - IME flag 动态切换：展开输入框时允许焦点和键盘，收起时恢复 NOT_FOCUSABLE
 *
 * 单例语义：一次只能有一个浮窗实例。重复 [show] 是 no-op。
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
    private var speechHelper: SpeechRecognizerHelper? = null
    @Volatile
    private var agentState: AgentState = AgentState.IDLE

    fun isShown(): Boolean = composeView != null

    /** 更新 Agent 执行状态，浮窗 UI 会随之变化。 */
    fun updateState(state: AgentState) {
        agentState = state
    }

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
                    onFocusableChanged = { focusable -> updateFocusable(focusable) },
                    state = agentState,
                    onMic = {
                        val helper = speechHelper ?: SpeechRecognizerHelper(
                            appContext,
                            onResult = { text -> onIntent(text) },
                            onError = { Log.w(TAG, "ASR error: $it") },
                        ).also { speechHelper = it }
                        if (helper.isAvailable) {
                            helper.startListening()
                        } else {
                            Log.w(TAG, "SpeechRecognizer not available")
                        }
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
        speechHelper?.destroy()
        speechHelper = null
        runCatching { wm.removeView(v) }
        owner.onDetached()
        composeView = null
        params = null
        Log.i(TAG, "FloatingBubble hidden")
    }

    /** 切换 WindowManager flags 以允许/禁止键盘焦点。 */
    private fun updateFocusable(focusable: Boolean) {
        val view = composeView ?: return
        val p = params ?: return
        if (focusable) {
            p.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            // 收起时隐藏键盘
            val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        runCatching { wm.updateViewLayout(view, p) }
        Log.d(TAG, "focusable=$focusable flags=${p.flags}")
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
