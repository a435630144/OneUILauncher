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

package app.lawnchair.allapps

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.AllAppsGridAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.search.SearchAdapterProvider

/**
 * Section row shown between alphabetical groups in the app drawer.
 *
 * The view type deliberately includes [BaseAllAppsAdapter.VIEW_TYPE_ALL_APPS_DIVIDER]
 * so [AlphabeticalAppsList] resets its row counter at every section. The custom
 * adapter below intercepts the exact type before the AOSP divider handling.
 */
class OneUiSectionItem(val sectionName: String) : AdapterItem(VIEW_TYPE) {

    override fun isContentSame(other: AdapterItem?): Boolean =
        other is OneUiSectionItem && other.sectionName == sectionName

    companion object {
        const val VIEW_TYPE = (1 shl 11) or BaseAllAppsAdapter.VIEW_TYPE_ALL_APPS_DIVIDER
    }
}

/**
 * Add the One UI alphabetical section headers to the stock all-apps grid.
 *
 * Everything else (icons, folders, work profile cards, search results) still
 * goes through [AllAppsGridAdapter].
 */
class OneUiAllAppsGridAdapter(
    launcher: Launcher,
    inflater: LayoutInflater,
    apps: AlphabeticalAppsList<Launcher>,
    adapterProvider: SearchAdapterProvider<*>,
) : AllAppsGridAdapter<Launcher>(launcher, inflater, apps, adapterProvider) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseAllAppsAdapter.ViewHolder {
        if (viewType == OneUiSectionItem.VIEW_TYPE) {
            return BaseAllAppsAdapter.ViewHolder(
                mLayoutInflater.inflate(R.layout.oneui_all_apps_section_header, parent, false),
            )
        }
        return super.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: BaseAllAppsAdapter.ViewHolder, position: Int) {
        if (holder.itemViewType == OneUiSectionItem.VIEW_TYPE) {
            val item = mApps.adapterItems[position] as OneUiSectionItem
            (holder.itemView as TextView).text = item.sectionName
            return
        }
        super.onBindViewHolder(holder, position)
    }
}
