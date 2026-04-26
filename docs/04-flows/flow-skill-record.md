# 流程：Skill 录制

## 触发

- 设置 → Skill 管理 → 录制新 Skill
- 用户在对话中说"把这个流程记下来"（V0.6）

## 步骤

1. UI 进入"录制模式"
   - 浮窗变红色，提示"正在录制"
   - 屏幕角落显示步骤计数
2. EventRecorder 订阅 AccessibilityEvent：
   - WINDOW_STATE_CHANGED → 记录 `launch_app`
   - VIEW_CLICKED → 记录 `click_node`（带 resource_id / text / desc / bounds）
   - VIEW_TEXT_CHANGED → 记录 `input_text`
   - 用户点浮窗"暂停" → 录制断点
3. 用户操作完成后点击"停止录制"
4. EventRecorder 输出原始事件流
5. **后处理**：
   - 合并相邻同节点事件
   - 剔除明显无关步骤（如误触桌面）
   - 智能识别需要参数化的字段（如搜索词、地址）
6. UI 显示"步骤审阅页"：
   - 用户可以删除 / 重排 / 编辑步骤
   - 用户可以标记参数（"把这一步的文字标为输入参数"）
7. 用户填写：id / name / description
8. 序列化为 YAML，写入 `files/skills/<id>.yaml`，更新 SkillStore

## 录制的边界

- 系统级操作（解锁屏 / 输密码）不录制
- 用户主动暂停时不录制
- 默认上限 30 步，超出提示

## 隐私

- 录制内容仅本地保存
- 用户主动导出时才生成 YAML 文件可分享
- 录制中默认关闭 LLM 上行

## 验收

- 录一个三步操作，回放成功率 ≥ 90%
- 录一个含输入参数的操作，参数化后用不同输入跑通
- 录制 UI 不影响其他应用使用
