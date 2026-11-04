package app.lawnchair.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextClock
import kotlin.math.roundToInt
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.oneui.OneUiClockWidgetController
import com.android.launcher3.R

/**
 * Fallback One UI style clock / date block for the first workspace page.
 *
 * The real clock is a home screen app widget (see
 * [app.lawnchair.oneui.OneUiClockWidgetProvider]). This view stays in the
 * layout as a fallback: it is hidden as soon as the widget has been placed and
 * comes back if the widget is removed again, so the home screen never ends up
 * without a clock.
 *
 * It is anchored to the left edge of page 0 and shifts horizontally with the
 * workspace scroll, so swiping to another page carries it off screen and
 * swiping back brings it in again. The offset is read once per frame in an
 * [ViewTreeObserver.OnPreDrawListener] instead of a scroll listener so it also
 * works when the workspace is restored to a page before the view is attached,
 * and it stays in sync with the frame that is about to be drawn.
 */
class OneUiClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var lastScrollX = Int.MIN_VALUE
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var preDrawObserver: ViewTreeObserver? = null

    private val widgetStateListener: () -> Unit = { syncWidgetVisibility() }
    private val clockStyleListener = app.lawnchair.preferences.PreferenceChangeListener { applyClockStyle() }

    private val timeClock: TextClock
        get() = requireNotNull(findViewById<TextClock>(R.id.oneui_clock_time))
    private val dateClock: TextClock
        get() = requireNotNull(findViewById<TextClock>(R.id.oneui_clock_date))

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    /**
     * Applies the four One UI clock styles to the fallback overlay, mirroring the
     * RemoteViews layouts used by the app widget (see OneUiClockWidgetProvider).
     */
    private fun applyClockStyle() {
        val style = app.lawnchair.preferences.PreferenceManager
            .getInstance(context).clockStyle.get()
        when (style) {
            0 -> { // default: large time + date
                orientation = VERTICAL
                timeClock.textSize = 44f
                timeClock.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                dateClock.visibility = VISIBLE
                dateClock.textSize = 14f
                (timeClock.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
                gravity = Gravity.START
            }
            1 -> { // compact: time + date on one line
                orientation = HORIZONTAL
                timeClock.textSize = 30f
                dateClock.visibility = VISIBLE
                dateClock.textSize = 13f
                (dateClock.layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(10f)
                gravity = Gravity.CENTER_VERTICAL
            }
            2 -> { // time only
                orientation = VERTICAL
                timeClock.textSize = 64f
                timeClock.typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
                dateClock.visibility = GONE
                gravity = Gravity.START
            }
            else -> { // centered: date above, time below
                orientation = VERTICAL
                timeClock.textSize = 38f
                dateClock.visibility = VISIBLE
                dateClock.textSize = 13f
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        app.lawnchair.preferences.PreferenceManager.getInstance(context)
            .clockStyle.addListener(clockStyleListener)
        applyClockStyle()
        OneUiClockWidgetController.addWidgetStateListener(widgetStateListener)
        syncWidgetVisibility()

        val ws = rootView.findViewById<View>(R.id.workspace) ?: return
        syncTranslation(ws.scrollX)

        preDrawObserver = viewTreeObserver.also { observer ->
            preDrawListener = ViewTreeObserver.OnPreDrawListener {
                val scrollX = ws.scrollX
                if (scrollX != lastScrollX) {
                    syncTranslation(scrollX)
                }
                true
            }.also(observer::addOnPreDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        app.lawnchair.preferences.PreferenceManager.getInstance(context)
            .clockStyle.removeListener(clockStyleListener)
        OneUiClockWidgetController.removeWidgetStateListener(widgetStateListener)
        preDrawListener?.let { preDrawObserver?.removeOnPreDrawListener(it) }
        preDrawListener = null
        preDrawObserver = null
        super.onDetachedFromWindow()
    }

    private fun syncTranslation(scrollX: Int) {
        lastScrollX = scrollX
        translationX = -scrollX.toFloat()
    }

    /**
     * The overlay clock steps aside once the real app widget has been placed,
     * and comes back if the widget is removed again.
     */
    private fun syncWidgetVisibility() {
        val hasWidget = OneUiClockWidgetController.hasBoundWidget(context)
        val target = if (hasWidget) View.GONE else View.VISIBLE
        if (visibility != target) {
            visibility = target
        }
    }
}
