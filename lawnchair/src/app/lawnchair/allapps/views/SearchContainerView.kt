package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import app.lawnchair.allapps.OneUiAllAppsGridAdapter
import app.lawnchair.search.LawnchairSearchUiDelegate
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.LauncherAllAppsContainerView

class SearchContainerView(context: Context?, attrs: AttributeSet?) : LauncherAllAppsContainerView(context, attrs) {

    override fun createSearchUiDelegate() = LawnchairSearchUiDelegate(this)

    /**
     * Use the One UI adapter so alphabetical sections get a label row. All
     * other item types are still handled by the stock `AllAppsGridAdapter`.
     */
    override fun createAdapter(appsList: AlphabeticalAppsList<Launcher>): BaseAllAppsAdapter<Launcher> {
        return OneUiAllAppsGridAdapter(
            getContext() as Launcher,
            LayoutInflater.from(getContext()),
            appsList,
            mainAdapterProvider,
        )
    }

    /**
     * One UI keeps the app drawer search field docked to the bottom edge instead
     * of the AOSP top-aligned bar. The container then lays the header/RecyclerView
     * out from the top, and the bar floats above the last row of icons.
     */
    override fun isSearchBarDockedBottom(): Boolean = true
}
