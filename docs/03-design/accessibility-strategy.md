# Accessibility 策略

## 服务声明

`a11y/src/main/res/xml/accessibility_service_config.xml`：

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRequestEnhancedWebAccessibility|flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/a11y_description" />
```

## 节点查找策略

优先级（从高到低）：

1. `resource-id`（最稳定）
2. `content-description`（多语言友好）
3. `text` 精确匹配
4. `text` contains
5. `class-name + index`
6. 坐标（最后兜底，记录但不优先）

## 操作原语

| 原语 | 实现方式 |
|---|---|
| 点击节点 | `node.performAction(ACTION_CLICK)`，失败回退到 `dispatchGesture(GestureDescription)` |
| 长按节点 | `dispatchGesture(GestureDescription, 1000ms)` |
| 输入文本 | 优先 `ACTION_SET_TEXT`（API 21+）；不支持则 focus + clipboard paste |
| 滑动 | `dispatchGesture(swipe path)` |
| 滚动列表 | `node.performAction(ACTION_SCROLL_FORWARD)` |
| 返回 | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| 回到桌面 | `performGlobalAction(GLOBAL_ACTION_HOME)` |

## 屏幕语义快照

- 入口：`getRootInActiveWindow()`
- 遍历方式：BFS，深度限制 30，节点数限制 200
- 输出：精简 JSON `{ class, id, text, desc, bounds, clickable }`
- 用于 LLM 上下文（精简后通常 < 500 token）

## 节点 NodeInfo 回收

- 每次查找完毕显式 `recycle()`
- 用 `use { }` 扩展函数封装

## 失败诊断

- 每次原语失败都打 INFO 日志：含期望节点描述、实际窗口包名、root tree 摘要
- 触发 recovery：`fallback_to_llm` / `prompt_user` / `abort`

## 性能

- 节点查找平均 < 50ms
- 单步原语执行（含等待）平均 < 200ms

## 厂商兼容

- MIUI：A11y 服务后台保活需用户在"安全中心 → 应用 → 显示悬浮窗"中开启
- HarmonyOS：需在"应用助手 → 无障碍"开启
- 部分国行机型对 A11y 有"使用次数限制"，需在 onServiceConnected 时主动重连

## 风险

- App 改版导致节点 id 失效 → DSL 用多重 fallback（id + text + desc）
- 部分 App 反 A11y → 检测到 `WindowChangedEvent` 异常时记录并通知用户

## 验收（V0.1）

- 在 Pixel 6 ARM64 模拟器上 5 次连续运行成功率 ≥ 95%
- 单步原语日志完整
- 异常路径有清晰的错误分类
