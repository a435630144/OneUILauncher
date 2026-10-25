# 开发日志

> v0.1–v0.6 的 Compose 版开发记录已移到 [legacy/CHANGELOG.md](legacy/CHANGELOG.md)。

## v0.7 — 迁移到 Lawnchair 15 + One UI 设计层（2026-10-26）

**架构**

- 底层换成 Lawnchair 15（Launcher3 / Android 15，Apache-2.0），保留 Quickstep。
- 新增 `core / design / sesl` 三层约定：`core` 只负责行为，`design` 只负责外观，
  `sesl` 为可选适配层。第一阶段不引入 SESL 构建依赖，避免与 AndroidX 同包名冲突。
- 旧 Compose 实现整体移入 `legacy/`，保留作功能移植参考。

**首屏**

- 桌面网格改为 4 列 × 6 行，Dock 5 位；数据库随之变为 `launcher_6_4_5.db`。
- 关闭 smartspace 独立首屏页与 Dock 上方搜索条，贴近 One UI 首页。
- 新增 `lawnchair/res/layout/launcher.xml`：44sp 时间 + 14sp 中文日期的时钟覆盖层。
- 新增 `default_workspace_4x6.xml`：第 0 行留给时钟，第 1 行 4 图标，Dock 5 图标。
- 图标形状默认改为三星 squircle（`sammy`），抽屉标签改为单行。

**视觉**

- 设置页圆角体系改为 12 / 16 / 20 / 26dp。
- 字号体系改为 20sp 标题 / 16sp 行标题 / 15sp 正文 / 13sp 说明。
- 新增 One UI 风格应用图标（蓝色渐层 + 嵌套 squircle）。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过，APK ≈ 48 MB。
- 模拟器 `medium_phone`：首屏 4×6、Dock 5 位、时钟正常；抽屉 / 长按菜单 / 最近任务正常。
