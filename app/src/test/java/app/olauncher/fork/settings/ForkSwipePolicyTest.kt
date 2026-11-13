package app.olauncher.fork.settings

import app.olauncher.fork.widgets.WidgetScreenPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkSwipePolicyTest {

    @Test
    fun `full matrix of widget screen position and swipe enabled flags resolves expected permissions`() {
        data class Case(
            val position: WidgetScreenPosition,
            val swipeLeftEnabled: Boolean,
            val swipeRightEnabled: Boolean,
            val expectLeftReserved: Boolean,
            val expectRightReserved: Boolean,
            val expectCanOpenLeft: Boolean,
            val expectCanOpenRight: Boolean,
        )

        val cases = listOf(
            Case(WidgetScreenPosition.DISABLED, true, true, false, false, true, true),
            Case(WidgetScreenPosition.DISABLED, true, false, false, false, true, false),
            Case(WidgetScreenPosition.DISABLED, false, true, false, false, false, true),
            Case(WidgetScreenPosition.DISABLED, false, false, false, false, false, false),

            // LEFT puts Widget Screen on the left -> swiping RIGHT navigates to it -> right app disabled
            Case(WidgetScreenPosition.LEFT, true, true, false, true, true, false),
            Case(WidgetScreenPosition.LEFT, true, false, false, true, true, false),
            Case(WidgetScreenPosition.LEFT, false, true, false, true, false, false),
            Case(WidgetScreenPosition.LEFT, false, false, false, true, false, false),

            // RIGHT puts Widget Screen on the right -> swiping LEFT navigates to it -> left app disabled
            Case(WidgetScreenPosition.RIGHT, true, true, true, false, false, true),
            Case(WidgetScreenPosition.RIGHT, true, false, true, false, false, false),
            Case(WidgetScreenPosition.RIGHT, false, true, true, false, false, true),
            Case(WidgetScreenPosition.RIGHT, false, false, true, false, false, false),
        )

        for (case in cases) {
            val label = "pos=${case.position}, L=${case.swipeLeftEnabled}, R=${case.swipeRightEnabled}"
            assertEquals(label, case.expectLeftReserved, ForkSwipePolicy.isSwipeLeftReserved(case.position))
            assertEquals(label, case.expectRightReserved, ForkSwipePolicy.isSwipeRightReserved(case.position))
            assertEquals(
                label,
                case.expectCanOpenLeft,
                ForkSwipePolicy.canOpenSwipeLeftApp(case.position, case.swipeLeftEnabled),
            )
            assertEquals(
                label,
                case.expectCanOpenRight,
                ForkSwipePolicy.canOpenSwipeRightApp(case.position, case.swipeRightEnabled),
            )
            assertEquals(
                label,
                case.expectCanOpenLeft,
                ForkSwipePolicy.isSwipeLeftEnabled(case.position, case.swipeLeftEnabled),
            )
            assertEquals(
                label,
                case.expectCanOpenRight,
                ForkSwipePolicy.isSwipeRightEnabled(case.position, case.swipeRightEnabled),
            )
        }
    }

    @Test
    fun `display states replace reserved label and dim accordingly while keeping opposite side intact`() {
        val reserved = "Reserved for Widget screen"

        val leftWhenLeftPos = ForkSwipePolicy.resolveSwipeLeftDisplay(
            position = WidgetScreenPosition.LEFT,
            swipeLeftEnabled = true,
            appNameSwipeLeft = "Camera",
            reservedLabel = reserved,
        )
        assertEquals("Camera", leftWhenLeftPos.label)
        assertFalse(leftWhenLeftPos.isReservedForWidgets)
        assertFalse(leftWhenLeftPos.isDimmed)
        assertTrue(leftWhenLeftPos.canSelectApp)

        val rightWhenLeftPos = ForkSwipePolicy.resolveSwipeRightDisplay(
            position = WidgetScreenPosition.LEFT,
            swipeRightEnabled = true,
            appNameSwipeRight = "Phone",
            reservedLabel = reserved,
        )
        assertEquals(reserved, rightWhenLeftPos.label)
        assertTrue(rightWhenLeftPos.isReservedForWidgets)
        assertTrue(rightWhenLeftPos.isDimmed)
        assertFalse(rightWhenLeftPos.canSelectApp)

        val leftWhenRightPos = ForkSwipePolicy.resolveSwipeLeftDisplay(
            position = WidgetScreenPosition.RIGHT,
            swipeLeftEnabled = true,
            appNameSwipeLeft = "Camera",
            reservedLabel = reserved,
        )
        assertEquals(reserved, leftWhenRightPos.label)
        assertTrue(leftWhenRightPos.isReservedForWidgets)
        assertTrue(leftWhenRightPos.isDimmed)
        assertFalse(leftWhenRightPos.canSelectApp)

        val rightWhenRightPosDisabledPref = ForkSwipePolicy.resolveSwipeRightDisplay(
            position = WidgetScreenPosition.RIGHT,
            swipeRightEnabled = false,
            appNameSwipeRight = "Phone",
            reservedLabel = reserved,
        )
        assertEquals("Phone", rightWhenRightPosDisabledPref.label)
        assertFalse(rightWhenRightPosDisabledPref.isReservedForWidgets)
        assertTrue(rightWhenRightPosDisabledPref.isDimmed)
        assertFalse(rightWhenRightPosDisabledPref.canSelectApp)
    }
}
