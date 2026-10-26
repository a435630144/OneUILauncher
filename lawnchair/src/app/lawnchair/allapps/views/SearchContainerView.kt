package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import app.lawnchair.search.LawnchairSearchUiDelegate
import com.android.launcher3.allapps.LauncherAllAppsContainerView

class SearchContainerView(context: Context?, attrs: AttributeSet?) : LauncherAllAppsContainerView(context, attrs) {

    override fun createSearchUiDelegate() = LawnchairSearchUiDelegate(this)

    /**
     * One UI keeps the app drawer search field docked to the bottom edge instead
     * of the AOSP top-aligned bar. The container then lays the header/RecyclerView
     * out from the top, and the bar floats above the last row of icons.
     */
    override fun isSearchBarDockedBottom(): Boolean = true
}
