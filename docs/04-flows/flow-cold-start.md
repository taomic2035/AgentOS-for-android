# 流程：冷启动

## 触发

- 用户首次安装 / 启动
- 用户清空数据后重启

## 步骤

1. **MainActivity** 拉起
2. 检测权限：
   - SYSTEM_ALERT_WINDOW
   - AccessibilityService 启用状态
   - POST_NOTIFICATIONS（API 33+）
3. 缺权限 → 引导页（按权限逐项引导跳转系统设置）
4. 全权限通过 → 检测 LLM 配置
5. 缺 LLM 配置 → 设置页（让用户填 base_url / api_key / model）
6. 配置完成后启动 **AgentForegroundService**
7. Service 启动后：
   - 初始化 AgentCore
   - 加载内置 skill 包（assets/skills/*.yaml）
   - 加载用户 skill（files/skills/*.yaml）
   - 创建浮窗（默认右下角）
   - 显示前台通知（点击 = 打开主屏）
8. UI 提示"AgentOS 已就绪"

## 失败处理

- A11y 未开 → 浮窗仍可拖动，但执行会提示"未启用无障碍"
- LLM 未配 → 浮窗能呼出但只接受 Skill 命中

## 性能

- 冷启动到浮窗呼出可见 < 2 秒

## 验收

- 三种状态（首装 / 已配 / 缺权限）都有正确分支
- 重启后浮窗自动恢复
