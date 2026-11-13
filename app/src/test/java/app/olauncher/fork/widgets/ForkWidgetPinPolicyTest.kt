package app.olauncher.fork.widgets

import android.appwidget.AppWidgetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkWidgetPinPolicyTest {

    @Test
    fun `canPinExternalWidget allows LEFT and RIGHT and rejects DISABLED`() {
        assertFalse(ForkWidgetPinPolicy.canPinExternalWidget(WidgetScreenPosition.DISABLED))
        assertTrue(ForkWidgetPinPolicy.canPinExternalWidget(WidgetScreenPosition.LEFT))
        assertTrue(ForkWidgetPinPolicy.canPinExternalWidget(WidgetScreenPosition.RIGHT))
    }

    @Test
    fun `calculateInitialEntry enforces 2x2 default floor when 1x toggles are disabled`() {
        val entry = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 7,
            minWidthDp = 40,
            minHeightDp = 40,
            minResizeWidthDp = 40,
            minResizeHeightDp = 40,
            resizeMode = ForkWidgetEntry.RESIZE_BOTH,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
            order = 3,
        )

        assertEquals(7, entry.appWidgetId)
        assertEquals(2, entry.colSpan)
        assertEquals(2, entry.rowSpan)
        assertEquals(1, entry.minColSpan)
        assertEquals(1, entry.minRowSpan)
        assertEquals(ForkWidgetEntry.RESIZE_BOTH, entry.resizeMode)
        assertEquals(3, entry.order)
    }

    @Test
    fun `calculateInitialEntry allows 1x spans when 1x toggles are enabled`() {
        val entry = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 12,
            minWidthDp = 40,
            minHeightDp = 40,
            minResizeWidthDp = 40,
            minResizeHeightDp = 40,
            resizeMode = ForkWidgetEntry.RESIZE_BOTH,
            allowMinWidth1 = true,
            allowMinHeight1 = true,
        )

        assertEquals(1, entry.colSpan)
        assertEquals(1, entry.rowSpan)
        assertEquals(1, entry.minColSpan)
        assertEquals(1, entry.minRowSpan)
    }

    @Test
    fun `calculateInitialEntry maps minWidth and minHeight dp to grid spans and handles RESIZE_NONE`() {
        val wideResizable = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 20,
            minWidthDp = 250,
            minHeightDp = 180,
            minResizeWidthDp = 110,
            minResizeHeightDp = 110,
            resizeMode = ForkWidgetEntry.RESIZE_BOTH,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(4, wideResizable.colSpan)
        assertEquals(3, wideResizable.rowSpan)
        assertEquals(2, wideResizable.minColSpan)
        assertEquals(2, wideResizable.minRowSpan)

        val fixed = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 21,
            minWidthDp = 180,
            minHeightDp = 110,
            minResizeWidthDp = 40,
            minResizeHeightDp = 40,
            resizeMode = ForkWidgetEntry.RESIZE_NONE,
            allowMinWidth1 = true,
            allowMinHeight1 = true,
        )
        assertEquals(3, fixed.colSpan)
        assertEquals(2, fixed.rowSpan)
        assertEquals(3, fixed.minColSpan)
        assertEquals(2, fixed.minRowSpan)
        assertEquals(ForkWidgetEntry.RESIZE_NONE, fixed.resizeMode)
    }

    @Test
    fun `calculateInitialEntry locks non-resizable axis when only one axis is resizable`() {
        val horizontalOnly = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 31,
            minWidthDp = 180,
            minHeightDp = 110,
            minResizeWidthDp = 110,
            minResizeHeightDp = 40,
            resizeMode = ForkWidgetEntry.RESIZE_HORIZONTAL,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(2, horizontalOnly.minColSpan)
        assertEquals(2, horizontalOnly.minRowSpan)

        val verticalOnly = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = 32,
            minWidthDp = 180,
            minHeightDp = 180,
            minResizeWidthDp = 40,
            minResizeHeightDp = 110,
            resizeMode = ForkWidgetEntry.RESIZE_VERTICAL,
            allowMinWidth1 = false,
            allowMinHeight1 = false,
        )
        assertEquals(3, verticalOnly.minColSpan)
        assertEquals(2, verticalOnly.minRowSpan)
    }

    @Test
    fun `shouldCleanupOnCancel cleans up valid allocated ids only on cancellation`() {
        assertTrue(ForkWidgetPinPolicy.shouldCleanupOnCancel(resultCodeOk = false, appWidgetId = 55))
        assertFalse(ForkWidgetPinPolicy.shouldCleanupOnCancel(resultCodeOk = true, appWidgetId = 55))
        assertFalse(
            ForkWidgetPinPolicy.shouldCleanupOnCancel(
                resultCodeOk = false,
                appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        )
    }
}
