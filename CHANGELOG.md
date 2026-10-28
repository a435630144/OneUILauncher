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

## v0.10 — 抽屉 One UI 底部菜单（2026-10-27）

**应用抽屉**

- 三点菜单不再直接跳设置页，改为弹出 One UI 风格底部菜单：
  顶部拖动条、56dp 菜单行、24dp 图标，主菜单为「排序 / 设置」。
- 新增排序子页：返回箭头 + 20sp 标题，「字母顺序」单选打勾，
  「自定义」置灰并标注「即将支持」；`BackHandler` 先回主菜单，
  再返回或点遮罩才关闭弹层。
- 打开菜单前清除搜索框焦点并收起键盘，避免键盘压住菜单；
  关闭菜单后抽屉保持打开。
- 设置行关闭菜单并跳转 `PreferenceActivity` 的 `AppDrawer` 路由。
- 新增 `app.lawnchair.ui.popup.OneUiDrawerMenu`；`oneui_drawer_menu` 改为
  「更多选项」，新增 `oneui_drawer_sort*` 中英文案。

**文档**

- `ARCHITECTURE.md` 新增 §5.6 抽屉底部菜单，更新 §5.5 交互、§7 验证与 §8 里程碑。
- `HANDOVER.md` 增补 §3.2 菜单实现、已知问题与验收项，更新已完成表。
- 新增截图 `docs/refactor/shots/drawer-menu.png`、`drawer-sort.png`。

## v0.11 — 抽屉字母分组标题（2026-10-28）

**应用抽屉**

- 字母序抽屉按 `AppInfo.sectionName` 插入首字母标题（B / C / D / …），
  中文应用按拼音首字母分组；文件夹仍排在最前。
- 新增 `OneUiAllAppsGridAdapter` 与 `OneUiSectionItem`：自定义 viewType 带
  `VIEW_TYPE_ALL_APPS_DIVIDER` 位，分组处重置行计数，标题整行渲染；
  图标、文件夹、工作资料与搜索结果仍由 AOSP `AllAppsGridAdapter` 处理。
- `LawnchairAlphabeticalAppsList` 新增 `addAppsWithAlphabetSections()`：
  每个新首字母前插入标题，组内继续调用 AOSP `addAppsWithSections()`，
  快速滚动条的 section 位置保持正确。
- 停靠搜索栏时容器顶部补 `insets.top + 8dp`，并在停靠模式下清除
  `FloatingHeaderView` 对列表顶部的 36dp 裁剪，首字母不再被状态栏或列表裁掉。
- 字母分组、长按菜单、搜索结果、键盘避让均验证正常；搜索和分类模式不受影响。

**文档**

- `ARCHITECTURE.md` 新增 §5.7 分组实现与「One UI 实际用右侧索引」的差异说明。
- `HANDOVER.md` 增补 §3.3、已知问题与下一步（右侧字母索引 / 自定义排序）。
- 新增截图 `docs/refactor/shots/drawer-sections.png`。

## v0.12 — 抽屉右侧字母索引条（2026-10-28）

**应用抽屉**

- 新增 `OneUiFastScroller`（继承 `RecyclerViewFastScroller`），只替换绘制与气泡位置，
  section 映射、触摸判定和滚动动画仍走 AOSP：
  - 轨道：3dp 宽、`TextColorPrimary` 10% 透明度、圆角胶囊；
  - 滑块：静止 3dp，拖动时 150ms 展开到 6dp，高度 52dp；
  - 气泡：52dp 蓝色 `#3E8EFF` 圆形 + 白色 22sp medium 字母，拖动时从 0.7 缩放弹出；
  - `positionBubble()` 让气泡中心对齐滑块，并夹在轨道范围内；保留手势排除区域。
- `all_apps_fast_scroller.xml` 换成 `OneUiFastScroller` + `OneUiFastScrollerPopup`；
  小部件选择器保持 AOSP 样式。
- 新增「字母分组标题」开关（设置 → 应用抽屉 → 网格，默认开）。关闭后
  `addAppsWithSections` 直接走 AOSP 实现，抽屉只剩右侧索引，与 One UI 原版一致。
- 实测拖动 B→D→W 时列表正确跳转，气泡字母与 section 一致。

**文档**

- `ARCHITECTURE.md` 新增 §5.8 索引条实现，§7 增加索引条/开关验证项与新截图，§8 里程碑 4 标记完成。
- `HANDOVER.md` 新增 §3.4（含 `input motionevent` 抓气泡的方法）、更新已知问题与验收清单。
- 新增截图 `docs/refactor/shots/drawer-index.png`、`drawer-index-drag.png`。

## v0.13 — 抽屉自定义排序（2026-10-29）

**应用抽屉**

- 新增两种排序模式：`alphabetical`（默认）和 `custom`，分别存在
  `pref_drawerSortMode` / `pref_drawerCustomOrder`。
- 抽屉菜单「排序」变成可用的单选；自定义模式下多出「编辑自定义顺序」入口，
  设置 → 应用抽屉 → 排序 也可进入。
- `LawnchairAlphabeticalAppsList` 在自定义模式下按保存的 component key 重排：
  - 不插字母标题，应用连续排列；
  - 每行合成空名 `FastScrollSectionInfo`，索引条可拖动但字母气泡自动隐藏；
  - 未记录的应用（新装）排在末尾并保持字母序。
- 新增 `AppDrawerSortPreference`：`ListPreference` 选模式 +
  `ReorderablePreferenceGroup` 拖拽列表（复用 `AppItem` / `ReorderableDragHandle`），
  松手即写入完整顺序。
- 排序偏好变化时通过 `PreferenceManager#refreshAllApps` 刷新
  `personalAppList.updateAdapterItems()`，不重建 Activity；抽屉打开时切模式立即生效。
- 实测：CarMore 从第 1 位拖到第 4 位后，抽屉首行变为
  「OneUI 桌面 / Search / TMobile / CarMore」；切回字母顺序后分组恢复。

**文档**

- `ARCHITECTURE.md` 新增 §5.9，§7 增加自定义排序 / 编辑器 / 新装应用验证项与截图，
  §8 里程碑 6 标记完成。
- `HANDOVER.md` 新增 §3.5（模式、数据格式、渲染与手动验证），更新已知问题与验收清单。
- 新增截图 `drawer-sort-menu.png`、`drawer-sort-custom.png`、`drawer-sort-editor.png`、
  `drawer-sort-mode.png`。
