package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import com.android.launcher3.R

/**
 * One UI style clock / date block shown at the top of the first workspace page.
 *
 * This is still a plain view rather than an app widget (see
 * docs/refactor/HANDOVER.md, "时钟 AppWidget"), but it already behaves like a
 * page item: it is anchored to the left edge of page 0 and shifts horizontally
 * with the workspace scroll, so swiping to another page carries it off screen
 * and swiping back brings it in again.
 *
 * The offset is read once per frame in an [ViewTreeObserver.OnPreDrawListener]
 * instead of a scroll listener so it also works when the workspace is restored
 * to a page before the view is attached, and it stays in sync with the frame
 * that is about to be drawn.
 *
 * Once the real widget host lands this class can be replaced by an
 * [android.appwidget.AppWidgetHostView] without touching the launcher layout.
 */
class OneUiClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var lastScrollX = Int.MIN_VALUE
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var preDrawObserver: ViewTreeObserver? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        preDrawListener?.let { preDrawObserver?.removeOnPreDrawListener(it) }
        preDrawListener = null
        preDrawObserver = null
        super.onDetachedFromWindow()
    }

    private fun syncTranslation(scrollX: Int) {
        lastScrollX = scrollX
        translationX = -scrollX.toFloat()
    }
}
