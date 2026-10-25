package com.example.oneuidesktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    onLayoutChange: (List<List<HomeItem?>>, List<String>) -> Unit,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    var dragTotal by remember { mutableStateOf(0f) }
    var homeMenuAt by remember { mutableStateOf<Offset?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }

    var draggingItem by remember { mutableStateOf<HomeItem?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
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

    fun handleDragEnd(dragged: HomeItem) {
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
                detectTapGestures(
                    onLongPress = {
                        homeMenuAt = it
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
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
                            dragPos += amount
                            if (d != null) handleMove(d, dragPos)
                        },
                        onDragEnd = {
                            val dragged = draggingItem
                            val pos = dragPos
                            draggingItem = null
                            if (dragged != null) handleDragEnd(dragged)
                        },
                    )
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
private fun PageGrid(
    items: List<HomeItem?>,
    spec: GridSpec,
    page: Int,
    highlightIndex: Int,
    appsByKey: Map<String, AppInfo>,
    showLabels: Boolean,
    dimId: String?,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (HomeFolder) -> Unit,
    onCellPosition: (String, LayoutCoordinates) -> Unit,
    onStartDrag: (HomeItem, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { alpha = if (dim) 0.4f else 1f }
            .clickable { onOpen(folder) }
            .padding(vertical = 6.dp),
    ) {
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
