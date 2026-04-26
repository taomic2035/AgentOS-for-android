# 权限模型

## 必须权限

| 权限 | 用途 | 用户授予方式 | 引导版本 |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 浮窗 | 系统设置 → 应用 → 显示在其他应用上层 | V0.1 引导 |
| `BIND_ACCESSIBILITY_SERVICE`（系统授予） | 控制其他 App | 系统设置 → 无障碍 → AgentOS | V0.1 引导 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 常驻服务 | manifest 静态 | 自动 |
| `POST_NOTIFICATIONS` (API 33+) | 前台服务通知 | 运行时弹窗 | V0.1 |
| `RECORD_AUDIO` | 语音输入 | 运行时弹窗 | V0.3 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 调 LLM API | manifest 静态 | 自动 |
| `QUERY_ALL_PACKAGES` | 启动其他 App | manifest 静态（需 Play Store 申请） | 自动 |

## 可选权限

| 权限 | 用途 | 版本 |
|---|---|---|
| `ACCESS_FINE_LOCATION` | 打车场景填起点 | V0.6（用户可关） |
| `READ_CONTACTS` | "给妈妈打电话"等场景 | V0.7（用户可关） |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | 读取系统通知做触发 | V0.8（默认关闭） |

## 不申请权限

- `READ_SMS` / `RECEIVE_SMS`：不读短信
- `READ_CALL_LOG`：不读通话记录
- `WRITE_EXTERNAL_STORAGE`：不写外部存储
- `BIND_DEVICE_ADMIN`：不设备管理

## 权限引导流程（V0.1）

1. MainActivity 检测各项权限状态
2. 用人话解释每项权限的用途（合规要求）
3. 引导跳转到系统设置页
4. 返回后再次检测；未通过则禁用核心功能并提示
5. 全部通过后引导用户配置 LLM key（V0.2 起）

## 国内厂商白名单（V0.9）

针对以下 ROM 写专门引导：

- MIUI：自启动 + 后台弹出权限 + 锁屏显示
- HarmonyOS / EMUI：受保护应用 + 启动管理
- ColorOS：自启动 + 关联启动 + 后台冻结
- OriginOS：高耗电应用控制
- 原生 Android / Pixel：电池优化排除

每种 ROM 提供：
- 步骤截图（中文 / 英文）
- 文字说明
- "我已开启"按钮（点击后再次检测）

## 撤销与隐私

- 任何权限用户均可在系统设置撤销
- App 启动时检测权限状态变化，缺权限的功能优雅降级
- 设置页提供"权限自检"页面，一键查看与跳转
