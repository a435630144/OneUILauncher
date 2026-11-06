/*
 * Copyright 2026, OneUI Desktop
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.oneui.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.PagedView
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pageindicators.PageIndicator
import com.android.launcher3.touch.ItemClickHandler
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One UI style paged grid for the app drawer's custom sort mode: apps are laid
 * out on fixed pages (workspace grid size) that are browsed horizontally,
 * exactly like the One UI drawer — no vertical scrollbar.
 *
 * Each cell is a [BubbleTextView] so click (launch + usage counting), long
 * press (drag edit mode via [OneUiDrawerDragOverlay]) and icon rendering are
 * shared with the rest of the launcher.
 */
class OneUiDrawerPagedGrid(
    context: Context,
) : PagedView<OneUiDrawerPagedGrid.Page>(context) {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private var apps: List<Any> = emptyList()
    private var appsByKey: Map<String, AppInfo> = emptyMap()
    private var folderByPlaceholder = HashMap<String, DrawerFolderStore.Folder>()
    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // The grid paints the One UI page dots itself, below the icon area,
        // and keeps the same top breathing room as the scrolling list.
        setWillNotDraw(false)
        setPadding(
            0,
            resources.getDimensionPixelSize(com.android.launcher3.R.dimen.oneui_allapps_top_spacing),
            0,
            (24f * resources.displayMetrics.density).roundToInt(),
        )
    }

    /**
     * Rebuilds the pages from [list]: plain [AppInfo] entries or drawer
     * folders ([DrawerFolderStore.Folder]), rendered through their first
     * member so drag flows keep seeing an AppInfo tag.
     */
    fun setEntries(list: List<Any>, byKey: Map<String, AppInfo>) {
        apps = list
        appsByKey = byKey
        folderByPlaceholder.clear()
        for (entry in list) {
            val folder = entry as? DrawerFolderStore.Folder ?: continue
            folder.placeholderKey?.let { folderByPlaceholder[it] = folder }
        }
        syncPages()
    }

    private fun folderOf(tag: Any?): DrawerFolderStore.Folder? {
        val app = tag as? AppInfo ?: return null
        return folderByPlaceholder[app.toComponentKey().toString()]
    }

    /** Persists the browsed page so the next drawer open lands here directly. */
    override fun notifyPageSwitchListener(prevPage: Int) {
        super.notifyPageSwitchListener(prevPage)
        if (mCurrentPage != prefs.drawerLastPage.get()) {
            prefs.drawerLastPage.set(mCurrentPage)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // One UI page dots sit just above the docked search capsule, centered,
        // and stay fixed on screen like the search bar itself. PagedView
        // scrolls via scrollTo(), which shifts this view's canvas by -scrollX,
        // so the dot row is drawn at scrollX to cancel that offset.
        if (childCount == 0 || visibility != VISIBLE) return
        val dot = 2.6f * resources.displayMetrics.density
        val gap = 14f * resources.displayMetrics.density
        val y = height - paddingBottom / 2f
        val startX = scrollX + width / 2f - (childCount - 1) * gap / 2f
        for (i in 0 until childCount) {
            pageDotPaint.color = Color.WHITE
            pageDotPaint.alpha = if (i == mCurrentPage) 230 else 80
            canvas.drawCircle(startX + i * gap, y, dot, pageDotPaint)
        }
    }

    fun showPage(index: Int) {
        if (index in 0 until childCount) {
            snapToPage(index)
        }
    }

    private fun syncPages() {
        val perPage = columns() * rows()
        val wanted = ceil(apps.size / perPage.toDouble()).toInt().coerceAtLeast(1)
        while (childCount < wanted) {
            addView(
                Page(context, columns(), rows()),
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        while (childCount > wanted) {
            removeViewAt(childCount - 1)
        }
        for (pageIndex in 0 until childCount) {
            fillPage(getPageAt(pageIndex) as Page, pageIndex, perPage)
        }
        // Restore the last browsed page *before* the first frame: the grid is
        // rebuilt on every drawer open and the page index resets, which used
        // to flash page 0 before jumping to the remembered page.
        val saved = prefs.drawerLastPage.get()
        val target = if (saved in 0 until childCount) saved else 0
        setCurrentPage(target)
        if (target != 0) {
            scrollTo(getScrollForPage(target), 0)
        }
    }

    private fun fillPage(page: Page, pageIndex: Int, perPage: Int) {
        page.removeAllViews()
        val iconPx = launcher.deviceProfile.allAppsIconSizePx
        val padPx = launcher.deviceProfile.allAppsIconDrawablePaddingPx
        val textPx = launcher.deviceProfile.allAppsIconTextSizePx
        for (cell in 0 until perPage) {
            val index = pageIndex * perPage + cell
            // A plain icon+label layout instead of BubbleTextView: the bare
            // BubbleTextView constructor renders its compound-drawable label
            // unreliably in this manual-layout grid (text ended up hidden
            // behind the icon). Icon size, label color and positions are now
            // fully deterministic, and the name is baked into the icon bitmap
            // because the TextView path was not drawn at all.
            val icon = android.widget.ImageView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(iconPx, iconPx)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            val label = android.widget.TextView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = padPx }
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFFF1F1F7.toInt())
                textSize = textPx / resources.displayMetrics.density
            }
            val cellView = DraggableCellView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                addView(icon)
                addView(label)
                // Folders open a sheet; plain apps launch through the shared handler.
                setOnClickListener { v ->
                    val folder = folderOf(v.tag)
                    if (folder != null) {
                        showFolderSheet(folder)
                    } else {
                        ItemClickHandler.INSTANCE.onClick(v)
                    }
                }
                setOnLongClickListener { v ->
                    startDrawerDrag(v)
                }
            }
            val entry = apps.getOrNull(index)
            val folder = entry as? DrawerFolderStore.Folder
            if (folder != null) {
                val placeholder = folder.placeholderKey?.let { appsByKey[it] }
                if (placeholder != null) {
                    // Render the folder through a clone of its first member so
                    // the composed preview tile and folder title show up while
                    // the drag pipeline still sees a plain AppInfo tag.
                    val folderTitle = folder.title.ifBlank {
                        context.getString(com.android.launcher3.R.string.oneui_drawer_folder_name)
                    }
                    val tempApp = AppInfo().apply {
                        componentName = placeholder.componentName
                        user = placeholder.user
                        intent = placeholder.intent
                        title = folderTitle
                        bitmap = com.android.launcher3.icons.BitmapInfo.of(
                            folderPreviewBitmap(folder),
                            placeholder.bitmap.color,
                        )
                    }
                    icon.setImageBitmap(composeLabeledIcon(tempApp, folderTitle))
                    label.visibility = GONE
                    cellView.tag = tempApp
                    cellView.visibility = VISIBLE
                } else {
                    cellView.visibility = INVISIBLE
                    cellView.tag = null
                }
            } else if (entry != null) {
                val app = entry as AppInfo
                icon.setImageBitmap(composeLabeledIcon(app, app.title.toString()))
                label.visibility = GONE
                cellView.tag = app
                cellView.visibility = VISIBLE
            } else {
                cellView.visibility = INVISIBLE
                cellView.tag = null
            }
            page.addView(cellView)
        }
    }

    /**
     * Composes the app icon and its label into one bitmap. The paged grid's
     * TextView labels were unreliable under the manual cell layout (text set
     * but never drawn), so the name is baked into the icon image instead —
     * the ImageView path is verified to always render.
     */
    private fun composeLabeledIcon(app: AppInfo, label: CharSequence): android.graphics.Bitmap {
        val size = launcher.deviceProfile.allAppsIconSizePx
        val density = resources.displayMetrics.density
        val textH = (26f * density).roundToInt()
        val bitmap = android.graphics.Bitmap.createBitmap(
            size, size + textH, android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        app.bitmap.icon?.let { icon ->
            canvas.drawBitmap(
                icon,
                android.graphics.Rect(0, 0, icon.width, icon.height),
                android.graphics.Rect(0, 0, size, size),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        }
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF1F1F7.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 13f * density
        }
        val ellipsized = android.text.TextUtils.ellipsize(
            label, tp, size - 6f * density, android.text.TextUtils.TruncateAt.END,
        )
        canvas.drawText(ellipsized.toString(), size / 2f, size + textH * 0.78f, tp)
        return bitmap
    }

    private fun folderPreviewBitmap(folder: DrawerFolderStore.Folder): android.graphics.Bitmap {
        val size = launcher.deviceProfile.allAppsIconSizePx
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        val radius = size * 0.28f
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bg)
        val members = folder.keys.mapNotNull { appsByKey[it] }.take(4)
        val cell = size / 2f
        val iconSize = cell * 0.62f
        members.forEachIndexed { i, app ->
            val icon = app.bitmap.icon
            if (icon != null) {
                val left = (i % 2) * cell + (cell - iconSize) / 2f
                val top = (i / 2) * cell + (cell - iconSize) / 2f
                val src = android.graphics.Rect(0, 0, icon.width, icon.height)
                val dst = android.graphics.RectF(left, top, left + iconSize, top + iconSize)
                canvas.drawBitmap(icon, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
        }
        return bitmap
    }

    /** One UI drawer folder sheet: grid of members plus add/rename/delete. */
    private fun showFolderSheet(folder: DrawerFolderStore.Folder) {
        OneUiDrawerFolderSheet(
            context = context,
            folderId = folder.id,
            appsByKey = appsByKey,
            onChanged = { (launcher as? LawnchairLauncher)?.syncOneUiDrawerPagedGrid() },
        ).show()
    }

    private fun startDrawerDrag(view: android.view.View): Boolean {
        val app = view.tag as? AppInfo ?: return false
        val overlay = (launcher as? LawnchairLauncher)?.overlayDrawerDrag ?: return false
        val options = DragOptions().apply { isDrawerDrag = true }
        launcher.workspace.beginDragShared(view, launcher.appsView, options)
        return true
    }

    /**
     * Live "make room" preview while a drag hovers: the icons between the
     * source and the landing index slide one cell aside with a short
     * animation, exactly like the One UI drawer, instead of snapping at
     * drop time. [from]/[to] are indices into the flat display order.
     */
    fun previewShift(from: Int, to: Int) {
        if (from == to || from < 0 || to < 0 || from >= apps.size) {
            clearPreviewShift()
            return
        }
        val perPage = columns() * rows()
        val pageIdx = mCurrentPage.coerceIn(0, childCount - 1)
        val page = getPageAt(pageIdx) as? Page ?: return
        val pageStart = pageIdx * perPage
        val pageEnd = pageStart + perPage
        val samePage = from in pageStart until pageEnd && to in pageStart..pageEnd
        val cellW = page.width.toFloat() / columns()
        val cellH = page.height.toFloat() / rows()
        val interpolator = android.view.animation.DecelerateInterpolator(1.6f)

        for (viewIdx in 0 until page.childCount) {
            val view = page.getChildAt(viewIdx) ?: continue
            val globalIdx = pageStart + viewIdx
            val target = when {
                globalIdx == from -> globalIdx // source is lifted by the DragView
                samePage && from < to && globalIdx > from && globalIdx <= to -> globalIdx - 1
                samePage && from > to && globalIdx >= to && globalIdx < from -> globalIdx + 1
                !samePage && from !in pageStart until pageEnd && globalIdx >= to && globalIdx < pageEnd -> globalIdx + 1
                !samePage && from in pageStart until pageEnd && globalIdx >= from && globalIdx < pageEnd -> globalIdx - 1
                else -> globalIdx
            }
            if (target == globalIdx) {
                if (view.translationX != 0f || view.translationY != 0f) {
                    view.animate().translationX(0f).translationY(0f)
                        .setDuration(140L).setInterpolator(interpolator).start()
                }
                continue
            }
            val tx = ((target % columns()) - (viewIdx % columns())) * cellW
            val ty = ((target / columns()) - (viewIdx / columns())) * cellH
            view.animate().translationX(tx.roundToInt().toFloat())
                .translationY(ty.roundToInt().toFloat())
                .setDuration(220L).setInterpolator(interpolator).start()
        }
    }

    /** Animates (or instantly resets with [instant]) every shifted cell back home. */
    fun clearPreviewShift(instant: Boolean = false) {
        for (pageIdx in 0 until childCount) {
            val page = getPageAt(pageIdx) as? Page ?: continue
            for (viewIdx in 0 until page.childCount) {
                val view = page.getChildAt(viewIdx) ?: continue
                if (view.translationX == 0f && view.translationY == 0f) continue
                if (instant) {
                    view.animate().cancel()
                    view.translationX = 0f
                    view.translationY = 0f
                } else {
                    view.animate().translationX(0f).translationY(0f).setDuration(140L).start()
                }
            }
        }
    }

    private fun columns(): Int = launcher.deviceProfile.inv.numColumns

    private fun rows(): Int = launcher.deviceProfile.inv.numRows

    /**
     * A drawer cell: icon + label column that also satisfies the drag
     * pipeline's [com.android.launcher3.dragndrop.DraggableView] contract.
     * Without it [Workspace.beginDragShared] crashes with an NPE before the
     * drag ever starts, which is why drag interactions appeared dead.
     */
    class DraggableCellView(context: Context) :
        android.widget.LinearLayout(context),
        com.android.launcher3.dragndrop.DraggableView {

        override fun getViewType(): Int =
            com.android.launcher3.dragndrop.DraggableView.DRAGGABLE_ICON

        override fun getSourceVisualDragBounds(bounds: android.graphics.Rect) {
            bounds.set(0, 0, width, height)
        }
    }

    /**
     * One drawer page: a fixed cols x rows grid whose cells host
     * [BubbleTextView] icons. Page dots are drawn once by the parent
     * [OneUiDrawerPagedGrid], so this class only implements the
     * [PageIndicator] contract required by [PagedView].
     */
    class Page(
        context: Context,
        private val cols: Int,
        private val rows: Int,
    ) : FrameLayout(context), PageIndicator {

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val cellW = (width - paddingLeft - paddingRight).toFloat() / cols
            val cellH = (height - paddingTop - paddingBottom).toFloat() / rows
            for (i in 0 until childCount) {
                val col = i % cols
                val row = i / cols
                getChildAt(i).layout(
                    (paddingLeft + col * cellW).roundToInt(),
                    (paddingTop + row * cellH).roundToInt(),
                    (paddingLeft + (col + 1) * cellW).roundToInt(),
                    (paddingTop + (row + 1) * cellH).roundToInt(),
                )
            }
        }

        override fun setScroll(currentScroll: Int, totalScroll: Int) = Unit

        override fun setActiveMarker(activePage: Int) = Unit

        override fun setMarkersCount(numMarkers: Int) = Unit
    }
}
