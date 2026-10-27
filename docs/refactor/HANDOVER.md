# 交接文档（OneUI 桌面 / Lawnchair 15 基线）

> 更新时间：2026-10-28
> 工作目录：`E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`
> 分支：`main`（HEAD 以 `git log` 为准）
> 架构与设计取舍见 [ARCHITECTURE.md](./ARCHITECTURE.md)，SESL 取舍见 [SESL-DECISION.md](./SESL-DECISION.md)。

---

## 1. 一句话状态

底层 Launcher3 / Lawnchair 15 已经能构建、能装、能当默认桌面；首屏 4×6 + 5 Dock
+ **随页面滑动的时钟覆盖层**完成；应用抽屉的**底部搜索胶囊**（One UI 8.0 样式）、
**三点菜单打开的 One UI 底部菜单**（排序 / 设置）和**字母分组标题**已完成，并在模拟器
验证布局、键盘避让、排序子页进出、遮罩关闭、输入态背景保持和分组标题不被裁剪。
下一步是右侧字母索引、自定义排序、设置页卡片化和动画复刻；时钟 AppWidget 仍卡在
`BIND_APPWIDGET` 授权流程。

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
| 仓库 | Lawnchair 15-dev 快照并入根目录，旧 Compose v0.6 移入 `legacy/` | `README.md`、`NOTICE`、`tools/oneui-overlay.txt` |
| 构建 | 单模块 Gradle，`platform_frameworks_libs_systemui` 随仓库分发，clone 后可离线构建 | `build.gradle`、`gradle.properties` |
| 应用身份 | `com.oneui.desktop`、显示名「OneUI 桌面」、无 `.debug` 后缀 | `build.gradle` |
| 网格 | 4 列 × 6 行、Dock 5 位，DB 名 `launcher_6_4_5.db` | `device_profiles.xml`、`PreferenceManager.kt` |
| 首屏 | 第 0 行留空给时钟；第 1 行 4 图标；Dock 5 图标 | `default_workspace_4x6.xml` |
| 时钟 | 44sp 时间 + 14sp 中文日期，随 Workspace 页面滚动（非 AppWidget） | `lawnchair/res/layout/launcher.xml`、`OneUiClockView.kt` |
| 视觉 | squircle 图标、单行抽屉标签、12/16/20/26dp 圆角、One UI 字号 | `config.xml`、`Shape.kt`、`Type.kt` |
| 抽屉 | 底部停靠搜索胶囊、24dp 圆角、右端三点菜单、键盘弹出时上移 | `search_container_all_apps.xml`、`AllAppsSearchInput.kt`、`ActivityAllAppsContainerView.java` |
| 抽屉菜单 | 三点菜单打开 One UI 底部菜单：拖动条、排序 / 设置行、排序子页（字母序选中、自定义「即将支持」）、返回与遮罩关闭 | `ui/popup/OneUiDrawerMenu.kt`、`ComposeBottomSheet.kt` |
| 抽屉分组 | 字母序按首字母插入分组标题（B/C/D/…）；整行渲染、分组处重置行计数，文件夹在最前，搜索 / 分类模式不受影响 | `OneUiAllAppsGridAdapter.kt`、`LawnchairAlphabeticalAppsList.kt`、`oneui_all_apps_section_header.xml`、`FloatingHeaderView.java` |
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

### 3.1 时钟随页面滚动

时钟在 `launcher.xml` 里仍然是 `DragLayer` 的子 View，但 `OneUiClockView` 会跟随
第一页：

1. 在 `onAttachedToWindow` 里找到 `R.id.workspace`，用
   `ViewTreeObserver.OnPreDrawListener` 每帧读取 `workspace.scrollX`；
2. `translationX = -scrollX`，滑到第二页时时钟被推出屏幕，滑回第一页时再推回来；
3. 只在 `scrollX` 变化时更新，避免每帧重复写属性；
4. 启动时如果恢复到非 0 页，pre-draw 会在首帧前修正；
5. 用 pre-draw 而不是 `setOnScrollChangeListener`：实测后者在页面切换时不可靠。

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

---

## 4. 已知问题 / 风险

| 问题 | 影响 | 处理建议 |
| --- | --- | --- |
| GitHub 网络不稳定 | 无法 push / 拉取上游 | 网络恢复后 `git push origin main`；不要尝试 unshallow 旧 fork |
| 模拟器偶尔退出 | 验证中断 | 用 `-no-window -no-snapshot -gpu off` 启动；截图用 `adb shell screencap` + `adb pull`（PowerShell 5.1 不要用 `>` 重定向二进制）；必要时重启 AVD |
| 时钟不能当作页面子项 | 已随第一页滚动，但长按不能选中/移除，样式固定 | 迁移到 AppWidget（见 §6.2）；已实现方案 B 的滚动同步 |
| 抽屉三点菜单 | ~~只是入口~~ 已改为 One UI 底部菜单（排序 / 设置） | 排序子页已就位；「自定义」待实现排序模式与拖拽持久化（见 §6.3） |
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

> **先决条件**：`BIND_APPWIDGET` 是签名权限，侧载 APK 默认拿不到（`dumpsys package`
> 里只在 requested permissions，不在 install permissions）。`DefaultLayoutParser`
> 的 `<appwidget>` 调 `bindAppWidgetIdIfAllowed` 失败后会直接丢弃该 widget。
> 因此做真 AppWidget 前必须先决定绑定路径：
> - 方案 A：首次启动时用 `AppWidgetHost.startAppWidgetBinding()` 弹系统确认框，
>   用户同意后再写入数据库；
> - 方案 B：把时钟做成页面内自绘控件（随 Workspace 滚动、设置里可隐藏），
>   等有系统签名或绑定流程后再升级为 AppWidget。
> 当前代码是 `launcher.xml` 的 `OneUiClockView`，已完成方案 B 的页面滚动同步；
> 剩余缺口是「可长按移除 / 可换样式」。

目标：把 `launcher.xml` 里的 `@id/oneui_clock` 覆盖层替换为真正的 AppWidget，
让它可长按移除、可在「小部件」里换样式（随页面滚动已由 `OneUiClockView` 实现）。

建议步骤：
1. 新建 `lawnchair/src/app/lawnchair/widget/OneUiClockWidgetProvider.kt`（继承
   `AppWidgetProvider`），提供 4 种布局（大时间 / 时间 + 日期 / 数字 / 极简）。
2. 在 `lawnchair/res/xml/` 添加 `oneui_clock_widget_info.xml`（`appwidget-provider`），
   设置 `minWidth/minHeight`、`resizeMode`、`updatePeriodMillis=0`。
3. 在 `lawnchair/AndroidManifest.xml` 注册 receiver 与 `<meta-data>`。
4. `default_workspace_4x6.xml` 第 0 行改成 `<appwidget>`，指向该 provider。
5. 删除 `launcher.xml` 的 `oneui_clock` 覆盖层，确认长按/拖拽不再被挡。

### 6.3 抽屉分组 + 排序

- **分组与索引（已完成）**：字母序下按首字母插入行内标题（§3.3），右侧细灰索引条
  + 蓝色圆形字母气泡（§3.4）；行内标题由「字母分组标题」开关控制，默认开。
  剩余可选项：One UI 8.5 的窄居中搜索胶囊，只改宽度/外边距/圆角，不动停靠逻辑。
- **自定义排序**：参考 `legacy/app/src/main/java/com/example/oneuidesktop/` 的
  `HomeItem` JSON；在 `lawnchair/src/app/lawnchair/allapps/` 新增 `AppSortMode`
  （字母 / 手动 / 频率），持久化到 SharedPreferences，拖拽调序复用
  `AllAppsGridAdapter` 的 drag 回调。排序子页已在 `OneUiDrawerMenu.kt` 就位：
  实现 `AppSortMode` 后把「字母顺序」的单选状态和「自定义」的置灰逻辑接到真实
  偏好，去掉「即将支持」副标题。
- 底部菜单主入口已经完成，后续只补「搜索设置」等 One UI 8.5 菜单项。

### 6.4 设置页 One UI 化

- `lawnchair/src/app/lawnchair/ui/preferences/` 用分组卡片重排；
- 隐藏与桌面无关的 Lawnchair 项（更新、备份、实验特性等）；
- 一级页只保留：主屏、应用抽屉、图标、手势、通知、关于。

### 6.5 动画复刻

- 抽屉开合：背景模糊 + 图标错落（`AllAppsTransitionController` / `AllAppsBackgroundDrawable`）；
- 图标按压：`0.92` 缩放 + `FastOutSlowIn`；
- 页面切换：One UI 的轻微视差与淡入；
- 长按菜单：从触点弹性展开（`ArrowPopup` / `OptionsPopupView`）。

---

## 7. 验收清单（每次提交前）

- [ ] `:assembleLawnWithQuickstepGithubDebug` 成功，无新增 `error:`
- [ ] 首屏 4×6、Dock 5、时钟时间/日期正常
- [ ] 首页/第二页来回滑动时时钟跟随第一页移出/恢复，其他页面没有残影
- [ ] 抽屉底部搜索胶囊：位置、圆角、提示文字、三点菜单
- [ ] 三点菜单打开底部菜单；排序子页可进入/返回；设置行跳转设置页
- [ ] 键盘弹出时点菜单先收键盘；点遮罩关闭后抽屉仍打开
- [ ] 键盘弹出后搜索栏在键盘上方，三点菜单可点
- [ ] 抽屉字母分组：首字母完整可见、顺序正确；文件夹 / 搜索结果 / 长按菜单正常
- [ ] 抽屉索引条：静止为 3dp 细灰轨道；拖动时滑块加粗、蓝色圆形气泡字母正确、
      列表跳到对应 section；松手后滑块恢复细条
- [ ] 「字母分组标题」开关：关闭后无行内标题，仅保留右侧索引，重新打开后恢复
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
