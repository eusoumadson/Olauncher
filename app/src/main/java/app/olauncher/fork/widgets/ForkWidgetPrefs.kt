package app.olauncher.fork.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Isolates all Widget Screen preferences and serialized widget entries from upstream [app.olauncher.data.Prefs].
 */
class ForkWidgetPrefs(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    )

    var widgetScreenPosition: WidgetScreenPosition
        get() = WidgetScreenPosition.fromStorageValue(
            prefs.getString(KEY_WIDGET_SCREEN_POSITION, WidgetScreenPosition.DISABLED.name)
        )
        set(value) = prefs.edit { putString(KEY_WIDGET_SCREEN_POSITION, value.name) }

    var allowMinWidth1: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_MIN_WIDTH_1, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_MIN_WIDTH_1, value) }

    var allowMinHeight1: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_MIN_HEIGHT_1, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_MIN_HEIGHT_1, value) }

    fun getWidgets(): List<ForkWidgetEntry> =
        ForkWidgetEntryJson.decode(prefs.getString(KEY_WIDGET_ENTRIES, null))

    fun saveWidgets(entries: List<ForkWidgetEntry>) {
        prefs.edit { putString(KEY_WIDGET_ENTRIES, ForkWidgetEntryJson.encode(entries)) }
    }

    /**
     * Inserts [entry] (or replaces an existing entry with the same [ForkWidgetEntry.appWidgetId])
     * and persists the list sorted by [ForkWidgetEntry.order].
     */
    fun upsertWidget(entry: ForkWidgetEntry): List<ForkWidgetEntry> {
        val current = getWidgets().toMutableList()
        val existingIndex = current.indexOfFirst { it.appWidgetId == entry.appWidgetId }
        if (existingIndex >= 0) {
            current[existingIndex] = entry
        } else {
            val nextOrder = if (entry.order > 0) entry.order else (current.maxOfOrNull { it.order }?.plus(1) ?: 0)
            current += entry.copy(order = nextOrder)
        }
        val sorted = current.sortedBy { it.order }
        saveWidgets(sorted)
        return sorted
    }

    /**
     * Removes [appWidgetId] and compacts remaining [ForkWidgetEntry.order] indices sequentially.
     */
    fun removeWidget(appWidgetId: Int): List<ForkWidgetEntry> = removeWidgets(setOf(appWidgetId))

    /**
     * Removes all entries whose `appWidgetId` is in [appWidgetIds] and re-indexes remaining items.
     */
    fun removeWidgets(appWidgetIds: Collection<Int>): List<ForkWidgetEntry> {
        if (appWidgetIds.isEmpty()) return getWidgets()
        val removeSet = appWidgetIds.toSet()
        val updated = getWidgets()
            .filterNot { it.appWidgetId in removeSet }
            .sortedBy { it.order }
            .mapIndexed { index, entry -> entry.copy(order = index) }
        saveWidgets(updated)
        return updated
    }

    /**
     * Reorders persisted widgets to match [orderedAppWidgetIds], preserving any unlisted entries at the end,
     * and reassigns sequential [ForkWidgetEntry.order] values.
     */
    fun reorderWidgets(orderedAppWidgetIds: List<Int>): List<ForkWidgetEntry> {
        val current = getWidgets()
        if (current.isEmpty()) return emptyList()
        val byId = current.associateBy { it.appWidgetId }
        val reordered = mutableListOf<ForkWidgetEntry>()
        val seen = HashSet<Int>(current.size)

        for (id in orderedAppWidgetIds) {
            val entry = byId[id]
            if (entry != null && seen.add(id)) {
                reordered += entry.copy(order = reordered.size)
            }
        }
        for (entry in current.sortedBy { it.order }) {
            if (seen.add(entry.appWidgetId)) {
                reordered += entry.copy(order = reordered.size)
            }
        }
        saveWidgets(reordered)
        return reordered
    }

    companion object {
        private const val PREFS_FILENAME = "app.olauncher"
        private const val KEY_WIDGET_SCREEN_POSITION = "FORK_WIDGET_SCREEN_POSITION"
        private const val KEY_ALLOW_MIN_WIDTH_1 = "FORK_ALLOW_MIN_WIDTH_1"
        private const val KEY_ALLOW_MIN_HEIGHT_1 = "FORK_ALLOW_MIN_HEIGHT_1"
        private const val KEY_WIDGET_ENTRIES = "FORK_WIDGET_ENTRIES"
    }
}
