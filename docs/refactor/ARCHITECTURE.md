# 重构架构：Launcher3 + One UI 设计层

> 基线：`LawnchairLauncher/lawnchair` 分支 `15-dev`，提交 `505dbc4`，Apache-2.0。
> 工作分支：`main`，仓库根目录 `E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`。
> 本文档记录第一阶段的骨架状态；设计取舍见 [SESL-DECISION.md](./SESL-DECISION.md)。

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
| **首屏 One UI 时钟 / 日期** | `lawnchair/res/layout/launcher.xml`（`oneui_clock`） |
| **首屏默认排布（4 图标 + 5 Dock）** | `lawnchair/res/xml/default_workspace_4x6.xml` |

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

### 5.3 时钟覆盖层

`lawnchair/res/layout/launcher.xml` 在 `DragLayer` 内、`Workspace` 之后放了一个
`LinearLayout`（`@id/oneui_clock`）：

- `TextClock` 44sp `sans-serif-light`，`HH:mm`，负字距，白字 + 柔和阴影。
- 日期 `TextClock` 14sp，格式 `M月d日 EEEE`，直接输出中文星期。
- `clickable=false` / `focusable=false` / `importantForAccessibility=no`，不抢长按和拖拽。
- 因为挂在 `DragLayer` 而不是 `Workspace` 里，它**不随页面滚动**——这是第一阶段的
  简化，后续应改为真正的 AppWidget（见 §7）。

### 5.4 关掉的两处 Lawnchair 默认 UI

- **smartspace 独立首屏页**：`FeatureFlags.topQsbOnFirstScreenEnabled()` 读取
  `config_default_enable_smartspace`，置 `false` 后不再插入那个「点击以设置」的空页。
- **Dock 上方搜索条**：`config_default_hotseat_mode = disabled` 会去掉
  `search_container_hotseat` 里的 QSB，但保留 Dock 图标本身。

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

## 7. 验证记录（模拟器 `medium_phone`，Android 16）

| 项目 | 结果 |
| --- | --- |
| 构建 | `:assembleLawnWithQuickstepGithubDebug` 成功，APK ≈ 48 MB |
| 网格 | 4 列 × 6 行，Dock 5 位，DB 为 `launcher_6_4_5.db` |
| 首屏 | 时钟 `15:24` + 日期 `10月25日 星期日`；第 1 行 4 图标；Dock 5 图标 |
| 旧 UI 残留 | 无 AOSP 搜索条、无 smartspace「点击以设置」页 |
| 抽屉 | 4 列网格、单行标签、squircle 图标、顶部搜索胶囊 |
| 长按桌面 | 「背景 / 小部件 / 启动器设置」菜单正常，时钟层未阻塞手势 |
| 最近任务 | Quickstep 正常，时钟不会出现在任务快照里 |

截图：`docs/refactor/shots/home.png`（首屏）、`drawer.png`（抽屉）、
`long-press.png`（长按菜单）、`recents.png`（最近任务）。

## 8. 下一步里程碑

1. ~~**首屏形态**：4×6 网格 + 5 Dock + 时钟/日期 + 默认排布~~ ✅ 已完成
2. **时钟组件化**：把覆盖层改成真正的 AppWidget（`OneUiClockWidgetProvider` +
   `<appwidget>` 默认布局），让它随页面滚动、可移除、可换样式（One UI 有 4 种时钟样式）。
3. **抽屉形态**：搜索框移到底部 + 圆角胶囊、按首字母分组吸顶、隐藏标签开关。
   当前入口：`lawnchair/src/app/lawnchair/allapps/views/SearchContainerView.kt`、
   `lawnchair/res/layout/search_container_all_apps.xml`。
4. **自定义排序**：Lawnchair 15 只有字母序（`LawnchairAlphabeticalAppsList`），
   需要新增排序模式（字母 / 手动 / 使用频率）和拖拽调序，
   数据结构对齐现有 Compose 版 `HomeItem` 的 JSON 持久化。
5. **设置页收口**：只保留桌面相关项，其余隐藏；二级页改用 One UI 分组卡片。
6. **裁剪 Quickstep**：15-dev 已无 `withoutQuickstep` flavor，需要自己接一个
   不带 recents 的构建变体，把 332 MB 的基线压下来。
