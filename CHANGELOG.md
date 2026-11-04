# 开发日志

> v0.1–v0.6 的 Compose 版开发记录见 git 历史提交 `3cd6f19`
> （`git show 3cd6f19:legacy/CHANGELOG.md`；legacy 目录已随 v0.21 项目清理移除）。

## v0.7 — 迁移到 Lawnchair 15 + One UI 设计层（2026-10-26）

**架构**

- 底层换成 Lawnchair 15（Launcher3 / Android 15，Apache-2.0），保留 Quickstep。
- 新增 `core / design / sesl` 三层约定：`core` 只负责行为，`design` 只负责外观，
  `sesl` 为可选适配层。第一阶段不引入 SESL 构建依赖，避免与 AndroidX 同包名冲突。
- 旧 Compose 实现整体移入 `legacy/`（v0.21 项目清理时移除，功能已全部移植，
  源码在 git 历史提交 `3cd6f19` 中）。

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

## v0.19 — 时钟升级为 AppWidget（2026-10-29）

**功能**

- 新增 `OneUiClockWidgetProvider`：4×1 主屏幕小组件，44sp 时间 + 14sp 中文日期；
  两个 `TextClock` 自走时，`updatePeriodMillis=0`，无配置页。
- 新增 `OneUiClockWidgetController`：首次启动通过 `Launcher.addPendingItem` 把时钟
  落到第 0 页 (0,0)；首次绑定走系统 `ACTION_APPWIDGET_BIND` 授权框，勾选
  「始终允许」后只需确认一次。
- 绑定成功后关闭 `TYPE_WIDGET_RESIZE_FRAME`，不留调整框；自动清理失效的 widget id。
- 原 `OneUiClockView` 保留为回退：检测到已绑定小组件时隐藏，删除小组件后恢复。
  显隐改为 `OneUiClockWidgetController` 的监听器回调，不再依赖 pre-draw 轮询。

**验证**

- 模拟器首次绑定、落位、RemoteViews 时间/日期刷新通过；`dumpsys appwidget`
  确认 `OneUiClockWidgetProvider` 已托管，桌面第 0 行读取到时间与日期。
- 长按小组件拖到「移除」后，撤销条消失时覆盖层恢复；点「撤销」小组件恢复且覆盖层
  再次隐藏。

**文档**

- `ARCHITECTURE.md` §5.3 改写为「AppWidget + 覆盖层回退」，更新 §7 验证、§8 里程碑。
- `HANDOVER.md` 更新 §1/§2/§3.1/§4/§6.2/§7。
- `README.md` 功能列表与目录结构同步。
- `tools/oneui-overlay.txt` 增加时钟相关新文件。

## v0.20 — 抽屉拖拽编辑模式（2026-10-30）

**功能**

- 新增 `OneUiDrawerDragOverlay`（DragLayer 上的全屏覆盖层，同时是 DropTarget 与
  DragListener）：长按抽屉图标即进入 One UI 拖拽编辑模式，抽屉保持打开。
- **编辑态视觉**（对照真机截图）：背景压暗，应用网格缩放为 0.84 的圆角卡片悬浮，
  搜索胶囊淡出；网格交叉点绘制定位小点，落点格子绘制白色圆角描边；卡片下方显示
  页点 + 「+」提示。
- **落点语义**（对齐 One UI 真机）：
  - 卡片内：最近格显示描边预览，松手按自定义顺序移到目标应用旁（字母模式下
    Toast 提示需切换自定义排序）；
  - 卡片左右边缘 48dp：像翻页一样按页滚动列表；
  - 卡片**上方/下方空白区**：松手把应用加到主屏幕第一个空格（`Workspace.addAppFromDrawer`，
    Toast 反馈，空间不足提示）。
- 框架接入：`ItemLongClickListener.onAllAppsItemLongClick` 通过 `newDragOptions` 设置
  `DragOptions.isDrawerDrag`（搜索态关闭）；`Workspace` 跳过常规 drag 起手并在
  `onDragEnd` 提前返回；`LauncherDragView` 在 ALL_APPS 态保持拖拽浮层可见。
- 挂载：`LawnchairLauncher.setupOneUiDrawerDragOverlay()` 把 overlay 加入 DragLayer，
  注册 DragListener 与 DropTarget。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过。
- 真机交互比对基准：One UI 真机截图（抽屉拖动页：缩放卡片 + 格点 + 描边 + 分页页点）。
- 交互手势（长按进入、上下空白投放）待真机实测。

**文档**

- `HANDOVER.md` 更新 §2 完成表、新增 §3.10 实现要点、§6.3 剩余项收敛、§10 最近提交。
- `ARCHITECTURE.md` 新增拖拽编辑模式小节。
- `tools/oneui-overlay.txt` 增加 `oneui/drawer/OneUiDrawerDragOverlay.kt` 与框架改动。

## v0.21 — 抽屉拖拽改为真分页网格（2026-10-30）

**修正**（真机实测反馈：拖到左右边缘只是滚动，不会创建/切换分页）

- 拖拽编辑模式不再挂在滚动列表上：拖起时整体接管（`appsView` 隐藏），
  `OneUiDrawerDragOverlay` 自绘**分页网格卡片**——当前页 + 左右邻页边缘预览、
  格点、落点白描边、页点 + 「+」。
- **翻页**：拖到卡片左右 52dp 边缘带停留即翻页（340ms 步进），拖过最后一页
  右边缘**自动创建新分页**（页点随页数增长），图标放进新页任意格即追加。
- **落位**：卡片内格位 → 按自定义顺序移到目标位置（提交
  `pref_drawerCustomOrder`）；卡片外上/下空白区 → 加入主屏幕
  （`Workspace.addAppFromDrawer`）。字母模式拖起时自动切换为自定义排序
  （当前显示顺序作为初始顺序，与 One UI 一致）。
- 图标绘制走 `AppInfo.bitmap.newIcon`，标签单行裁剪；翻页 200ms 缓动。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过。
- 翻页/建页/落位待真机实测（编辑态视觉上一版已确认）。

**文档**

- `HANDOVER.md` §1/§3.10、`tools/oneui-overlay.txt` 同步。

## v0.22 — 时钟四种样式 + 拖拽菜单 + 抽屉背景模糊（2026-10-31）

**功能**

- **时钟四种样式**（对照 One UI 主屏幕设置）：默认（44sp 时间+日期）/ 紧凑（30sp
  时间+日期同行）/ 仅时间（64sp 细体）/ 居中（日期上、38sp 时间下）。四套
  RemoteViews 布局 + `pref_clockStyle` 持久化；设置 → 主屏幕新增「时钟样式」组，
  四行可点选（当前项带"当前样式"副标题），切换即时通过
  `OneUiClockWidgetController.applyStyleToWidgets` 刷新全部已绑定小组件。
  覆盖层回退保持默认样式（仅在小组件未绑定的短暂窗口可见）。
- **拖拽 One UI 菜单**（对照真机长按浮窗）：拖起图标即弹出白色圆角菜单
  （应用名标题 + ℹ️ 应用信息 + 「添加至主屏幕」/「卸载」），移动超过 8dp 菜单
  收起、进入纯拖拽；「添加至主屏幕」落第一个空格并 Toast 反馈。纯代码构建
  UI（绕开 inflate/R 常量的 AAPT 环节）。
- **抽屉背景模糊**：抽屉展开时对 Workspace 应用 `RenderEffect.createBlurEffect`
  （半径随展开程度 0→24f 插值，API 31+，低于 31 降级为不模糊）。挂在
  `AllAppsTransitionController.setProgress`，半径变化超 1f 才重建 RenderEffect，
  避免每帧开销。

**构建**

- 根 `build.gradle` 短暂把输出重定向到 D 盘后又回滚：KSP/protobuf 与项目源码
  跨盘符时 relativize 报 "different roots"。OneDrive 占位符问题的正解是构建前
  清理 `build/tmp` 受影响的文件，或在 OneDrive 设置中排除 `build` 文件夹。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过（K2 修复：Java 基类继承需
  显式 `AppWidgetProvider()`、`DragView<Launcher>` 泛型显式化、
  `setMargins` 链式改 `apply`）。
- 时钟样式切换、拖拽菜单、模糊效果待真机实测。

**文档**

- `HANDOVER.md`/`tools/oneui-overlay.txt` 同步新文件与设置入口。

## v0.23 — 抽屉拖拽编辑态几何校准与落点语义（2026-11-04）

**功能**

- **落点语义**：抽屉内长按拖折叠卡片进入自定义全屏覆盖层，松手位置即目标格——
  落在网格内只重排（不创建主页图标），落在卡片上/下 8dp 死区之外才「添加到主屏幕」
  （抽屉应用保留）；卡片左右边缘带仅翻分页，无邻页时按最外列落位，不再 flyBack。
- **视觉对齐 One UI 真机**：卡片 2.8%/9.67%/90.29% 内缩、网格内缩 4/3.5/34dp、
  格 250×306、图标 `min(0.62×格宽, 0.505×格高)`（中心 40%）、描边 `iconSize+2dp`
  /0.26/白 170、点阵 1.3dp/白 125、页点 3.4dp/19dp、邻页条 11.5dp、边缘带 12dp、
  圆角 24dp。真机截图实测网格 241..2077、格高 306、页点圆心 y=2124 一致。
- **条目菜单**：拖起后延迟 120ms 显示（标题 + ⓘ + 添加至主屏幕 + 卸载），
  位移超过 8dp 收起；位移基准改为首次 `onDragOver` 坐标（`onDragStart` 时
  `dragObject.x/y` 仍为 0，旧逻辑会立刻误判位移）。定位改为
  `DragView.getDragRegion()` 取浮层内真实图标矩形（排除阴影边距）后
  `showAtLocation`：水平以图标为中心并 8dp clamp，指针尖端距图标 10dp；上方
  放不下则翻到下方、指针翻到卡片顶部，指针 x 跟随图标。卡片宽 300dp、
  底色 `#F7F7FA`。
- 抽屉拖拽不再走 `startLongPressAction()` 的 `preDragCondition`（长按直接进编辑态），
  该弹窗只保留给主页图标；拖拽期间 `appsView` 置 `INVISIBLE`，由覆盖层接管绘制。

**构建**

- 构建目录经 init 脚本 `layout.buildDirectory` 指向 `E:/OneUiBuild`（与项目同盘）。
  跨盘符会让 KSP/protobuf 报 "this and base files have different roots"，写回仓库
  `build/` 又会被 OneDrive 脱水成占位文件。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过。
- 实测：网格内松手只重排、主页图标数量不变；上带松手新增主页图标且抽屉应用仍在；
  长按不动 120ms 菜单弹出；无 AOSP 长按菜单。
- 多页翻页/邻页边缘条受模拟器仅 16 个应用限制未覆盖。

## v0.24 — 主屏页面切换视差与淡入（2026-11-04）

**功能**

- **轻微视差**：主屏页面滑动时图标内容相对页面框架反向平移（幅度 30px ×
  scrollProgress），图标向屏幕中心"拖尾"，贴近 One UI 的页面切换手感；
  RTL 下方向取反。
- **轻微淡入**：相邻页淡入改为轻量式（alpha = 1 − |progress| × 0.55），
  滑走的页保留 45% 可见度而非完全消失；无障碍可见性阈值同步调整为 0.1。
- 不再依赖 `mWorkspaceFadeInAdjacentScreens`（原开关仅在竖排布局下生效），
  平铺主屏同样有淡入；`updatePageScrollValues` / `updatePageAlphaValues`
  由滚动与布局回调驱动，静止页面 progress 为 0、无额外开销。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过；页面滑动视差/淡入待真机
  实测（单页桌面需先拖出第二页）。

**文档**

- `HANDOVER.md` §6.5 勾掉「页面切换」剩余项。

## v0.25 — 窄居中搜索胶囊 + 使用频率排序 + 手势页整理（2026-11-04）

**功能**

- **窄居中搜索胶囊**（One UI 8.5）：抽屉底部搜索胶囊左右各内缩 64dp、水平
  居中（约 69% 屏宽），停靠与键盘避让逻辑不变。
- **使用频率排序**：新增第三种抽屉排序——按本桌面启动次数降序（次数相同按
  字母序）。`LaunchCounter` 在 `ItemClickHandler` 的应用启动路径自维护计数
  （JSON 持久化，无需特殊权限，上限 200 项）；频率模式按普通网格渲染（与
  自定义排序同管线）。抽屉三点菜单排序子页与设置 → 应用抽屉排序均已加入
  「使用频率」选项。
- **手势页整理**：「上滑」提至首位（最常用），其余保持双击 / 下滑 / 主屏键 /
  返回键顺序。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过；Kotlin `object` 方法补
  `@JvmStatic` 供 `ItemClickHandler`（Java）调用。
- 频率排序的实际排序效果需在模拟器上多次启动应用后观察（计数≥1 才与字母序
  有差异）。

**文档**

- `HANDOVER.md` §6.3 更新（使用频率排序完成）、§3 新增频率排序说明。
- `tools/oneui-overlay.txt` 增加 `LaunchCounter.kt`、`ItemClickHandler.java`。

## v0.26 — 编辑态过渡动画与菜单弹出动画（2026-11-04）

**动画**

- **拖拽编辑态进入过渡**：长按拖起后 220ms 内完成 One UI 式过渡——背景压暗渐入
  （scrim alpha 0→215 随 editProgress 插值）、分页卡片从全屏矩形缩放归位到目标
  卡片矩形（四边 lerp）、卡片内容（格点/图标/描边）经 `saveLayerAlpha` 整体淡入，
  邻页边缘条与页点在过渡后半程（progress > 0.6）出现。
- **拖拽菜单弹出动画**：菜单卡片从锚点尖角处弹性展开——pivot 在图标侧
  （上方布局 pivot 底部中心、下方布局 pivot 顶部），0.86 → 1.0 缩放 + 淡入，
  120ms，与长按菜单同节奏。
- 退出即恢复（松手时直接还原，不与 DragView 落位动画叠加）。

**实现**

- `OneUiDrawerDragOverlay` 新增 `editProgress`（0→1 ValueAnimator，FastOutSlowIn）
  与 `startEditAnimation()`；`onDraw` 将 scrim alpha、卡片矩形（RectF lerp）、
  内容层 alpha 全部参数化。菜单弹出动画挂在 `showItemMenu` 的
  `showAtLocation` 之后（content 属性动画）。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过；过渡/弹出动画待真机实测。

**文档**

- `CHANGELOG.md`、`tools/oneui-overlay.txt` 同步。

## v0.27 — 自定义排序浏览态改为横向分页网格（2026-11-04）

**修正**（严重 BUG：自定义排序出现纵向滚动轴）

- 自定义排序模式的**浏览态**不再使用纵向滚动列表：新增
  `OneUiDrawerPagedGrid`（PagedView 子类），按主屏网格（4×6）把应用切分为
  横向分页，每页一个 GridLayout，cell 为 `BubbleTextView`（点击启动 +
  长按进入拖拽编辑模式 + 使用计数全部复用），左右滑动翻页、无纵向滚动轴。
- 挂载于 `appsView` 内部，随抽屉开合动画；仅自定义排序 + 抽屉全开 +
  非搜索态显示，其余状态（字母序 / 搜索 / 离开抽屉）恢复滚动列表。
- 页点自绘于每页底部（当前页高亮）。
- 拖拽编辑模式（OneUiDrawerDragOverlay）接管期间分页网格隐藏，两者使用
  同一份自定义顺序数据，无状态不一致。

**验证**

- `:assembleLawnWithQuickstepGithubDebug` 构建通过；分页浏览 / 翻页 /
  长按拖拽入口待真机实测。

**文档**

- `HANDOVER.md` §2/§6.3 更新；`tools/oneui-overlay.txt` 增加
  `OneUiDrawerPagedGrid.kt`。

## v0.32 — 抽屉文件夹 + 走查修复（2026-11-05）

**抽屉文件夹（One UI 抽屉叠放合并复刻）**

- **数据**：`drawerCustomOrder` 升级为 token 序列（`K:<componentKey>` /
  `F:<folderId>`，兼容旧无前缀行）；文件夹内容存于新偏好 `drawerFolders`
  （JSON：title + 成员 key 列表，`DrawerFolderStore`）。
- **合并交互**：编辑态拖 A 到 B 上 → 建文件夹（拖出的 app 离开顶层序列）；
  拖到已有文件夹上 → 加入该文件夹；文件夹本身可拖动换位（经首个成员占位）。
- **渲染**：文件夹 cell 显示 2×2 合成预览（圆角底 + 最多四个成员图标）与
  文件夹名；点击弹出成员列表（点击启动，主题色文字）。
- **让位预览兼容**：占位与文件夹条目在展示序列里同构，previewShift/drop
  重建无需特判。

**走查修复（本轮代码审核）**

1. 文件夹占位在拖拽中因 tempApp 实例不同而 `indexOf` 失败 → 改按
   ComponentKey 匹配（文件夹可拖动）。
2. 字母排序 `applyCustomOrder` 不认识 token 前缀 → 剥 `K:` 前缀、跳过
   `F:` 行（字母模式下自定义顺序正确失效，保持字母序）。
3. drop 提交后分页网格不刷新（松手后仍是旧顺序）→ onDrop 末尾统一
   `syncOneUiDrawerPagedGrid()` 重建。
4. 拖文件夹到上/下空白区会把占位 app 复制到主屏 → 文件夹改为回弹。
5. 文件夹弹窗浅色主题下白字不可读 → 文字色取 `textColorPrimary`。

## v0.31 — One UI 页面总览编辑器 + 抽屉真机修复（2026-11-05）

**页面总览编辑器（真机截图复刻）**

- **进入/退出**：长按主屏空白直接进入 EDIT_MODE（原弹窗菜单移入编辑器底部）；
  back、点击页面卡片（进入该页）退出；`EditModeState` 缩放 0.78 / 300ms，
  两侧露出相邻页卡片边缘，hotseat 移出屏幕。
- **OneUiPageEditOverlay**（dragLayer 覆盖层，自绘）：
  - 顶部中央 **🏠**：把当前页设为 home 键默认页（`Workspace.setUserDefaultPage`
    + `oneUiDefaultPage` 偏好；`moveToDefaultScreen` 改用用户页）。
  - 当前页卡片顶部 **🗑**：删除该页（`Workspace.removePageAt`：图标/文件夹
    及内容/小组件逐类入库删除）；空白页直接删，有内容弹确认框。
  - 编辑态页点行 **≡ 🏠 ● ● +**：overlay 自绘（独立指示器在编辑态隐藏），
    🏠 = 默认主页标记（非当前页），当前页圆点放大高亮，页点点击跳页、
    + 追加页（`addUserPage`）。
  - **+ 建页卡片**：进入编辑态自动在末尾挂 EXTRA_EMPTY 页（中央大 +），
    点击建真页；退出编辑态自动移除未使用的空页。
  - 底部四按钮：壁纸和风格 / 主题（系统显示设置）/ 小组件 / 设置。
- 拖拽让位预览：`OneUiDrawerPagedGrid.previewShift` —— 抽屉编辑态悬停时
  被挤开的图标实时 150ms 平移让位（One UI 手感），松手瞬间复位重建，
  不再是松手才瞬移。

**抽屉真机修复（用户截图反馈）**

- **页点落在搜索栏内 / 末行图标与标签被搜索栏遮挡**：真机手势导航的
  insets.bottom 远大于模拟器，`pagedGrid` 的 bottomMargin 此前只含 72+8dp；
  现在 `updatePagedGridBottomMargin` 每次同步时加上 `dragLayer.getInsets().bottom`。
- **上滑过程中图标画出边界**：`all_apps_rv_layout` 的 RecyclerView
  `clipToPadding=false` 让内容画进底部保留区；改回默认裁剪，图标滚到搜索栏
  边缘即被裁（One UI 观感）。

**已知待办**

- 抽屉内拖拽到另一图标上合并文件夹（需自定义顺序结构支持 FolderInfo）。
- 编辑态页点行的 ≡ 页面列表动作。
- 编辑器四按钮的图标为简化自绘，后续可换 SESL 素材风格。

## v0.30 — One UI 大文件夹 + 抽屉留白（2026-11-05）

**应用文件夹（真机 ref09 复刻）**

- **固定 3 列大图标网格**：`FolderGridOrganizer.createFolderGridOrganizer` 统一改为
  (3, numFolderRows)——分页、预览判断、DB 坐标重算三条路径共享同一组织器，
  网格与落库 rank/坐标保持一致。
- **面板几何**：cell 宽 = 76% 屏宽 ÷ 3（`FolderPagedView` 常量），高宽比 1.22，
  面板自然撑到 ~76% 屏宽；图标沿用 folderChildIconSizePx，间距随 cell 放大。
- **标题栏移到顶部**：`user_folder_icon_normalized.xml` 中 footer（标题 + 页点）
  移至网格上方，标题左对齐（26dp 起始 padding）、22sp medium 字重（One UI 头条样式）。
- **毛玻璃面板**：`round_rect_folder.xml` 圆角 8dp→28dp；面板色改半透明
  （light #E8F5F4F2 / dark #CC1F2020），配合打开时的工作区模糊。
- **背景模糊**：`Folder.animateOpen` 完成回调与 `closeComplete` 接入
  `OneUiAppBlur`（软件渲染自动跳过）。
- 文件夹内 + / ○ 顶部按钮未实现（One UI 的添加应用与排序编辑，后续任务）。

**应用抽屉**

- 顶部 64dp 留白（`oneui_allapps_top_spacing`）：滚动列表与自定义排序分页网格
  双路径生效，首行不再贴状态栏。
- 自定义排序分页网格模式下隐藏右侧字母滚动条（滚动条只属于列表模式）。

**验证**

- 模拟器：抽屉首行离开状态栏、页点 2 个居中、无滚动条；主屏/时钟/hotseat 正常。
- 文件夹 UI adb 手势无法模拟（已知限制），待真机验证；构建通过、静态审查一致。

## v0.29 — 主屏 One UI 页点 + 图标消失根因修复 + 抽屉细节修正（2026-11-04）

**修正：主屏图标全部消失（用户报告"重启/长按后桌面图标全没了"）**

- **根因**：v0.26 视差提交引入的 `updatePageAlphaValues` 中
  `alpha = 1 - |scrollProgress| * 0.55f` 无任何钳制；启动期间
  `insertNewWorkspaceScreen` / 首次 `onScrollChanged` 在首布局前触发时
  `getMeasuredWidth() == 0`，`getScrollProgress` 除以 0 产生 NaN/±Inf，
  `setAlpha(NaN)` 令 `ShortcutAndWidgetContainer` 整体不渲染且不接收触摸
  （视图树节点与绑定均正常，只有像素消失——与用户描述完全吻合）。
- **修复**：`updatePageAlphaValues` / `updatePageScrollValues` 增加
  `getMeasuredWidth() <= 0` 早退与 NaN/Inf 跳过，alpha 钳制到 [0.45, 1]；
  顺带清理视差提交中重复的两段 `setTranslationX`。
- 验证：重启后 7 图标 + 时钟覆盖层 + hotseat 全部正常渲染，点击可启动。

**主屏：One UI 页点行（真机 ref03/ref04 复刻）**

- 重写 `WorkspacePageIndicator`：当前页为**房子轮廓**（One UI home 标记）、
  其余页为小圆点，行常驻不自动隐藏；绘制于 hotseat 下方、导航条上方
  （`setInsets` 设 bottomMargin = 导航条 + 8dp）。
- 行尾**加号按钮**：点击追加一页（`Workspace.addUserPage()`：addExtraEmptyScreens
  → commitExtraEmptyScreens → 记录 user-created → 滑至新页）。真机上 + 位于
  页面总览编辑器内；本构建尚无总览入口，故常驻行尾（已知差异）。
- `Workspace.stripEmptyScreens` 跳过 user-created 页，空页在会话内保留；
  放入第一个图标时经既有 drop→commit 路径持久化。空页跨重启不保留
  （DB 空页持久化为后续任务）。
- `config_default_enable_dot_pagination` 默认值 true→false：该开关会把
  launcher.xml 的 `WorkspacePageIndicator` 运行时替换为 AOSP 风格
  `PageIndicatorDots`（`Launcher.onCreateView`），导致 One UI 页点不生效。
- 验证：`🏠 ● ● +` 页点行、双页互滑、页点随当前页切换、+ 建页后空页保留。

**抽屉：自定义排序分页网格细节（用户真机截图反馈）**

- 页点从 `Page.onDraw`（误用 cell 数、画在搜索栏底下）移到
  `OneUiDrawerPagedGrid` 顶层 `onDraw`：数量=页数、高亮当前页、
  位置在底部保留区中央（搜索胶囊正上方）。
- `OneUiDrawerPagedGrid` 增加 24dp 底部 padding 作页点保留区；
  `LawnchairLauncher.setupOneUiDrawerPagedGrid` 给 pagedGrid 设
  bottomMargin = search_box_container_height + oneui_search_bar_bottom_margin，
  图标网格不再与搜索栏重叠（与滚动列表同款保留量）。
- 单元格 `gravity = CENTER_HORIZONTAL`：图标与文件名一并水平居中
  （BubbleTextView 图标为顶部 compound drawable，gravity 同时控制两者）。
- 搜索胶囊加 64dp 水平 padding（窄居中，One UI 8.5 样式）。
- 验证：抽屉 2 页页点居中于搜索栏上方、首行不压状态栏、标签居中。

**文档**

- `docs/refactor/HANDOVER.md`、`tools/oneui-overlay.txt` 同步。

## v0.28 — 分页网格布局修复（2026-11-04）

**修正**（v0.27 的分页网格显示空白 / 格子排成一行）

- **根因**：`OneUiDrawerPagedGrid.Page` 曾用 `GridLayout` + weight spec，实测
  24 个 cell 被排成一行 24 列（每格宽 45px、高全屏）；且 Page 的默认
  LayoutParams 为 WRAP_CONTENT，空网格测出 0 宽，导致整个分页视图塌陷
  （无障碍树中 bounds 宽 0）。
- **修复**：`Page` 改为 `FrameLayout` 子类，`onLayout` 里按 cell 索引
  （col = i % cols, row = i / cols）精确布局每格；Page 以
  MATCH_PARENT 加入 PagedView（PagedView 负责测量与页间布局）；
  删除冗余的 onMeasure/onLayout 重写。
- 验证：抽屉分页网格 4 列正常渲染、25 个应用含溢出页、无纵向滚动轴、
  窄居中搜索胶囊生效。

**文档**

- `tools/oneui-overlay.txt` 同步。
