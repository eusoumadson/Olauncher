package app.olauncher.fork.widgets

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkWidgetHostManagerTest {

    @Test
    fun `host id constant matches fork specification`() {
        assertEquals(1024, ForkWidgetHostManager.FORK_WIDGET_HOST_ID)
    }

    @Test
    fun `shouldStartListening and shouldStopListening enforce idempotent state transitions`() {
        assertFalse(
            ForkWidgetHostCoordinator.shouldStartListening(
                position = WidgetScreenPosition.DISABLED,
                hasWidgets = true,
                currentlyListening = false,
            )
        )
        assertFalse(
            ForkWidgetHostCoordinator.shouldStartListening(
                position = WidgetScreenPosition.LEFT,
                hasWidgets = false,
                currentlyListening = false,
            )
        )
        assertFalse(
            ForkWidgetHostCoordinator.shouldStartListening(
                position = WidgetScreenPosition.RIGHT,
                hasWidgets = true,
                currentlyListening = true,
            )
        )
        assertTrue(
            ForkWidgetHostCoordinator.shouldStartListening(
                position = WidgetScreenPosition.LEFT,
                hasWidgets = true,
                currentlyListening = false,
            )
        )
        assertTrue(
            ForkWidgetHostCoordinator.shouldStartListening(
                position = WidgetScreenPosition.RIGHT,
                hasWidgets = true,
                currentlyListening = false,
            )
        )

        assertFalse(ForkWidgetHostCoordinator.shouldStopListening(currentlyListening = false))
        assertTrue(ForkWidgetHostCoordinator.shouldStopListening(currentlyListening = true))
    }

    @Test
    fun `onStart does not start listening when disabled or empty and onStop is no-op without start`() {
        val prefs = ForkWidgetPrefs(FakeSharedPreferences())
        val backend = FakeWidgetHostBackend(validIds = setOf(10))
        val coordinator = ForkWidgetHostCoordinator(prefs, backend)

        // Disabled with widgets
        prefs.widgetScreenPosition = WidgetScreenPosition.DISABLED
        prefs.saveWidgets(listOf(ForkWidgetEntry(appWidgetId = 10)))
        coordinator.onStart()
        coordinator.onStop()
        assertEquals(0, backend.startCalls)
        assertEquals(0, backend.stopCalls)
        assertFalse(coordinator.isListening)

        // Enabled without widgets
        prefs.widgetScreenPosition = WidgetScreenPosition.LEFT
        prefs.saveWidgets(emptyList())
        coordinator.onStart()
        coordinator.onStop()
        assertEquals(0, backend.startCalls)
        assertEquals(0, backend.stopCalls)
        assertFalse(coordinator.isListening)
    }

    @Test
    fun `onStart and onStop execute startListening and stopListening once when active with widgets`() {
        val prefs = ForkWidgetPrefs(FakeSharedPreferences())
        prefs.widgetScreenPosition = WidgetScreenPosition.RIGHT
        prefs.saveWidgets(listOf(ForkWidgetEntry(appWidgetId = 10, order = 0)))

        val backend = FakeWidgetHostBackend(validIds = setOf(10))
        val coordinator = ForkWidgetHostCoordinator(prefs, backend)

        coordinator.onStart()
        coordinator.onStart()
        assertTrue(coordinator.isListening)
        assertEquals(1, backend.startCalls)
        assertEquals(0, backend.stopCalls)

        coordinator.onStop()
        coordinator.onStop()
        assertFalse(coordinator.isListening)
        assertEquals(1, backend.startCalls)
        assertEquals(1, backend.stopCalls)
    }

    @Test
    fun `onStart removes orphan widget ids calls deleteAppWidgetId and skips listening if none survive`() {
        val prefs = ForkWidgetPrefs(FakeSharedPreferences())
        prefs.widgetScreenPosition = WidgetScreenPosition.LEFT
        prefs.saveWidgets(
            listOf(
                ForkWidgetEntry(appWidgetId = 11, colSpan = 2, rowSpan = 2, order = 0),
                ForkWidgetEntry(appWidgetId = 22, colSpan = 4, rowSpan = 2, order = 1),
                ForkWidgetEntry(appWidgetId = 33, colSpan = 2, rowSpan = 3, order = 2),
            )
        )

        val backend = FakeWidgetHostBackend(validIds = setOf(22))
        val coordinator = ForkWidgetHostCoordinator(prefs, backend)

        val surviving = coordinator.onStart()

        assertEquals(listOf(11, 33), backend.deletedIds)
        assertEquals(listOf(22), surviving.map { it.appWidgetId })
        assertEquals(listOf(0), surviving.map { it.order })
        assertEquals(surviving, prefs.getWidgets())
        assertTrue(coordinator.isListening)
        assertEquals(1, backend.startCalls)

        // Now all remaining widgets become orphans too -> cleanup must happen and listening must stop/not start
        coordinator.onStop()
        backend.validIds = emptySet()
        val afterAllOrphans = coordinator.onStart()

        assertTrue(afterAllOrphans.isEmpty())
        assertEquals(listOf(11, 33, 22), backend.deletedIds)
        assertTrue(prefs.getWidgets().isEmpty())
        assertFalse(coordinator.isListening)
        assertEquals(1, backend.startCalls)
    }

    private class FakeWidgetHostBackend(
        var validIds: Set<Int>,
    ) : ForkWidgetHostBackend {
        var startCalls = 0
        var stopCalls = 0
        val deletedIds = mutableListOf<Int>()
        private var nextId = 100

        override fun startListening() {
            startCalls++
        }

        override fun stopListening() {
            stopCalls++
        }

        override fun allocateAppWidgetId(): Int = nextId++

        override fun deleteAppWidgetId(appWidgetId: Int) {
            deletedIds += appWidgetId
        }

        override fun hasProviderInfo(appWidgetId: Int): Boolean = appWidgetId in validIds
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
