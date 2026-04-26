# ADR 0001 — 控制路径选用 AccessibilityService

- **状态**：Accepted
- **日期**：2026-04-26
- **决策人**：铲屎官 + Claude

## 背景

AgentOS 需要"代替用户操作其他 App"，候选路径包括：

1. AccessibilityService（无障碍服务） + 悬浮窗 + MediaProjection
2. Shizuku（用户激活后获得 ADB 等价能力）
3. Root + 系统签名
4. PC 端 ADB 桥接（Demo 路径）

## 决策

**选用 AccessibilityService 主路径**，未来视用户群体可叠加 Shizuku 增强通道。

## 理由

- 唯一可上架的、面向普通用户的合规路径
- 能力覆盖核心需求：读取屏幕语义、点击 / 滑动 / 输入
- 可读取 resource-id / text / desc，避免脆弱的坐标点击
- 国内主流厂商系统均允许（虽然杀后台是另一个问题）

## 取舍

- ⚠️ 首次使用需引导用户开 OVERLAY 和 A11y 两个系统开关
- ⚠️ 部分应用对 A11y 做了识别和拦截（极少数，主要是金融 App）
- ⚠️ 不能直接绕过其他应用的密码 / 二次验证（这本来也是设计目标）
- ✅ 商店审核可过；用户隐私可控
- ✅ 不依赖特定 ROM / Root，覆盖面最大

## 影响

- 引导页须重点说明 A11y 与 OVERLAY 权限的用途和合规性
- 所有动作必须经过 a11y 模块单点出口，便于审计
- 失败回退策略必须设计：节点找不到时回退 LLM 或提示用户
