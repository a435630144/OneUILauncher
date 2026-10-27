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

## v0.8 — 抽屉底部搜索栏（2026-10-27）

**应用抽屉**

- 搜索栏从顶部移到抽屉底部，`layout_alignParentBottom` 停靠；网格可滚动到搜索栏下方。
- 新增 `ActivityAllAppsContainerView.isSearchBarDockedBottom()` 钩子，
  `SearchContainerView` 覆写为 `true`，与 AOSP 的 floating search bar 路径分离。
- 搜索胶囊：48dp 高、24dp 圆角、16sp「搜索」提示；去掉放大镜，右端增加三点菜单。
- 三点菜单打开 `PreferenceActivity` 的 `AppDrawer` 路由（后续替换为 One UI 底部菜单）。
- 键盘弹出时通过 IME inset 重算 `bottomMargin`，搜索栏保持在键盘上方。
- 列表底部预留搜索栏高度，快速滚动条同步上移；隐藏搜索栏开关下不预留空白。
- 新增 `oneui_ic_more_vert`、`oneui_search_hint`、`oneui_drawer_menu` 资源。

**文档**

- 新增 `docs/refactor/HANDOVER.md`（开发 / 交接状态、已知问题、下一步）。
- `ARCHITECTURE.md` 增补 §5.5 键盘与三点菜单说明，更新验证记录和里程碑。
- README 增加交接文档入口。

## v0.9 — 时钟随页面滚动（2026-10-27）

**首屏**

- 新增 `app.lawnchair.views.OneUiClockView`，把时钟从固定覆盖层改为跟随第一页滑动：
  在 `OnPreDrawListener` 里读取 `Workspace.getScrollX()`，用 `translationX = -scrollX`
  定位；滑到第二页时推出屏幕，滑回第一页时恢复。
- 改用 pre-draw 而不是 `setOnScrollChangeListener`：后者在页面切换时没有回调；
  pre-draw 也能覆盖启动时恢复到非 0 页的情况。
- 长按桌面、拖拽图标、抽屉、最近任务均不受影响；时钟仍不是可移除的 AppWidget，
  `BIND_APPWIDGET` 签名权限限制见 `docs/refactor/HANDOVER.md` §6.2。

**文档**

- `ARCHITECTURE.md` §5.3 改写时钟实现，§7 增加翻页验证，§8 更新时钟里程碑。
- `HANDOVER.md` 增补 §3.1 时钟滚动说明、已知问题与验收项。
- 更新首屏截图 `docs/refactor/shots/home.png`。
