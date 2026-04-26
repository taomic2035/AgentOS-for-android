package com.taomic.agent.core

/**
 * Core 与 a11y 模块之间的契约。
 *
 * a11y 模块负责实现，core 模块负责调用，避免 core 直接依赖 Android API。
 * V0.1 仅声明骨架，方法签名将在 T-006 落地时补全。
 */
interface A11yController {
    /** 暴露当前是否已就绪（系统设置中已开启 AccessibilityService）。 */
    val isReady: Boolean
}
