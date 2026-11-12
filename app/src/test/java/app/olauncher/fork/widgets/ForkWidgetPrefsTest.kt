package app.olauncher.fork.widgets

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkWidgetPrefsTest {

    @Test
    fun `default preferences match disabled widget screen and 2x2 minimum spans`() {
        val prefs = ForkWidgetPrefs(InMemorySharedPreferences())

        assertEquals(WidgetScreenPosition.DISABLED, prefs.widgetScreenPosition)
        assertFalse(prefs.widgetScreenPosition.isEnabled)
        assertFalse(prefs.allowMinWidth1)
        assertFalse(prefs.allowMinHeight1)
        assertTrue(prefs.getWidgets().isEmpty())
    }

    @Test
    fun `position and minimum 1x flags persist accurately`() {
        val backing = InMemorySharedPreferences()
        val prefs = ForkWidgetPrefs(backing)

        prefs.widgetScreenPosition = WidgetScreenPosition.LEFT
        prefs.allowMinWidth1 = true
        prefs.allowMinHeight1 = true

        val reloaded = ForkWidgetPrefs(backing)
        assertEquals(WidgetScreenPosition.LEFT, reloaded.widgetScreenPosition)
        assertTrue(reloaded.widgetScreenPosition.isEnabled)
        assertTrue(reloaded.allowMinWidth1)
        assertTrue(reloaded.allowMinHeight1)

        reloaded.widgetScreenPosition = WidgetScreenPosition.RIGHT
        assertEquals(WidgetScreenPosition.RIGHT, ForkWidgetPrefs(backing).widgetScreenPosition)
    }

    @Test
    fun `json round trip preserves order appWidgetId and all span fields`() {
        val entries = listOf(
            ForkWidgetEntry(
                appWidgetId = 42,
                colSpan = 4,
                rowSpan = 2,
                order = 0,
                minColSpan = 2,
                minRowSpan = 1,
                resizeMode = ForkWidgetEntry.RESIZE_BOTH,
            ),
            ForkWidgetEntry(
                appWidgetId = 105,
                colSpan = 2,
                rowSpan = 3,
                order = 1,
                minColSpan = 1,
                minRowSpan = 2,
                resizeMode = ForkWidgetEntry.RESIZE_NONE,
            ),
        )

        val encoded = ForkWidgetEntryJson.encode(entries)
        val decoded = ForkWidgetEntryJson.decode(encoded)

        assertEquals(entries, decoded)
    }

    @Test
    fun `json decoder tolerates whitespace reordered keys unknown keys and corrupted input`() {
        val pretty = """
            [
              {
                "extra": "ignored",
                "nested": [1, 2, {"a": true}],
                "resizeMode": 0,
                "order": 3,
                "rowSpan": 4,
                "colSpan": 2,
                "appWidgetId": 77
              }
            ]
        """.trimIndent()

        val decoded = ForkWidgetEntryJson.decode(pretty)
        assertEquals(
            listOf(
                ForkWidgetEntry(
                    appWidgetId = 77,
                    colSpan = 2,
                    rowSpan = 4,
                    order = 3,
                    minColSpan = ForkWidgetEntry.DEFAULT_MIN_SPAN,
                    minRowSpan = ForkWidgetEntry.DEFAULT_MIN_SPAN,
                    resizeMode = ForkWidgetEntry.RESIZE_NONE,
                )
            ),
            decoded,
        )

        assertTrue(ForkWidgetEntryJson.decode(null).isEmpty())
        assertTrue(ForkWidgetEntryJson.decode("").isEmpty())
        assertTrue(ForkWidgetEntryJson.decode("[]").isEmpty())
        assertTrue(ForkWidgetEntryJson.decode("{broken json").isEmpty())
    }

    @Test
    fun `save remove and reorder entries maintain consistent order and data`() {
        val backing = InMemorySharedPreferences()
        val prefs = ForkWidgetPrefs(backing)

        val first = ForkWidgetEntry(appWidgetId = 10, colSpan = 2, rowSpan = 2, order = 0)
        val second = ForkWidgetEntry(appWidgetId = 20, colSpan = 4, rowSpan = 2, order = 1)
        val third = ForkWidgetEntry(appWidgetId = 30, colSpan = 2, rowSpan = 3, order = 2)
        prefs.saveWidgets(listOf(first, second, third))

        val reordered = prefs.reorderWidgets(listOf(30, 10, 20))
        assertEquals(listOf(30, 10, 20), reordered.map { it.appWidgetId })
        assertEquals(listOf(0, 1, 2), reordered.map { it.order })
        assertEquals(reordered, ForkWidgetPrefs(backing).getWidgets())

        val afterRemove = prefs.removeWidget(10)
        assertEquals(listOf(30, 20), afterRemove.map { it.appWidgetId })
        assertEquals(listOf(0, 1), afterRemove.map { it.order })
        assertEquals(4, afterRemove[1].colSpan)
        assertEquals(afterRemove, ForkWidgetPrefs(backing).getWidgets())
    }

    @Test
    fun `upsert replaces existing widget id without duplicating and appends new ones`() {
        val prefs = ForkWidgetPrefs(InMemorySharedPreferences())
        prefs.upsertWidget(ForkWidgetEntry(appWidgetId = 1, colSpan = 2, rowSpan = 2))
        prefs.upsertWidget(ForkWidgetEntry(appWidgetId = 2, colSpan = 3, rowSpan = 2))
        prefs.upsertWidget(ForkWidgetEntry(appWidgetId = 1, colSpan = 4, rowSpan = 3, order = 0))

        val stored = prefs.getWidgets()
        assertEquals(2, stored.size)
        assertEquals(4, stored[0].colSpan)
        assertEquals(3, stored[0].rowSpan)
        assertEquals(2, stored[1].appWidgetId)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            ((data[key] as? Set<String>)?.toMutableSet()) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
                pending.clear()
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) target.clear()
                for ((k, v) in pending) {
                    if (v == null) target.remove(k) else target[k] = v
                }
            }
        }
    }
}
