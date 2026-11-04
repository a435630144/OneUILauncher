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
    private var apps: List<AppInfo> = emptyList()
    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        // The grid paints the One UI page dots itself, below the icon area.
        setWillNotDraw(false)
        setPadding(0, 0, 0, (24f * resources.displayMetrics.density).roundToInt())
    }

    /** Rebuilds the pages from [list] (display order of the drawer). */
    fun setApps(list: List<AppInfo>) {
        apps = list
        syncPages()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // One UI page dots sit just above the docked search capsule, centered.
        if (childCount == 0 || visibility != VISIBLE) return
        val dot = 2.6f * resources.displayMetrics.density
        val gap = 14f * resources.displayMetrics.density
        val y = height - paddingBottom / 2f
        val startX = width / 2f - (childCount - 1) * gap / 2f
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
        if (mCurrentPage >= childCount) {
            snapToPage(childCount - 1)
        }
    }

    private fun fillPage(page: Page, pageIndex: Int, perPage: Int) {
        page.removeAllViews()
        for (cell in 0 until perPage) {
            val index = pageIndex * perPage + cell
            val cellView = BubbleTextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // One UI centers the label horizontally under the icon.
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener(ItemClickHandler.INSTANCE)
                setOnLongClickListener { v ->
                    startDrawerDrag(v as BubbleTextView)
                }
            }
            val app = apps.getOrNull(index)
            if (app != null) {
                cellView.applyFromApplicationInfo(app)
                cellView.tag = app
                cellView.visibility = VISIBLE
            } else {
                cellView.visibility = INVISIBLE
                cellView.tag = null
            }
            page.addView(cellView)
        }
    }

    private fun startDrawerDrag(view: BubbleTextView): Boolean {
        val app = view.tag as? AppInfo ?: return false
        val overlay = (launcher as? LawnchairLauncher)?.overlayDrawerDrag ?: return false
        val options = DragOptions().apply { isDrawerDrag = true }
        launcher.workspace.beginDragShared(view, launcher.appsView, options)
        return true
    }

    private fun columns(): Int = launcher.deviceProfile.inv.numColumns

    private fun rows(): Int = launcher.deviceProfile.inv.numRows

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
