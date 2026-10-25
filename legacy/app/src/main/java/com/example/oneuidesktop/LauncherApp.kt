package com.example.oneuidesktop

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    BadgeStore.showNumbers = store.badgeNumbers()

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var order by remember { mutableStateOf<List<AppInfo>>(emptyList()) } // 抽屉
    var customOrder by remember { mutableStateOf(store.isCustomOrder()) }
    var drawerOpen by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(store.showLabels()) }

    var homePages by remember { mutableStateOf<List<List<HomeItem?>>>(emptyList()) }
    var dockKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var layoutLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { AppRepository.loadApps(context) }
    }

    // 抽屉排序
    LaunchedEffect(allApps, customOrder) {
        order = if (customOrder) {
            val saved = store.loadCustomOrder()
            val byKey = allApps.associateBy { it.key }
            val savedSet = saved.toHashSet()
            saved.mapNotNull { byKey[it] } + allApps.filter { it.key !in savedSet }
        } else {
            allApps
        }
    }

    // 主屏布局：首次按默认生成，之后读存储
    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty() && !layoutLoaded) {
            val saved = store.loadHomeLayout()
            if (saved != null) {
                homePages = saved.pages
                dockKeys = saved.dock
            } else {
                val def = defaultHomeLayout(allApps.map { it.key })
                homePages = def.pages
                dockKeys = def.dock
                store.saveHomeLayout(def)
            }
            layoutLoaded = true
        }
    }

    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }

    // 小组件宿主：可见期间监听更新
    val widgetHost = remember { LauncherWidgetHost(context) }
    val widgetManager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        runCatching { widgetHost.startListening() }
        onDispose { runCatching { widgetHost.stopListening() } }
    }

    fun launch(app: AppInfo) {
        runCatching { context.startActivity(app.launchIntent) }
    }

    fun changeLayout(pages: List<List<HomeItem?>>, dock: List<String>) {
        homePages = pages
        dockKeys = dock
        store.saveHomeLayout(HomeLayout(pages, dock))
    }

    // 抽屉「添加到主屏」：找第一个空槽，找不到就追加到最后一页
    fun addToHome(app: AppInfo) {
        if (homePages.any { page -> page.any { it?.id == app.key } }) return
        val newPages = homePages.map { it.toMutableList() }.toMutableList()
        outer@ for (pi in newPages.indices) {
            val page = newPages[pi]
            for (si in 0 until gridSpec(false).perPage) {
                if (si >= page.size || page[si] == null) {
                    while (page.size <= si) page.add(null)
                    page[si] = HomeApp(app.key)
                    break@outer
                }
            }
        }
        if (newPages.none { page -> page.any { it?.id == app.key } }) {
            (newPages.lastOrNull() ?: mutableListOf<HomeItem?>().also { newPages.add(it) }).add(HomeApp(app.key))
        }
        changeLayout(newPages, dockKeys)
    }

    // 设置页返回后刷新设置项
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        showLabels = store.showLabels()
        customOrder = store.isCustomOrder()
    }

    // 壁纸：启动早期 peekDrawable 可能返回 null，延迟重试；最终用内置壁纸兜底
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var wallpaperDebug by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val (bmp, dbg) = wallpaperImageDebug(context)
            wallpaper = bmp
            wallpaperDebug = dbg
            if (wallpaper == null) {
                delay(1500)
                val (bmp2, dbg2) = wallpaperImageDebug(context)
                wallpaper = bmp2
                wallpaperDebug = dbg2
            }
        }
    }
    val homeScale by animateFloatAsState(if (drawerOpen) 0.93f else 1f)

    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(Modifier.fillMaxSize()) {
        val wp = wallpaper
        if (wp != null) {
            Image(
                bitmap = wp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF2C3E6B), Color(0xFF12172B)),
                        ),
                    ),
            ) {
                if (wallpaperDebug.isNotBlank()) {
                    Text(
                        "壁纸调试: $wallpaperDebug",
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(6.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    transformOrigin = TransformOrigin(0.5f, 0.55f)
                },
        ) {
            HomeScreen(
                appsByKey = appsByKey,
                pages = homePages,
                dockKeys = dockKeys,
                showLabels = showLabels,
                widgetHost = widgetHost,
                widgetManager = widgetManager,
                onLayoutChange = { pages, dock -> changeLayout(pages, dock) },
                onOpenDrawer = { drawerOpen = true },
                onLaunch = { launch(it) },
                onOpenSettings = {
                    settingsLauncher.launch(Intent(context, SettingsActivity::class.java))
                },
                onSetWallpaper = {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "选择壁纸"))
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(160)),
        ) {
            AppDrawer(
                apps = order,
                customOrder = customOrder,
                showLabels = showLabels,
                onSetCustomOrder = { enabled ->
                    customOrder = enabled
                    store.setCustomOrder(enabled)
                },
                onCommit = { newOrder ->
                    order = newOrder
                    store.saveCustomOrder(newOrder.map { it.key })
                },
                onLaunch = { launch(it) },
                onOpenSettings = {
                    settingsLauncher.launch(Intent(context, SettingsActivity::class.java))
                },
                onDismiss = { drawerOpen = false },
                onAddToHome = { app ->
                    addToHome(app)
                },
                onDropToHome = { app ->
                    drawerOpen = false
                    addToHome(app)
                    android.widget.Toast.makeText(
                        context,
                        "已添加到主屏",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}

private fun wallpaperImageDebug(context: Context): Pair<ImageBitmap?, String> {
    val log = StringBuilder()
    return try {
        val wm = WallpaperManager.getInstance(context)
        var d: android.graphics.drawable.Drawable? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                d = wm.getDrawable(WallpaperManager.FLAG_SYSTEM)
                log.append("getDrawable=").append(d != null)
            } catch (e: Exception) {
                log.append("getDrawable-ex:").append(e.javaClass.simpleName)
            }
        }
        if (d == null) {
            try {
                d = wm.peekDrawable()
                log.append(" peek=").append(d != null)
            } catch (e: Exception) {
                log.append(" peek-ex:").append(e.javaClass.simpleName)
            }
        }
        if (d == null) {
            try {
                d = wm.builtInDrawable
                log.append(" builtin=").append(d != null)
            } catch (e: Exception) {
                log.append(" builtin-ex:").append(e.javaClass.simpleName)
            }
        }
        if (d == null) return null to log.toString()
        val bmp = try {
            drawableToBitmap(d, 720).asImageBitmap()
        } catch (e: Exception) {
            return null to log.append(" bmp-ex:").append(e.javaClass.simpleName).toString()
        }
        bmp to log.toString()
    } catch (e: Exception) {
        null to "wm-ex:${e.javaClass.simpleName}:${e.message ?: ""}"
    }
}

// ---------------- 共享组件 ----------------

@Composable
fun OneUiClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(30_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now) }
    Column(modifier) {
        Text(
            date,
            style = TextStyle(
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.92f),
                shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 8f),
            ),
        )
        Text(
            time,
            style = TextStyle(
                fontSize = 78.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 12f),
            ),
        )
    }
}

@Composable
fun DockBar(
    dockApps: List<AppInfo>,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.18f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockApps.forEach { app ->
                BadgeStore.revision
                val badge = remember(app.key, BadgeStore.revision) {
                    BadgeStore.count(app.key.substringBefore('/'))
                }
                Box {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(50.dp)
                            .clickable { onLaunch(app) },
                    )
                    if (badge > 0) {
                        BadgeDot(badge, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
                    }
                }
            }
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
                    .clickable { onOpenDrawer() },
                contentAlignment = Alignment.Center,
            ) {
                DrawerGridIcon(Modifier.size(20.dp))
            }
        }
    }
}

/** One UI 角标：圆点模式 / 数字模式（跟随 BadgeStore.showNumbers） */
@Composable
fun BadgeDot(badge: Int, modifier: Modifier = Modifier) {
    if (BadgeStore.showNumbers) {
        Box(
            modifier
                .size(19.dp)
                .background(Color(0xFFFF453A), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge.coerceAtMost(99).toString(),
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        Box(
            modifier
                .size(11.dp)
                .background(Color(0xFFFF453A), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
        )
    }
}

@Composable
fun AppIconCell(
    app: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    darkText: Boolean,
    showLabel: Boolean = true,
    dim: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f)
    // 通知角标（One UI：图标右上角彩色圆点）
    BadgeStore.revision
    val badge = remember(app.key, BadgeStore.revision) {
        BadgeStore.count(app.key.substringBefore('/'))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (dim) 0.4f else 1f
            }
            .clickable(interactionSource = interaction, indication = null) { onLaunch(app) }
            .padding(vertical = 6.dp),
    ) {
        Box {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(52.dp),
            )
            if (badge > 0) {
                BadgeDot(badge, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp),
                style = if (darkText) {
                    LocalTextStyle.current
                } else {
                    TextStyle(
                        color = Color.White,
                        shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
                    )
                },
            )
        }
    }
}

@Composable
fun DrawerGridIcon(modifier: Modifier = Modifier) {
    val color = Color(0xFF3B3F45)
    Canvas(modifier) {
        val step = size.width / 3f
        val radius = size.width * 0.10f
        for (i in 0..2) {
            for (j in 0..2) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(step * (i + 0.5f), step * (j + 0.5f)),
                )
            }
        }
    }
}

@Composable
fun PageDots(count: Int, active: Int, darkText: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val base = if (darkText) Color(0xFF5A5E66) else Color.White
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (i == active) base else base.copy(alpha = 0.35f)),
            )
        }
    }
}
