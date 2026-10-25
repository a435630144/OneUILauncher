# 交接文档（HANDOVER）

> 最后更新：2026-10-25（v0.6，提交 3cd6f19）
> 面向：接手本项目的开发者或 AI 会话。配合 [CHANGELOG.md](CHANGELOG.md)（时间线与踩坑记录）阅读。

## 1. 项目概述

**OneUI 桌面（OneUi_Desktop）**：复刻三星 One UI 风格的第三方安卓桌面启动器（Launcher），Kotlin + Jetpack Compose 全自研。核心卖点：应用抽屉的自定义排序（横向分页 + 长按拖动 + 点选移动 + 跨页建页）、One UI 视觉（方形图标主色底板、大时钟、Dock）、原生小组件承载、通知圆点角标。

- 仓库：https://github.com/a435630144/OneUILauncher（main 分支，GPL-3.0）
- 本地路径：`E:\OneDrive\家里台式\桌面\temp\OneUi_Desktop`（**含中文路径**，已用 `android.overridePathCheck=true` 放行）
- 包名：`com.example.oneuidesktop`；版本号在 README 与设置页文案中（改版本时两处同步）
- Git 身份：a435630144 / 435630144@qq.com，Credential Manager 已配好 HTTPS 推送（push 偶遇网络重置，重试即可）

## 2. 环境速查

| 组件 | 位置 / 命令 |
|---|---|
| JDK 17（Temurin） | `D:\AndroidDevelopment\jdk`（已写入用户级 JAVA_HOME） |
| Android SDK | `D:\AndroidDevelopment\Sdk`（ANDROID_HOME，platforms 35/36.1，build-tools 35.0.0/36.1.0） |
| Gradle 用户缓存 | `D:\AndroidDevelopment\gradle-home`（GRADLE_USER_HOME；wrapper 分发走腾讯镜像） |
| Gradle 8.9 | `D:\AndroidDevelopment\gradle-8.9`；项目用 `gradlew`（8.9） |
| 模拟器 | `D:\AndroidDevelopment\Sdk\emulator\emulator.exe -avd medium_phone`（AVD 在 `D:\AndroidDevelopment\avd`，WHPX 已启用） |
| adb | `D:\AndroidDevelopment\Sdk\platform-tools\adb.exe` |
| 环境变量脚本 | `D:\AndroidDevelopment\env.sh`（Git Bash）/ `env.bat`（CMD） |
| Lawnchair 骨架（备选） | `D:\AndroidDevelopment\lawnchair`（15-dev，可构建，见 CHANGELOG 附录） |

**构建/安装/设为默认桌面：**

```bash
cd "E:/OneDrive/家里台式/桌面/temp/OneUi_Desktop"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.example.oneuidesktop/com.example.oneuidesktop.MainActivity
# 还原系统桌面：
adb shell cmd package set-home-activity com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher
```

**模拟器重启后必跑的三条授权**（系统级授权不跨重启）：

```bash
adb shell appwidget grantbind --package com.example.oneuidesktop          # 小组件绑定
adb shell cmd notification allow_listener com.example.oneuidesktop/.BadgeListener  # 通知角标
adb shell cmd package set-home-activity com.example.oneuidesktop/com.example.oneuidesktop.MainActivity
```

## 3. 代码导览（app/src/main/java/com/example/oneuidesktop/）

| 文件 | 职责 | 关键点 |
|---|---|---|
| `MainActivity.kt` | 入口，HOME intent | enableEdgeToEdge |
| `LauncherApp.kt` | 根组合：状态中枢（allApps/order/homePages/dockKeys）、壁纸、抽屉开关动画、`AppIconCell`/`DockBar`/`BadgeDot`/时钟等共享组件 | `addToHome()` 找空槽；widgetHost 生命周期 DisposableEffect |
| `HomeScreen.kt` | 主屏：HorizontalPager 多页网格、拖拽（排序/建夹/交换/Dock/上缘移除）、空白长按菜单、文件夹弹窗、`PageGrid`（含小组件绝对定位层） | 可空槽位语义；`trim()` 清尾 |
| `AppDrawer.kt` | 抽屉：分页+搜索+长按拖动排序（拖放式）+点选移动+编辑模式+上缘拖出 | `dropAt` 用 `targetPage`；`dragMoved<12dp` 判定菜单 vs 拖拽 |
| `HomeItem.kt` | 数据模型：`HomeApp/HomeFolder/HomeWidget` sealed + `HomeLayout`（pages: List<List<HomeItem?>>）JSON 序列化 | 槽位 null=空格；`GridSpec` 横竖屏 6×4/4×5 |
| `AppInfo.kt` | 应用扫描（PackageManager）+ **One UI 图标处理**（方形蒙版/主色底板提取） | Collator 中文排序 |
| `LauncherStore.kt` | 持久化：抽屉顺序、主屏布局 JSON、显示名称、角标数字模式 | SharedPreferences |
| `LauncherWidgets.kt` | AppWidgetHost 封装（HOST_ID=4242）+ 小组件目录（label/预览/span 估算） | |
| `BadgeListener.kt` | 通知角标：`BadgeListener` 服务 + `BadgeStore`（revision 驱动重组）+ `showNumbers` 模式 | 常驻通知排除 |
| `SettingsActivity.kt` | 桌面设置页 | 授权入口、调试角标按钮 |
| `Theme.kt` | 动态取色 + One UI 大圆角 Shapes | |

**持久化键**（prefs `oneui_launcher`）：`order`（抽屉顺序 JSON）、`home_layout`（pages+dock JSON）、`custom_order`、`show_labels`、`badge_numbers`。调试时用 `adb shell run-as com.example.oneuidesktop cat shared_prefs/oneui_launcher.xml`。

## 4. 交互模型速记

- **抽屉**：上滑/Dock 按钮打开；长按图标不动=菜单（添加到主屏/信息/卸载），长按拖动=排序（松手落位，自动持久化为自定义顺序）；拖到左右边缘 48dp 自动翻页；最后一页右边缘=移到末尾建新页；拖到**网格上缘=移到主屏**；抽屉内下滑关闭；⋮ → 整理应用（编辑模式：点选移动 + 完成保存）
- **主屏**：长按图标不动=菜单（移除/编辑文件夹/解散/信息/卸载），长按拖动=移动（拖到图标上建夹/并入文件夹/文件夹与应用交换）；拖到 Dock=换图标；拖到**网格上缘=移除回抽屉**；空白长按=小组件/壁纸/桌面设置
- **根因约定**：根层手写长按 520ms（> cell 拖拽 400ms）+ `draggingItem == null` 让位——**勿改回 detectTapGestures**（多手势根节点上长按不触发，原因未明）

## 5. 已知问题与规避

1. **车软星球市场（asia.carmix.market）是干扰源**：车机桌面类应用，反复自启动、抢默认桌面角色、会 `pm disable` 竞品桌面。已 `pm disable-user --user 0` 但它可能自我恢复。桌面"消失"时依次：`pm list packages -d` 查禁用 → `pm enable` → `cmd package set-home-activity`。建议卸载（`adb uninstall asia.carmix.market`）。
2. **Lawnchair（app.lawnchair.debug）** 也装在模拟器上，会抢默认桌面角色，同样用 set-home 切回。
3. **小组件空白占位**：bind 未完成时渲染空 FrameLayout。重跑 grantbind + 重新添加即可。
4. **rotation 频繁变化**：模拟器窗口旋转导致横竖屏切换，adb 注入坐标必须匹配当前方向（注入系=当前显示方向，横屏 2400×1080 / 竖屏 1080×2400；截图可缩放，wm size 给的是物理面板方向）。y 超界=点在屏外，无效果。
5. **settings.gradle 的 `android.overridePathCheck=true`**：中文路径放行，AGP 升级后仍需保留；若遇资源编译异常，优先怀疑中文路径（Lawnchair 曾因此迁移到 D 盘）。
6. **角标为会话态**：BadgeStore 内存计数，桌面进程重启清零（真实通知需 listener 在线期间到达）。重启后由 onListenerConnected 恢复当前活跃通知。

## 6. 调试经验（来之不易，务必阅读）

- **adb 注入手势不可靠**：`input draganddrop`/`swipe`/`motionevent` 的 MOVE/UP 常丢，长按有时触发有时不触发——**这是注入器问题，不代表应用有问题**。验证手势用：①手势中途截图（`DOWN → sleep → screencap → UP`）②屏幕上渲染调试文字 ③最终请用户鼠标实测。
- **logcat 不可用**：该模拟器 logcat 环形缓冲极小（~187 行），应用 Log.d 基本看不到。用 Compose Text 屏上调试（用完删）。
- **uiautomator dump**：偶发 "null root node" 重试即可；坐标是真实注入系坐标；`text=` 与 `content-desc=` 分清楚（Compose 图标是 content-desc，标签是 text）。
- **长按后必须先 UP 再 tap 菜单项**：第二触点的 down 会被 DropdownMenu 当 outside-click 关菜单。
- **截图 vs 注入坐标系**：screencap 可能缩放（2000×900 vs 注入 2400×1080，比例 1.2），换算后再 tap。
- **XML/Compose 引号**：Bash 内联 PowerShell 的 `$_` 会被 bash 吞——复杂脚本一律写成 .ps1/.sh 文件执行。

## 7. 路线图（未完成项）

按优先级建议：
1. **通知角标增强**：角标点击显示通知列表（One UI 行为）、渠道级 showBadge 过滤、重启后持久化
2. **小组件增强**：尺寸自适应调整（拖边改 span）、widget 长按拖动重定位（当前仅菜单移除）、configure activity 流程
3. **主屏编辑模式**：One UI 式捏合进入编辑、页管理（增删空白页）、多选
4. **视觉**：One UI Sans 替代字体（三星版权资产不可打包，选开源近似字体）、深色模式全面自测、文件夹大视图（One UI 点开是全屏网格）
5. **稳定性**：应用安装/卸载广播监听增量刷新 allApps（当前仅启动时全量加载）
6. **备选大迁移**：若拖拽物理/复杂 Widget 需求增长，评估迁移 Lawnchair 骨架（见 CHANGELOG 附录）

## 8. 协议合规提醒

- GPL-3.0 强 copyleft：分发衍生品必须同协议开源
- 依赖（Compose/AndroidX/Kotlin）均 Apache-2.0，兼容 ✓；从 Lawnchair（Apache-2.0）移植代码允许但保留其版权头
- **三星版权资产（One UI 字体/官方图标/壁纸/系统应用反编译代码）严禁入仓**
