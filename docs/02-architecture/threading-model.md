# 线程模型

## 线程划分

| 线程 | 用途 | 调度方式 |
|---|---|---|
| Main | UI 渲染 / Activity / Compose | Android 主线程 |
| Service-Worker | AgentForegroundService 内业务编排 | `Dispatchers.Default` 协程 |
| A11y-Service | AccessibilityService 回调（系统线程） | 系统调度，**禁止阻塞** |
| IO | 网络 / 数据库 / 文件 | `Dispatchers.IO` |
| Speech | ASR 回调 | SpeechRecognizer 系统线程 |
| Llm-Stream | 流式响应解析 | `Dispatchers.IO` |

## 关键约束

1. **A11y 回调禁止阻塞**
   - `onAccessibilityEvent()` 内只做轻量分发，重活转 `Dispatchers.Default` 协程
   - 节点查找有时间预算（默认 500ms 超时）

2. **UI 状态走 StateFlow**
   - AgentForegroundService 暴露 `StateFlow<AgentState>`
   - Activity / 浮窗以 `collectAsStateWithLifecycle()` 订阅

3. **协程作用域**
   - Service：`CoroutineScope(SupervisorJob() + Dispatchers.Default)`，Service onDestroy 时 cancel
   - Activity：`lifecycleScope`
   - Compose：`rememberCoroutineScope`

4. **同步控制**
   - Skill 执行串行（同时只一个）
   - 用 `Mutex` 防止并发

## 状态机

```
 IDLE ──user input──> THINKING ──skill hit──> EXECUTING ──done──> IDLE
                          │                       │
                          └─llm needed─> CALLING_LLM ──tool calls──> EXECUTING
                                              │
                                              └─error──> ERROR ──user retry──> IDLE
                                              
EXECUTING ──risk detected──> CONFIRMING ──user confirm──> EXECUTING
                                  │
                                  └─user cancel──> IDLE
```

## 取消与超时

- 用户随时可点浮窗的"停止"按钮 → 取消整个协程链
- LLM 调用 30s 超时
- 单步 Skill 步骤 5s 超时（DSL 内可覆盖）
- 全场景超时 60s

## 错误传播

- Result 类型贯穿所有跨模块返回
- 错误转 Sealed `AgentError`，UI 层映射到友好文案
- 严重错误打日志 + Timber 上报（V0.9 接入崩溃服务）

## 资源回收

- AccessibilityService 持有的 NodeInfo 必须 recycle（V0.1 在 a11y 模块封装）
- 浮窗 Compose 内存：使用 `DisposableEffect` 确保订阅释放
- LLM 流式响应：超时强制关闭连接
