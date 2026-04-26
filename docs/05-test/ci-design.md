# CI 设计

## 阶段

| 阶段 | 触发 | 内容 | 时长 |
|---|---|---|---|
| pre-commit | 本地 git hook | ktfmt + detekt 格式化检查 | < 5s |
| PR | 提交 PR | 编译 + Unit 测试 | ~5min |
| main | 合并 main | 编译 + Unit + Instrumentation（AVD） | ~15min |
| nightly | 每晚 0:30 | 全量 + E2E + perf | ~45min |
| release | tag v* | 全量 + 签名 + 上传产物 | ~30min |

## 工具

- 本地：Gradle wrapper（已配阿里云 / 腾讯云镜像）
- 本地脚本：`scripts/run-emulator.sh` / `scripts/install-and-launch.sh` / `scripts/e2e-*.sh`
- CI 平台：先 GitHub Actions 占位（V0.9 视情况切自建）
- AVD：`pixel_6_arm64`

## 任务

```bash
# Unit
./gradlew :core:test :skill:test :llm:test

# Instrumentation
emulator -avd pixel_6_arm64 -no-snapshot-load &
adb wait-for-device
./gradlew connectedDebugAndroidTest

# E2E
./scripts/e2e-tencent-video.sh
./scripts/e2e-jd-shopping.sh   # V0.6+
./scripts/e2e-meituan-reorder.sh  # V0.6+
./scripts/e2e-didi-call.sh   # V0.6+
./scripts/e2e-search-weather.sh   # V0.6+

# Build
./gradlew :app:assembleRelease
```

## 失败处理

- PR 红 → 阻塞合并
- nightly 红 → 自动开 issue 通知
- E2E 不稳定（< 80% 成功率）→ 标记为 quarantine 隔离

## 产物

- `app-debug.apk` / `app-release.apk`
- `test-report.html`（聚合所有测试结果）
- `coverage-report.html`（JaCoCo）
- `screenshots/`（E2E 截图）

## 性能门禁（V0.6+）

- APK 大小 > 30MB → 警告
- 冷启动 > 2s → 警告
- 内存常驻 > 80MB → 警告

## 安全门禁

- detect-secrets 扫描提交内容
- gitleaks 扫描历史
- 依赖漏洞扫描（OWASP）

## 验收

- pre-commit 不能跳过
- PR 必须 CI 绿才能合
- 每个 release tag 都有完整产物归档
