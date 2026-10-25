# 开发日志（CHANGELOG）

项目：OneUI 桌面（OneUi_Desktop）——复刻三星 One UI 风格的第三方安卓桌面启动器
技术栈：Kotlin + Jetpack Compose（BOM 2024.12.01）/ Material 3 / AGP 8.7.3 / Kotlin 2.0.21 / minSdk 23 / targetSdk 35
仓库：https://github.com/a435630144/OneUILauncher（main 分支，GPL-3.0）

所有开发在 2026-10-25 一天内完成（AI 结对开发）。以下按版本时间线记录，
每版的「问题与解决」小节是本项目最有价值的经验沉淀。

---

## v0.1 — 项目起步：主屏 + 应用抽屉（提交 a16e017 之前）

**功能**
- 项目创建：复用 TodoApp 练手项目验证过的版本组合（AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01），`local.properties` 指向 `D:/AndroidDevelopment/Sdk`
- Manifest 注册 `HOME` + `DEFAULT` + `LAUNCHER` intent-filter（priority=1），成为可选桌面；`<queries>` 声明解决 Android 11+ 包可见性
- 主屏：One UI 风格大号时钟+日期（投影文字）、壁纸、4×5 图标网格、半透明圆角 Dock（4 应用 + 九宫格抽屉按钮）、页点
- 应用抽屉：HorizontalPager 横向分页（4×5/页）、药丸形搜索栏实时过滤、⋮ 菜单排序切换（自定义/字母）、自定义顺序持久化（SharedPreferences + JSON）
- 图标：自适应图标（AdaptiveIconDrawable）裁剪
- 上滑打开抽屉 / 返回键关闭；点击图标启动应用

**问题与解决**
- **中文路径构建被拒**：项目路径 `E:\OneDrive\家里台式\桌面\...` 含非 ASCII，AGP 直接拒绝构建。`gradle.properties` 加 `android.overridePathCheck=true` 放行，实测资源编译无问题。
- **Compose API 版本差异**：`localPositionOf` 在 BOM 2024.12.01 不存在，改用 `positionInRoot()` 差值换算。

## v0.2 — 拖拽排序、文件夹、设置页

**功能**
- 抽屉拖拽排序（初版实时重排 → 后改拖放式）、「整理应用」编辑模式（提示文字 + 完成按钮 + 点选移动：点 A 选中、点 B 落位）
- 主屏文件夹：图标拖到图标上创建，弹窗可重命名/解散/继续拖入/点击启动成员
- 桌面设置页（SettingsActivity）：显示应用名称开关、系统壁纸选择器入口
- 主屏拖到 Dock 槽位替换 Dock 图标（旧应用回到网格）

**问题与解决（拖拽三连坑，耗时最多）**
1. **手势修饰符随状态拆装 = 手势被杀**：v0.1 中 `dragging` 状态变化导致重组合时 `pointerInput` 从组件树被移除，正在执行的长按拖拽被系统取消——表现为用户报告的"长按抽动一下后无法选中"。修复：手势修饰符常驻（`dragEnabled` 不随 dragging 变化），用回调内部守卫防并发。
2. **嵌套网格跨父容器移动 = 节点身份丢失**：Column/Row 嵌套网格里格子换位 = 换父容器，`key(app.key)` 失效，手势节点被销毁。修复：改**拖放式**（拖起 → 目标格高亮 → 松手落位，不做实时重排），彻底绕开。
3. **HorizontalPager 拦截长按**：pager 可滚动时子节点长按注入不稳定（手动实测也可能被微颤破坏）。最终交互：排序统一走「整理应用」编辑模式（`userScrollEnabled=false`），与 One UI 官方一致。
- **adb 注入手势不可靠**（贯穿全程）：`input draganddrop`/`swipe`/`motionevent` 时好时坏，MOVE/UP 常丢。确立验证方法论：**手势中途截图 + 屏幕上渲染调试文字**（该模拟器 logcat 环形缓冲极小，Log.d 不可见）。
- **应用包被 pm disable**：车软星球市场（车机桌面类应用，见 v0.5）多次禁用本桌面，导致 "Activity does not exist"、set-home 失败。恢复：`pm enable com.example.oneuidesktop`。

## v0.3 — 壁纸、横竖屏、交互对齐 One UI

**功能**
- 壁纸加载加固：`getDrawable(FLAG_SYSTEM)` → `peekDrawable()` → `builtInDrawable` 三重兜底 + 1.5s 延迟重试 + 深蓝渐变最终兜底
- 动态网格 `GridSpec`：竖屏 4×5、横屏 6×4，主屏/抽屉全部参数化
- 抽屉**任何状态长按即拖**（不再必须先进编辑模式），拖完自动持久化为自定义顺序
- 边缘交互：左右边缘 48dp 触发区自动翻页（450ms 节流）；最后一页右边缘移到列表末尾自动生成新页并跟随；`dropAt` 用 `targetPage` 处理翻页动画中松手

**问题与解决**
- **壁纸丢失**：force-stop 后立即启动时壁纸服务瞬态不可用（`peekDrawable` 返回 null）。三重兜底 + 重试解决；调试期用"屏上红字渲染异常信息"定位（logcat 不可用）。
- `getWallpaperDrawable` API 名记错，实际是 `getDrawable(int which)`（API 24+）。

## v0.4 — One UI 视觉：方形图标 + 主屏多页

**功能**
- **One UI 圆角方形图标**：自适应图标裁进 27% 圆角方形；普通位图图标自动提取主色（24×24 采样 → HSV 柔和化：低饱和高明度；灰白图标落 `#F1F3F5` 浅灰底）生成底板，内容 62% 居中——Samsung 对无底板图标的处理方式
- **主屏多页**：HorizontalPager 多页 + 页点联动；布局模型升级为**可空槽位**（`List<List<HomeItem?>>`，JSON 支持显式 null），拖放精确落位到空白格；跨页拖拽（边缘翻页 + 页满自动建新页跟随）；尾部空页/空槽自动清理；文件夹跨页弹窗
- 主屏空白长按菜单（壁纸/桌面设置）、抽屉下滑 180px 关闭
- 手势修复：抽屉长按拖动与 Pager 滚动竞争 → 编辑模式禁滚；点选移动保留

**问题与解决**
- **槽位语义**：稀疏布局（空格=列表缺失项）下 `add(index)` 会错位。引入可空槽位 + `while(dst.size <= index) add(null)` 显式补位，序列化用 `JSONObject.NULL`。
- 横屏 6×4 在编辑模式/抽屉/主屏三处网格循环 + `cellIndexAt`/`targetIndexFor` 同步参数化，Activity 重建时全量重组（无 stale lambda 问题）。

## v0.5 — 应用管理菜单 + 原生小组件

**功能**
- 长按菜单双通道：**长按不动 = 菜单，拖动 = 排序**（`dragMoved < 12dp` 判定）
  - 抽屉：添加到主屏（找第一个空槽）/ 应用信息 / 卸载
  - 主屏：移除 / 编辑文件夹 / 解散文件夹 / 应用信息 / 卸载
- **原生小组件**：`LauncherWidgetHost`（AppWidgetHost，HOST_ID=4242）+ 自建选择器（列出 installedProviders + 预览图）+ `bindAppWidgetIdIfAllowed`（失败则发系统绑定 Intent）+ PageGrid 绝对定位层跨格渲染 `AppWidgetHostView`（AndroidView）+ `HomeWidget` 持久化（appWidgetId/spanCols/spanRows，按 minWidth 估算 span）+ 移除时 `deleteAppWidgetId`
- 手写长按检测（`awaitEachGesture` + `withTimeoutOrNull(longPressTimeoutMillis)`）替换 `detectTapGestures`

**问题与解决**
- **detectTapGestures 在多手势根节点上长按不触发**（原因未查明，v0.2 时期就有疑云）：统一改手写检测，语义完全可控。
- **bind 授权**：模拟器上 `bindAppWidgetIdIfAllowed` 返回 false → 系统弹确认框没人点 → 渲染空白 FrameLayout 占位。解决：`adb shell appwidget grantbind --package com.example.oneuidesktop`（模拟器重启后需重授）。
- **手势竞争发现**：空白长按菜单与图标拖拽同时触发（同一触点双检测器都到时）→ v0.6 修复。

## v0.6 — 通知角标 + 主屏⇄抽屉互移（提交 3cd6f19）

**功能**
- **通知圆点角标**：`BadgeListener`（NotificationListenerService，常驻通知排除）+ `BadgeStore` 单例（按包名统计活跃通知 key 集合，`revision` 版本号驱动 Compose 重组）；渲染覆盖主屏/抽屉/Dock 图标 + 文件夹（角标=成员之和）；One UI 风格右上角红点白描边
- **角标双模式**：圆点（默认）/ 数字（19dp 红圆白字，上限 99+），`BadgeDot` 统一组件，设置页开关持久化
- **主屏⇄抽屉互移**：抽屉图标拖到网格上缘 → 关抽屉+放入主屏空槽（Toast）；主屏图标拖到上缘 → 移除回抽屉（Toast）
- 设置页：通知使用权授权入口（状态显示 + 跳转系统设置）、时钟/图库测试角标调试按钮

**问题与解决**
- **手势竞争修复**：根长按（空白菜单）与 cell 长按（图标拖拽）同时到时。修复：根长按 520ms（晚于 cell 400ms）+ `draggingItem == null` 让位。实测：图标上长按只拖拽+目标格高亮，空白处只弹菜单。
- **listener 授权**：`cmd notification allow_listener com.example.oneuidesktop/.BadgeListener`（模拟器重启后需重跑）。
- **验证策略**：`BadgeStore.debugSet()` 调试按钮注入假角标做视觉验证（真实通知路径代码同源）。

## 协议与发布（穿插进行）

- **GPL-3.0**：LICENSE 首版凭记忆写出（当时 gnu.org/GitHub raw/国内镜像全不通），后经 GitHub API（choosealicense.com 仓库正文）逐字校验，发现 **7 处实质偏差**（最严重：第 13 节整节写成了 2006 讨论稿的 "Remote Network Interaction"，官方是 "Use with the GNU Affero General Public License"）→ 替换为逐字官方文本（diff IDENTICAL）。README 含协议说明、版权声明模板、依赖兼容性（Apache-2.0 ✓）与三星版权资产合规声明。
- **GitHub 发布**：https://github.com/a435630144/OneUILauncher（main）。github.com 主站与 api.github.com 通，raw.githubusercontent.com 不通（push 需重试 1-8 次不等，脚本自动重试）。

## 附：Lawnchair 骨架验证（未采用，备选路线）

- clone Lawnchair 15-dev（Gradle 9.3.0/JDK 17/AGP 9.0.1/compileSdk 36.1）到 `D:\AndroidDevelopment\lawnchair`，完整构建成功（首建 34 分钟），装进模拟器运行正常
- 抽屉排序改造接缝已定位：`src/com/android/launcher3/allapps/AlphabeticalAppsList.java:241`
- 结论：如后续需要企业级拖拽物理/WidgetHost/角标基建，可迁移到该骨架（Apache-2.0 兼容 GPL-3.0）；当前自研 Compose 路线继续
