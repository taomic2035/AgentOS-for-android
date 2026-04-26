# 记忆与习惯设计

## 数据来源

每次用户交互结束都写一条 `habit_events`（V0.5 起）：

- 时间戳 / 意图原文 / 命中 skill / 结果（成功 / 失败 / 取消） / 耗时 / token / 屏幕摘要

## 偏好抽取

定时任务（V0.5）：

- 每 24 小时扫描最近 30 天 `habit_events`
- 用 LLM（小批量调用）从中抽取偏好元组：`(key, value, confidence)`
- 例：`(favorite_video_app, 腾讯视频, 0.92)` / `(home_address, 西二旗, 0.85)`
- 写入 `preference` 表，更新 confidence

## Skill 命中（IntentRouter）

V0.5 起：

1. 用户输入意图 → embedding 化（端侧 sentence-transformer-m）
2. 从已注册 skill 名 + 描述构成的向量库里找 top-3 候选
3. 用阈值过滤（cos > 0.78）
4. 命中 → 抽取参数（用 LLM 小调用 or 规则）→ 直接执行
5. 不命中 → 走 Planner

## 短期记忆（对话上下文）

- 最近 6 轮对话
- 当前屏幕语义摘要
- 当前 App 包名

## 长期记忆（跨会话）

- 用户偏好（preference 表）
- 最近 30 天的 Skill 使用统计（用于排序）
- 用户曾经"记下来"的具体事实（V0.6+）

## Token 节省路径

| 阶段 | 节省策略 |
|---|---|
| Intent 命中 | 直接执行 Skill，绕过 LLM |
| Plan 缓存 | 同一意图 24 小时内的 plan 复用 |
| Context 压缩 | 屏幕摘要 / 偏好仅取相关项 |
| 工具列表精简 | 按当前场景动态裁剪 tools |

## 端侧 embedding 模型

候选：

- `text2vec-base-chinese-paraphrase`（~200MB）
- `bge-small-zh-v1.5`（~100MB）

V0.5 默认 bge-small（小、够用）。模型按需懒加载，初次使用时下载。

## 隐私

- 偏好抽取不上传完整事件，只上传必要原文
- 用户可一键清空所有 habit_events / preference
- 上传 LLM 时偏好以摘要 + 字段名传递，不带具体值（除非用户开启"高精度模式"）

## 测试

- 模拟 100 条 habit_events，跑 preference 抽取，断言关键字段被识别
- 输入近义意图（"我想看三体" / "三体最新一集"），同 skill 命中
- 不同意图（"看三体" / "看三国"）不应共享 plan 缓存
