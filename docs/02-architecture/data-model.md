# 数据模型

## Room 数据库表（V1.0 计划）

### `skills`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT (PK) | Skill 唯一 ID（如 `tencent_video_play`） |
| name | TEXT | 用户可见名称 |
| version | INTEGER | DSL 版本 |
| source | TEXT | `builtin` / `recorded` / `imported` |
| yaml | TEXT | 序列化后的 YAML |
| description | TEXT | 描述 |
| input_schema | TEXT (JSON) | 输入参数 schema |
| created_at | INTEGER | 时间戳 |
| updated_at | INTEGER | 时间戳 |
| hit_count | INTEGER | 命中次数（用于排序） |
| last_used_at | INTEGER | 最近使用时间 |

### `habit_events`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER (PK auto) | |
| ts | INTEGER | 时间戳 |
| intent | TEXT | 意图原文 |
| matched_skill_id | TEXT? | 命中的 skill |
| outcome | TEXT | `success` / `cancelled` / `failed` / `timeout` |
| latency_ms | INTEGER | 端到端耗时 |
| token_used | INTEGER | LLM token 消耗 |
| ctx | TEXT (JSON) | 当时屏幕摘要 / 时间 / 位置（脱敏） |

### `preference`

| 字段 | 类型 | 说明 |
|---|---|---|
| key | TEXT (PK) | 偏好 key（如 `home_address`、`favorite_food_shop`） |
| value | TEXT (JSON) | 偏好值 |
| confidence | REAL | 置信度 0-1 |
| updated_at | INTEGER | |

### `chat_history`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER (PK auto) | |
| ts | INTEGER | |
| role | TEXT | `user` / `agent` / `tool` |
| content | TEXT | |
| metadata | TEXT (JSON) | tool_call_id / cost / 截图 ref |

### `runtime_logs`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER (PK auto) | |
| ts | INTEGER | |
| level | TEXT | DEBUG / INFO / WARN / ERROR |
| tag | TEXT | |
| msg | TEXT | （脱敏后） |

## 加密策略

- DB 整库加密：SQLCipher（V0.9 启用）
- API key：Android KeyStore + EncryptedSharedPreferences
- 截图缓存：进程私有目录，定期清理
- 上传 LLM 内容：默认走"摘要"管道，敏感字段脱敏

## 存储路径

- DB：`/data/data/com.taomic.agent/databases/agentos.db`
- Skill 包（内置）：`assets/skills/*.yaml`（运行时复制到 internal storage）
- Skill 包（用户）：`/data/data/com.taomic.agent/files/skills/*.yaml`
- 日志：`/data/data/com.taomic.agent/files/logs/*.log`（滚动）

## 数据保留策略

| 表 | 保留时长 | 用户清空入口 |
|---|---|---|
| skills | 永久（除非用户删除） | Skill 管理页 |
| habit_events | 90 天滚动 | 设置 → 习惯回放 → 一键清空 |
| preference | 永久（除非用户清空） | 设置 → 隐私 → 清空偏好 |
| chat_history | 30 天滚动 | 设置 → 隐私 → 清空对话 |
| runtime_logs | 7 天滚动 | 自动 |

## 备份策略

- V0.9 起支持加密备份导出（用户主动）
- Android Auto Backup 默认关闭（避免敏感数据外泄）
