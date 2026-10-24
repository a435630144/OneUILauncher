package com.example.oneuidesktop

import org.json.JSONArray
import org.json.JSONObject

internal const val GRID_COLS = 4
internal const val GRID_ROWS = 5
internal const val CELLS_PER_PAGE = GRID_COLS * GRID_ROWS

/** 网格规格：竖屏 4×5，横屏 6×4（One UI 横屏加列） */
data class GridSpec(val cols: Int, val rows: Int) {
    val perPage: Int get() = cols * rows
}

fun gridSpec(isLandscape: Boolean): GridSpec =
    if (isLandscape) GridSpec(6, 4) else GridSpec(4, 5)

/** 主屏条目：单个应用或文件夹 */
sealed interface HomeItem {
    val id: String
}

data class HomeApp(override val id: String) : HomeItem

data class HomeFolder(
    override val id: String,
    val name: String,
    val members: List<String>,
) : HomeItem

data class HomeLayout(val pages: List<List<HomeItem>>, val dock: List<String>)

fun defaultHomeLayout(appKeys: List<String>): HomeLayout {
    val dock = appKeys.take(4)
    val page = appKeys.drop(4).take(CELLS_PER_PAGE).map { HomeApp(it) }
    return HomeLayout(listOf(page), dock)
}

fun HomeLayout.toJson(): String {
    fun itemJson(item: HomeItem): JSONObject = when (item) {
        is HomeApp -> JSONObject().put("t", "a").put("id", item.id)
        is HomeFolder -> JSONObject().put("t", "f").put("id", item.id)
            .put("name", item.name).put("m", JSONArray(item.members))
    }
    val pagesArr = JSONArray()
    for (page in pages) {
        val arr = JSONArray()
        for (item in page) arr.put(itemJson(item))
        pagesArr.put(arr)
    }
    return JSONObject().put("pages", pagesArr).put("dock", JSONArray(dock)).toString()
}

fun parseHomeLayout(raw: String): HomeLayout? = try {
    val root = JSONObject(raw)
    val pages = mutableListOf<List<HomeItem>>()
    val pagesArr = root.getJSONArray("pages")
    for (i in 0 until pagesArr.length()) {
        val arr = pagesArr.getJSONArray(i)
        val page = mutableListOf<HomeItem>()
        for (j in 0 until arr.length()) {
            val obj = arr.getJSONObject(j)
            val item: HomeItem? = when (obj.getString("t")) {
                "a" -> HomeApp(obj.getString("id"))
                "f" -> HomeFolder(
                    id = obj.getString("id"),
                    name = obj.optString("name", "文件夹"),
                    members = obj.optJSONArray("m")?.let { m ->
                        List(m.length()) { k -> m.getString(k) }
                    } ?: emptyList(),
                )
                else -> null
            }
            if (item != null) page += item
        }
        pages += page
    }
    HomeLayout(pages, root.optJSONArray("dock")?.let { d -> List(d.length()) { d.getString(it) } } ?: emptyList())
} catch (_: Exception) {
    null
}
