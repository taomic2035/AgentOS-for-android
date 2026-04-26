# PRD — 非功能需求

## 性能

| 指标 | 目标 |
|---|---|
| 冷启动到浮窗可呼出 | < 2s |
| 浮窗点击到输入框出现 | < 200ms |
| Skill 命中场景端到端 | < 3s |
| LLM 规划场景端到端 | < 8s（受 LLM 延迟影响） |
| APK 大小 | < 30MB（不含本地大模型） |
| 内存常驻 | < 80MB |
| CPU 平均（待机） | < 1% |

## 可靠性

- 服务被杀后自动重启（START_STICKY + JobScheduler 兜底）
- 网络中断时降级到本地兜底（V0.8 起）
- Skill 失败必有清晰日志，不崩溃
- 崩溃率（V1.0 灰度 7 天）< 0.1%

## 隐私

- 默认所有屏幕内容上行 LLM 时只传"摘要"
- 用户可在设置中关闭云端、强制走本地
- API key 走 Android KeyStore 加密
- 数据库加密（SQLCipher）
- 日志默认脱敏（手机号 / 地址 / 姓名）
- 用户可一键清空所有学习数据

## 无障碍合规

- App 自身遵守 Android 无障碍标准（contentDescription 完整）
- 引导页对 A11y 用途有醒目说明（防止商店审核驳回）
- 不滥用 A11y：仅用于代用户操作，不读取后台敏感信息

## 兼容性

- minSdk 26（Android 8.0）/ targetSdk 35（Android 15）
- 在 MIUI / HarmonyOS / EMUI / OriginOS / 原生 Android 五种 ROM 上验证
- 暗色模式
- 中英双语
- 横竖屏

## 安全

- 不可逆操作（付款 / 删除 / 发送）必经 RiskGuard
- 第三方 LLM 通过用户配置的 url，不强制走我们的中转
- 所有外发请求 HTTPS
- 接收推送或 Intent 时校验来源

## 可维护性

- 模块边界清晰（见 `02-architecture/module-design.md`）
- 单元 + 仪器 + E2E 三层测试
- 每个 V0.x 都附 release-notes 与回归测试
