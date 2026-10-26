# 交接文档（OneUI 桌面 / Lawnchair 15 基线）

> 更新时间：2026-10-27
> 工作目录：`E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`
> 分支：`main`，当前 HEAD `c57d1ba`（以 `git log` 为准）
> 架构与设计取舍见 [ARCHITECTURE.md](./ARCHITECTURE.md)，SESL 取舍见 [SESL-DECISION.md](./SESL-DECISION.md)。

---

## 1. 一句话状态

底层 Launcher3 / Lawnchair 15 已经能构建、能装、能当默认桌面；首屏 4×6 + 5 Dock
+ 时钟覆盖层完成；应用抽屉的**底部搜索胶囊**（One UI 8.0 样式）已完成并在模拟器
验证布局、三点菜单、键盘避让和输入态背景保持。下一步是时钟 AppWidget、抽屉分组、
自定义排序、设置页卡片化和动画复刻。

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
| 时钟 | 44sp 时间 + 14sp 中文日期覆盖层（非 AppWidget） | `lawnchair/res/layout/launcher.xml` |
| 视觉 | squircle 图标、单行抽屉标签、12/16/20/26dp 圆角、One UI 字号 | `config.xml`、`Shape.kt`、`Type.kt` |
| 抽屉 | 底部停靠搜索胶囊、24dp 圆角、右端三点菜单、键盘弹出时上移 | `search_container_all_apps.xml`、`AllAppsSearchInput.kt`、`ActivityAllAppsContainerView.java` |
| 文档 | 架构、验证记录、上游同步、交接说明 | `docs/refactor/` |

---

## 3. 抽屉搜索栏的实现要点

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
6. **三点菜单**：目前跳到 `PreferenceActivity` 的 `AppDrawer` 路由；
   后续要替换成 One UI 风格底部菜单（排序 / 设置）。
7. **输入态**：`ActivityAllAppsContainerView` 的 `bgVisible` 判断在停靠模式下
   强制为 `true`，否则 `isSearching()` 会把胶囊背景设为透明。

> 注意：`hideAppDrawerSearchBar` 打开时，列表不再预留底部空白，搜索栏本身由
> `AllAppsSearchInput` 的 `isInvisible` / 高度 0 逻辑隐藏。

---

## 4. 已知问题 / 风险

| 问题 | 影响 | 处理建议 |
| --- | --- | --- |
| GitHub 网络不稳定 | 无法 push / 拉取上游 | 网络恢复后 `git push origin main`；不要尝试 unshallow 旧 fork |
| 模拟器偶尔退出 | 验证中断 | 用 `-no-window -no-snapshot -gpu off` 启动；截图用 `adb shell screencap` + `adb pull`（PowerShell 5.1 不要用 `>` 重定向二进制）；必要时重启 AVD |
| 时钟仍是覆盖层 | 不随页面滚动、不能移除、不能换样式 | 迁移到 AppWidget（见 §6） |
| 抽屉三点菜单只是入口 | 不是 One UI 的底部菜单 | 先复用 `Preferences` 路由，后续做 `OneUiDrawerMenuBottomSheet` |
| 抽屉没有分组 / 排序 | 与 One UI 仍有差距 | 见 §6 的排序与分组里程碑 |
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

### 6.1 抽屉搜索栏（已完成，提交 `39e0fc5`）

当前工作树应保持干净。后续如果继续改抽屉，提交前确认：
- `git status` 没有 `build/`、`.gradle/`、`local.properties` 等产物；
- `tools/oneui-overlay.txt` 已包含本轮所有文件；
- 截图已更新到 `docs/refactor/shots/`；
- `CHANGELOG.md` 与 `ARCHITECTURE.md` 的验证记录同步。

### 6.2 时钟 AppWidget

> **先决条件**：`BIND_APPWIDGET` 是签名权限，侧载 APK 默认拿不到（`dumpsys package`
> 里只在 requested permissions，不在 install permissions）。`DefaultLayoutParser`
> 的 `<appwidget>` 调 `bindAppWidgetIdIfAllowed` 失败后会直接丢弃该 widget。
> 因此做真 AppWidget 前必须先决定绑定路径：
> - 方案 A：首次启动时用 `AppWidgetHost.startAppWidgetBinding()` 弹系统确认框，
>   用户同意后再写入数据库；
> - 方案 B：把时钟做成页面内自绘控件（随 Workspace 滚动、设置里可隐藏），
>   等有系统签名或绑定流程后再升级为 AppWidget。
> 当前代码仍是 `launcher.xml` 的覆盖层，属于方案 B 的过渡形态。

目标：把 `launcher.xml` 里的 `@id/oneui_clock` 覆盖层替换为真正的 AppWidget，
让它随 Workspace 页面滚动、可长按移除、可在「小部件」里换样式。

建议步骤：
1. 新建 `lawnchair/src/app/lawnchair/widget/OneUiClockWidgetProvider.kt`（继承
   `AppWidgetProvider`），提供 4 种布局（大时间 / 时间 + 日期 / 数字 / 极简）。
2. 在 `lawnchair/res/xml/` 添加 `oneui_clock_widget_info.xml`（`appwidget-provider`），
   设置 `minWidth/minHeight`、`resizeMode`、`updatePeriodMillis=0`。
3. 在 `lawnchair/AndroidManifest.xml` 注册 receiver 与 `<meta-data>`。
4. `default_workspace_4x6.xml` 第 0 行改成 `<appwidget>`，指向该 provider。
5. 删除 `launcher.xml` 的 `oneui_clock` 覆盖层，确认长按/拖拽不再被挡。

### 6.3 抽屉分组 + 排序

- 分组：`LawnchairAlphabeticalAppsList` 已有字母 section，可加 sticky header；
  One UI 的「按字母分组」开关接到 `PreferenceManager2`。
- 排序：参考 `legacy/app/src/main/java/com/example/oneuidesktop/` 的 `HomeItem` JSON；
  在 `lawnchair/src/app/lawnchair/allapps/` 新增 `AppSortMode`（字母 / 手动 / 频率），
  持久化到 SharedPreferences，拖拽调序复用 `AllAppsGridAdapter` 的 drag 回调。
- 三点菜单改为 One UI 底部菜单：排序、抽屉设置、搜索设置。

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
- [ ] 抽屉底部搜索胶囊：位置、圆角、提示文字、三点菜单
- [ ] 键盘弹出后搜索栏在键盘上方，三点菜单可点
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
| `c57d1ba` | 文档：修正交接文档的 HEAD 与截图命令 |
| `09c197b` | 文档：校正模拟器版本与 APK 体积 |
| `2faf724` | 文档：记录 AppWidget 绑定限制并校正覆盖层清单 |
| `e227978` | 文档：补充抽屉搜索栏验证记录与交接说明 |
| `39e0fc5` | 抽屉：One UI 底部搜索栏、三点菜单与键盘避让 |
| `c58f304` | 构建：支持中文路径，补充上游同步脚本 |
