# OneUI 桌面 (OneUi_Desktop)

第三方安卓桌面启动器，复刻三星 One UI 风格。当前重点：应用抽屉的**自定义排序**。

## 已实现（v0.2）

- **主屏**：One UI 时钟、壁纸、图标网格、半透明 Dock；**长按拖动排序**、**拖图标到图标上创建文件夹**（文件夹可打开/重命名/解散/继续拖入）、拖到 Dock 换图标；布局持久化
- **应用抽屉**：横向分页（4×5/页）、页码点、药丸搜索栏实时过滤
- **排序**：⋮ 菜单 →「整理应用」进入编辑模式 → **长按拖动**（拖到左右边缘自动翻页）或 **点选移动**（点 A 再点 B）→「完成」保存；也可切「字母顺序」
- **桌面设置**页（显示应用名称开关、选择壁纸），入口：抽屉 ⋮ 菜单
- **动画**：抽屉开合（主屏缩放+滑动）、图标按压缩放、时钟投影文字
- 图标自适应裁圆（One UI 风格）

## 已知交互说明

- 抽屉非编辑模式下长按拖动会被翻页手势拦截（One UI 同样需进入编辑/长按菜单后拖动），请在「整理应用」编辑模式下排序
- 用 adb 注入手势测试长按拖动不可靠（MOVE/UP 事件常丢失），请用模拟器窗口鼠标实测

## 构建与运行

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.example.oneuidesktop/com.example.oneuidesktop.MainActivity
# 还原系统桌面
adb shell cmd package set-home-activity com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher
```

注意：项目路径含中文，已通过 `android.overridePathCheck=true` 放行 AGP 的路径检查。

## 技术栈

Kotlin + Jetpack Compose（BOM 2024.12.01）/ Material 3 / AGP 8.7.3 / Kotlin 2.0.21 / minSdk 23 / targetSdk 35

## 代码结构

```
app/src/main/java/com/example/oneuidesktop/
├── MainActivity.kt    # 入口（HOME intent-filter，注册为桌面）
├── LauncherApp.kt     # 根组合：壁纸/状态/抽屉开关 + 主屏（时钟/网格/Dock）+ 共享图标组件
├── AppDrawer.kt       # 抽屉：横向分页、搜索、排序菜单、长按拖拽（含跨页翻页）
├── AppInfo.kt         # 应用数据 + PackageManager 查询 + 图标圆形裁剪
├── LauncherStore.kt   # 排序模式与自定义顺序持久化
└── Theme.kt           # One UI 主题（动态取色 + 大圆角）
```

## 距离 1:1 One UI 还差的（后续迭代）

- 主屏多页 + 主屏图标拖放/文件夹
- 抽屉：字母侧边索引条、Samsung Free/Google Discover 左右扩展页
- One UI 手势：抽屉内下滑关闭、主屏双指缩放
- 通知角标（需要 NotificationListener 权限）、小部件支持
- One UI Sans 字体（官方字体为三星版权资产，不可直接打包）

## 开源协议

本项目基于 **GPL-3.0** 协议开源，详见 [LICENSE](LICENSE)。

```
OneUI 桌面 (OneUi_Desktop) — 一款复刻三星 One UI 风格的第三方安卓桌面
Copyright (C) 2026 a435630144

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

注意：GPL-3.0 为强著佐权（copyleft）协议——任何基于本项目的二次开发作品
分发时必须同样以 GPL-3.0 开源并提供完整源码。项目依赖（Kotlin/AndroidX/
Compose，均为 Apache-2.0）与 GPL-3.0 兼容。三星 One UI 官方图标、字体等
版权资产未包含在本仓库中。
