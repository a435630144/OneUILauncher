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

## v0.14 — 设置页 One UI 化（2026-10-29）

**设置**

- 设置首页改成四张 One UI 分组卡片：布局 / 外观 / 交互 / 关于；
  行内只有标题、说明和箭头，去掉前置图标与 Lawnchair 公告。
- 布局：主屏幕、应用抽屉、Dock 栏、文件夹；外观：图标与风格、资讯一览、
  搜索栏、通知；交互：手势、最近任务；关于单独一张卡片。
- 通知圆点从「通用」拆成独立页面 `NotificationsPreferences`，新增 `Notifications`
  路由；授予通知使用权后显示开关、圆点颜色、数量与文字颜色。
- `GeneralPreferences` 改名为「图标与风格」，只保留旋转、字体、图标、颜色。
- 移除首页的备份 / 恢复 / 实验特性 / 重启入口；调试菜单仅在打开对应偏好时显示。
- 新增文案 `oneui_settings_appearance` / `oneui_settings_notifications` /
  `oneui_settings_section_look` / `oneui_settings_section_controls`。

**文档**

- `ARCHITECTURE.md` 新增 §5.10，§7 增加设置首页 / 图标与风格 / 通知页验证项与截图，
  §8 里程碑 7 标记完成。
- `HANDOVER.md` 新增 §3.6（设置页结构与通知授权命令），更新 §6.4 与验收清单。
- 新增截图 `settings-root.png`、`settings-appearance.png`、`settings-notifications.png`。

## v0.15 — 图标按压动画（2026-10-29）

**动画**

- `FastBitmapDrawable` 按下缩放从 AOSP 的 1.1 放大改为 One UI 的 **0.92 缩小**，
  松开回到 1.0；只缩放图标，文字标签不动。
- `CLICK_FEEDBACK_DURATION` 200 → 150ms；按下用减速插值（立即响应），
  松开用加速插值。
- 工作区、抽屉、Dock、文件夹预览共用同一 drawable，一处改动全场景生效。

**文档**

- `ARCHITECTURE.md` 新增 §5.11，§7 增加图标按压验证项，§8 里程碑 8 标记部分完成。
- `HANDOVER.md` 新增 §3.7（参数与截图方法），更新 §6.5 与验收清单。
- 新增对比截图 `anim-icon-press.png`。

## v0.16 — 关于页与抽屉错落（2026-10-29）

**设置**

- 「关于」页改成 One UI 结构：图标 + 名称 + 版本，下面「法律信息」卡片放
  致谢 / 隐私政策，底部一行开源声明。
- 移除夜间更新检查、社区链接与贡献者列表；中文 `legal` 改为「法律信息」。
- 新增 `about_open_source_notice`、`acknowledgements_description`、
  `privacy_policy_description` 文案（中英）。

**动画**

- 抽屉打开时网格按行错落归位：行延迟按 `childTop / 列表高度` 归一化后乘 0.3，
  每行额外下移 12dp 再回 0；只改 `translationY`，不改 alpha，避免空白面板。
- 参数集中在 `AllAppsTransitionController`，抽屉打开后滚动 / 搜索 / 返回桌面
  不残留位移。

**文档**

- `ARCHITECTURE.md` 新增 §5.10 第 5 点与 §5.12，更新 §7 截图与验证、§8 里程碑。
- `HANDOVER.md` 新增 §3.8，补关于页说明、验收清单与最近提交表。
- 新增截图 `settings-about.png`。

## v0.17 — 设置二级页整理（2026-10-29）

**设置**

- 主屏幕页重排：布局（网格 / 锁定 / 页面指示点）→ 通用（新应用 / 无限滚动）→
  订阅 → 样式 → 壁纸 → 弹出菜单 → 状态栏 → 图标 → 小组件 → 清空主屏幕。
- 双击手势与独立「手势」页重复，从主屏幕页移除；「清空主屏幕」移到末尾。
- 应用抽屉页把「网格」提到「通用」之前，顺序为布局 → 网格 → 通用 → 样式 →
  图标 → 高级。
- 设置首页不再显示开发版警告条；主屏幕副标题改为「网格、壁纸、图标」。
- 补齐中文文案：无限滚动、页面指示点、Material You 弹出菜单、搜索栏独立背景、
  清空主屏幕、主屏幕操作。

**文档**

- `ARCHITECTURE.md` §5.10 增加二级页整理，§7 增加验证与截图。
- `HANDOVER.md` §3.6 / §6.4 / 验收清单同步。
- 新增截图 `settings-home.png`、`settings-app-drawer.png`，更新 `settings-root.png`。

## v0.18 — 长按菜单弹性展开（2026-10-29）

**动画**

- `ArrowPopup` 的 Material You 展开从 `0.5 → 1.02` 改为 `0.86 → 1.015 → 1.0`，
  展开 240ms、回弹 120ms，淡入 90ms / 子项 110ms。
- 收起 233 → 180ms，缩到 `0.9`；长按菜单、文件夹、快捷方式、小组件弹窗共用。
- 只改参数，不改 `OptionsPopupView` 结构。

**文档**

- `ARCHITECTURE.md` 新增 §5.13，更新 §7 与 §8 里程碑。
- `HANDOVER.md` 新增 §3.9、验收项与提交表。
- `tools/oneui-overlay.txt` 增加 `ArrowPopup.java`。
