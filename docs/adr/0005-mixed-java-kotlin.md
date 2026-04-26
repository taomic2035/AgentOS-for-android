# ADR 0005 — 语言策略：Java + Kotlin 混合

- **状态**：Accepted
- **日期**：2026-04-26
- **决策人**：铲屎官 + Claude

## 背景

Android 项目可选纯 Kotlin、纯 Java，或两者混合。Kotlin 是 Google 当前的主推；Java 仍有大量遗产代码与第三方 SDK；许多业务团队两种语言都用。

## 决策

**同时支持 Java 与 Kotlin，按用途分工**：

| 场景 | 推荐语言 | 理由 |
|---|---|---|
| Activity / Service 入口、AccessibilityService、四大组件 | Kotlin | Coroutines、Compose、扩展函数对 Android 原语友好 |
| Compose UI、ViewModel、状态流 | Kotlin | Compose 必须 Kotlin |
| Skill DSL 解析 / Agent 编排 / LLM 客户端 | Kotlin | sealed class / data class / Coroutines 红利显著 |
| Java 老库的桥接、第三方 SDK 包装、纯算法工具类 | **Java 可选** | 与生态对齐，避免不必要的 Kotlin overhead |
| `@JvmStatic`-friendly 公共 API、自动化脚本生成的 POJO | **Java 可选** | Kotlin 互操作时更直接 |
| 单元测试 | 两种皆可 | 团队偏好优先 |

**规则**：

1. 同一类的实现内部不混合两种语言（避免 method dispatch 调试痛）
2. 模块间通过接口契约通信，调用方语言不感知
3. KGP 默认编译顺序：Kotlin 先编译，Java 后编译；双向引用都已自动支持
4. 公共 API 优先 Kotlin（声明在 `core` 模块），从 Java 调用时使用 `@JvmName / @JvmStatic / @JvmField` 优化签名

## 理由

- 团队若有 Java 背景的成员可直接贡献
- Android 历史教程、StackOverflow、第三方代码仍以 Java 为主，参考成本低
- 部分场景（POJO、纯算法）Java 更直观
- KGP 已成熟支持，无额外构建成本

## 取舍

- ⚠️ 团队需建立两份代码风格规范（detekt + checkstyle）
- ⚠️ 重构跨语言接口的工具支持略弱
- ✅ 灵活，便于人才招募
- ✅ 渐进改造可行

## 影响

- 每个模块的 `src/main/` 下同时保留 `kotlin/` 和 `java/` 目录
- detekt（Kotlin）+ checkstyle / pmd（Java）双 lint
- CI 同时跑两种语言的测试
- README 写明"业务侧默认 Kotlin、互操作侧可用 Java"

## 范例

业务核心（Kotlin）：

```kotlin
// core/src/main/kotlin/com/taomic/agent/core/AgentCore.kt
object AgentCore { const val VERSION: String = "0.1.0" }
```

互操作工具（Java）：

```java
// core/src/main/java/com/taomic/agent/core/CoreInterop.java
public final class CoreInterop {
    private CoreInterop() {}
    public static String greet(String name) { return "Hello, " + name; }
}
```

两者可在同一模块互相调用。
