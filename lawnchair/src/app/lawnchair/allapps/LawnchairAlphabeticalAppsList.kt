package app.lawnchair.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.model.FolderOrderUtils
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.AlphabeticalAppsList.FastScrollSectionInfo
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel: FolderViewModel by (context as ComponentActivity).viewModels()
    private var folderList = mutableListOf<FolderInfo>()
    private val filteredList = mutableListOf<AppInfo>()

    private val folderOrder = FolderOrderUtils.stringToIntList(prefs.drawerListOrder.get())

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
    }

    private fun observeFolders() {
        viewModel.foldersLiveData.observe(context as LifecycleOwner) { folders ->
            folderList = folders
                .sortedBy { folderOrder.indexOf(it.id) }
                .toMutableList()
            updateAdapterItems()
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        if (appList.isNullOrEmpty()) return startPosition
        val drawerListDefault = prefs.drawerList.get()
        val customSort = prefs.drawerSortMode.get() == SORT_CUSTOM
        filteredList.clear()
        var position = startPosition

        // Show app drawer folders only on main profile, to prevent state complexity
        if (isWorkOrPrivateSpace(appList)) return super.addAppsWithSections(appList, position)

        if (!drawerListDefault) {
            val validApps = appList.mapNotNull { it }
            val finalCategorizedApps = categorizeAppsWithSystemAndGoogle(validApps, context)

            finalCategorizedApps.forEach { (category, apps) ->
                if (apps.size == 1) {
                    mAdapterItems.add(AdapterItem.asApp(apps.first()))
                } else {
                    val folderInfo = FolderInfo().apply {
                        title = category
                        apps.forEach { add(it) }
                    }
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                }
                position++
            }
        } else {
            folderList.forEach { folder ->
                if (folder.getContents().size > 1) {
                    val folderInfo = FolderInfo()
                    folderInfo.title = folder.title
                    mAdapterItems.add(AdapterItem.asFolder(folderInfo))
                    folder.getContents().forEach { app ->
                        (appsStore.getApp(app.componentKey) as? AppInfo)?.let {
                            folderInfo.add(it)
                            if (prefs.folderApps.get()) filteredList.add(it)
                        }
                    }
                }
                position++
            }
            val orderedApps = if (customSort) applyCustomOrder(appList.filterNotNull()) else appList
            val remainingApps = orderedApps.filterNotNull().filterNot { app ->
                filteredList.contains(app) && prefs.folderApps.get()
            }
            position = when {
                customSort -> addAppsInCustomOrder(remainingApps, position)
                prefs.drawerSectionHeaders.get() -> addAppsWithAlphabetSections(remainingApps, position)
                else -> super.addAppsWithSections(remainingApps, position)
            }
        }

        return position
    }

    /**
     * Applies the user's custom order, keeping apps that are not in the saved
     * list (new installs) at the end in alphabetical order.
     */
    private fun applyCustomOrder(appList: List<AppInfo>): List<AppInfo> {
        val order = prefs.drawerCustomOrder.get()
        if (order.isBlank()) return appList

        val index = HashMap<String, Int>()
        order.lineSequence().forEachIndexed { i, key ->
            if (key.isNotBlank()) index.putIfAbsent(key, i)
        }
        if (index.isEmpty()) return appList

        return appList.sortedBy { app ->
            index[app.toComponentKey().toString()] ?: Int.MAX_VALUE
        }
    }

    /**
     * Adds apps in custom order as one continuous grid: no alphabet headers and
     * no letter sections. One empty fast-scroll section per row keeps the index
     * bar usable (it scrolls proportionally) while hiding the letter bubble.
     */
    private fun addAppsInCustomOrder(appList: List<AppInfo>, startPosition: Int): Int {
        val columns = context.deviceProfile.numShownAllAppsColumns.coerceAtLeast(1)
        var position = startPosition

        appList.forEachIndexed { index, app ->
            if (index % columns == 0) {
                fastScrollerSections.add(FastScrollSectionInfo("", position))
            }
            mAdapterItems.add(AdapterItem.asApp(app))
            position++
        }
        return position
    }

    /**
     * Adds the remaining alphabetical apps with a One UI section label before
     * each new initial. Each group still goes through the AOSP implementation
     * so the fast-scroller section positions stay correct.
     */
    private fun addAppsWithAlphabetSections(appList: List<AppInfo>, startPosition: Int): Int {
        if (appList.isEmpty()) return startPosition

        var position = startPosition
        var lastSection: String? = null
        var groupStart = 0

        appList.forEachIndexed { index, app ->
            val section = app.sectionName.ifBlank { "#" }
            if (section != lastSection) {
                if (lastSection != null) {
                    position = super.addAppsWithSections(appList.subList(groupStart, index), position)
                }
                mAdapterItems.add(OneUiSectionItem(section))
                position++
                lastSection = section
                groupStart = index
            }
        }
        position = super.addAppsWithSections(appList.subList(groupStart, appList.size), position)

        return position
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }

    companion object {
        const val SORT_ALPHABETICAL = "alphabetical"
        const val SORT_CUSTOM = "custom"
    }

    /**
     * Moves [dragged] next to [target] in the custom drawer order and refreshes the list.
     *
     * @return false when custom sorting is not active or the apps cannot be found.
     */
    fun moveAppInCustomOrder(dragged: AppInfo, target: AppInfo, insertAfter: Boolean): Boolean {
        if (prefs.drawerSortMode.get() != SORT_CUSTOM) return false
        val draggedKey = dragged.toComponentKey().toString()
        val targetKey = target.toComponentKey().toString()
        if (draggedKey == targetKey) return false

        val keys = adapterItems
            .asSequence()
            .filter { it.viewType == BaseAllAppsAdapter.VIEW_TYPE_ICON }
            .mapNotNull { it.itemInfo?.toComponentKey()?.toString() }
            .toMutableList()
        val from = keys.indexOf(draggedKey)
        if (from < 0) return false
        keys.removeAt(from)
        val targetIndex = keys.indexOf(targetKey)
        if (targetIndex < 0) return false

        keys.add(if (insertAfter) targetIndex + 1 else targetIndex, draggedKey)
        prefs.drawerCustomOrder.set(keys.joinToString("\n"))
        onAppsUpdated()
        return true
    }
}
