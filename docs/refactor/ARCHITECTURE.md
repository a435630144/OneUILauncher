# 重构架构：Launcher3 + One UI 设计层

> 基线：`LawnchairLauncher/lawnchair` 分支 `15-dev`，提交 `505dbc4`，Apache-2.0。
> 工作分支：`main`，仓库根目录 `E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`。
> 本文档记录骨架阶段与抽屉搜索栏阶段的实现状态；交接事项见 [HANDOVER.md](./HANDOVER.md)，
> 设计取舍见 [SESL-DECISION.md](./SESL-DECISION.md)。

## 1. 为什么是 Lawnchair 而不是裸 AOSP Launcher3

Launcher3 在 AOSP 里是一个 **Soong/make 工程**，没有可用的 Gradle 构建。
把它接进 Gradle 需要自己补：隐藏 API stub、`compatLib` 各 SDK 变体、protobuf、
资源编译、Quickstep 依赖…… 这是几千行的构建工程活。

Lawnchair 15-dev 已经把这些做完了，并且：

- `namespace com.android.launcher3`，`compileSdk 36.1`，`minSdk 26`，JDK 17
- 桌面拖拽、文件夹、小组件、应用抽屉、通知角标全部可用
- 设置页是 Jetpack Compose（`app.lawnchair.ui.preferences.*`），改视觉成本低
- 有成熟的图标形状系统（内置 `IconShape.Sammy`，就是三星的 squircle）

代价是它带了 Quickstep（最近任务）这一整套 SystemUI 相关代码，体积大、构建慢，
但「能跑」优先，后面可以再裁。

## 2. 代码地图

```
E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop
├── src/                      Launcher3 内核（Java，514 个文件）
│   ├── com/android/launcher3/workspace/      桌面
│   ├── com/android/launcher3/allapps/        应用抽屉
│   ├── com/android/launcher3/model/          数据加载、排序
│   └── com/android/launcher3/dragndrop/      拖拽
├── res/                      内核资源（布局 / 颜色 / 样式）
├── quickstep/                最近任务（recents）
├── lawnchair/src/            Lawnchair 自有代码（Kotlin）
│   ├── app/lawnchair/LawnchairLauncher.kt         桌面 Activity
│   ├── app/lawnchair/allapps/                     抽屉扩展（搜索、排序）
│   ├── app/lawnchair/icons/shape/IconShape.kt     图标形状
│   ├── app/lawnchair/preferences/PreferenceManager.kt   网格默认值（实际生效）
│   ├── app/lawnchair/preferences2/PreferenceManager2.kt 新偏好定义
│   ├── app/lawnchair/ui/theme/                    Compose 主题（颜色 / 字号 / 圆角）
│   └── app/lawnchair/ui/preferences/              设置页
├── lawnchair/res/            Lawnchair 资源（覆盖 res/）
│   ├── layout/launcher.xml                       首屏根布局（含 One UI 时钟）
│   └── xml/default_workspace_4x6.xml             首屏默认图标排布
├── docs/refactor/            架构 / SESL 决策 / 验证截图
├── legacy/                   v0.6 Compose 实现（参考用，不参与构建）
└── build.gradle              单模块：根工程就是 app
```

Android 资源合并优先级：`lawnchair/res` > `res`。所以 One UI 的资源覆盖统一写在
`lawnchair/res` 里，内核 `res` 尽量不动，方便以后跟上游对比。

## 3. 分层约定

```
core    Launcher3 内核 + Lawnchair 引擎       —— 不出现任何 One UI 专有类型
design  app.oneui.design                     —— 颜色 / 圆角 / 字号 / 自绘控件
sesl    app.oneui.design.sesl（暂不启用）    —— 唯一允许 import SESL 的地方
```

- `core` 只负责行为：数据、布局算法、动画、手势。
- `design` 只负责外观：token、drawable、style、自绘 View/Composable。
- `sesl` 现在是空目录 + 文档，见 [SESL-DECISION.md](./SESL-DECISION.md)。
- 当前 `design` 层的实现落在 Lawnchair 原有的主题位置
  （`app/lawnchair/ui/theme/` 与 `lawnchair/res/values/`），等设计层代码量上来
  再拆独立包，避免第一阶段就引入跨模块编译。

## 4. 已完成的改动（分支 `main`）

| 改动 | 位置 |
| --- | --- |
| 应用 ID → `com.oneui.desktop` | `build.gradle` 的 `github` / `nightly` / `play` flavor |
| 应用名 → `OneUI 桌面` | `build.gradle` 的 `derived_app_name` |
| 去掉 `.debug` 后缀 | `build.gradle` 的 `debug` buildType |
| 默认图标形状 → 三星 squircle | `lawnchair/res/values/config.xml` 的 `config_default_icon_shape = sammy` |
| 抽屉标签改成单行 | `config_default_enable_two_line_allapps = false` |
| 设置入口改名「桌面设置」 | `lawnchair/res/values/strings.xml` |
| One UI 圆角体系（12/16/20/26dp） | `app/lawnchair/ui/theme/Shape.kt` |
| One UI 字号体系（20sp 标题 / 16sp 行标题 / 13sp 说明） | `app/lawnchair/ui/theme/Type.kt` |
| 应用图标重绘（蓝色渐层 + 嵌套 squircle） | `lawnchair/res/drawable/oneui_icon_*.xml`、`ic_launcher_home.xml` |
| **桌面网格 4 列 × 6 行、Dock 5 位** | `lawnchair/res/xml/device_profiles.xml`（`4_by_6`）+ `PreferenceManager.kt` 默认值 |
| **关闭独立 smartspace 首屏页** | `config_default_enable_smartspace = false` |
| **关闭 Dock 上方搜索条** | `config_default_hotseat_mode = disabled` |
| **首屏 One UI 时钟 / 日期（4×1 AppWidget + 覆盖层回退）** | `lawnchair/src/app/lawnchair/oneui/OneUiClockWidgetProvider.kt`、`OneUiClockWidgetController.kt`、`lawnchair/res/layout/oneui_clock_widget.xml`、`lawnchair/res/layout/launcher.xml`（`oneui_clock`）、`lawnchair/src/app/lawnchair/views/OneUiClockView.kt` |
| **首屏默认排布（4 图标 + 5 Dock）** | `lawnchair/res/xml/default_workspace_4x6.xml` |
| **抽屉底部搜索栏 + 三点菜单** | `lawnchair/res/layout/search_container_all_apps.xml`、`AllAppsSearchInput.kt`、`ActivityAllAppsContainerView.java` |
| **抽屉 One UI 底部菜单（排序 / 设置）** | `lawnchair/src/app/lawnchair/ui/popup/OneUiDrawerMenu.kt` |
| **抽屉字母分组标题** | `lawnchair/src/app/lawnchair/allapps/OneUiAllAppsGridAdapter.kt`、`LawnchairAlphabeticalAppsList.kt`、`FloatingHeaderView.java` |

## 5. 首屏形态（本阶段核心）

### 5.1 网格：4 × 6 + 5 Dock

One UI 手机首页是 4 列 × 6 行，Dock 5 个位置。Lawnchair 里网格由三处共同决定，
**改的时候必须一起看**，否则会出现「界面 4×6、数据库还是 5×4」的错位：

| 层 | 文件 | 作用 |
| --- | --- | --- |
| 设备配置 | `lawnchair/res/xml/device_profiles.xml` | 定义 `4_by_6` 这个 `GridOption`（行/列/Dock 数）以及默认布局 ID |
| 覆盖层 | `lawnchair/src/app/lawnchair/DeviceProfileOverrides.kt` | 把偏好值套到 `DBGridInfo` 上 |
| 偏好默认值 | `lawnchair/src/app/lawnchair/preferences/PreferenceManager.kt` | `pref_workspaceColumns / prec_workspaceRows / pref_hotseatColumns` 的默认值 |

> **关键坑**：真正决定首次启动网格的是 `PreferenceManager.kt` 里的
> `IntPref` 默认值（`workspaceRows = 6`、`hotseatColumns = 5`），
> 只改 `device_profiles.xml` 不够。改完后数据库名会从 `launcher_5_4_4.db`
> 变成 `launcher_6_4_5.db`，这可以作为「是否真的生效」的判据。

### 5.2 首屏布局

`default_workspace_4x6.xml`：

- **第 0 行留空**，给时钟/日期块让位。
- 第 1 行 4 个图标：设置 / 时钟 / 日历 / 文件。
- Dock 5 个：电话 / 短信 / 相机 / 图库 / 通讯录。
- 每一项都是 `<resolve>` + 意图回退：设备上没装某个应用时只跳过该格，不会让整份布局解析失败。
- DocumentsUI 的 `MAIN/LAUNCHER` 意图在本机无法解析（系统把它挂在 `APP_FILES` 分类下），
  必须用 `launcher:packageName` + `launcher:className` 显式组件。

### 5.3 时钟：AppWidget + 覆盖层回退

时钟现在是一个真正的主屏幕小组件（`OneUiClockWidgetProvider`），原自绘
`OneUiClockView` 保留为回退层。

**小组件**

- `lawnchair/res/xml/oneui_clock_widget_info.xml`：4×1、`targetCellWidth/Height`、
  `resizeMode=none`、`updatePeriodMillis=0`、无配置页。
- `lawnchair/res/layout/oneui_clock_widget.xml`：44sp `sans-serif-light` 时间 +
  14sp 中文日期；两个 `TextClock` 自己走时，所以不需要定时 `onUpdate`。
- `OneUiClockWidgetProvider` 注册在 `lawnchair/AndroidManifest.xml`；`onUpdate`
  写入 RemoteViews 并登记 widget id，`onDeleted` 注销 id。
- `OneUiClockWidgetController` 在 `LawnchairLauncher.onResume` 里：
  - 首次启动（`auto_add_attempted` 未记录）用 `Launcher.addPendingItem` 把 4×1
    小组件落到第 0 页 (0,0)；
  - `BIND_APPWIDGET` 由系统 `ACTION_APPWIDGET_BIND` 对话框授权，勾选「始终允许」
    后只需确认一次；取消则保留覆盖层，不会自动重试；
  - 绑定成功后关闭 `TYPE_WIDGET_RESIZE_FRAME`，避免 4×1 小组件显示调整框；
  - 清理已经不存在于 `AppWidgetManager` 的 id。
- 小组件可长按拖动、拖到「移除」删除，与普通 One UI 小组件一致。

**回退覆盖层**

- 首次绑定被取消、或用户删除小组件时，`launcher.xml` 里的 `oneui_clock`
  继续显示 44sp 时间 + 14sp 日期，保证首屏永远有时钟。
- 覆盖层通过 `OneUiClockWidgetController` 的状态回调切换 `GONE / VISIBLE`，
  不再用 pre-draw 轮询：窗口静止时不会再有帧，轮询会漏掉删除事件。
- 随页面滚动仍由 `OnPreDrawListener` 读取 `Workspace.getScrollX()` 实现
  （`translationX = -scrollX`）；滑到第二页时移出屏幕，滑回第一页恢复。

**已知限制**

- 只有一种样式，没有 One UI 的 4 种时钟样式 / 配置页。
- `BIND_APPWIDGET` 首次仍需用户确认一次；这是侧载应用无法绕过的系统限制。

### 5.4 关掉的两处 Lawnchair 默认 UI

- **smartspace 独立首屏页**：`FeatureFlags.topQsbOnFirstScreenEnabled()` 读取
  `config_default_enable_smartspace`，置 `false` 后不再插入那个「点击以设置」的空页。
- **Dock 上方搜索条**：`config_default_hotseat_mode = disabled` 会去掉
  `search_container_hotseat` 里的 QSB，但保留 Dock 图标本身。

### 5.5 应用抽屉底部搜索栏

One UI 的抽屉搜索框在**底部**，网格从顶部开始、可以滚到搜索框下面。Lawnchair/AOSP
原本把搜索框作为 `ActivityAllAppsContainerView` 的第一个子 View 放在顶部，并由
`all_apps_header` 的 `layout_below` / `layoutBelowSearchContainer()` 决定列表位置。
这里用「底部停靠」模式改造，和 AOSP 的 `isSearchBarFloating()` 区分开：

| 层 | 改动 |
| --- | --- |
| 容器 | `ActivityAllAppsContainerView` 新增 `isSearchBarDockedBottom()` 钩子（默认 `false`） |
| Lawnchair | `SearchContainerView` 覆写为 `true` |
| 布局 | `search_container_all_apps.xml` 根布局 `layout_alignParentBottom="true"`；`all_apps_content.xml` 去掉 header 的 `layout_below` |
| Insets | `AllAppsSearchInput#setInsets` 在停靠模式下把导航栏 inset 当作 `bottomMargin`，并跳过原来的 `offsetTopAndBottom(allAppsSearchVerticalOffset)`；`onApplyWindowInsets` 另外读取 IME inset，键盘弹出时把搜索栏抬到键盘上方 |
| 列表 | 停靠时 `AdapterHolder#applyPadding` 额外预留 `search_box_container_height + oneui_search_bar_bottom_margin`，最后一排图标可滚出搜索框；快速滚动条同样上移 |
| 外观 | 48dp 高、24dp 圆角胶囊、16sp 提示文字「搜索」；无放大镜，右端是 48dp 三点菜单按钮（`oneui_ic_more_vert`） |
| 交互 | 点搜索框获得焦点并弹出键盘；点三点菜单先收起键盘，再打开 One UI 风格底部菜单（见 §5.6） |

关键点：

- header 与列表改为 `alignParentTop()`（与 `isSearchBarFloating()` 同一条分支），
  但**不**走 floating 的 LauncherState 定位逻辑——那条路径依赖 hotseat QSB，本项目
  已关闭 `config_default_hotseat_mode`，直接复用会 NPE。
- 停靠模式下不再绘制顶部 header protection 遮罩（`dispatchDraw` 的判断），
  否则遮罩高度会少算状态栏那一段。
- 搜索框隐藏开关（`hideAppDrawerSearchBar`）生效时，列表不再预留底部空白。
- **键盘**：Launcher 的窗口是 `adjustPan`，系统不会自动把底部搜索栏推到键盘上方，
  因此在 `onApplyWindowInsets` 里用 `max(navBottom, imeBottom) + 8dp` 重算
  `bottomMargin`。`setInsets()` 也走同一段 `applyDockedBottomMargin()`，避免被
  容器后续的 inset 分发覆盖。
- 视觉基线取 One UI 8.0 的**全宽胶囊 + 三点菜单**（左对齐「搜索」）。One UI 8.5
  改成更窄的居中浮层，后续如要跟进，只需改 `search_wrapper` 的宽度/外边距和
  `search_input_fg` 的圆角，不必动停靠逻辑。

### 5.6 抽屉底部菜单

三点菜单不再直接跳设置页，而是先弹 One UI 风格底部菜单
（`lawnchair/src/app/lawnchair/ui/popup/OneUiDrawerMenu.kt`）：

- **入口**：`AllAppsSearchInput` 点击 `menu_btn` 时先 `input.clearFocus()` +
  `launcher.hideKeyboard()`，再用 `ComposeBottomSheet.show(launcher)` 弹层。
  这样键盘不会压在菜单上，抽屉本身保持打开。
- **主菜单**：顶部 32×4dp 拖动条；两行 56dp 选项，左侧 24dp 图标、16sp 文案，
  右侧当前值 + 箭头：
  - **排序**：右侧显示「字母顺序」，点进去是排序子页。
  - **设置**：关闭菜单并打开 `PreferenceActivity` 的 `AppDrawer` 路由。
- **排序子页**：返回箭头 + 20sp 标题；「字母顺序」单选并打勾；「自定义」置灰，
  副标题「即将支持」。自定义排序需要新增排序模式与拖拽持久化（见 §8），
  所以这里先如实标注，不做假开关。
- **返回行为**：排序子页用 `BackHandler` 回到主菜单；再返回或点遮罩才关闭整个
  菜单。关闭后抽屉仍停留在应用列表，不会退回桌面。
- **外观**：复用 `ComposeBottomSheet` 的 24dp 顶部圆角、遮罩和开合动画；
  内容加 `navigationBarsOrDisplayCutoutPadding()`，三按钮导航下不会压住按钮。

### 5.7 抽屉字母分组

字母序抽屉按 `AppInfo.sectionName` 插入首字母标题：

| 层 | 改动 |
| --- | --- |
| 数据 | `LawnchairAlphabeticalAppsList#addAppsWithAlphabetSections` 按 sectionName 切组，每个新首字母前插入 `OneUiSectionItem`；组内仍调用 AOSP `addAppsWithSections`，快速滚动条的 section 位置保持正确 |
| 视图类型 | `OneUiSectionItem.VIEW_TYPE = (1 shl 11) or VIEW_TYPE_ALL_APPS_DIVIDER`；带上 divider 位后 `AlphabeticalAppsList` 会在每个分组处重置行计数，`GridSpanSizer` 也按整行处理 |
| Adapter | `OneUiAllAppsGridAdapter` 只拦截该 viewType，用 `oneui_all_apps_section_header.xml`（14sp medium、0.06 字距、次级文字色）渲染；图标、文件夹、工作资料卡片、搜索结果仍由 `AllAppsGridAdapter` 处理 |
| 顶部留白 | 停靠搜索栏时 `ActivityAllAppsContainerView#setInsets` 使用 `max(allAppsPadding.top, insets.top) + oneui_drawer_top_margin(8dp)`，避免第一个字母被状态栏裁切 |
| 列表裁剪 | `FloatingHeaderView#applyVerticalMove` 在停靠模式下把列表 `clipTop` 置 0；AOSP 原本把列表顶部裁掉 36dp 给悬浮 header 让位，会把第一个分组标题吃掉 |
| 范围 | 只影响字母序（`drawerList = true`）；文件夹仍排在最前；搜索结果和分类模式不插入标题 |

> One UI 7 的字母序抽屉实际没有行内字母标题，而是右侧字母索引条 + 拖动时的大号字母
> 气泡。行内标题现在由「字母分组标题」开关控制（默认开），关掉后只保留右侧索引。

### 5.8 抽屉右侧索引条

AOSP 的 `RecyclerViewFastScroller` 画的是 6–8dp 的主题色长条，拖动时用
`FastScrollThumbDrawable` 的泪滴气泡。One UI 的做法是「细灰轨道 + 拖动加粗的滑块 +
蓝色圆形字母气泡」，因此新增 `OneUiFastScroller` 子类，只替换绘制和气泡定位；
滚动位置映射、触摸判定、section 切换仍然沿用 AOSP 实现。

| 层 | 改动 |
| --- | --- |
| 布局 | `all_apps_fast_scroller.xml` 的标签换成 `app.lawnchair.views.OneUiFastScroller`；只作用于应用抽屉，小部件选择器仍用 AOSP 版本 |
| 轨道 | 3dp 宽、`TextColorPrimary` 10% 透明度、圆角胶囊，从列表顶部贯穿到底部 |
| 滑块 | 静止 3dp、拖动 6dp（150ms 展开），高度沿用 `fastscroll_thumb_height`（52dp），40% 透明度 |
| 气泡 | 52dp 蓝色（`#3E8EFF`）圆形 + 白色 22sp medium 字母；拖动开始时从 0.7 缩放到 1.0 |
| 定位 | `OneUiFastScroller#positionBubble` 用滑块中心对齐气泡中心，并夹在轨道范围内；AOSP 的泪滴气泡公式不再使用 |
| 手势 | 继续调用 `systemGestureExclusionRects`，贴着右边缘拖动不会触发返回手势 |
| 分组标题 | 新增 `pref_drawerSectionHeaders`（默认开）。关闭后 `addAppsWithSections` 直接走 AOSP 实现，抽屉只剩右侧索引，与 One UI 原版一致 |

改动集中在三处：`OneUiFastScroller.kt`（绘制 / 气泡）、`all_apps_fast_scroller.xml`
（换 View 类型）、`AppDrawerPreferences.kt`（开关）。`AllAppsRecyclerView` 的
`scrollToPositionAtProgress` 和 `AllAppsFastScrollHelper` 没有改动。

### 5.9 抽屉自定义排序

抽屉支持两种排序模式：`alphabetical`（默认）和 `custom`。模式与顺序分别存在
`pref_drawerSortMode` 和 `pref_drawerCustomOrder`（换行分隔的 `ComponentKey.toString()`）。

| 层 | 改动 |
| --- | --- |
| 排序模式 | `PreferenceManager#drawerSortMode`（`StringPref`，默认 `"alphabetical"`）；模式切换时通过主监听器直接刷新抽屉，不重建 Activity |
| 重排 | `LawnchairAlphabeticalAppsList#addAppsWithSections` 在自定义模式下先按 `drawerCustomOrder` 重排；未记录的应用（新装 / 恢复）保持字母序排到末尾 |
| 渲染 | 自定义模式走 `addAppsInCustomOrder()`：不插字母标题，应用连续排列；每行合成一个空名 `FastScrollSectionInfo`，索引条仍可拖动，但 `animatePopupVisibility(!TextUtils.isEmpty(...))` 会自动隐藏字母气泡 |
| 编辑入口 | 抽屉底部菜单「排序 → 编辑自定义顺序」，或设置 → 应用抽屉 → 排序 |
| 编辑器 | `AppDrawerSortPreference.kt`：`ListPreference` 选模式，自定义模式下显示 `ReorderablePreferenceGroup` + `AppItem` + `ReorderableDragHandle`，松手时把整份列表写回偏好 |
| 刷新 | `PreferenceManager#refreshAllApps` 调用 `personalAppList.updateAdapterItems()`；抽屉打开时切模式立即生效，设置页拖完返回也已是新顺序 |

**为什么在设置页拖拽而不是抽屉内拖拽**：AOSP `AllAppsGridAdapter` 没有行拖拽支持，
`AlphabeticalAppsList` 的 adapter item 也是每次 `updateAdapterItems()` 重建的；
在抽屉里接 `ItemTouchHelper` 需要同时处理搜索、文件夹、工作资料和分页，成本远高于
复用设置页已有的 `ReorderablePreferenceGroup`。抽屉只负责呈现结果，编辑在设置页完成。

**数据顺序**：自定义顺序保存的是完整应用列表的 component key。应用被卸载后 key 留在
字符串里但不会匹配；新装应用没有 key，`sortedBy` 时拿到 `Int.MAX_VALUE`，因此排在
末尾并保持字母序，直到用户再次拖动。

### 5.10 设置页 One UI 化

设置首页原来是 Lawnchair 的一张「大卡片 + 每行带图标」，且整页带公告、备份、实验特性等
与桌面无关的入口。One UI 化后分成四张按用途分组的卡片，行内只有标题 + 说明 + 箭头，
不再有前置图标：

| 分组 | 行 |
| --- | --- |
| 布局 | 主屏幕、应用抽屉、Dock 栏、文件夹 |
| 外观 | 图标与风格、资讯一览、搜索栏、通知 |
| 交互 | 手势、最近任务（Quickstep，仅启用时） |
| （无标题） | 关于 |

具体改动：

1. **`PreferencesDashboard.kt`**：用 `PreferenceGroup(heading = …)` +
   `NavigationActionPreference` 重写，去掉 `PreferenceCategoryGroup` 与
   `PreferencesOverflowMenu`；公告 `AnnouncementPreference` 不再显示，备份 / 恢复 /
   实验特性入口从 UI 移除（路由仍保留，供调试或以后迁移到「关于」）。
2. **通知独立成页**：`NotificationsPreferences.kt` 从 `GeneralPreferences` 抽出
   「通知圆点」分组（开关、圆点颜色、数量、文字颜色与对比度警告），新增
   `Notifications` 顶层路由；`GeneralPreferences` 改名为「图标与风格」，
   只保留旋转、字体、图标、颜色。
3. **文案**：新增 `oneui_settings_appearance`、`oneui_settings_notifications`、
   `oneui_settings_section_look`、`oneui_settings_section_controls`；`general_description`
   改为「图标、颜色与字体」（通知已移出）。
4. **调试入口**：只有打开「调试菜单」偏好时才在标题栏显示扳手图标，普通用户看不到。
5. **关于页**：`about/About.kt` 改成 One UI 结构：图标 + 应用名 + 版本，下面一张
   「法律信息」卡片（致谢 / 隐私政策），底部一行开源声明。移除夜间更新检查、
   社区链接与贡献者列表——自编译分支不需要这些入口，致谢仍可从卡片进入。
6. **二级页整理**：主屏幕页把「布局」（网格 / 锁定 / 页面指示点）提到最前，
   清空主屏幕移到末尾；双击手势与独立「手势」页重复，从主屏幕页移除。
   应用抽屉页把「网格」提到「通用」之前。设置首页不再显示开发版警告条；
   补译无限滚动、页面指示点、Material You 弹出菜单、搜索栏独立背景、
   清空主屏幕等中文文案，主屏幕副标题改为「网格、壁纸、图标」。

### 5.11 图标按压动画

AOSP 的 `FastBitmapDrawable` 在按下时把图标**放大**到 1.1 倍，One UI 则是轻微
**缩小**。改动只在 `iconloaderlib` 一处：

| 参数 | AOSP | One UI 层 |
| --- | --- | --- |
| `PRESSED_SCALE` | `1.1f` | `0.92f` |
| `HOVERED_SCALE` | `1.1f` | `1.05f`（鼠标悬停，触屏不生效） |
| `CLICK_FEEDBACK_DURATION` | `200ms` | `150ms` |
| 按下插值 | `AccelerateInterpolator` | `DecelerateInterpolator`（按下立即响应） |
| 松开插值 | `DecelerateInterpolator` | `AccelerateInterpolator` |

缩放只作用于图标 drawable，文字标签保持不动，与 One UI 一致；工作区、文件夹预览、
抽屉、Dock 共用同一个 drawable，所以一处改动全场景生效。截图见
`docs/refactor/shots/anim-icon-press.png`（左静止、右按下）。

### 5.12 抽屉开合错落

One UI 的抽屉不是整块网格刚性上移，图标落位时有一个由近及远的轻微错落。
实现放在 `AllAppsTransitionController#setProgress`，每次进度更新时遍历
`AllAppsRecyclerView` 的可见子项：

| 参数 | 值 | 说明 |
| --- | --- | --- |
| `ICON_STAGGER_MAX_DELAY` | `0.3` | 按 `childTop / recyclerHeight` 归一化，顶部行 0、底部行 0.3 |
| `ICON_STAGGER_TRANSLATION_DP` | `12dp` | 行落位前的额外下移量，结束后回到 0 |
| 作用 | `translationY` | 不改 alpha，避免滑动过程中出现空白面板 |

`mProgress` 的 1 表示桌面、0 表示抽屉完全打开，所以代码用 `1 - progress` 得到
开合进度；顶部行先进入视口先稳定，越靠下的行越晚。只影响抽屉网格子项，
工作区、Dock、搜索结果、索引条不受影响，抽屉打开后滚动也不会残留位移。

### 5.13 长按菜单弹性展开

长按桌面的菜单走 `ArrowPopup` 的 Material You 分支（`config_default_enable_material_u_popup`
默认 `true`）。AOSP 的展开是 `0.5 → 1.02 → 1.0`、200ms；One UI 的幅度更小、
尾巴更短，参数改为：

| 参数 | AOSP | One UI 层 |
| --- | --- | --- |
| 展开缩放 | `0.5 → 1.02` | `0.86 → 1.015`，再从 `1.015 → 1.0` |
| 展开时长 | `200ms` | `240ms` |
| 回弹时长 | `200ms` | `120ms`（接在主缩放之后） |
| 淡入时长 | `83ms` | `90ms`，子项 `110ms` |
| 收起时长 | `233ms` | `180ms`，缩到 `0.9` |

只改 `ArrowPopup` 的参数，长按菜单、文件夹弹出菜单、快捷方式菜单和小组件弹窗
会一起生效。收起仍然先淡出再移除视图。

## 6. 构建与运行

```powershell
cd E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop

# 环境（当前机器已配好）
$env:JAVA_HOME = 'D:\AndroidDevelopment\jdk'
$env:ANDROID_HOME = 'D:\AndroidDevelopment\Sdk'

# 构建（首次约 30 分钟，增量 1–5 分钟）
.\gradlew.bat :assembleLawnWithQuickstepGithubDebug

# 产物
# build\outputs\apk\lawnWithQuickstepGithub\debug\*.apk

# 安装并设为默认桌面
adb install -r -g <apk>
adb shell cmd package set-home-activity com.oneui.desktop/app.lawnchair.LawnchairLauncher
```

模拟器：`D:\AndroidDevelopment\Sdk\emulator\emulator.exe -avd medium_phone`。

> **改默认布局 / 网格后必须清数据**，否则旧的 `launcher_*.db` 和 SharedPreferences
> 会把新默认值挡住：
>
> ```powershell
> adb shell pm clear com.oneui.desktop
> ```
>
> 验证是否生效：`adb shell "run-as com.oneui.desktop ls databases"`，
> 应出现 `launcher_6_4_5.db`。

## 7. 验证记录（模拟器 `medium_phone`，Android 15 / API 35）

| 项目 | 结果 |
| --- | --- |
| 构建 | `:assembleLawnWithQuickstepGithubDebug` 成功，APK ≈ 46 MB |
| 网格 | 4 列 × 6 行，Dock 5 位，DB 为 `launcher_6_4_5.db` |
| 首屏 | 时钟 `13:17` + 日期 `10月27日 星期二`；第 1 行 4 图标；Dock 5 图标 |
| 首屏翻页 | 临时增加第二页后左滑：时钟随第一页移出屏幕；右滑回第一页恢复；`OnPreDraw` 跟随中间帧 |
| 时钟小组件 | 首次启动弹系统「是否允许创建微件」授权框；勾选「始终允许」后 4×1 小组件落到第 0 页 (0,0)，`dumpsys appwidget` 确认 provider 已托管 |
| 时钟回退 | 长按小组件拖到「移除」后，撤销条消失时覆盖层恢复；点「撤销」则小组件恢复、覆盖层再次隐藏 |
| 旧 UI 残留 | 无 AOSP 搜索条、无 smartspace「点击以设置」页 |
| 抽屉 | 4 列网格、单行标签、squircle 图标；搜索胶囊停靠底部（48dp 高、24dp 圆角、左对齐「搜索」、右侧三点菜单） |
| 抽屉键盘 | 点搜索框弹出键盘后，搜索栏保持在键盘上方，输入时胶囊背景不消失 |
| 抽屉三点菜单 | 打开 One UI 底部菜单；排序页可进入/返回，设置页正常跳转，点遮罩关闭后抽屉仍在 |
| 抽屉菜单 + 键盘 | 键盘弹出时点三点菜单，键盘先收起，菜单出现在抽屉底部 |
| 抽屉分组 | 字母序下按 B/C/D/O/R/S/T/W/X 分组；首字母不被状态栏或列表裁剪；文件夹、搜索结果、长按菜单、键盘避让不受影响 |
| 抽屉索引条 | 静止 3dp 半透明轨道；拖动时滑块 3dp→6dp、蓝色 52dp 圆形气泡显示当前字母；列表跳到对应 section（B→D→W 已验） |
| 分组标题开关 | 关闭后无行内标题、仅保留右侧索引；重新打开恢复分组 |
| 自定义排序 | 菜单切「自定义」后抽屉立即变连续网格（无字母标题、无字母气泡），索引条仍可拖动；切回字母序分组恢复 |
| 排序编辑器 | 设置 → 应用抽屉 → 排序：显示当前模式与拖拽列表；把 CarMore 从第 1 位拖到第 4 位后返回抽屉，顺序一致且重启后保持 |
| 新装应用 | 代码路径为「缺失 key → `Int.MAX_VALUE` → 末尾字母序」，尚未用真实新装应用实测 |
| 设置首页 | 四张卡片：布局 / 外观 / 交互 / 关于；行内无前置图标，公告、备份、实验特性入口不再出现 |
| 图标与风格 | 设置 → 图标与风格：标题正确，只保留旋转、字体、图标、颜色；不再包含通知圆点 |
| 通知页 | 设置 → 外观 → 通知：独立页显示通知圆点开关、圆点颜色、数量开关；授予通知使用权后控件正常出现 |
| 关于页 | 图标 + 名称 + 版本 +「法律信息」卡片（致谢 / 隐私政策）+ 开源声明；无更新检查与社区链接 |
| 二级设置页 | 主屏幕：布局 → 通用 → 订阅 → 样式 → 壁纸 → 弹出菜单 → 状态栏 → 图标 → 小组件 → 清空；应用抽屉：布局 → 网格 → 通用 → 样式 → 图标 → 高级；无开发版警告条 |
| 长按菜单 | 长按空白处从触点轻微放大 + 回弹展开；点遮罩、返回、进出应用后正常关闭 |
| 图标按压 | 长按前按下图标可见缩小到 0.92、松开 150ms 回弹；文字标签不缩放（`anim-icon-press.png`） |
| 抽屉开合 | 打开时网格行级 12dp 错落归位；打开后滚动、索引条、搜索、返回桌面均正常，图标无残留位移 |
| 长按桌面 | 「背景 / 小部件 / 启动器设置」菜单正常，时钟层未阻塞手势 |
| 最近任务 | Quickstep 正常，时钟不会出现在任务快照里 |

截图：`docs/refactor/shots/home.png`（首屏）、`drawer.png`（抽屉）、
`drawer-sections.png`（抽屉字母分组）、`drawer-index.png`（索引条静止状态）、
`drawer-index-drag.png`（拖动字母气泡）、`drawer-keyboard.png`（抽屉 + 键盘）、
`drawer-menu.png`（抽屉底部菜单）、`drawer-sort.png`（排序子页）、
`drawer-sort-menu.png`（自定义排序菜单）、`drawer-sort-custom.png`（自定义顺序抽屉）、
`drawer-sort-editor.png`（拖拽排序编辑器）、`drawer-sort-mode.png`（排序方式弹窗）、
`settings-root.png`（设置首页）、`settings-home.png`（主屏幕设置）、
`settings-app-drawer.png`（应用抽屉设置）、`settings-appearance.png`（图标与风格）、
`settings-notifications.png`（通知页）、`settings-about.png`（关于页）、
`anim-icon-press.png`（图标按压对比）、`clock-widget.png`（时钟 AppWidget）、
`clock-widget-remove.png`（长按拖动移除）、
`long-press.png`（长按菜单）、`recents.png`（最近任务）。

## 8. 下一步里程碑

1. ~~**首屏形态**：4×6 网格 + 5 Dock + 时钟/日期 + 默认排布~~ ✅ 已完成
2. ~~**抽屉底部搜索栏**：底部停靠胶囊 + 三点菜单 + 键盘避让~~ ✅ 已完成
3. ~~**抽屉底部菜单**：排序 / 设置底部菜单、排序子页、返回与键盘处理~~ ✅ 已完成
4. ~~**抽屉分组 / 索引**：行内字母标题、右侧细灰索引条、拖动字母气泡、分组标题开关~~
   ✅ 已完成。剩余可选项：One UI 8.5 的窄居中搜索胶囊（只改宽度/外边距/圆角，
   不动停靠逻辑）。
5. ~~**时钟组件化**：4×1 AppWidget + 系统绑定授权 + 覆盖层回退~~ ✅ 已完成（§5.3）。
   剩余可选项：One UI 的 4 种时钟样式与配置页。
6. ~~**自定义排序**：模式（字母 / 自定义）+ 设置页拖拽调序 + SharedPreferences 持久化~~
   ✅ 已完成（§5.9）。剩余：按使用频率排序（需要接 `UsageStatsManager` 或
   Lawnchair 的 `PredictionProvider`），以及抽屉内直接长按拖拽。
7. ~~**设置页收口**：只保留桌面相关项，其余隐藏；二级页改用 One UI 分组卡片~~
   ✅ 已完成（§5.10）。主屏幕 / 应用抽屉二级页已重排并补齐中文。
   剩余：手势页文案与卡片顺序，应用抽屉「糖果」实验布局入口是否隐藏。
8. **动画复刻**：抽屉开合（背景模糊 + 图标错落）、图标按压缩放、页面切换、
   长按菜单弹出/收起，统一用 One UI 的 `FastOutSlowIn` / 弹簧参数。
   - ~~图标按压~~ ✅ 已改为 0.92 缩小 + 150ms（§5.11）。
   - ~~抽屉开合错落~~ ✅ 行级 12dp 错落归位（§5.12）。
   - ~~长按菜单弹性展开~~ ✅ 0.86 起缩放 + 120ms 回弹（§5.13）。
   - 剩余：抽屉背景模糊、页面切换视差。
9. **裁剪 Quickstep**：15-dev 已无 `withoutQuickstep` flavor，需要自己接一个
   不带 recents 的构建变体，把 332 MB 的基线压下来。

## 9. 仓库与上游同步

本仓库不是 Lawnchair 的完整历史 fork：基线快照 `505dbc4` 来自一份 shallow
clone，所以历史里只有一个「Import Lawnchair 15-dev」根提交，而不是上游完整的
提交图。这样做的好处是仓库自包含、clone 后无需联网即可构建
（`platform_frameworks_libs_systemui` 也已从 submodule 改为随仓库分发的普通源码）。

One UI 的改动被刻意收在十几个文件里（见 `tools/oneui-overlay.txt`）。
跟进 Lawnchair 上游时：

1. 重新 clone 一份干净的 Lawnchair 15-dev（或更高版本）。
2. 运行 `.\tools\export-overlay.ps1 -Target <clean-checkout>`。
3. 解决冲突（主要是 `device_profiles.xml`、`PreferenceManager.kt`、`launcher.xml`）。
4. 在目标仓库构建，并按 §7 的清单验证。
5. 把结果提交回本仓库，替换 `src/`、`res/`、`lawnchair/` 等基线目录。

这样即使 Lawnchair 上游停更、或以后要换回 SESL/自研控件，也只需要换基线 +
重放覆盖层，不会把 One UI 的视觉代码和 Launcher3 内核绑死。

远程：

- `origin`   `https://github.com/a435630144/OneUILauncher.git`
- `upstream` `https://github.com/LawnchairLauncher/lawnchair.git`（只作参照）

## 10. 抽屉拖拽编辑模式

- 入口：`ItemLongClickListener.onAllAppsItemLongClick` → `newDragOptions` 设
  `DragOptions.isDrawerDrag`（`LawnchairLauncher` 且非搜索态）。
  `Workspace` 在 drag 起手 / 结束时跳过常规 workspace 处理。
- 视觉与落点由 `OneUiDrawerDragOverlay`（DragLayer 全屏覆盖层 = DropTarget +
  DragListener）负责：压暗背景、网格缩放 0.84 卡片、点阵 + 白描边落点、
  左右边缘按页滚动、卡片外上下空白区投放主屏（`Workspace.addAppFromDrawer`）。
- 重排落库：`LawnchairAlphabeticalAppsList.moveAppInCustomOrder` 写
  `pref_drawerCustomOrder` 后 `onAppsUpdated()` 即时刷新。
- K2 注意：Java raw `DragView` 参数在 Kotlin 声明处需写 `DragView<Launcher>`，
  否则报 "One type argument expected"。

- `upstream` `https://github.com/LawnchairLauncher/lawnchair.git`（只作参照）
