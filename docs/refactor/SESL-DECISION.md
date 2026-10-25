# SESL 评估与取舍

> 结论先行：**第一阶段不把 SESL 引入构建**。SESL 作为「以后可以加上的加速器」保留，
> 而不是「地基」。原因不是拿不到包，而是 SESL 的集成方式和我们原来的设想不一样。

## 1. 我们原以为的 SESL

以为 `io.github.tribalfs:oneui-design` / `sesl-androidx` 是一套**附加组件库**：
加一行 `implementation(...)`，就能在现有工程里用上一批 One UI 风格的按钮、开关、设置行。

## 2. 实际验证到的 SESL

下载并解包了 `appcompat`（SESL8）：

```
appcompat-1.8.0+1.0.21-sesl8+rev2.aar
  └── classes.jar
        androidx/appcompat/widget/...   266 个 class
        androidx/appcompat/app/...       87 个 class
        androidx/appcompat/view/...      69 个 class
        ...
```

**包名没有改。** SESL 不是新增一套 `sesl.*` 组件，而是把 AndroidX / Material Components
**整包重新发布**，用同名类替换掉官方实现（三星内部就是这么做的）。

由此推出两个硬约束：

1. **不能并存。** 工程里只要有官方 `androidx.appcompat`，再加 SESL 的 appcompat，
   就是 duplicate class，编译直接失败。
2. **要用就得全局替换。** 必须把所有用到的 AndroidX / Material 模块整体换成 SESL 版本
   （`androidx.appcompat` → `sesl.androidx.appcompat:appcompat`、`androidx.preference` → …），
   并且 SESL 得覆盖我们依赖的每一个模块。任何一个模块没被覆盖，就会出现「官方版 + SESL 版」
   混用，资源属性和主题继承关系都可能对不上。

Launcher3 本身几乎不用 AndroidX UI 栈，但 Lawnchair 的**设置页是 Jetpack Compose**，
底层仍然挂着 `Theme.AppCompat` 和 `com.google.android.material`。所以这不是「加个库」，
而是「换掉整个 UI 底座」。

## 3. 分发渠道的问题

SESL8/9 只在 GitHub Packages，**即使公开包也必须带 token**：

```
GET https://maven.pkg.github.com/tribalfs/sesl-androidx/...
  → 401 Unauthorized（匿名）
  → 401 Unauthorized（本机 GitHub token，缺 read:packages）
```

包页面本身是公开的，HTML 里带 5 分钟有效的签名下载地址，可以脚本抓取后落到本地 Maven 仓库
（`tools/fetch-sesl.ps1` 就是干这个的）。但这条路只解决「能不能拿到」，
解决不了上面第 2 节「要全局替换」的问题。

## 4. 关于「上游停更」

你的判断是对的：**如果 tribalfs 停更，我们最终还是要回到自研。** 更具体一点：

- SESL 冻结在某个版本 = 那几个 AndroidX 模块也冻结了。
- AndroidX 每年都在动（targetSdk 提升、新的 edge-to-edge、新的权限模型），
  冻结的 fork 迟早会和系统行为脱节。
- 到那时换回官方 AndroidX，等于把当年省下的工作一次性还回去，还要重做一遍视觉。

所以真正的风险不是「停更」，而是**让 SESL 变成不可替换的地基**。

## 5. 采用的方案：可替换的 One UI 设计层

```
app/
├── core/      Launcher3 内核（桌面、抽屉、拖拽、文件夹、小组件）   ← 不出现任何 SESL 类型
├── design/    One UI 设计层：颜色 / 圆角 / 字号 / 间距 / 自绘控件  ← 目前纯自研
└── sesl/      可选适配层：SESL 类型只允许出现在这里                 ← 现在不参与构建
```

三条规则：

1. **design 层必须能独立还原视觉**。SESL 只是加速器，去掉它，外观不能变。
2. **SESL 类型不越过 `sesl/` 边界**。`core` 和 `design` 不 import 任何 SESL 类。
3. **版本 pin + 本地 vendor**。真要接入时，AAR 落到仓库内本地 Maven，
   构建不依赖 GitHub Packages 在线可用。

这样即使 SESL 明天消失，删掉 `sesl/` 目录 + 几行 Gradle 即可，界面不受影响；
反之如果 SESL 活得很好，我们随时可以把它接进来提速。

## 6. 如果要接入，需要改什么（备查）

```groovy
// 只在决定接入时启用
configurations.all {
    resolutionStrategy.eachDependency { details ->
        if (details.requested.group == 'androidx.appcompat') {
            details.useTarget "sesl.androidx.appcompat:appcompat:1.8.0+1.0.21-sesl8+rev2"
        }
        if (details.requested.group == 'androidx.preference') {
            details.useTarget "sesl.androidx.preference:preference:1.2.1+1.0.0-sesl8+rev1"
        }
        if (details.requested.group == 'com.google.android.material') {
            details.useTarget "sesl.com.google.android.material:material:1.12.0+1.0.32-sesl8+rev3"
        }
        // core / fragment / recyclerview / viewpager2 / drawerlayout / coordinatorlayout / ...
    }
}
```

已知可用坐标（SESL8，`sesl-androidx` 仓库 18 个包）：

| 模块 | 版本 |
| --- | --- |
| appcompat | `1.8.0+1.0.21-sesl8+rev2` |
| core | `1.19.0+1.0.7-sesl8+rev1` |
| core-ktx | `1.19.0+1.0.0-sesl8+rev0` |
| preference | `1.2.1+1.0.0-sesl8+rev1` |
| recyclerview | `1.4.0+1.0.12-sesl8+rev3` |
| fragment | `1.9.0+1.0.5-sesl8+rev1` |
| viewpager2 | `1.1.0+1.0.0-sesl8+rev0` |
| material | `1.12.0+1.0.32-sesl8+rev3` |
| oneui-design | `0.9.20+oneui8` |

注意 `oneui-design` 还额外拉 camera / mlkit-barcode / lottie，对桌面启动器是纯负担，
真要接入也建议只用 `sesl-androidx` 那 18 个模块。
