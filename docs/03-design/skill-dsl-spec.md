# Skill DSL 规范

## 顶层结构（YAML）

```yaml
id: <string, 唯一>
name: <string, 用户可见>
version: <int, DSL 版本>
description: <string, 可选>
inputs:
  - { name: <string>, type: string|int|bool, required: bool, default: <any> }
steps:
  - <step>
  - <step>
recovery:
  on_node_not_found: fallback_to_llm | prompt_user | abort
  on_timeout: retry | abort
  on_error: prompt_user | abort
metadata:
  author: <string>
  created_at: <iso8601>
  tags: [<string>]
```

## 步骤类型（V0.1 必须）

### `launch_app`
```yaml
- action: launch_app
  package: com.tencent.qqlive
  uri: <可选 deep link>
```

### `wait_node`
```yaml
- action: wait_node
  text: <精确文本> | contains_text: <模糊> | resource_id: <id> | desc: <content_description>
  class_name: <可选 class>
  timeout_ms: 3000
```

### `click_node`
```yaml
- action: click_node
  text / contains_text / resource_id / desc
  index: 0  # 多个匹配时取第几个
```

### `input_text`
```yaml
- action: input_text
  target: { resource_id: ... | class_name: ... }
  text: ${title}        # 引用 inputs
  clear_first: true
```

### `press_key`
```yaml
- action: press_key
  key: ENTER | BACK | HOME | RECENT
```

### `scroll`
```yaml
- action: scroll
  direction: up | down | left | right
  times: 1
```

### `sleep`
```yaml
- action: sleep
  ms: 500
```

## 步骤类型（V0.2+）

### `assert_node`
```yaml
- action: assert_node
  text: ...
  fail: abort | warn
```

### `take_screenshot`
```yaml
- action: take_screenshot
  store_as: <var>
```

### `confirm_with_user`
```yaml
- action: confirm_with_user
  message: "即将付款 ${amount} 元，确认？"
  on_cancel: abort
```

### `if`
```yaml
- if: ${has_logged_in}
  then:
    - <step>
  else:
    - <step>
```

## 变量与表达式

- 输入参数引用：`${input_name}`
- 屏幕变量：`${screen.text}` / `${screen.amount}`
- 偏好引用：`${pref.home_address}`
- 简单表达式：`${amount > 100}`（V0.7 起）

## 验证规则

- DSL 加载时校验 schema（kotlinx.serialization + 自定义校验器）
- 未声明的 input 引用直接报错
- step 类型未注册的报错
- 循环 / 分支必须有出口

## 兼容性

- `version: 1` = V0.1 起的稳定子集
- `version: 2` = V0.6 起加 if / loop / take_screenshot
- runner 必须能拒绝高于自身能处理版本的 DSL

## 测试

- 每个 step 类型都有单元测试
- 整体 DSL 至少 5 个端到端 case（五大场景）
