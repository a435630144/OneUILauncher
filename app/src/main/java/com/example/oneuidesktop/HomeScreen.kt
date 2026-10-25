package com.example.oneuidesktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * One UI 主屏：时钟 + 多页图标网格（HorizontalPager，可空槽位）+ Dock。
 * - 页内长按拖动排序（槽位语义）/ 拖到图标上建文件夹 / 拖入 Dock 换图标 / 拖到应用上交换位置
 * - 拖到左右边缘自动翻页；最后一页右边缘页满时移入新页
 * - 空白处长按 → 壁纸 / 桌面设置
 */
@Composable
fun HomeScreen(
    appsByKey: Map<String, AppInfo>,
    pages: List<List<HomeItem?>>,
    dockKeys: List<String>,
    showLabels: Boolean,
    widgetHost: android.appwidget.AppWidgetHost,
    widgetManager: android.appwidget.AppWidgetManager,
    onLayoutChange: (List<List<HomeItem?>>, List<String>) -> Unit,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    var dragTotal by remember { mutableStateOf(0f) }
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }

    var draggingItem by remember { mutableStateOf<HomeItem?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragMoved by remember { mutableStateOf(0f) }
    var menuItem by remember { mutableStateOf<HomeItem?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dockCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cellCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var lastFlipAt by remember { mutableStateOf(0L) }

    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val spec = remember(isLandscape) { gridSpec(isLandscape) }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    /** 尾部空页/尾槽清理（至少保留 1 页） */
    fun trim(pagesList: MutableList<MutableList<HomeItem?>>): List<List<HomeItem?>> {
        var end = pagesList.size
        while (end > 1 && pagesList[end - 1].all { it == null }) end--
        return pagesList.subList(0, end).map { page ->
            var e = page.size
            while (e > 0 && page[e - 1] == null) e--
            page.subList(0, e).toList()
        }
    }

    fun cellIndexAt(pos: Offset): Int {
        if (gridSize == IntSize.Zero) return -1
        val padH = with(density) { 16.dp.toPx() }
        val padV = with(density) { 12.dp.toPx() }
        val cellW = (gridSize.width - 2 * padH) / spec.cols
        val cellH = (gridSize.height - 2 * padV) / spec.rows
        if (cellW <= 0 || cellH <= 0) return -1
        val col = ((pos.x - padH) / cellW).toInt().coerceIn(0, spec.cols - 1)
        val row = ((pos.y - padV) / cellH).toInt().coerceIn(0, spec.rows - 1)
        return row * spec.cols + col
    }

    /** 长按未拖动 → 弹菜单；解散文件夹（成员回原槽位） */
    fun disbandFolder(folder: HomeFolder) {
        val newPages = pages.map { it.toMutableList() }.toMutableList()
        val pageIdx = newPages.indexOfFirst { list -> list.any { it is HomeFolder && it.id == folder.id } }
        if (pageIdx >= 0) {
            val list = newPages[pageIdx]
            val idx = list.indexOfFirst { it is HomeFolder && it.id == folder.id }
            val f = list.removeAt(idx) as HomeFolder
            f.members.forEachIndexed { k, key ->
                list.add(idx + k, HomeApp(key))
            }
        }
        onLayoutChange(trim(newPages), dockKeys)
    }

    fun removeItem(item: HomeItem) {
        if (item is HomeWidget) {
            runCatching { widgetHost.deleteAppWidgetId(item.appWidgetId) }
        }
        val newPages = pages.map { it.toMutableList() }.toMutableList()
        newPages.forEach { list -> list.removeAll { it?.id == item.id } }
        onLayoutChange(trim(newPages), dockKeys)
    }

    val context2 = androidx.compose.ui.platform.LocalContext.current

    /** 选定小组件：分配 id → 绑定 → 放到当前页第一个可容纳的空槽 */
    fun addWidget(info: android.appwidget.AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        val bound = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)
        if (!bound) {
            runCatching {
                context2.startActivity(
                    android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                )
            }
        }
        val (sc, sr) = WidgetCatalog.suggestedSpan(context2, info, spec.cols)
        val widget = HomeWidget(
            id = "widget_$id",
            appWidgetId = id,
            spanCols = sc.coerceAtMost(spec.cols),
            spanRows = sr.coerceAtMost(spec.rows - 1),
        )
        val pageIdx = pagerState.currentPage.coerceIn(0, pages.size - 1)
        val newPages = pages.map { it.toMutableList() }.toMutableList()
        if (newPages.isEmpty()) newPages.add(mutableListOf())
        val page = newPages[pageIdx]
        // 找连续空槽（能容纳 span 的起始格），找不到就追加
        var placed = false
        for (start in 0 until spec.perPage) {
            val col = start % spec.cols
            val row = start / spec.cols
            if (col + widget.spanCols > spec.cols || row + widget.spanRows > spec.rows) continue
            val slots = (0 until widget.spanRows).flatMap { r ->
                (0 until widget.spanCols).map { c -> (row + r) * spec.cols + (col + c) }
            }
            val free = slots.all { i -> page.getOrNull(i) == null }
            if (free) {
                slots.forEach { i -> while (page.size <= i) page.add(null); page[i] = widget }
                placed = true
                break
            }
        }
        if (!placed) page.add(widget)
        onLayoutChange(trim(newPages), dockKeys)
    }

    fun handleDragEnd(dragged: HomeItem) {
        // 拖到网格上缘 → 从主屏移除（应用回到抽屉）
        if (gridSize != IntSize.Zero && dragPos.y < with(density) { 40.dp.toPx() } && dragged is HomeApp) {
            removeItem(dragged)
            android.widget.Toast.makeText(
                context2,
                "已从主屏移除，可在抽屉中找到",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val dc = dockCoords
        val gc = gridCoords
        // 拖到 Dock：替换对应槽位的应用，旧应用回到第一页末尾
        if (dc != null && gc != null && dragged is HomeApp) {
            val rootPos = gc.positionInRoot() + dragPos
            val local = rootPos - dc.positionInRoot()
            if (local.x >= 0f && local.y >= 0f &&
                local.x <= dc.size.width.toFloat() && local.y <= dc.size.height.toFloat()
            ) {
                val slot = (local.x / (dc.size.width / 5f)).toInt().coerceIn(0, 4)
                if (slot < 4 && dockKeys.getOrNull(slot) != dragged.id) {
                    val oldKey = dockKeys.getOrNull(slot)
                    val newPages = pages.map { it.toMutableList() }.toMutableList()
                    newPages.forEach { list -> list.removeAll { it?.id == dragged.id } }
                    if (!oldKey.isNullOrEmpty() && oldKey != dragged.id) {
                        val firstPage = newPages.firstOrNull() ?: mutableListOf<HomeItem?>()
                        if (newPages.isEmpty()) newPages.add(firstPage)
                        firstPage.add(HomeApp(oldKey))
                    }
                    val newDock = dockKeys.toMutableList()
                    while (newDock.size < 4) newDock.add("")
                    newDock[slot] = dragged.id
                    onLayoutChange(trim(newPages), newDock.filter { it.isNotEmpty() })
                    return
                }
            }
        }
        val page = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        val index = cellIndexAt(dragPos)
        if (index < 0) return
        val fromPage = pages.indexOfFirst { it.any { it === dragged } }
        if (fromPage < 0) return
        val fromSlot = pages[fromPage].indexOfFirst { it === dragged }
        val newPages = pages.map { it.toMutableList() }.toMutableList()
        val target = pages.getOrNull(page)?.getOrNull(index)
        when {
            target == null || target.id == dragged.id -> {
                // 移动到目标槽位（空白格或原位）
                newPages[fromPage][fromSlot] = null
                while (newPages.size <= page) newPages.add(mutableListOf())
                val dst = newPages[page]
                while (dst.size <= index) dst.add(null)
                dst[index] = dragged
            }
            dragged is HomeFolder -> {
                // 文件夹拖到应用上：交换位置
                val dst = newPages[page]
                val tIdx = dst.indexOfFirst { it === target }
                if (tIdx >= 0) {
                    dst[tIdx] = dragged
                    newPages[fromPage][fromSlot] = target
                }
            }
            dragged is HomeApp && target is HomeApp -> {
                // 应用叠放建文件夹
                newPages[fromPage][fromSlot] = null
                val dst = newPages[page]
                val tIdx = dst.indexOfFirst { it === target }
                if (tIdx >= 0) {
                    dst[tIdx] = HomeFolder(
                        id = "folder_${System.currentTimeMillis()}",
                        name = "文件夹",
                        members = listOf(target.id, dragged.id),
                    )
                }
            }
            dragged is HomeApp && target is HomeFolder -> {
                // 拖入已有文件夹
                newPages[fromPage][fromSlot] = null
                val dst = newPages[page]
                val tIdx = dst.indexOfFirst { it === target }
                if (tIdx >= 0) {
                    dst[tIdx] = target.copy(members = target.members + dragged.id)
                }
            }
        }
        onLayoutChange(trim(newPages), dockKeys)
    }

    /** 拖动中的边缘处理：左右自动翻页；最后一页右边缘页满时移入新页 */
    fun handleMove(dragged: HomeItem, pos: Offset) {
        if (gridSize == IntSize.Zero) return
        val padH = with(density) { 16.dp.toPx() }
        val edge = with(density) { 48.dp.toPx() }
        val page = pagerState.currentPage
        val now = System.currentTimeMillis()
        if (now - lastFlipAt <= 450) return
        when {
            pos.x < padH + edge && page > 0 -> {
                lastFlipAt = now
                scope.launch { pagerState.animateScrollToPage(page - 1) }
            }
            pos.x > gridSize.width - padH - edge && page < pagerState.pageCount - 1 -> {
                lastFlipAt = now
                scope.launch { pagerState.animateScrollToPage(page + 1) }
            }
            pos.x > gridSize.width - padH - edge -> {
                // 最后一页右边缘：页满则把被拖项放到新页并跟过去
                lastFlipAt = now
                val cur = pages.getOrNull(page) ?: return
                val pageFull = cur.count { it != null } >= spec.perPage
                if (pageFull) {
                    val fromPage = pages.indexOfFirst { it.any { it === dragged } }
                    if (fromPage >= 0) {
                        val fromSlot = pages[fromPage].indexOfFirst { it === dragged }
                        val newPages = pages.map { it.toMutableList() }.toMutableList()
                        newPages[fromPage][fromSlot] = null
                        newPages.add(mutableListOf(dragged))
                        onLayoutChange(trim(newPages), dockKeys)
                        scope.launch {
                            snapshotFlow { pagerState.pageCount }.first { it > page }
                            pagerState.animateScrollToPage(pagerState.pageCount - 1)
                        }
                    }
                }
            }
        }
    }

    val openFolderResolved = pages.asSequence()
        .flatMap { it.asSequence() }
        .filterIsInstance<HomeFolder>()
        .firstOrNull { it.id == openFolderId }

    val highlightPage = if (draggingItem != null) pagerState.currentPage else -1
    val highlightIndex = if (draggingItem != null) cellIndexAt(dragPos) else -1

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -160f) onOpenDrawer() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragTotal += amount
                    },
                )
            }
            .pointerInput(Unit) {
                // 手写长按检测（500ms，晚于图标拖拽的 400ms）：
                // 长按落在图标上时 cell 先进入拖拽（draggingItem 非空），根菜单让位
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + 120) {
                        waitForUpOrCancellation()
                    }
                    if (up == null && draggingItem == null) {
                        homeMenuAt = down.position
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        waitForUpOrCancellation()
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            OneUiClockHeader(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 28.dp, top = 18.dp, bottom = 8.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        gridCoords = it
                        gridSize = it.size
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = draggingItem == null,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val items = pages.getOrNull(page) ?: emptyList()
                    PageGrid(
                        items = items,
                        spec = spec,
                        page = page,
                        highlightIndex = if (highlightPage == page) highlightIndex else -1,
                        appsByKey = appsByKey,
                        showLabels = showLabels,
                        dimId = draggingItem?.id,
                        widgetHost = widgetHost,
                        widgetManager = widgetManager,
                        onLaunch = onLaunch,
                        onOpenFolder = { openFolderId = it.id },
                        onCellPosition = { id, coords -> cellCoords[id] = coords },
                        onStartDrag = { item, offset ->
                            val gc = gridCoords
                            val cc = cellCoords[item.id]
                            if (draggingItem == null && gc != null && cc != null) {
                                draggingItem = item
                                dragPos = cc.positionInRoot() - gc.positionInRoot() + offset
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragMove = { amount ->
                            val d = draggingItem
                            dragMoved += amount.getDistance()
                            dragPos += amount
                            if (d != null) handleMove(d, dragPos)
                        },
                        onDragEnd = {
                            val dragged = draggingItem
                            val pos = dragPos
                            draggingItem = null
                            if (dragged != null) {
                                if (dragMoved < with(density) { 12.dp.toPx() }) {
                                    menuItem = dragged
                                } else {
                                    handleDragEnd(dragged)
                                }
                            }
                        },
                    )
                }

                // 主屏图标长按菜单
                menuItem?.let { item ->
                    val context = LocalContext.current
                    Box(Modifier.offset { IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) }) {
                        DropdownMenu(expanded = true, onDismissRequest = { menuItem = null }) {
                            if (item is HomeApp) {
                                DropdownMenuItem(
                                    text = { Text("移除") },
                                    onClick = {
                                        menuItem = null
                                        removeItem(item)
                                    },
                                )
                            }
                            if (item is HomeFolder) {
                                DropdownMenuItem(
                                    text = { Text("编辑文件夹") },
                                    onClick = {
                                        menuItem = null
                                        openFolderId = item.id
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("解散文件夹") },
                                    onClick = {
                                        menuItem = null
                                        disbandFolder(item)
                                    },
                                )
                            }
                            item.id.substringBefore('/').takeIf { item is HomeApp }?.let { pkg ->
                                DropdownMenuItem(
                                    text = { Text("应用信息") },
                                    onClick = {
                                        menuItem = null
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    android.net.Uri.parse("package:$pkg"),
                                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("卸载") },
                                    onClick = {
                                        menuItem = null
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_DELETE,
                                                    android.net.Uri.parse("package:$pkg"),
                                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // 跟随手指的浮动图标
                draggingItem?.let { item ->
                    val iconHalf = with(density) { 28.dp.toPx() }
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (dragPos.x - iconHalf).roundToInt(),
                                    (dragPos.y - iconHalf).roundToInt(),
                                )
                            }
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = 1.22f
                                scaleY = 1.22f
                                alpha = 0.95f
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        FloatingItemContent(item, appsByKey)
                    }
                }
            }
            PageDots(
                count = pagerState.pageCount,
                active = pagerState.currentPage,
                darkText = false,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            Box(
                Modifier
                    .onGloballyPositioned { dockCoords = it }
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            ) {
                DockBar(
                    dockApps = dockKeys.mapNotNull { appsByKey[it] },
                    onOpenDrawer = onOpenDrawer,
                    onLaunch = onLaunch,
                )
            }
        }

        // 空白处长按菜单
        homeMenuAt?.let { pos ->
            Box(Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { homeMenuAt = null }) {
                    DropdownMenuItem(
                        text = { Text("小组件") },
                        onClick = {
                            homeMenuAt = null
                            showWidgetPicker = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("壁纸") },
                        onClick = {
                            homeMenuAt = null
                            onSetWallpaper()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("桌面设置") },
                        onClick = {
                            homeMenuAt = null
                            onOpenSettings()
                        },
                    )
                }
            }
        }

        // 小组件选择器
        if (showWidgetPicker) {
            WidgetPickerDialog(
                context2 = context2,
                onPick = { info ->
                    showWidgetPicker = false
                    addWidget(info)
                },
                onDismiss = { showWidgetPicker = false },
            )
        }

        // 文件夹弹窗
        openFolderResolved?.let { folder ->
            FolderDialog(
                folder = folder,
                appsByKey = appsByKey,
                onDismiss = { newName ->
                    val newPages = pages.map { list ->
                        list.map {
                            if (it is HomeFolder && it.id == folder.id) it.copy(name = newName) else it
                        }
                    }
                    onLayoutChange(newPages, dockKeys)
                    openFolderId = null
                },
                onLaunch = { app ->
                    onLaunch(app)
                    openFolderId = null
                },
                onDisband = {
                    val newPages = pages.map { it.toMutableList() }.toMutableList()
                    val pageIdx = newPages.indexOfFirst { list -> list.any { it is HomeFolder && it.id == folder.id } }
                    if (pageIdx >= 0) {
                        val list = newPages[pageIdx]
                        val idx = list.indexOfFirst { it is HomeFolder && it.id == folder.id }
                        val f = list.removeAt(idx) as HomeFolder
                        f.members.forEachIndexed { k, key ->
                            list.add(idx + k, HomeApp(key))
                        }
                    }
                    onLayoutChange(trim(newPages), dockKeys)
                    openFolderId = null
                },
            )
        }
    }
}

@Composable
private fun WidgetPickerDialog(
    context2: android.content.Context,
    onPick: (android.appwidget.AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = remember { WidgetCatalog.installed(context2) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("小组件", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    if (providers.isEmpty()) {
                        item {
                            Text(
                                "系统暂无可用小组件",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(providers.size) { idx ->
                        val info = providers[idx]
                        val label = remember(info) { WidgetCatalog.label(context2, info) }
                        val preview = remember(info) { WidgetCatalog.preview(context2, info, 480) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onPick(info) }
                                .padding(10.dp),
                        ) {
                            if (preview != null) {
                                Image(
                                    bitmap = preview,
                                    contentDescription = label,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(72.dp),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(label, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageGrid(
    items: List<HomeItem?>,
    spec: GridSpec,
    page: Int,
    highlightIndex: Int,
    appsByKey: Map<String, AppInfo>,
    showLabels: Boolean,
    dimId: String?,
    widgetHost: android.appwidget.AppWidgetHost,
    widgetManager: android.appwidget.AppWidgetManager,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeFolder) -> Unit,
    onCellPosition: (String, LayoutCoordinates) -> Unit,
    onStartDrag: (HomeItem, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val cellW = maxWidth / spec.cols
        val cellH = maxHeight / spec.rows
        // 小组件层（跨格绝对定位）
        items.forEachIndexed { index, item ->
            if (item is HomeWidget) {
                val col = index % spec.cols
                val row = index / spec.cols
                AndroidView(
                    factory = { ctx ->
                        WidgetCatalog.createView(widgetHost, widgetManager, item.appWidgetId)
                            ?: android.widget.FrameLayout(ctx)
                    },
                    update = { },
                    modifier = Modifier
                        .offset(x = cellW * col, y = cellH * row)
                        .size(cellW * item.spanCols, cellH * item.spanRows)
                        .pointerInput(item.appWidgetId) {
                            // 长按小组件 → 拖动/菜单；短按交给小组件自身
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation()
                                }
                                if (up == null) {
                                    onStartDrag(item, down.position)
                                    waitForUpOrCancellation()
                                }
                            }
                        },
                )
            }
        }
        // 图标网格
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until spec.rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until spec.cols) {
                        val index = row * spec.cols + col
                        val item = items.getOrNull(index)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (index == highlightIndex) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(7.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.18f),
                                            RoundedCornerShape(20.dp),
                                        ),
                                )
                            }
                            if (item != null) {
                                key(item.id) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .onGloballyPositioned { onCellPosition(item.id, it) }
                                            .pointerInput(item.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        onStartDrag(item, offset)
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        onDragMove(amount)
                                                    },
                                                    onDragEnd = onDragEnd,
                                                    onDragCancel = onDragEnd,
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        HomeItemContent(
                                            item = item,
                                            appsByKey = appsByKey,
                                            showLabels = showLabels,
                                            onLaunch = onLaunch,
                                            onOpenFolder = onOpenFolder,
                                            dim = dimId == item.id,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeItemContent(
    item: HomeItem,
    appsByKey: Map<String, AppInfo>,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeFolder) -> Unit,
    dim: Boolean,
) {
    when (item) {
        is HomeApp -> appsByKey[item.id]?.let { app ->
            AppIconCell(app = app, onLaunch = onLaunch, darkText = false, showLabel = showLabels, dim = dim)
        }
        // 小组件由 PageGrid 的绝对定位层渲染
        is HomeWidget -> Unit
        is HomeFolder -> FolderIcon(
            folder = item,
            appsByKey = appsByKey,
            showLabel = showLabels,
            onOpen = onOpenFolder,
            dim = dim,
        )
    }
}

@Composable
private fun FloatingItemContent(item: HomeItem, appsByKey: Map<String, AppInfo>) {
    when (item) {
        is HomeApp -> appsByKey[item.id]?.let { app ->
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(52.dp))
        }
        is HomeWidget -> Box(
            Modifier
                .size(88.dp, 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.5f)),
        )
        is HomeFolder -> Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            val icons = item.members.mapNotNull { appsByKey[it] }.take(4)
            Column {
                icons.chunked(2).take(2).forEach { pair ->
                    Row {
                        pair.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
                        repeat(2 - pair.size) { Spacer(Modifier.size(19.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderIcon(
    folder: HomeFolder,
    appsByKey: Map<String, AppInfo>,
    showLabel: Boolean,
    onOpen: (HomeFolder) -> Unit,
    dim: Boolean = false,
) {
    // 文件夹角标 = 成员应用通知数之和
    BadgeStore.revision
    val badge = remember(folder.members, BadgeStore.revision) {
        folder.members.sumOf { key -> BadgeStore.count(key.substringBefore('/')) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { alpha = if (dim) 0.4f else 1f }
            .clickable { onOpen(folder) }
            .padding(vertical = 6.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center,
            ) {
                val icons = folder.members.mapNotNull { appsByKey[it] }.take(4)
                Column {
                    icons.chunked(2).take(2).forEach { pair ->
                        Row {
                            pair.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(19.dp)) }
                            repeat(2 - pair.size) { Spacer(Modifier.size(19.dp)) }
                        }
                    }
                }
            }
            if (badge > 0) {
                BadgeDot(badge, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                folder.name,
                fontSize = 12.sp,
                maxLines = 1,
                style = TextStyle(
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 8f),
                ),
            )
        }
    }
}

@Composable
private fun FolderDialog(
    folder: HomeFolder,
    appsByKey: Map<String, AppInfo>,
    onDismiss: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onDisband: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val members = folder.members.mapNotNull { appsByKey[it] }
    Dialog(onDismissRequest = { onDismiss(name.ifBlank { folder.name }) }) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("文件夹名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    members.chunked(4).forEach { rowApps ->
                        Row(Modifier.fillMaxWidth()) {
                            rowApps.forEach { app ->
                                Box(Modifier.weight(1f)) {
                                    AppIconCell(
                                        app = app,
                                        onLaunch = { onLaunch(app) },
                                        darkText = true,
                                    )
                                }
                            }
                            repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDisband, modifier = Modifier.align(Alignment.End)) {
                    Text("解散文件夹")
                }
            }
        }
    }
}
