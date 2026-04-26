# 浮窗 UI 设计

## 目标

随时呼出、不挡操作、视觉轻量、可拖拽。

## 形态

- **收起态**：直径 56dp 的圆形悬浮按钮（带 logo）
- **展开态**：从悬浮按钮展开为底部抽屉式输入面板（含语音 / 文字切换、最近会话、停止按钮）
- **执行态**：浮窗变为半透明状态指示条（显示当前步骤 + 取消按钮）

## 实现方案

- 基于 `WindowManager` + `ComposeView`（在 `uikit` 模块）
- WindowParams：`TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE` + `FLAG_LAYOUT_NO_LIMITS`
- 拖拽吸附：onTouchListener 计算偏移量，松手时吸附到屏幕左 / 右边缘最近边
- 高度遮挡其他应用按钮时支持长按隐藏（只露 1/3）

## 状态变迁

```
COLLAPSED ──tap──> EXPANDED ──submit──> EXECUTING ──done──> COLLAPSED
                       │
                       └─tap outside──> COLLAPSED
```

## 交互细节

- 输入面板默认聚焦语音（V0.3 起）
- 长按悬浮按钮 = 紧急停止当前任务
- 双击悬浮按钮 = 进入设置页

## 性能预算

- 浮窗渲染首帧 < 16ms
- 拖拽 60fps
- 内存占用 < 8MB

## 多设备适配

- 小屏（< 5 寸）：悬浮按钮 48dp
- 折叠屏：跟随当前活动窗口
- 横屏：悬浮按钮固定右下角

## 风险

- 国内 ROM 后台限制导致浮窗被回收 → 由 AgentForegroundService 持有引用避免
- 部分应用全屏沉浸时浮窗被遮挡 → 提供"长按隐藏"逃生

## 验收（V0.1）

- 拖拽流畅 60fps
- 在 launcher / 浏览器 / 视频应用上层均可显示
- 重启后自动恢复（service 重启即重建浮窗）
