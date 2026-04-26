# ADR 0004 — 语音识别采用 Android SpeechRecognizer 起步

- **状态**：Accepted
- **日期**：2026-04-26
- **决策人**：铲屎官 + Claude

## 背景

ASR 候选：

1. Android `SpeechRecognizer`（系统能力，国内厂商机型走自家引擎）
2. 云端 ASR（科大讯飞 / 百度 / Whisper API）
3. 端侧 sherpa-onnx / whisper.cpp

## 决策

**V0.3 起用 Android `SpeechRecognizer`**；接口隔离便于后续替换。

## 理由

- 零集成成本，APK 体积不增加
- 国内主流机型有厂商自家 ASR（小米 / 华为 / vivo / OPPO）
- 中文识别质量可接受
- 首版让用户先把闭环跑起来比追求识别率更重要

## 取舍

- ⚠️ 不同机型识别质量差异大
- ⚠️ 部分国行机型或精简版 ROM 缺失 `SpeechRecognitionService`，需检测并降级（设置面板提示用户改用文字 / 启用 sherpa）
- ✅ 接口隔离：`AsrEngine` 抽象后期可平替

## 影响

- `core` 模块定义 `AsrEngine` 接口，`SpeechRecognizer` 实现放在 `app` 或专门 `asr` 子模块
- 需检测系统是否有可用的识别服务，无则降级到文字输入并提示
- V0.8 可考虑加端侧 sherpa-onnx 作为离线选项
