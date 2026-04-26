package com.taomic.agent.core;

/**
 * V0.1 占位：Java/Kotlin 混合策略（ADR 0005）的最小示例。
 *
 * 提供一个静态工具方法，证明 Java 与同模块 Kotlin 代码可双向调用。
 * 实际项目中此类工具类适合放纯算法、POJO、第三方 SDK 包装等场景。
 */
public final class CoreInterop {
    private CoreInterop() {
    }

    public static String greet(String name) {
        return "Hello, " + name + " from AgentOS core (Java side)";
    }
}
