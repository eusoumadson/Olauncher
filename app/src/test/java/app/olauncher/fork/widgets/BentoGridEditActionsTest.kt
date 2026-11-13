package app.olauncher.fork.widgets

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BentoGridEditActionsTest {

    @Test
    fun `drag and drop reorder updates order sequentially and persists`() {
        val prefs = ForkWidgetPrefs(FakeSharedPreferences())
        val initial = listOf(
            ForkWidgetEntry(appWidgetId = 1, colSpan = 2, rowSpan = 2, order = 0),
            ForkWidgetEntry(appWidgetId = 2, colSpan = 2, rowSpan = 2, order = 1),
            ForkWidgetEntry(appWidgetId = 3, colSpan = 4, rowSpan = 2, order = 2),
        )
        prefs.saveWidgets(initial)

        val reordered = BentoGridEditActions.reorderByDrop(
            entries = initial,
            draggedAppWidgetId = 3,
            targetAppWidgetId = 1,
        )
        assertEquals(listOf(3, 1, 2), reordered.map { it.appWidgetId })
        assertEquals(listOf(0, 1, 2), reordered.map { it.order })

        val persisted = BentoGridEditActions.persistOrderAndSpans(reordered, prefs)
        assertEquals(listOf(3, 1, 2), persisted.map { it.appWidgetId })
        assertEquals(persisted, prefs.getWidgets())
    }

    @Test
    fun `findDropTargetWidgetId locates target widget under drop coordinates`() {
        val plan = BentoGridLayoutPlanner.plan(
            entries = listOf(
                WidgetPlacementInput(appWidgetId = 1, requestedColSpan = 2, requestedRowSpan = 2),
                WidgetPlacementInput(appWidgetId = 2, requestedColSpan = 2, requestedRowSpan = 2),
            ),
            widthDp = 360,
        )

        // Cell size = 100px, gap = 10px, padding = 0 -> widget 2 occupies x=[220..430], y=[0..210]
        val hit = BentoGridEditActions.findDropTargetWidgetId(
            placements = plan.placements,
            draggedAppWidgetId = 1,
            pointerX = 300f,
            pointerY = 100f,
            paddingLeft = 0f,
            paddingTop = 0f,
            cellSizePx = 100f,
            gapPx = 10f,
        )
        assertEquals(2, hit)
    }

    @Test
    fun `resizeWidget clamps minimum 2x2 vs 1x and blocks RESIZE_NONE`() {
        val entries = listOf(
            ForkWidgetEntry(
                appWidgetId = 10,
                colSpan = 3,
                rowSpan = 3,
                order = 0,
                minColSpan = 1,
                minRowSpan = 1,
                resizeMode = ForkWidgetEntry.RESIZE_BOTH,
            ),
            ForkWidgetEntry(
                appWidgetId = 20,
                colSpan = 2,
                rowSpan = 2,
                order = 1,
                minColSpan = 2,
                minRowSpan = 2,
                resizeMode = ForkWidgetEntry.RESIZE_NONE,
            ),
            ForkWidgetEntry(
                appWidgetId = 30,
                colSpan = 2,
                rowSpan = 2,
                order = 2,
                minColSpan = 1,
                minRowSpan = 1,
                resizeMode = ForkWidgetEntry.RESIZE_HORIZONTAL,
            ),
        )

        // Default floor (allowMinWidth1=false, allowMinHeight1=false) clamps 1x1 request to 2x2
        val clampedDefault = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = 10,
            requestedColSpan = 1,
            requestedRowSpan = 1,
            columnCount = 4,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(2, clampedDefault[0].colSpan)
        assertEquals(2, clampedDefault[0].rowSpan)

        // Upper clamp to columnCount
        val clampedMax = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = 10,
            requestedColSpan = 8,
            requestedRowSpan = 4,
            columnCount = 4,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(4, clampedMax[0].colSpan)
        assertEquals(4, clampedMax[0].rowSpan)

        // With 1x toggles enabled, 1x1 is allowed
        val allow1x = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = 10,
            requestedColSpan = 1,
            requestedRowSpan = 1,
            columnCount = 4,
            allowMinWidth1 = true,
            allowMinHeight1 = true,
        )
        assertEquals(1, allow1x[0].colSpan)
        assertEquals(1, allow1x[0].rowSpan)

        // RESIZE_NONE ignores resize requests completely
        val fixedUnchanged = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = 20,
            requestedColSpan = 4,
            requestedRowSpan = 4,
            columnCount = 4,
            allowMinWidth1 = true,
            allowMinHeight1 = true,
        )
        assertEquals(2, fixedUnchanged[1].colSpan)
        assertEquals(2, fixedUnchanged[1].rowSpan)

        // RESIZE_HORIZONTAL allows width change but locks height
        val horizontalOnly = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = 30,
            requestedColSpan = 4,
            requestedRowSpan = 5,
            columnCount = 4,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(4, horizontalOnly[2].colSpan)
        assertEquals(2, horizontalOnly[2].rowSpan)
    }

    @Test
    fun `removeWidgetAndPersist deletes host appWidgetId and compacts persisted order`() {
        val prefs = ForkWidgetPrefs(FakeSharedPreferences())
        prefs.saveWidgets(
            listOf(
                ForkWidgetEntry(appWidgetId = 10, colSpan = 2, rowSpan = 2, order = 0),
                ForkWidgetEntry(appWidgetId = 20, colSpan = 2, rowSpan = 2, order = 1),
                ForkWidgetEntry(appWidgetId = 30, colSpan = 4, rowSpan = 2, order = 2),
            )
        )
        val deletedIds = mutableListOf<Int>()

        val remaining = BentoGridEditActions.removeWidgetAndPersist(
            appWidgetId = 20,
            prefs = prefs,
            deleteHostWidgetId = { deletedIds += it },
        )

        assertEquals(listOf(20), deletedIds)
        assertEquals(listOf(10, 30), remaining.map { it.appWidgetId })
        assertEquals(listOf(0, 1), remaining.map { it.order })
        assertEquals(remaining, prefs.getWidgets())

        val afterAllRemoved = BentoGridEditActions.removeWidgetAndPersist(10, prefs) { deletedIds += it }
            .let { BentoGridEditActions.removeWidgetAndPersist(30, prefs) { deletedIds += it } }
        assertTrue(afterAllRemoved.isEmpty())
        assertTrue(prefs.getWidgets().isEmpty())
        assertEquals(listOf(20, 10, 30), deletedIds)
    }

    private class FakeSharedPreferences : SharedPreferences {
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
