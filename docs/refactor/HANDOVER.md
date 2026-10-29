# 交接文档（OneUI 桌面 / Lawnchair 15 基线）

> 更新时间：2026-10-29
> 工作目录：`E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`
> 分支：`main`（HEAD 以 `git log` 为准）
> 架构与设计取舍见 [ARCHITECTURE.md](./ARCHITECTURE.md)，SESL 取舍见 [SESL-DECISION.md](./SESL-DECISION.md)。

---

## 1. 一句话状态

底层 Launcher3 / Lawnchair 15 已经能构建、能装、能当默认桌面；首屏 4×6 + 5 Dock
+ **4×1 时钟 AppWidget**（未绑定时覆盖层回退）完成；应用抽屉的**底部搜索胶囊**、
**One UI 底部菜单**、**字母分组标题**、**右侧索引条**、**自定义排序**全部完成。
设置首页已改成四张 One UI 卡片，通知独立成页，「关于」页改成图标 + 版本 + 法律信息卡。
动画方面图标按压改成 0.92 缩小、抽屉打开时网格行级错落归位、长按菜单弹性展开。
**抽屉拖拽编辑模式完成**：长按图标进入编辑态（背景压暗、网格缩放卡片、格点 +
描边预览），卡片内拖动重排、左右边缘翻页、上下空白区投放主屏。
抽屉拖拽编辑模式已升级为**真分页网格**（拖到边缘翻页、末页右边缘自动建新页、跨页落位，v0.21）。
下一步是抽屉背景模糊、页面切换视差，以及 One UI 时钟的 4 种样式 / 配置页。

当前 APK：

```
build\outputs\apk\lawnWithQuickstepGithub\debug\*.apk
```

> 文件名形如 `Lawnchair.15.Dev.(<短哈希>).github.debug.apk`；短哈希取构建时的 HEAD，
> 重新构建后会与 `git log` 最新提交一致。

---

## 2. 已完成

| 模块 | 内容 | 关键文件 |
| --- | --- | --- |
| 仓库 | Lawnchair 15-dev 快照并入根目录，旧 Compose v0.6 曾移入 `legacy/`（v0.21 清理移除，源码在 git 历史 `3cd6f19`） | `README.md`、`NOTICE`、`tools/oneui-overlay.txt` |
| 构建 | 单模块 Gradle，`platform_frameworks_libs_systemui` 随仓库分发，clone 后可离线构建 | `build.gradle`、`gradle.properties` |
| 应用身份 | `com.oneui.desktop`、显示名「OneUI 桌面」、无 `.debug` 后缀 | `build.gradle` |
| 网格 | 4 列 × 6 行、Dock 5 位，DB 名 `launcher_6_4_5.db` | `device_profiles.xml`、`PreferenceManager.kt` |
| 首屏 | 第 0 行留空给时钟；第 1 行 4 图标；Dock 5 图标 | `default_workspace_4x6.xml` |
| 时钟 | 4×1 AppWidget（44sp 时间 + 14sp 中文日期），首次启动自动落位；未绑定 / 删除后覆盖层回退 | `oneui/OneUiClockWidgetProvider.kt`、`OneUiClockWidgetController.kt`、`launcher.xml`、`OneUiClockView.kt` |
| 视觉 | squircle 图标、单行抽屉标签、12/16/20/26dp 圆角、One UI 字号 | `config.xml`、`Shape.kt`、`Type.kt` |
| 抽屉 | 底部停靠搜索胶囊、24dp 圆角、右端三点菜单、键盘弹出时上移 | `search_container_all_apps.xml`、`AllAppsSearchInput.kt`、`ActivityAllAppsContainerView.java` |
| 抽屉菜单 | 三点菜单打开 One UI 底部菜单：拖动条、排序 / 设置行、排序子页（字母序选中、自定义「即将支持」）、返回与遮罩关闭 | `ui/popup/OneUiDrawerMenu.kt`、`ComposeBottomSheet.kt` |
| 抽屉分组 | 字母序按首字母插入分组标题（B/C/D/…）；整行渲染、分组处重置行计数，文件夹在最前，搜索 / 分类模式不受影响 | `OneUiAllAppsGridAdapter.kt`、`LawnchairAlphabeticalAppsList.kt`、`oneui_all_apps_section_header.xml`、`FloatingHeaderView.java` |
| 抽屉索引 | One UI 细灰轨道、拖动加粗、蓝色圆形字母气泡 | `OneUiFastScroller.kt`、`all_apps_fast_scroller.xml` |
| 抽屉排序 | 字母 / 自定义两种模式，设置页拖拽调序并持久化 | `AppDrawerSortPreference.kt`、`LawnchairAlphabeticalAppsList.kt` |
| 设置 | 四张 One UI 卡片、独立通知页、One UI 关于页 | `PreferencesDashboard.kt`、`NotificationsPreferences.kt`、`about/About.kt` |
| 动画 | 图标按压 0.92 缩小、抽屉打开时网格行级错落 | `FastBitmapDrawable.java`、`AllAppsTransitionController.java` |
| 文档 | 架构、验证记录、上游同步、交接说明 | `docs/refactor/` |

---

## 3. 抽屉搜索栏与时钟的实现要点

1. **底部停靠**：`search_container_all_apps.xml` 根布局 `layout_alignParentBottom="true"`；
   `AllAppsContent` 的 header 去掉了 `layout_below`。
2. **容器钩子**：`ActivityAllAppsContainerView.isSearchBarDockedBottom()` 默认 `false`，
   `SearchContainerView`（Lawnchair 侧）覆写为 `true`。
3. **列表留白**：`AdapterHolder#applyPadding` 额外加
   `search_box_container_height + oneui_search_bar_bottom_margin`；
   快速滚动条同步上移。
4. **Insets**：`AllAppsSearchInput#setInsets` 负责导航栏 inset；
   `onApplyWindowInsets` 读取 IME inset，`max(nav, ime) + 8dp` 作为 bottomMargin，
   因此键盘弹出后搜索栏仍在键盘上方。
5. **外观**：48dp 高胶囊、24dp 圆角、16sp「搜索」、右端 `oneui_ic_more_vert` 三点。
   **没有放大镜**（与 One UI 8.0 一致）。
6. **三点菜单**：点击后先 `input.clearFocus()` + `launcher.hideKeyboard()`，
   再用 `ComposeBottomSheet` 打开 One UI 底部菜单（默认 / 排序子页）；设置行
   关闭菜单后跳 `PreferenceActivity` 的 `AppDrawer` 路由，详见 §3.2。
7. **输入态**：`ActivityAllAppsContainerView` 的 `bgVisible` 判断在停靠模式下
   强制为 `true`，否则 `isSearching()` 会把胶囊背景设为透明。

> 注意：`hideAppDrawerSearchBar` 打开时，列表不再预留底部空白，搜索栏本身由
> `AllAppsSearchInput` 的 `isInvisible` / 高度 0 逻辑隐藏。

### 3.1 时钟 AppWidget 与覆盖层回退

时钟首屏由真实 AppWidget 提供，`OneUiClockView` 只作回退。

**AppWidget**

1. `oneui_clock_widget_info.xml`：4×1、`resizeMode=none`、`updatePeriodMillis=0`，
   无配置页；`oneui_clock_widget.xml` 里两个 `TextClock` 自走时。
2. `LawnchairLauncher.onResume` 调 `OneUiClockWidgetController.onLauncherResume`：
   首次启动用 `Launcher.addPendingItem` 落到第 0 页 (0,0)。
3. 首次绑定弹系统 `ACTION_APPWIDGET_BIND` 对话框；勾选「始终允许」后只需确认一次。
4. `onUpdate` 登记 widget id，`onDeleted` 注销；控制器 `onResume` 时清理失效 id。
5. 小组件可长按拖动，拖到顶部「移除」删除。

**覆盖层回退**

1. `launcher.xml` 的 `@id/oneui_clock` 仍保留，绑定成功后 `GONE`。
2. `OneUiClockWidgetController` 维护状态监听器；`onWidgetsBound` /
   `onWidgetsDeleted` 直接通知覆盖层，不依赖窗口绘制帧。
3. 删除小组件、取消授权或清数据后未绑定，覆盖层保持可见。
4. 页面滚动仍由 `OneUiClockView` 的 `OnPreDrawListener` 同步
   `translationX = -workspace.scrollX`；只在 `scrollX` 变化时更新。

验证方法：给 Workspace 增加第二页（拖一个图标到右边缘，或临时改数据库的
`favorites.screen`），左滑后时钟应完全离开屏幕，右滑回第一页后恢复；抽屉、长按菜单、
最近任务不受影响。

### 3.2 抽屉底部菜单

`lawnchair/src/app/lawnchair/ui/popup/OneUiDrawerMenu.kt` 用 Compose 实现
One UI 8 的抽屉溢出菜单，复用 `ComposeBottomSheet.show(launcher)`：

1. **入口**：`AllAppsSearchInput` 的 `menu_btn` 先清焦点、收键盘，再弹层，
   避免键盘顶住菜单；抽屉保持打开，不会退回桌面。
2. **主菜单**：顶部 32×4dp 拖动条；两行 56dp 选项（24dp 图标 + 16sp 文案）：
   「排序」右侧显示当前模式「字母顺序」，点入排序子页；「设置」关闭菜单后打开
   `PreferenceActivity.createIntent(context, AppDrawer)`。
3. **排序子页**：20sp 标题 + 返回箭头；「字母顺序」单选打勾；「自定义」置灰，
   副标题「即将支持」。Lawnchair 15 只有字母序，不做假开关。
4. **返回**：`BackHandler(enabled = showSortOptions)` 先回主菜单；再按返回或点遮罩
   才关闭整个弹层。`ComposeBottomSheet.close(true)` 会同时收起键盘。
5. **资源**：`oneui_drawer_menu` 改为「更多选项」，新增
   `oneui_drawer_sort*` 中英文案；覆盖层清单已加入 `OneUiDrawerMenu.kt`。

### 3.3 抽屉字母分组

`OneUiAllAppsGridAdapter.kt` + `LawnchairAlphabeticalAppsList.kt` 负责字母序抽屉的
首字母标题，单独改 adapter 或分组逻辑都不够：

1. **数据切组**：`LawnchairAlphabeticalAppsList#addAppsWithAlphabetSections` 遍历
   已排序的 app 列表，`sectionName`（中文按拼音首字母）变化时先插入
   `OneUiSectionItem`，再把这一组交给 `super.addAppsWithSections()`。
   组内仍用 AOSP 实现，`mFastScrollerSections` 的位置才正确。
2. **视图类型**：`OneUiSectionItem.VIEW_TYPE` 包含
   `VIEW_TYPE_ALL_APPS_DIVIDER` 位，`AlphabeticalAppsList` 会在每组分界处重置
   `numAppsInSection` / `rowIndex`；`GridSpanSizer` 对未知类型返回整行宽度。
3. **渲染**：`OneUiAllAppsGridAdapter` 拦截该 viewType，inflate
   `oneui_all_apps_section_header.xml`；其他 viewType 原样交给
   `AllAppsGridAdapter`，所以图标、文件夹、工作资料、搜索结果不需要改。
4. **顶部留白**：停靠搜索栏时容器没有悬浮 header 占位，`setInsets` 用
   `max(allAppsPadding.top, insets.top) + 8dp` 给列表留出状态栏高度。
5. **列表裁剪**：AOSP `FloatingHeaderView#applyVerticalMove` 会把列表顶部裁掉
   `all_apps_header_top_padding`（36dp），用来给悬浮 header 让位。停靠模式下
   这个裁剪会吃掉第一个字母，因此改为 `clipTop = 0`。
6. **范围**：只在字母序（`drawerList = true`）生效；文件夹仍排在所有字母组之前；
   搜索结果和分类模式不插入标题。中文 app 的 `sectionName` 由
   `AlphabeticIndexCompat` 计算，实测「拨打电话 → B、车软星球市场 → C、
   待办清单 → D、日历 → R、设置/时钟 → S」。

> 真实的 One UI 7 字母序抽屉没有行内字母标题，只有右侧索引条和拖动时的字母气泡。
> 行内标题现在由「字母分组标题」开关控制（默认开），关掉后只保留右侧索引。

### 3.4 抽屉右侧索引条

AOSP 的 `RecyclerViewFastScroller` 画的是 6–8dp 主题色长条 + 泪滴气泡，One UI 则是
「细灰轨道 + 拖动加粗滑块 + 蓝色圆形字母气泡」。实现方式是**子类替换绘制**，滚动与
触摸逻辑完全不动：

1. **新类**：`lawnchair/src/app/lawnchair/views/OneUiFastScroller.kt` 继承
   `RecyclerViewFastScroller`，只覆盖 `onDraw` / `setPopupView` / `setThumbOffsetY` /
   `handleTouchEvent`。
2. **轨道**：3dp 宽、`TextColorPrimary` 10% 透明度，圆角胶囊，范围取
   `mRv.scrollBarTop` + `mRv.scrollbarTrackHeight`（注意 `getScrollBarTop()` 返回
   `all_apps_header_top_padding`，不是 `getPaddingTop()`）。
3. **滑块**：静止 3dp、拖动 6dp，`ValueAnimator` 150ms 过渡；高度沿用
   `fastscroll_thumb_height`（52dp）。
4. **气泡**：`setPopupView` 里把背景换成 `GradientDrawable(OVAL)` + `#3E8EFF`，
   52dp 圆、白色 22sp medium；拖动开始时从 0.7 缩放到 1.0（160ms）。
   `positionBubble()` 用滑块中心对齐气泡中心，并夹在轨道范围内，不再用 AOSP 的
   泪滴偏移公式。
5. **布局**：`res/layout/all_apps_fast_scroller.xml` 的标签换成
   `app.lawnchair.views.OneUiFastScroller`，气泡样式换成 `OneUiFastScrollerPopup`。
   小部件选择器（`widgets_full_sheet.xml` / `widgets_two_pane_sheet.xml`）仍用 AOSP 版本。
6. **手势排除**：`onDraw` 里把滑块 bounds 写进 `systemGestureExclusionRects`，
   贴右边缘拖动不会触发返回手势。

**验证拖动**：`input swipe` 走的是列表滚动分支，抓不到气泡。必须用
`input motionevent DOWN/MOVE/.../UP`，且在滑块上按下（列表在顶部时约
`(1060, 220)`），先来一次小于 touch slop 的小位移再大幅移动：

```powershell
adb shell input motionevent DOWN 1060 240
adb shell input motionevent MOVE 1060 258   # 先越过 touch slop
adb shell input motionevent MOVE 1060 900
adb shell screencap -p /sdcard/drag.png
adb shell input motionevent UP 1060 900
```

### 3.5 抽屉自定义排序

抽屉支持「字母顺序 / 自定义」两种模式，顺序在设置页拖拽编辑。

1. **模式与数据**：`PreferenceManager#drawerSortMode`（`pref_drawerSortMode`，
   默认 `alphabetical`）与 `drawerCustomOrder`（`pref_drawerCustomOrder`，
   换行分隔的 `ComponentKey.toString()`）。
2. **重排**：`LawnchairAlphabeticalAppsList#addAppsWithSections` 在自定义模式下调用
   `applyCustomOrder()`；未记录的应用用 `Int.MAX_VALUE` 排到末尾并保持字母序。
3. **渲染**：`addAppsInCustomOrder()` 不插字母标题；每行加一个空名
   `FastScrollSectionInfo`，索引条可以拖动，但 `RecyclerViewFastScroller` 检测到
   section 名为空会自动隐藏字母气泡。
4. **即时刷新**：`PreferenceManager#refreshAllApps` 直接调
   `appsView.personalAppList.updateAdapterItems()`，切模式不需要重建 Activity。
5. **编辑器**：`AppDrawerSortPreference.kt`，复用 `ReorderablePreferenceGroup` /
   `AppItem` / `ReorderableDragHandle`。`onOrderChange` 把整份列表写成 component key
   字符串，所以一次拖动就会补全所有应用。
6. **入口**：抽屉底部菜单「排序 → 编辑自定义顺序」，以及设置 → 应用抽屉 → 排序。

**手动验证**（模拟器）：

```powershell
# 打开抽屉 → 三点菜单 → 排序 → 自定义
adb shell input tap 985 2212
adb shell input tap 540 2020
adb shell input tap 540 2140
# 此时抽屉应立即变为无标题网格；菜单里出现「编辑自定义顺序」

# 编辑顺序后返回抽屉
adb shell input tap 540 2160      # 编辑自定义顺序
# 长按右侧手柄拖动，例如把第一项拖到第四项
adb shell input keyevent KEYCODE_BACK
```

实测：把 CarMore 从第 1 位拖到第 4 位后，抽屉首行变为
「OneUI 桌面 / Search / TMobile / CarMore」；切回字母顺序后分组标题恢复。
编辑页的「排序方式」是 `ListPreference` 底部弹窗，切到字母顺序后提示文案与
拖拽列表立即消失，返回抽屉分组恢复（截图 `drawer-sort-mode.png`）。

> 拖动编辑器里的行必须**长按手柄**（`ReorderableDragHandle` 走
> `longPressDraggableHandle`），直接滑动只会滚页面。

### 3.6 设置页结构

设置首页按 One UI 拆成四张卡片，行内只有标题、说明和右箭头：

| 卡片 | 行 |
| --- | --- |
| 布局 | 主屏幕 / 应用抽屉 / Dock 栏 / 文件夹 |
| 外观 | 图标与风格 / 资讯一览 / 搜索栏 / 通知 |
| 交互 | 手势 / 最近任务 |
| 关于 | 关于 |

- **通知**从「通用」拆成独立页 `NotificationsPreferences.kt`，路由 `Notifications`；
  内容仍是 Lawnchair 的通知圆点开关、颜色、数量、文字颜色与对比度警告。
- **图标与风格** = 原 `GeneralPreferences`（旋转 / 字体 / 图标 / 颜色），
  标题改用 `oneui_settings_appearance`。
- 首页不再显示 Lawnchair 公告；备份、恢复、实验特性入口从 UI 移除（路由保留）。
  只有打开「调试菜单」偏好时才在标题栏出现扳手图标。
- 通知使用权未授权时，通知页只显示「需要通知使用权来读取内容」的警告。
  模拟器可用以下命令授权后查看完整控件：

```powershell
adb shell cmd notification allow_listener com.oneui.desktop/com.android.launcher3.notification.NotificationListener
```

- **关于页**（`about/About.kt`）：顶部应用图标 + 名称 + 版本，下面「法律信息」
  卡片放致谢（`AboutLicenses`）与隐私政策，底部一行开源声明。更新检查、
  社区链接和贡献者列表已移除；中文标题用 `legal` → 「法律信息」。
- **二级页**：主屏幕页顺序改为布局（网格 / 锁定 / 页面指示点）→ 通用（新应用 /
  无限滚动）→ 订阅 → 样式 → 壁纸 → 弹出菜单 → 状态栏 → 图标 → 小组件 →
  清空主屏幕；双击手势只在「手势」页出现。应用抽屉页改为布局 → 网格 → 通用 →
  样式 → 图标 → 高级。设置首页的开发版警告条已移除，缺失的中文文案已补齐。

### 3.7 图标按压动画

改的是 `platform_frameworks_libs_systemui/iconloaderlib/src/com/android/launcher3/icons/FastBitmapDrawable.java`：

- `PRESSED_SCALE` 1.1 → **0.92**，`HOVERED_SCALE` 1.1 → 1.05；
- `CLICK_FEEDBACK_DURATION` 200 → **150ms**；
- 按下用 `DecelerateInterpolator`（立即响应），松开用 `AccelerateInterpolator`。

图标缩小、文字标签不动；工作区 / 抽屉 / Dock / 文件夹预览共用该 drawable。
注意这个文件在 `iconloaderlib` 子模块快照里，不在 `lawnchair/src`。

**验证方法**（模拟器，按下后立刻截图，避免触发长按菜单）：

```powershell
adb shell input motionevent DOWN 151 500
adb shell screencap -p /sdcard/press.png
adb shell input motionevent UP 151 500
```

实测截图里设置图标从 240px 缩到约 220px，标签位置不变（`anim-icon-press.png`）。

### 3.8 抽屉开合错落

`AllAppsTransitionController#setProgress` 每次进度更新时遍历抽屉可见行：

- 行延迟按 `childTop / recyclerView.height` 归一化，再乘 `ICON_STAGGER_MAX_DELAY = 0.3`；
- 每行额外下移 `ICON_STAGGER_TRANSLATION_DP = 12dp`，随进度回到 0；
- 只改 `translationY`，**不改 alpha**，避免滑动过程中出现空白面板；
- `mProgress` 1 是桌面、0 是抽屉，所以用 `1 - progress` 作为打开进度；
- 顶部行先进入视口、先归位，越靠下的行越晚。

只影响抽屉网格子项，工作区 / Dock / 搜索结果 / 索引条不受影响；抽屉打开后
滚动、搜索、返回桌面都不会残留位移。模拟器掉帧明显，静帧不易看出错落，
验收以真机或高帧率录屏为准。

### 3.9 长按菜单弹性展开

Material You 弹窗默认开启，动画参数集中在 `ArrowPopup`：

- 展开：`0.86 → 1.015`（240ms），再 `1.015 → 1.0`（120ms 回弹）；
- 淡入：面板 90ms、子项 110ms，都从 0 延迟开始；
- 收起：180ms，缩到 `0.9` 后移除；
- 只改参数，不改 `OptionsPopupView` 的结构；文件夹、快捷方式、小组件弹窗共用。

模拟器上 240ms 的展开很难用单帧截图判断，调参数后重点确认菜单能正常打开、
点击、关闭，以及连续快速开关不残留半透明视图。

### 3.10 抽屉拖拽编辑模式

对照真机截图（抽屉拖动页：缩放卡片 + 格点 + 描边 + 分页页点）实现，入口
`ItemLongClickListener.onAllAppsItemLongClick` → `newDragOptions`（搜索态关闭）
→ `DragOptions.isDrawerDrag = true`。核心类 `lawnchair/src/app/lawnchair/oneui/drawer/
OneUiDrawerDragOverlay.kt`，由 `LawnchairLauncher.setupOneUiDrawerDragOverlay()`
挂到 DragLayer 并注册 DragListener + DropTarget。

- **编辑态视觉**：拖起时背景压暗（90/255 黑）、应用网格缩放 0.84 成圆角卡片
  （`activeRecyclerView.animate()`，180ms）、搜索胶囊淡出（`search_container_all_apps`
  alpha 0）；网格交叉点画定位小点（1.4dp，去重），落点格 2dp 白描边 18dp 圆角，
  卡片下方画页点 + 「+」。
- **落点语义**：卡片内 = 最近格预览，松手 `LawnchairAlphabeticalAppsList
  .moveAppInCustomOrder` 移到目标应用旁（字母模式 Toast 引导切换）；卡片左右
  48dp = 按页滚动（420ms 步进）；卡片**外**（上/下空白区）= 「添加到主屏幕」，
  `Workspace.addAppFromDrawer` 放第一个空格（Toast 反馈 / 空间不足提示）。
- **框架协作**：`Workspace.onDragStart` 遇 `isDrawerDrag` 跳过常规起手（不切
  SPRING_LOADED），`onDragEnd` 提前返回；`LauncherDragView.onStateTransitionComplete`
  在 ALL_APPS 态保持拖拽浮层可见；drop 时 `DragView` 需显式 `as DragView<Launcher>`
  （K2 下 raw `DragView` 参数声明会报 "One type argument expected"）。
- 落位动画：重排走 `DragLayer.animateViewIntoPosition`（180ms），加到主屏走淡出，
  失败回飞 `animateDragViewToOriginalPosition`。

---

## 4. 已知问题 / 风险

| 问题 | 影响 | 处理建议 |
| --- | --- | --- |
| GitHub 网络不稳定 | 无法 push / 拉取上游 | 网络恢复后 `git push origin main`；不要尝试 unshallow 旧 fork |
| 模拟器偶尔退出 | 验证中断 | 用 `-no-window -no-snapshot -gpu off` 启动；截图用 `adb shell screencap` + `adb pull`（PowerShell 5.1 不要用 `>` 重定向二进制）；必要时重启 AVD |
| 时钟只有一种样式 | 4×1 AppWidget 已可长按移除，但没有 One UI 的 4 种时钟样式 / 配置页 | 后续加配置 Activity 与多套 RemoteViews；覆盖层与 AppWidget 保持同款默认样式 |
| 首次绑定需系统确认 | 侧载应用受 `BIND_APPWIDGET` 限制，首次启动会弹「是否允许创建微件」 | 勾选「始终允许」即可；取消后覆盖层继续显示，不会自动重试 |
| 抽屉三点菜单 | 已改为 One UI 底部菜单（排序 / 设置），排序支持字母 / 自定义两种模式 | 排序编辑器在设置页；抽屉内长按拖拽已实现（见 §3.10） |
| 自定义排序覆盖范围 | 只对主用户的应用生效；工作资料 / 私密空间仍走 AOSP 顺序 | 多资料场景后续再扩展，当前不影响主用户 |
| 使用频率排序 | 尚未实现，只有字母 / 自定义 | 需要接 `UsageStatsManager` 或 `PredictionProvider` 的排序信号 |
| 抽屉分组与 One UI 不一致 | 行内字母标题默认开启，与 One UI 7 原版不同 | 设置 → 应用抽屉 → 网格里有「字母分组标题」开关，关掉即只剩右侧索引 |
| 索引条触摸区域 | 视觉上是 3dp 细条，实际可拖区域仍是整条 `fastscroll_width`，与 One UI 手感接近 | 如需更宽/更窄改 `res/values/dimens.xml` 的 `fastscroll_width`，别改绘制宽度 |
| 键盘弹出时列表底部被键盘遮住 | 搜索结果区域变小 | 后续在 IME 显示时减少列表底部 padding，或滚动到结果 |
| 模拟器 3 按钮导航 | 视觉与手势导航不同 | 最终需在 gesture navigation 下再验一次 inset |
| PowerShell 5.1 中文乱码 | 终端显示问题，不是文件问题 | 文件均为 UTF-8；用 `read` 工具或 VS Code 查看 |

---

## 5. 环境与常用命令

```powershell
cd E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop

# 构建
$env:JAVA_HOME = 'D:\AndroidDevelopment\jdk'
$env:ANDROID_HOME = 'D:\AndroidDevelopment\Sdk'
.\gradlew.bat :assembleLawnWithQuickstepGithubDebug --console=plain

# 安装 / 设为默认桌面
adb install -r -g build\outputs\apk\lawnWithQuickstepGithub\debug\*.apk
adb shell cmd package set-home-activity com.oneui.desktop/app.lawnchair.LawnchairLauncher

# 改布局 / 网格后清数据
adb shell pm clear com.oneui.desktop
adb shell "run-as com.oneui.desktop ls databases"   # 应为 launcher_6_4_5.db

# 打开抽屉并截图（PowerShell 5.1 不要用 > 重定向 PNG）
adb shell input keyevent KEYCODE_HOME
adb shell input swipe 540 1800 540 400 250
Start-Sleep -Seconds 2
adb shell screencap -p /sdcard/drawer.png
adb pull /sdcard/drawer.png D:\AndroidDevelopment\shots\drawer.png
```

模拟器：

```powershell
# 无窗口、纯软件渲染，较慢但稳定（当前机器验证用）
D:\AndroidDevelopment\Sdk\emulator\emulator.exe -avd medium_phone `
  -no-window -no-snapshot -gpu off -no-audio -no-boot-anim

# 有窗口（本机偶发 gfxstream / OpenGL 报错）
D:\AndroidDevelopment\Sdk\emulator\emulator.exe -avd medium_phone -gpu auto
```

> 如果模拟器启动后 `adb devices` 为空，先等 1–2 分钟；`-gpu off` 首次启动会明显变慢。
> 不要反复 `adb kill-server`，会打断正在启动的模拟器。

---

## 6. 下一步（按优先级）

### 6.1 抽屉搜索栏与底部菜单（已完成，提交 `39e0fc5` / `cec482e`）

当前工作树应保持干净。后续如果继续改抽屉，提交前确认：
- `git status` 没有 `build/`、`.gradle/`、`local.properties` 等产物；
- `tools/oneui-overlay.txt` 已包含本轮所有文件；
- 截图已更新到 `docs/refactor/shots/`；
- `CHANGELOG.md` 与 `ARCHITECTURE.md` 的验证记录同步。

底部菜单（`OneUiDrawerMenu.kt`）后续扩展时注意：新增菜单行保持 56dp 高、
左 24dp 图标、右端「当前值 + 箭头」；子页统一用 `BackHandler` 回主菜单，
不要直接关闭整个弹层。

### 6.2 时钟 AppWidget

已完成。关键结论：

- `BIND_APPWIDGET` 是签名权限，侧载 APK 不能静默绑定；但**可以**走系统
  `ACTION_APPWIDGET_BIND` 对话框，用户勾选「始终允许」后由系统授予该 provider 的绑定权。
  因此不要往 `default_workspace_4x6.xml` 写 `<appwidget>`（默认布局解析失败会直接丢弃），
  而是在 `onResume` 里用 `Launcher.addPendingItem` 主动添加，让 Launcher3 现成的
  `REQUEST_BIND_APPWIDGET` 链路处理授权和重试。
- 自动落位只尝试一次（`auto_add_attempted`）；用户取消授权后保留覆盖层，不反复弹框。
  需要重新触发时清应用数据，或手动从「小部件」里添加。
- 4×1 小组件添加后会出现 `TYPE_WIDGET_RESIZE_FRAME`；控制器在 1.5s / 4s / 8s 后关闭，
  避免时钟旁边残留调整框。
- **覆盖层显隐不要用 pre-draw 轮询**：窗口静止时不会再有帧，删除小组件后覆盖层会一直
  隐藏。现在由 `OneUiClockWidgetController` 的监听器在 `onWidgetsBound` /
  `onWidgetsDeleted` 时直接通知 `OneUiClockView`。
- 删除路径：长按小组件进入拖动 → 拖到顶部「移除」→ 松手；Launcher3 先显示可撤销
  的「项目已移除」条，撤销窗口结束后才真正删除绑定并触发 `onDeleted`，此时覆盖层
  恢复。点「撤销」会恢复小组件（系统可能分配新的 widget id），覆盖层再次隐藏。

后续扩展：配置 Activity + 4 套 RemoteViews（大时间 / 时间 + 日期 / 数字 / 极简），
设置里选择样式后 `AppWidgetManager.updateAppWidget` 重建。

### 6.3 抽屉分组 + 排序

- **分组与索引（已完成）**：字母序下按首字母插入行内标题（§3.3），右侧细灰索引条
  + 蓝色圆形字母气泡（§3.4）；行内标题由「字母分组标题」开关控制，默认开。
  剩余可选项：One UI 8.5 的窄居中搜索胶囊，只改宽度/外边距/圆角，不动停靠逻辑。
- **自定义排序（已完成）**：字母 / 自定义两种模式，顺序存在
  `pref_drawerCustomOrder`，在设置页用 `ReorderablePreferenceGroup` 拖拽编辑
  （§3.5）。抽屉菜单可直接切模式并跳到编辑器；切换后列表即时刷新。
  剩余：使用频率排序、工作资料 / 私密空间的自定义顺序；抽屉内长按拖拽已实现（§3.10），待真机实测。
- 底部菜单主入口已经完成，后续只补「搜索设置」等 One UI 8.5 菜单项。

### 6.4 设置页 One UI 化

- ~~`lawnchair/src/app/lawnchair/ui/preferences/` 用分组卡片重排~~ ✅ 设置首页已拆成
  布局 / 外观 / 交互 / 关于四张卡片（§3.6、ARCHITECTURE §5.10）。
- ~~隐藏与桌面无关的 Lawnchair 项（更新、备份、实验特性等）~~ ✅ 首页公告、备份 / 恢复、
  实验特性入口已从 UI 移除；调试菜单仅在偏好打开时显示。
- ~~一级页只保留：主屏、应用抽屉、图标、手势、通知、关于~~ ✅ 已覆盖；
  「通知」已从通用页拆成独立页面，「关于」已改成 One UI 卡片
  （`settings-about.png`）。
- ~~主屏幕 / 应用抽屉二级页排序与中文文案~~ ✅ 布局与网格提前、清空主屏幕置底、
  去掉重复双击项、补译缺失文案、移除开发版警告条（§3.6）。
- 剩余：手势页文案与卡片顺序；应用抽屉的「糖果」实验布局入口是否隐藏。

### 6.5 动画复刻

- ~~图标按压：`0.92` 缩放 + `FastOutSlowIn`~~ ✅ 已改为 0.92 + 150ms，按下减速、
  松开加速（§3.7）。
- ~~抽屉开合：图标错落~~ ✅ 行级 12dp 错落归位（§3.8）；背景模糊未做。
- ~~长按菜单：从触点弹性展开~~ ✅ `0.86` 起缩放 + 120ms 回弹（§3.9）。
- 页面切换：One UI 的轻微视差与淡入。

---

## 7. 验收清单（每次提交前）

- [ ] `:assembleLawnWithQuickstepGithubDebug` 成功，无新增 `error:`
- [ ] 首屏 4×6、Dock 5、时钟时间/日期正常
- [ ] 首次启动弹系统绑定框；允许后时钟为 4×1 小组件，可长按拖动、拖到「移除」删除
- [ ] 删除小组件后覆盖层恢复（撤销条消失后），撤销操作可让小组件恢复并再次隐藏；无重复时钟、无 resize frame
- [ ] 首页/第二页来回滑动时时钟跟随第一页移出/恢复，其他页面没有残影
- [ ] 抽屉底部搜索胶囊：位置、圆角、提示文字、三点菜单
- [ ] 三点菜单打开底部菜单；排序子页可进入/返回；设置行跳转设置页
- [ ] 键盘弹出时点菜单先收键盘；点遮罩关闭后抽屉仍打开
- [ ] 键盘弹出后搜索栏在键盘上方，三点菜单可点
- [ ] 抽屉字母分组：首字母完整可见、顺序正确；文件夹 / 搜索结果 / 长按菜单正常
- [ ] 抽屉索引条：静止为 3dp 细灰轨道；拖动时滑块加粗、蓝色圆形气泡字母正确、
      列表跳到对应 section；松手后滑块恢复细条
- [ ] 「字母分组标题」开关：关闭后无行内标题，仅保留右侧索引，重新打开后恢复
- [ ] 自定义排序：抽屉菜单切「自定义」立即生效（无标题 / 无字母气泡）；切回字母序分组恢复
- [ ] 排序编辑器：设置页拖动应用后返回抽屉顺序一致；杀进程 / 重启后仍保持
- [ ] 新装应用排在自定义顺序末尾，且保持字母序
- [ ] 设置首页：布局 / 外观 / 交互 / 关于四张卡片，无公告、无备份 / 实验入口
- [ ] 设置首页无开发版警告条；主屏幕 / 应用抽屉二级页顺序与中文文案符合 §3.6
- [ ] 设置 → 通知：授权通知使用权后显示开关、颜色、数量；未授权时只有警告
- [ ] 设置 → 图标与风格：标题正确，字体 / 图标 / 颜色正常，无通知圆点
- [ ] 设置 → 关于：图标 + 名称 + 版本 +「法律信息」卡片 + 开源声明，无更新 / 社区入口
- [ ] 图标按压：按住图标缩小、松开回弹，文字标签不动
- [ ] 抽屉开合：打开时网格行级错落归位；打开后滚动、索引、搜索、返回桌面无残留位移
- [ ] 长按菜单：弹性展开/收起正常，点遮罩关闭后桌面可继续操作
- [ ] 搜索框隐藏开关：打开后列表不留底部空白
- [ ] 长按桌面、拖拽图标、最近任务不受影响
- [ ] `docs/refactor/shots/` 截图更新
- [ ] `CHANGELOG.md` 与 `ARCHITECTURE.md` 同步

---

## 8. 与上游同步 / 备份

- `origin`：`https://github.com/a435630144/OneUILauncher.git`
- `upstream`：`https://github.com/LawnchairLauncher/lawnchair.git`（只作参照）
- One UI 改动清单：`tools/oneui-overlay.txt`
- 重放覆盖层：`.\tools\export-overlay.ps1 -Target <clean-lawnchair-checkout>`
- 新仓库完整备份：`D:\AndroidDevelopment\backups\oneui-main.bundle`（282 MB）
- 旧 fork 工作目录：`D:\AndroidDevelopment\lawnchair`（仅备份，不再使用）

> 本仓库是 Lawnchair 15-dev 的**自包含快照**，不是完整上游历史。不要再尝试
> `git fetch --unshallow` 旧 fork，会因缺少父对象 `00a58d0...` 失败。

---

## 9. 许可

- 项目整体：**GPL-3.0**（`LICENSE`）
- Launcher3 / Lawnchair 来源文件：**Apache-2.0**（`LICENSE.txt`、`NOTICE`），
  版权头保持原样；新增 One UI 文件在头部注明 `Modifications copyright 2026, OneUI Desktop`。

---

## 10. 最近提交

| 提交 | 内容 |
| --- | --- |
| `b8547d8` | 时钟：升级为 4×1 AppWidget |
| `318cf35` | 动画：长按菜单改为 One UI 弹性展开 |
| `9de5b20` | 文档：记录设置二级页整理 |
| `0cfcc6d` | 设置：二级页顺序与中文文案整理 |
| `ac15381` | 文档：记录关于页与抽屉错落 |
| `4980955` | 动画：抽屉打开时网格行级错落 |
| `9eae104` | 设置：关于页改为 One UI 结构 |
| `080207a` | 文档：交接文档记录动画提交 |
| `ef92885` | 动画：图标按压改为 One UI 缩放 |
| `6d072ff` | 文档：交接文档记录设置页提交 |
| `7320a7f` | 设置：One UI 分组卡片与独立通知页 |
| `b66d459` | 文档：交接文档记录自定义排序提交 |
| `1e45e44` | 抽屉：自定义排序与拖拽编辑器 |
| `f041c8a` | 文档：交接文档记录索引条提交 |
| `88d18ac` | 抽屉：One UI 右侧字母索引条 |
| `b6725f8` | 抽屉：字母分组标题 |
| `cec482e` | 抽屉：三点菜单改为 One UI 底部菜单 |
| `586b3f6` | 文档：交接文档记录时钟提交 |
| `a0bdc33` | 时钟：跟随 Workspace 首页滚动 |
| `0e90314` | 文档：交接文档补充最近提交列表 |
| `c57d1ba` | 文档：修正交接文档的 HEAD 与截图命令 |
| `09c197b` | 文档：校正模拟器版本与 APK 体积 |
| `2faf724` | 文档：记录 AppWidget 绑定限制并校正覆盖层清单 |
| `e227978` | 文档：补充抽屉搜索栏验证记录与交接说明 |
| `39e0fc5` | 抽屉：One UI 底部搜索栏、三点菜单与键盘避让 |
