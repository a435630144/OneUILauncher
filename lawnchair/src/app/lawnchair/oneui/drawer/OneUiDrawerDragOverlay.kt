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

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import app.lawnchair.LawnchairLauncher
import app.lawnchair.allapps.LawnchairAlphabeticalAppsList
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemLongClickListener
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One UI drawer drag edit mode. While an icon is dragged the whole drawer is taken over:
 * the background dims, the apps are laid out as paged grids (one page per
 * [DeviceProfile.inv.numColumns] x numRows cells), neighbouring pages peek from the screen
 * edges, and a dot grid + landing outline preview the target cell.
 *
 * Drop semantics (matching the real One UI):
 * - drop on a cell: move the app to that cell in the custom order;
 * - hold near the left / right edge: flip between the existing pages;
 * - drop in the empty area above / below the grid: add the app to the home screen.
 */
class OneUiDrawerDragOverlay(
    private val launcher: LawnchairLauncher,
) : View(launcher), DropTarget, DragController.DragListener {

    private val dragLayer: DragLayer get() = launcher.dragLayer
    private val appsView get() = launcher.appsView
    private val prefs: PreferenceManager = PreferenceManager.getInstance(context)
    private val density = resources.displayMetrics.density

    private val dimPaint = Paint()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val neighborPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandFillPaint = Paint()
    private val bandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pageDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-regular", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val iconCache = HashMap<String, Drawable>()

    private var active = false
    private var ordered: MutableList<AppInfo>? = null
    private var dragged: AppInfo? = null
    private var page = 0
    private var pageOffset = 0f
    private var pageAnimator: ValueAnimator? = null
    private var pageCount = 1

    /** 0 → 1 transition for the edit-mode card fade / scale-in. */
    private var editProgress = 0f
    private var editAnimator: ValueAnimator? = null

    private var targetIndex = -1
    private val targetRect = Rect()
    private var addToHomeEdge = 0
    private var edgeDirection = 0

    private var lastTouchX = 0
    private var lastTouchY = 0
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartSet = false
    private var itemMenu: PopupWindow? = null
    private var menuRunnable: Runnable? = null
    private var draggingView: DragView<*>? = null

    private val tmpRect = Rect()
    private val tmpGrid = Rect()
    private val location = IntArray(2)
    private var overlayTopOnScreen = -1

    private val addToHomeText = context.getString(R.string.oneui_drawer_drag_add_to_home)

    private val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)

    init {
        setWillNotDraw(false)
        // Invisible at rest: an INVISIBLE child is skipped by touch dispatch, so the drawer stays
        // fully interactive. It keeps its laid-out bounds, which the DropTarget hit rect needs.
        visibility = INVISIBLE
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        outlinePaint.strokeWidth = 1.2f * density
        plusPaint.strokeWidth = 1.6f * density
        bandTextPaint.textSize = 13.5f * resources.displayMetrics.scaledDensity
        labelPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        dotsPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 125)
        bandPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 26)
        bandFillPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 12)
        bandTextPaint.color = Color.WHITE
        plusPaint.color = Color.WHITE
        labelPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 235)
        // The page sheet is barely lighter than the dimmed background; the side rails, not a
        // bright card, are what delineate it in One UI.
        cardPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 6)
        neighborPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, 44)
        // The edit mode sits on a dark scrim even when the drawer itself uses a light theme.
        dimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, 215)
    }

    // region Data

    private val gridCols: Int get() = launcher.deviceProfile.inv.numColumns
    private val gridRows: Int get() = launcher.deviceProfile.inv.numRows
    private val cellsPerPage: Int get() = gridCols * gridRows

    /**
     * Snapshot of the drawer in display order. The preview keeps the current order; the custom
     * sort is only persisted when the user actually drops the icon on a new cell.
     */
    private fun initOrdered(): MutableList<AppInfo> {
        val list = ordered
        if (list != null) return list
        val recyclerView = appsView.activeRecyclerView
        val keys = (recyclerView?.apps as? LawnchairAlphabeticalAppsList<*>)
            ?.adapterItems
            ?.asSequence()
            ?.filter { it.viewType == BaseAllAppsAdapter.VIEW_TYPE_ICON }
            ?.mapNotNull { it.itemInfo as? AppInfo }
            ?.toMutableList()
            ?: mutableListOf()
        ordered = keys
        return keys
    }

    private fun pageOf(index: Int): Int = if (index < 0) 0 else index / cellsPerPage

    private fun commitOrder() {
        val list = ordered ?: return
        prefs.drawerCustomOrder.set(list.joinToString("\n") { it.toComponentKey().toString() })
        prefs.drawerSortMode.set(LawnchairAlphabeticalAppsList.SORT_CUSTOM)
    }

    // endregion

    // region DragController.DragListener

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        if (!options.isDrawerDrag) return
        val app = dragObject.dragInfo as? AppInfo
        val list = initOrdered()
        if (app == null || list.indexOf(app) < 0) {
            // Dragged item is not a drawer app (e.g. search result); nothing to take over.
            return
        }
        active = true
        dragged = app
        draggingView = dragObject.dragView
        iconCache.clear()
        page = pageOf(list.indexOf(app)).coerceAtLeast(0)
        pageOffset = page.toFloat()
        pageCount = pageCountFor(list.size)
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        // dragObject.x/y are only filled in by the first hit-test, so the menu slop baseline is
        // captured on the first onDragOver instead (otherwise the initial 0,0 reads as a 600px
        // move and the menu is cancelled before it ever appears).
        dragStartSet = false
        overlayTopOnScreen = -1
        // The overlay takes over completely; the live list (and its search capsule) hide.
        visibility = VISIBLE
        appsView.visibility = INVISIBLE
        // Play the One UI edit-mode enter transition (scrim fade + card scale-in).
        startEditAnimation()
        startEditAnimation()
        // One UI shows the item menu only when the icon is held still. A short delay keeps the
        // menu from flashing when the drag starts moving straight away.
        val source = dragObject.originalView as? View
        val menu = Runnable { showItemMenu(app, source) }
        menuRunnable = menu
        postDelayed(menu, ITEM_MENU_DELAY)
        invalidate()
    }

    override fun onDragEnd() {
        removeCallbacks(edgeFlipRunnable)
        edgeDirection = 0
        menuRunnable?.let { removeCallbacks(it) }
        menuRunnable = null
        dismissItemMenu()
        active = false
        dragged = null
        draggingView = null
        ordered = null
        iconCache.clear()
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        pageAnimator?.cancel()
        pageAnimator = null
        editAnimator?.cancel()
        editAnimator = null
        editProgress = 0f
        appsView.visibility = VISIBLE
        visibility = INVISIBLE
        invalidate()
    }

    // endregion

    // region Edit-mode enter animation

    private fun startEditAnimation() {
        editAnimator?.cancel()
        editProgress = 0f
        editAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = this@OneUiDrawerDragOverlay.interpolator
            addUpdateListener {
                editProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // endregion

    // region DropTarget

    override fun isDropEnabled(): Boolean =
        active && launcher.isInState(LauncherState.ALL_APPS)

    override fun onDragEnter(dragObject: DropTarget.DragObject) = Unit

    override fun onDragOver(dragObject: DropTarget.DragObject) {
        lastTouchX = dragObject.x
        lastTouchY = dragObject.y
        if (!dragStartSet) {
            dragStartX = lastTouchX
            dragStartY = lastTouchY
            dragStartSet = true
        }
        // Moving the icon beyond a small slop closes the One UI item menu.
        val dx = (lastTouchX - dragStartX).toDouble()
        val dy = (lastTouchY - dragStartY).toDouble()
        if (sqrt(dx * dx + dy * dy) > 8f * density) {
            menuRunnable?.let { removeCallbacks(it) }
            menuRunnable = null
            dismissItemMenu()
        }
        updateTarget(lastTouchX, lastTouchY)
        invalidate()
    }

    override fun onDragExit(dragObject: DropTarget.DragObject) = Unit

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = true

    override fun prepareAccessibilityDrop() = Unit

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        removeCallbacks(edgeFlipRunnable)
        edgeDirection = 0
        @Suppress("UNCHECKED_CAST")
        val dragView = dragObject.dragView as? DragView<Launcher>
        val app = dragged
        if (dragView == null || app == null) {
            resetTarget()
            return
        }
        when {
            addToHomeEdge != 0 -> {
                val added = launcher.workspace.addAppFromDrawer(app) != null
                val text = if (added) R.string.oneui_drawer_drag_added else R.string.oneui_drawer_drag_no_space
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                // The app stays in the drawer; adding it to the home screen only creates a copy.
                fadeOutDragView(dragView)
            }
            targetIndex >= 0 -> {
                val list = ordered ?: return
                val from = list.indexOf(app)
                if (from >= 0) {
                    val before = list.toList()
                    val to = targetIndex.coerceIn(0, list.size)
                    list.removeAt(from)
                    // Exact-cell semantics: the icon lands in the previewed cell; the apps in
                    // between shift by one to make room.
                    list.add(to.coerceIn(0, list.size), app)
                    if (list != before) {
                        val wasCustom = prefs.drawerSortMode.get() ==
                            LawnchairAlphabeticalAppsList.SORT_CUSTOM
                        commitOrder()
                        if (!wasCustom) {
                            Toast.makeText(
                                context,
                                R.string.oneui_drawer_drag_alphabetical,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                // The reflow preview already moved the neighbours; reset them
                // instantly so the rebuilt grid (final order) takes over.
                (launcher as? LawnchairLauncher)?.oneUiDrawerPagedGrid?.clearPreviewShift(instant = true)
                animateDragViewToTarget(dragView)
            }
            else -> flyBack(dragView)
        }
        resetTarget()
    }

    private fun resetTarget() {
        targetIndex = -1
        targetRect.setEmpty()
        addToHomeEdge = 0
        updatePreviewShift()
        invalidate()
    }

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        dragLayer.getDescendantRectRelativeToSelf(this, outRect)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // The overlay is purely visual: never take touches away from the drawer / workspace below.
        return false
    }

    // endregion

    // region Geometry

    private fun cardRect(out: Rect): Rect {
        val w = width
        if (w == 0 || height == 0) {
            out.setEmpty()
            return out
        }
        // The page sheet is centred on the whole screen in One UI: ~9.7% of empty scrim above
        // and below it. The overlay lives inside the DragLayer (below the status bar), so map
        // the screen ratio back into local coordinates.
        val screenH = resources.displayMetrics.heightPixels
        val horizontal = (w * 0.028f).roundToInt()
        val top = (screenH * 0.0967f).roundToInt() - overlayTop()
        val bottom = (screenH * 0.9029f).roundToInt() - overlayTop()
        out.set(horizontal, top, w - horizontal, bottom)
        return out
    }

    /**
     * The 4x6 cell area inside the sheet. One UI insets it slightly and reserves a strip at the
     * bottom for the page dots.
     */
    private fun gridRect(card: Rect, out: Rect): Rect {
        val hInset = (4f * density).roundToInt()
        val topInset = (3.5f * density).roundToInt()
        val bottomInset = (34f * density).roundToInt()
        out.set(
            card.left + hInset,
            card.top + topInset,
            card.right - hInset,
            card.bottom - bottomInset,
        )
        return out
    }

    private fun overlayTop(): Int {
        if (overlayTopOnScreen < 0) {
            getLocationOnScreen(location)
            overlayTopOnScreen = location[1]
        }
        return overlayTopOnScreen
    }

    private fun pageX(pageIndex: Int, card: Rect): Int =
        card.left + ((pageIndex - pageOffset) * (card.width() + 8f * density)).roundToInt()

    // endregion

    // region Target computation

    private fun updateTarget(x: Int, y: Int) {
        val card = cardRect(tmpRect)
        if (card.isEmpty) {
            resetTarget()
            return
        }
        val grid = gridRect(card, tmpGrid)

        // Above / below the paged card: drop to the home screen. A small dead zone keeps drops on
        // the very first / last row inside the grid instead of turning them into home-screen adds.
        val out = 8f * density
        if (y < card.top - out || y > card.bottom + out) {
            addToHomeEdge = if (y < card.top) 1 else 2
            targetIndex = -1
            targetRect.setEmpty()
            updatePreviewShift()
            stopEdgeFlip()
            invalidate()
            return
        }
        addToHomeEdge = 0

        // Left / right edge band: flip pages while held. The band matches the peek strip of the
        // neighbouring page, narrow enough that the outer columns stay usable as drop cells.
        val edge = 12f * density
        val direction = when {
            x < grid.left + edge -> -1
            x > grid.right - edge -> 1
            else -> 0
        }
        if (direction != 0 && page + direction in 0 until pageCount) {
            targetIndex = -1
            targetRect.setEmpty()
            updatePreviewShift()
            startEdgeFlip(direction)
            invalidate()
            return
        }
        stopEdgeFlip()

        // No page in that direction: the edge still belongs to the outer column, so drop there.
        // Clamping keeps the 8dp dead zone just outside the sheet snapping to the nearest row
        // instead of falling through to a fly-back.
        val clampedX = x.coerceIn(grid.left + 1, grid.right - 1)
        val clampedY = y.coerceIn(grid.top + 1, grid.bottom - 1)
        updateGridTarget(clampedX, clampedY, grid)
    }

    private fun updateGridTarget(x: Int, y: Int, grid: Rect) {
        val list = ordered ?: return
        val cellW = grid.width().toFloat() / gridCols
        val cellH = grid.height().toFloat() / gridRows
        val col = ((x - grid.left) / cellW).toInt().coerceIn(0, gridCols - 1)
        val row = ((y - grid.top) / cellH).toInt().coerceIn(0, gridRows - 1)
        val cell = row * gridCols + col

        val cellLeft = (grid.left + col * cellW).roundToInt()
        val cellTop = (grid.top + row * cellH).roundToInt()
        targetRect.set(cellLeft, cellTop, (cellLeft + cellW).roundToInt(), (cellTop + cellH).roundToInt())

        val pageStart = page * cellsPerPage
        targetIndex = when {
            // Drop on an occupied cell: the icon takes that exact cell and the rest reflow.
            pageStart + cell < list.size -> pageStart + cell
            // Empty cell on the last page: append to the end of the order.
            pageStart + cell >= list.size && page == pageCount - 1 -> list.size
            else -> -1
        }
        updatePreviewShift()
    }

    /**
     * One UI feel: while hovering, the icons that will be displaced slide
     * aside in real time (instead of snapping when the finger lifts).
     */
    private fun updatePreviewShift() {
        val grid = (launcher as? LawnchairLauncher)?.oneUiDrawerPagedGrid ?: return
        val app = dragged ?: return
        val list = ordered ?: return
        val from = list.indexOf(app)
        if (targetIndex < 0 || from < 0 || from == targetIndex) {
            grid.clearPreviewShift()
        } else {
            grid.previewShift(from, targetIndex)
        }
    }

    private fun pageCountFor(size: Int): Int = ceil(size / cellsPerPage.toDouble()).toInt().coerceAtLeast(1)

    private fun iconSize(cellW: Float, cellH: Float): Int =
        min(cellW * 0.62f, cellH * 0.505f).roundToInt()

    /** Icons and labels sit slightly above the vertical cell centre, as in the drawer itself. */
    private fun iconCenterY(cellTop: Int, cellH: Float): Float = cellTop + cellH * 0.40f

    // endregion

    // region Edge flip

    private fun startEdgeFlip(direction: Int) {
        if (edgeDirection == direction) return
        edgeDirection = direction
        removeCallbacks(edgeFlipRunnable)
        postDelayed(edgeFlipRunnable, EDGE_FLIP_DWELL)
    }

    private fun stopEdgeFlip() {
        if (edgeDirection == 0) return
        edgeDirection = 0
        removeCallbacks(edgeFlipRunnable)
    }

    private val edgeFlipRunnable = object : Runnable {
        override fun run() {
            if (edgeDirection == 0) return
            val target = page + edgeDirection
            if (target < 0 || target >= pageCount) {
                // A packed custom order cannot hold the gap that a brand-new page would need,
                // so the editor only flips between existing pages.
                stopEdgeFlip()
                return
            }
            animatePage(target)
            postDelayed(this, EDGE_FLIP_INTERVAL)
        }
    }

    private fun animatePage(target: Int) {
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageOffset, target.toFloat()).apply {
            duration = 220
            interpolator = this@OneUiDrawerDragOverlay.interpolator
            addUpdateListener {
                pageOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        page = target
    }

    // endregion

    // region One UI item menu

    /**
     * Shows the One UI item menu (title + add-to-home / uninstall) anchored to the source
     * icon. The menu closes as soon as the icon is moved; the drag itself keeps running.
     */
    private fun showItemMenu(app: AppInfo, source: View?) {
        dismissItemMenu()
        if (source == null) return
        val pkg = app.componentName?.packageName ?: return
        val density = resources.displayMetrics.density
        val textColor = Color.parseColor("#DE000000")

        fun px(v: Float): Int = (v * density).roundToInt()

        fun actionButton(iconRes: Int, label: String, onClick: () -> Unit): LinearLayout {
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#3B3F45"))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(px(24f), px(24f))
            }
            val text = TextView(context).apply {
                text = label
                setTextColor(textColor)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, px(6f), 0, 0) }
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(px(14f), px(10f), px(14f), px(10f))
                isClickable = true
                isFocusable = true
                addView(icon)
                addView(text)
                setOnClickListener { onClick() }
            }
        }

        val title = TextView(context).apply {
            text = app.title
            setTextColor(textColor)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val infoIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            setColorFilter(Color.parseColor("#66000000"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(px(30f), px(30f))
            setOnClickListener {
                dismissItemMenu()
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:$pkg"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(16f), px(12f), px(8f), px(4f))
            addView(title)
            addView(infoIcon)
        }
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                actionButton(R.drawable.ic_home_screen, context.getString(R.string.oneui_item_menu_add_home)) {
                    dismissItemMenu()
                    val added = launcher.workspace.addAppFromDrawer(app) != null
                    Toast.makeText(
                        context,
                        if (added) R.string.oneui_drawer_drag_added else R.string.oneui_drawer_drag_no_space,
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (added) {
                        launcher.dragController.cancelDrag()
                    }
                },
            )
            addView(
                actionButton(R.drawable.ic_remove_no_shadow, context.getString(R.string.oneui_item_menu_uninstall)) {
                    dismissItemMenu()
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        val cardColor = Color.parseColor("#F7F7FA")
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(24f).toFloat()
                setColor(cardColor)
            }
            elevation = px(12f).toFloat()
            addView(titleRow)
            addView(actionsRow)
        }
        // One UI points a small diamond from the card at the source icon; the card itself is
        // centred on the icon (clamped to the screen) so the pointer lands under the icon.
        val pointerSize = px(12f)
        val pointerLp = FrameLayout.LayoutParams(pointerSize, pointerSize).apply {
            gravity = Gravity.BOTTOM or Gravity.LEFT
            bottomMargin = px(4f)
        }
        val pointer = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = px(2f).toFloat()
                setColor(cardColor)
            }
            rotation = 45f
            layoutParams = pointerLp
        }
        val cardLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = px(12f) }
        val content = FrameLayout(context).apply {
            setPadding(0, 0, 0, px(12f))
            addView(container, cardLp)
            addView(pointer)
        }

        val widthPx = px(300f)
        // Measure first: the card flips below the icon when there is not enough room above it.
        content.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val menuHeight = content.measuredHeight

        // Anchor on the icon itself rather than the list row: the row view carries label
        // padding, which pushed the card ~50dp away from the icon.
        val card = cardRect(tmpRect)
        val grid = gridRect(card, tmpGrid)
        val cellW = grid.width().toFloat() / gridCols
        val cellH = grid.height().toFloat() / gridRows
        source.getLocationOnScreen(location)
        val viewCenterX = location[0] + source.width / 2f
        val viewCenterY = location[1] + source.height / 2f - overlayTop()
        val col = ((viewCenterX - grid.left) / cellW).toInt().coerceIn(0, gridCols - 1)
        val row = ((viewCenterY - grid.top) / cellH).toInt().coerceIn(0, gridRows - 1)
        val cellTop = (grid.top + row * cellH).roundToInt()
        val iconCx = grid.left + (col + 0.5f) * cellW
        val iconSizePx = iconSize(cellW, cellH).toFloat()
        val slotIconTop = iconCenterY(cellTop, cellH) - iconSizePx / 2f + overlayTop()
        val slotIconBottom = slotIconTop + iconSizePx

        // Point at the lifted icon (what the user actually sees). DragView.getDragRegion() is the
        // icon rect inside the *source* view, not inside the floating preview (the preview is
        // shifted by the blur outline and by how the bitmap is re-centred), so apply it to the
        // source view's screen position instead of the drag view's.
        var anchorCx = iconCx
        var anchorTop = slotIconTop
        var anchorBottom = slotIconBottom
        draggingView?.dragRegion?.takeIf { !it.isEmpty }?.let { region ->
            anchorCx = (location[0] + region.centerX()).toFloat()
            anchorTop = (location[1] + region.top).toFloat()
            anchorBottom = (location[1] + region.bottom).toFloat()
        }

        val screenW = resources.displayMetrics.widthPixels
        val sideMargin = px(8f)
        val left = (anchorCx - widthPx / 2f).roundToInt()
            .coerceIn(sideMargin, (screenW - widthPx - sideMargin).coerceAtLeast(sideMargin))
        pointerLp.leftMargin = (anchorCx - left - pointerSize / 2f).roundToInt()
            .coerceIn(px(26f), widthPx - px(26f) - pointerSize)

        val statusBar = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }
            ?.let { resources.getDimensionPixelSize(it) }
            ?: 0
        // In the "above" layout the diamond tip sits 4dp above the content bottom; in the
        // "below" layout it sits 16dp below the content top (12dp padding + 4dp margin).
        val tipBelow = px(4f)
        val tipAbove = px(16f)
        val tipGap = px(10f)
        val top: Int
        if (anchorTop - tipGap - (menuHeight - tipBelow) >= statusBar) {
            top = (anchorTop - tipGap - (menuHeight - tipBelow)).roundToInt()
        } else {
            // No room above: place the card under the icon and point the diamond upwards.
            content.setPadding(0, px(12f), 0, 0)
            cardLp.topMargin = px(12f)
            cardLp.bottomMargin = 0
            pointerLp.gravity = Gravity.TOP or Gravity.LEFT
            pointerLp.topMargin = px(4f)
            pointerLp.bottomMargin = 0
            top = (anchorBottom + tipGap - tipAbove).roundToInt()
        }
        val menu = PopupWindow(
            content,
            widthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isOutsideTouchable = true
        }
        menu.showAtLocation(this, Gravity.NO_GRAVITY, left, top)
        // One UI: the card springs out from the anchor tip.
        content.pivotX = content.measuredWidth / 2f
        content.pivotY = if (anchorTop - tipGap - (menuHeight - tipBelow) >= statusBar) {
            content.measuredHeight.toFloat()
        } else {
            0f
        }
        content.scaleX = 0.86f
        content.scaleY = 0.86f
        content.alpha = 0f
        content.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120)
            .setInterpolator(interpolator).start()
        itemMenu = menu
    }

    private fun dismissItemMenu() {
        itemMenu?.dismiss()
        itemMenu = null
    }    // endregion

    // region Drop animations

    private fun animateDragViewToTarget(dragView: DragView<Launcher>) {
        dragLayer.getLocationInDragLayer(this, location)
        val cellH = targetRect.height().toFloat()
        val targetLeft = targetRect.centerX() - dragView.measuredWidth / 2 + location[0]
        val targetTop = iconCenterY(targetRect.top, cellH) - dragView.measuredHeight / 2 + location[1]
        dragLayer.animateViewIntoPosition(
            dragView,
            targetLeft,
            targetTop.roundToInt(),
            1f,
            1f,
            1f,
            null,
            DragLayer.ANIMATION_END_DISAPPEAR,
            180,
            null,
        )
    }

    private fun fadeOutDragView(dragView: DragView<Launcher>) {
        dragLayer.getLocationInDragLayer(dragView, location)
        dragLayer.animateViewIntoPosition(
            dragView,
            location[0],
            location[1],
            0f,
            1f,
            1f,
            null,
            DragLayer.ANIMATION_END_DISAPPEAR,
            140,
            null,
        )
    }

    private fun flyBack(dragView: DragView<Launcher>) {
        val origin = (dragged?.toComponentKey()?.toString())?.let { key ->
            appsView.activeRecyclerView?.let { rv ->
                for (i in 0 until rv.childCount) {
                    val child = rv.getChildAt(i)
                    if (child is BubbleTextView && child.tag is AppInfo &&
                        (child.tag as AppInfo).toComponentKey().toString() == key
                    ) {
                        return@let child
                    }
                }
            }
            null
        }
        launcher.dragController.animateDragViewToOriginalPosition(null, origin, 160)
    }

    // endregion

    // region Drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        val list = ordered ?: return
        val target = cardRect(tmpRect)
        if (target.isEmpty) return
        val grid = gridRect(target, tmpGrid)

        // Edit-mode enter transition: the scrim fades in and the paged card scales out
        // from the full drawer area to its resting rect (One UI edit-mode transition).
        val p = editProgress
        val inv = 1f - p
        if (p <= 0.01f) return
        val card = RectF(
            target.left * p,
            target.top * p,
            width - inv * (width - target.right),
            height - inv * (height - target.bottom),
        )

        dimPaint.alpha = (215 * p).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val cellW = grid.width().toFloat() / gridCols
        val cellH = grid.height().toFloat() / gridRows
        val iconSize = iconSize(cellW, cellH)

        // Neighbouring pages peek from the screen edges as light rounded strips, exactly like the
        // One UI paged editor. They are drawn at rest only, while a page flip animation moves the
        // incoming page itself.
        if (pageOffset == page.toFloat() && p > 0.6f) {
            drawNeighborStrips(canvas, grid)
        }

        val currentLeft = pageX(page, target).toFloat()
        val pageCard = RectF(
            currentLeft,
            card.top.toFloat(),
            currentLeft + card.width(),
            card.bottom.toFloat(),
        )
        val radius = 24f * density
        canvas.drawRoundRect(pageCard, radius, radius, cardPaint)

        val hInset = grid.left - target.left
        val pageGrid = RectF(
            currentLeft + hInset,
            grid.top.toFloat(),
            currentLeft + card.width() - hInset,
            grid.bottom.toFloat(),
        )
        // Content fades in with the transition.
        val contentAlpha = (255 * p).toInt().coerceIn(0, 255)
        val save = canvas.saveLayerAlpha(pageCard, contentAlpha)
        drawDots(canvas, pageGrid, cellW, cellH)
        drawPageApps(canvas, list, page, pageGrid, iconSize)
        if (targetIndex >= 0 && !targetRect.isEmpty) {
            drawTargetOutline(canvas, cellW, cellH)
        }
        canvas.restoreToCount(save)

        if (addToHomeEdge != 0) {
            drawAddToHome(canvas, target)
        } else if (p > 0.6f) {
            drawPageDots(canvas, target, pageCount, page)
        }
    }

    private fun drawNeighborStrips(canvas: Canvas, grid: Rect) {
        val stripWidth = 11.5f * density
        val radius = 24f * density
        val top = grid.top.toFloat()
        val bottom = grid.bottom.toFloat()
        if (page > 0) {
            // Left rounded corners stay off screen, so only the inner edge reads as rounded.
            canvas.drawRoundRect(-radius, top, stripWidth, bottom, radius, radius, neighborPaint)
        }
        if (page < pageCount - 1) {
            val right = width.toFloat()
            canvas.drawRoundRect(right - stripWidth, top, right + radius, bottom, radius, radius, neighborPaint)
        }
    }

    private fun drawDots(canvas: Canvas, pageGrid: RectF, cellW: Float, cellH: Float) {
        val half = 1.3f * density
        // Only the internal gutter intersections: 3 vertical x 5 horizontal lines for a 4x6 grid.
        for (col in 1 until gridCols) {
            val x = pageGrid.left + col * cellW
            for (row in 1 until gridRows) {
                val y = pageGrid.top + row * cellH
                canvas.drawRect(x - half, y - half, x + half, y + half, dotsPaint)
            }
        }
    }

    private fun drawPageApps(canvas: Canvas, list: List<AppInfo>, page: Int, pageGrid: RectF, iconSize: Int) {
        val cellW = pageGrid.width() / gridCols
        val cellH = pageGrid.height() / gridRows
        val draggedKey = dragged?.toComponentKey()?.toString()
        val emptyCell = 5f * density
        for (cell in 0 until cellsPerPage) {
            val app = list.getOrNull(page * cellsPerPage + cell) ?: continue
            val key = app.toComponentKey().toString()
            if (draggedKey != null && key == draggedKey) continue
            val col = cell % gridCols
            val row = cell / gridCols
            val cellLeft = pageGrid.left + col * cellW
            val cellTop = pageGrid.top + row * cellH
            val cx = cellLeft + cellW / 2f
            val cy = iconCenterY(cellTop.roundToInt(), cellH)
            val drawable = iconCache.getOrPut(key) { app.bitmap.newIcon(context) }
            drawable.setBounds(
                (cx - iconSize / 2f).roundToInt(),
                (cy - iconSize / 2f).roundToInt(),
                (cx + iconSize / 2f).roundToInt(),
                (cy + iconSize / 2f).roundToInt(),
            )
            drawable.draw(canvas)

            canvas.save()
            canvas.clipRect(cellLeft, cellTop, cellLeft + cellW, cellTop + cellH)
            val label = TextUtils.ellipsize(
                app.title ?: "",
                labelPaint,
                cellW - emptyCell * 2,
                TextUtils.TruncateAt.END,
            )
            val labelTop = cy + iconSize / 2f + 12f * density
            val baseline = labelTop - labelPaint.fontMetrics.ascent
            canvas.drawText(label.toString(), cx, baseline, labelPaint)
            canvas.restore()
        }
    }

    private fun drawTargetOutline(canvas: Canvas, cellW: Float, cellH: Float) {
        val size = iconSize(cellW, cellH) + 2f * density
        val cx = targetRect.centerX().toFloat()
        val cy = iconCenterY(targetRect.top, cellH)
        val half = size / 2f
        val outline = RectF(cx - half, cy - half, cx + half, cy + half)
        val radius = size * 0.26f
        outlinePaint.alpha = 170
        canvas.drawRoundRect(outline, radius, radius, outlinePaint)
    }

    private fun drawAddToHome(canvas: Canvas, card: Rect) {
        // Tint the whole empty band so the add-to-home zone reads as its own area.
        val bandTop = if (addToHomeEdge == 1) 0f else card.bottom.toFloat()
        val bandBottom = if (addToHomeEdge == 1) card.top.toFloat() else height.toFloat()
        canvas.drawRect(0f, bandTop, width.toFloat(), bandBottom, bandFillPaint)

        // Centre the hint pill inside the band; the bottom band is short because the overlay ends
        // at the navigation bar.
        val available = bandBottom - bandTop
        val height = min(40f * density, available - 8f * density).coerceAtLeast(24f * density)
        val centerY = (bandTop + bandBottom) / 2f
        val margin = 32f * density
        val band = RectF(
            card.left + margin,
            centerY - height / 2f,
            card.right - margin,
            centerY + height / 2f,
        )
        canvas.drawRoundRect(band, height / 2f, height / 2f, bandPaint)

        val circleRadius = 8.5f * density
        val textGap = 10f * density
        val textWidth = bandTextPaint.measureText(addToHomeText)
        val contentWidth = circleRadius * 2f + textGap + textWidth
        val contentLeft = maxOf(
            band.centerX() - contentWidth / 2f,
            band.left + 16f * density,
        )
        val circleX = contentLeft + circleRadius
        val circleY = band.centerY()
        canvas.drawCircle(circleX, circleY, circleRadius, plusPaint)
        val plusHalf = 4.2f * density
        canvas.drawLine(circleX - plusHalf, circleY, circleX + plusHalf, circleY, plusPaint)
        canvas.drawLine(circleX, circleY - plusHalf, circleX, circleY + plusHalf, plusPaint)

        val textX = circleX + circleRadius + textGap
        val metrics = bandTextPaint.fontMetrics
        val baseline = band.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(addToHomeText, textX, baseline, bandTextPaint)
    }

    private fun drawPageDots(canvas: Canvas, card: Rect, count: Int, activePage: Int) {
        val radius = 3.4f * density
        val spacing = 19f * density
        val slotCount = count + 1
        val centerX = card.exactCenterX()
        // One UI keeps the page dots just inside the bottom of the sheet; the trailing "+" is an
        // extra slot in the same row, and the whole row (dots + plus) is centred on the sheet.
        val startX = centerX - (slotCount - 1) * spacing / 2f
        val y = card.bottom - 16f * density
        for (i in 0 until count) {
            pageDotPaint.color = Color.WHITE
            pageDotPaint.alpha = if (i == activePage) 255 else 110
            canvas.drawCircle(startX + i * spacing, y, radius, pageDotPaint)
        }
        val plusX = startX + count * spacing
        val half = 4.4f * density
        pageDotPaint.color = Color.WHITE
        pageDotPaint.alpha = 150
        canvas.drawLine(plusX - half, y, plusX + half, y, pageDotPaint)
        canvas.drawLine(plusX, y - half, plusX, y + half, pageDotPaint)
    }

    // endregion

    private companion object {
        const val EDGE_FLIP_DWELL = 380L
        const val EDGE_FLIP_INTERVAL = 420L
        const val ITEM_MENU_DELAY = 120L
    }
}
