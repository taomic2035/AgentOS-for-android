package com.taomic.agent.uikit.floating

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Compose 在 [androidx.compose.ui.platform.ComposeView] 上渲染时需要 ViewTree 上有
 * lifecycle / viewModelStore / savedStateRegistry 三件套。当我们把 ComposeView 直接挂到
 * WindowManager（绕过 Activity）时这些 owner 都没有，必须手动提供。
 *
 * 本类是最小自闭合实现：attach 时把 lifecycle 推到 RESUMED；detach 时推到 DESTROYED 并
 * 清空 ViewModelStore。SavedStateRegistry 不持久化（V0.1 无需保存浮窗状态）。
 */
internal class FloatingWindowOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onAttached() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDetached() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
