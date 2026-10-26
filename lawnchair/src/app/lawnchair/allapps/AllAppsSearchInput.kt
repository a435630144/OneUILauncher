package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.provider.SearchRecentSuggestions
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_POINT_MARK
import android.text.method.TextKeyListener
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.subscribeBlocking
import app.lawnchair.qsb.AssistantIconView
import app.lawnchair.qsb.LawnQsbLayout.Companion.getLensIntent
import app.lawnchair.qsb.LawnQsbLayout.Companion.getSearchProvider
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.setThemedIconResource
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.theme.drawable.DrawableTokens
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.Insettable
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Themes
import com.patrykmichalik.opto.core.firstBlocking
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.launch

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs),
    Insettable,
    OnIDPChangeListener,
    SearchUiManager,
    SearchCallback<AdapterItem>,
    AllAppsStore.OnUpdateListener,
    ViewTreeObserver.OnGlobalLayoutListener {

    private lateinit var hint: TextView
    private lateinit var input: FallbackSearchInputView
    private lateinit var actionButton: ImageButton
    private lateinit var menuButton: ImageButton

    private lateinit var micIcon: AssistantIconView
    private lateinit var lensIcon: ImageButton

    private val qsbMarginTopAdjusting = resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val allAppsSearchVerticalOffset = resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset)

    private val launcher = context.launcher
    private val searchBarController = AllAppsSearchBarController()
    private val searchQueryBuilder = SpannableStringBuilder().apply {
        Selection.setSelection(this, 0)
    }

    private lateinit var apps: LawnchairAlphabeticalAppsList<*>
    private lateinit var appsView: ActivityAllAppsContainerView<*>
    private var searchAlgorithm: LawnchairSearchAlgorithm? = null

    private var focusedResultTitle = ""
    private var canShowHint = false

    private val bg = DrawableTokens.SearchInputFg.resolve(context)
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
    }
    private var bgVisible = true
    private var bgAlpha = 1f
    private val suggestionsRecent = SearchRecentSuggestions(launcher, LawnchairRecentSuggestionProvider.AUTHORITY, LawnchairRecentSuggestionProvider.MODE)
    private val prefs = PreferenceManager.getInstance(launcher)
    private val prefs2 = PreferenceManager2.getInstance(launcher)

    private var initialPaddingLeft: Int = 0
    private var initialPaddingRight: Int = 0

    // Bottom insets used to keep the docked One UI search bar above both the
    // navigation bar and the software keyboard.
    private var lastNavBottomInset: Int = 0
    private var imeBottomInset: Int = 0

    override fun onFinishInflate() {
        super.onFinishInflate()

        val wrapper = ViewCompat.requireViewById<View>(this, R.id.search_wrapper)
        wrapper.background = bg
        setupPadding()
        launcher.deviceProfile.inv.addOnChangeListener(this)
        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }

        hint = ViewCompat.requireViewById(this, R.id.hint)

        input = ViewCompat.requireViewById(this, R.id.input)

        micIcon = ViewCompat.requireViewById(this, R.id.mic_btn)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_btn)

        val shouldShowIcons = prefs2.matchHotseatQsbStyle.firstBlocking()

        val searchProvider = getSearchProvider(context, prefs2)
        val isGoogle = searchProvider == Google || searchProvider == GoogleGo || searchProvider == PixelSearch
        val supportsLens = searchProvider == Google || searchProvider == PixelSearch

        val lensIntent = getLensIntent(context)
        val voiceIntent = AssistantIconView.getVoiceIntent(searchProvider, context)

        micIcon.isVisible = shouldShowIcons && voiceIntent != null
        lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null

        actionButton = ViewCompat.requireViewById(this, R.id.action_btn)
        with(actionButton) {
            isVisible = false
            setOnClickListener {
                input.reset()
                searchAlgorithm?.doZeroStateSearch(this@AllAppsSearchInput)
                updateHint()
            }
        }

        // One UI puts the app drawer overflow menu on the trailing edge of the
        // search field. For now it opens the app drawer preferences; a One UI
        // style bottom sheet (sort / settings) can replace this later.
        menuButton = ViewCompat.requireViewById(this, R.id.menu_btn)
        menuButton.setOnClickListener {
            context.startActivity(PreferenceActivity.createIntent(context, AppDrawer))
        }

        prefs2.themedHotseatQsb.subscribeBlocking(scope = viewAttachedScope) { themed ->
            with(micIcon) {
                setIcon(isGoogle, themed)
                setOnClickListener {
                    context.startActivity(voiceIntent)
                }
            }
            with(lensIcon) {
                if (lensIntent != null) {
                    setThemedIconResource(R.drawable.ic_lens_color, themed)
                    setOnClickListener {
                        runCatching { context.startActivity(lensIntent) }
                    }
                }
            }
        }
        val currentPaddingLeft = initialPaddingLeft
        val currentPaddingRight = initialPaddingRight
        input.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                input.setHint(R.string.oneui_search_hint)

                if (input.text.toString().isEmpty()) {
                    searchAlgorithm?.doZeroStateSearch(this)
                }

                if (isDockedToBottom) {
                    // One UI keeps the search pill visible while the field is
                    // focused; only its contents change.
                    setBackgroundVisibility(true, 1f)
                } else {
                    setBackgroundVisibility(false, 0f)
                    animatePadding(currentPaddingLeft / 2, currentPaddingRight / 2)
                }
                animateHintVisibility(true)

                // Sometimes the user has to click the input bar one more time
                // for the keyboard to show.
                input.showKeyboard()
            } else {
                setBackgroundVisibility(true, 1f)
                animateHintVisibility(false)
                if (prefs.searchResulRecentSuggestion.get()) {
                    val query = editText.text.toString()
                    suggestionsRecent.saveRecentQuery(query, null)
                }

                if (!isDockedToBottom) {
                    animatePadding(currentPaddingLeft, currentPaddingRight)
                }
                focusedResultTitle = ""
                input.setHint(R.string.oneui_search_hint)
                hint.text = ""
            }
        }

        input.addTextChangedListener(
            beforeTextChanged = { _, _, _, _ ->
                hint.isInvisible = true
            },
            afterTextChanged = {
                updateHint()
                if (input.text.isNullOrEmpty()) {
                    searchAlgorithm?.doZeroStateSearch(this)
                }
                if (input.text.toString() == "/lawnchairdebug") {
                    val enableDebugMenu = prefs.enableDebugMenu
                    enableDebugMenu.set(!enableDebugMenu.get())
                    launcher.stateManager.goToState(LauncherState.NORMAL)
                }

                actionButton.isVisible = !it.isNullOrEmpty()
                micIcon.isVisible = shouldShowIcons && voiceIntent != null && it.isNullOrEmpty()
                lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null && it.isNullOrEmpty()
            },
        )

        val hide = prefs2.hideAppDrawerSearchBar.firstBlocking()
        if (hide) {
            isInvisible = true
            layoutParams.height = 0
        }
    }

    private fun setupPadding() {
        launcher.deviceProfile.let { dp ->
            val padding = dp.getAllAppsIconStartMargin(context)
            initialPaddingLeft = padding
            initialPaddingRight = padding
            setPadding(padding, paddingTop, padding, paddingBottom)
        }
    }

    private fun animateHintVisibility(visible: Boolean) {
        val targetAlpha = if (visible) 1f else 0f
        val duration = if (visible) 300L else 200L

        if (visible) {
            hint.alpha = 0f
            hint.isVisible = true
        }

        hint.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (!visible) hint.isVisible = false
            }
            .start()
    }

    private fun animatePadding(newPaddingLeft: Int, newPaddingRight: Int) {
        val currentPaddingLeft = paddingLeft
        val currentPaddingRight = paddingRight

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val leftPadding = currentPaddingLeft + (newPaddingLeft - currentPaddingLeft) * fraction
                val rightPadding = currentPaddingRight + (newPaddingRight - currentPaddingRight) * fraction
                setPadding(leftPadding.toInt(), paddingTop, rightPadding.toInt(), paddingBottom)
            }
            start()
        }
    }

    override fun setFocusedResultTitle(title: CharSequence?, sub: CharSequence?, showArrow: Boolean) {
        focusedResultTitle = title?.toString().orEmpty()
        updateHint()
    }

    override fun refreshResults() {
        onAppsUpdated()
    }

    private fun updateHint() {
        val inputString = input.text.toString()
        val inputLowerCase = inputString.lowercase(Locale.getDefault())
        val focusedLowerCase = focusedResultTitle.lowercase(Locale.getDefault())
        if (canShowHint &&
            inputLowerCase.isNotEmpty() &&
            focusedLowerCase.isNotEmpty() &&
            focusedLowerCase.matches(Regex("^[\\x00-\\x7F]*$")) &&
            focusedLowerCase.startsWith(inputLowerCase)
        ) {
            val hintColor = Themes.getAttrColor(context, android.R.attr.textColorTertiary)
            val hintText = SpannableStringBuilder(inputString)
                .append(focusedLowerCase.substring(inputLowerCase.length))
            hintText.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, inputLowerCase.length, SPAN_POINT_MARK)
            hintText.setSpan(ForegroundColorSpan(hintColor), inputLowerCase.length, hintText.length, SPAN_POINT_MARK)
            hint.text = hintText
            hint.isVisible = true
        }
    }

    override fun onGlobalLayout() {
        canShowHint = input.layout?.getEllipsisCount(0) == 0
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        appsView.appsStore?.addUpdateListener(this)
        input.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        appsView.appsStore?.removeUpdateListener(this)
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onAppsUpdated() {
        searchBarController.refreshSearchResult()
    }

    override fun initializeSearch(appsView: ActivityAllAppsContainerView<*>) {
        apps = appsView.searchResultList as LawnchairAlphabeticalAppsList<*>
        this.appsView = appsView
        val algorithm = LawnchairSearchAlgorithm.create(context)
        this.searchAlgorithm = algorithm
        searchBarController.initialize(
            algorithm,
            input,
            launcher,
            this,
        )
        input.initialize(appsView)
    }

    override fun resetSearch() {
        searchBarController.reset()
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!searchBarController.isSearchFieldFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                !Character.isWhitespace(unicodeChar) &&
                !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance().onKeyDown(input, searchQueryBuilder, event.keyCode, event)
                if (gotKey && searchQueryBuilder.isNotEmpty()) {
                    searchBarController.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, items: ArrayList<AdapterItem>?) {
        if (items != null) {
            apps.setSearchResults(items)
            notifyResultChanged()
            appsView.setSearchResults(items)
        }
    }

    override fun clearSearchResult() {
        if (apps.setSearchResults(null)) {
            notifyResultChanged()
        }

        // Clear the search query
        searchQueryBuilder.clear()
        searchQueryBuilder.clearSpans()
        Selection.setSelection(searchQueryBuilder, 0)
        appsView.onClearSearchResult()
        appsView.floatingHeaderView?.setFloatingRowsCollapsed(false)
    }

    private fun notifyResultChanged() {
        appsView.mSearchRecyclerView.onSearchResultsChanged()
    }

    /**
     * True when the search bar is docked to the bottom of the all apps surface
     * (One UI style). In that case the navigation bar inset is applied as a
     * bottom margin and the legacy "drop down from the status bar" offset is
     * skipped.
     */
    private val isDockedToBottom: Boolean
        get() = (layoutParams as? RelativeLayout.LayoutParams)
            ?.rules?.get(RelativeLayout.ALIGN_PARENT_BOTTOM) == RelativeLayout.TRUE

    override fun setInsets(insets: Rect) {
        if (isDockedToBottom) {
            lastNavBottomInset = insets.bottom
            applyDockedBottomMargin()
        } else {
            (layoutParams as MarginLayoutParams).apply {
                topMargin = if (isInvisible) {
                    insets.top - allAppsSearchVerticalOffset
                } else {
                    max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting)
                }
            }
            requestLayout()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (isDockedToBottom) {
            imeBottomInset = WindowInsetsCompat.toWindowInsetsCompat(insets)
                .getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyDockedBottomMargin()
        }
        return super.onApplyWindowInsets(insets)
    }

    /**
     * Docked mode: the view is aligned to the bottom of the all apps container,
     * which itself extends behind the navigation bar and the keyboard. Lifting
     * it by the larger of the two insets reproduces One UI's search bar sitting
     * directly above the keyboard.
     */
    private fun applyDockedBottomMargin() {
        val bottom = max(lastNavBottomInset, imeBottomInset) +
            resources.getDimensionPixelSize(R.dimen.oneui_search_bar_bottom_margin)
        (layoutParams as MarginLayoutParams).bottomMargin = bottom
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!isDockedToBottom) {
            offsetTopAndBottom(allAppsSearchVerticalOffset)
        }
    }

    override fun getEditText() = input

    override fun setBackgroundVisibility(visible: Boolean, maxAlpha: Float) {
        if (bgVisible != visible) {
            bgVisible = visible
            bgAlpha = maxAlpha
            if (visible) {
                bgAlphaAnimator.start()
            } else {
                bgAlphaAnimator.reverse()
            }
        } else if (bgAlpha != maxAlpha && !bgAlphaAnimator.isRunning && visible) {
            bgAlpha = maxAlpha
            bgAlphaAnimator.setCurrentFraction(maxAlpha)
            updateBgAlpha()
        }
    }

    override fun getBackgroundVisibility(): Boolean {
        return bgVisible
    }

    private fun updateBgAlpha() {
        val fraction = bgAlphaAnimator.animatedFraction
        bg.alpha = (Utilities.mapRange(fraction, 0f, bgAlpha) * 255).toInt()
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        setupPadding()
        invalidate()
        requestLayout()
    }
}
