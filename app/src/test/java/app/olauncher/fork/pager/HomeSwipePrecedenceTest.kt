package app.olauncher.fork.pager

import android.view.MotionEvent
import app.olauncher.fork.settings.ForkSwipePolicy
import app.olauncher.fork.widgets.WidgetScreenPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSwipePrecedenceTest {

    @Test
    fun `openSwipeLeftApp and openSwipeRightApp guards block only the widget-reserved side`() {
        // DISABLED: both swipes follow user preference
        assertTrue(ForkSwipePolicy.canOpenSwipeLeftApp(WidgetScreenPosition.DISABLED, swipeLeftEnabled = true))
        assertTrue(ForkSwipePolicy.canOpenSwipeRightApp(WidgetScreenPosition.DISABLED, swipeRightEnabled = true))
        assertFalse(ForkSwipePolicy.canOpenSwipeLeftApp(WidgetScreenPosition.DISABLED, swipeLeftEnabled = false))
        assertFalse(ForkSwipePolicy.canOpenSwipeRightApp(WidgetScreenPosition.DISABLED, swipeRightEnabled = false))

        // LEFT widget screen: swiping RIGHT enters widgets -> right swipe app is blocked, left swipe app works
        assertFalse(ForkSwipePolicy.canOpenSwipeRightApp(WidgetScreenPosition.LEFT, swipeRightEnabled = true))
        assertTrue(ForkSwipePolicy.canOpenSwipeLeftApp(WidgetScreenPosition.LEFT, swipeLeftEnabled = true))
        assertFalse(ForkSwipePolicy.canOpenSwipeLeftApp(WidgetScreenPosition.LEFT, swipeLeftEnabled = false))

        // RIGHT widget screen: swiping LEFT enters widgets -> left swipe app is blocked, right swipe app works
        assertFalse(ForkSwipePolicy.canOpenSwipeLeftApp(WidgetScreenPosition.RIGHT, swipeLeftEnabled = true))
        assertTrue(ForkSwipePolicy.canOpenSwipeRightApp(WidgetScreenPosition.RIGHT, swipeRightEnabled = true))
        assertFalse(ForkSwipePolicy.canOpenSwipeRightApp(WidgetScreenPosition.RIGHT, swipeRightEnabled = false))
    }

    @Test
    fun `onBackPressed and onNewIntent return from WidgetScreen to Home page`() {
        // LEFT: Widgets=0, Home=1
        assertEquals(
            1,
            WorkspacePagerPolicy.resolveBackPressedTargetPage(WidgetScreenPosition.LEFT, currentPageIndex = 0),
        )
        assertEquals(
            1,
            WorkspacePagerPolicy.resolveBackToHomeTargetPage(WidgetScreenPosition.LEFT, currentPageIndex = 0),
        )
        assertNull(
            WorkspacePagerPolicy.resolveBackPressedTargetPage(WidgetScreenPosition.LEFT, currentPageIndex = 1),
        )
        assertNull(
            WorkspacePagerPolicy.resolveBackToHomeTargetPage(WidgetScreenPosition.LEFT, currentPageIndex = 1),
        )

        // RIGHT: Home=0, Widgets=1
        assertEquals(
            0,
            WorkspacePagerPolicy.resolveBackPressedTargetPage(WidgetScreenPosition.RIGHT, currentPageIndex = 1),
        )
        assertEquals(
            0,
            WorkspacePagerPolicy.resolveBackToHomeTargetPage(WidgetScreenPosition.RIGHT, currentPageIndex = 1),
        )
        assertNull(
            WorkspacePagerPolicy.resolveBackPressedTargetPage(WidgetScreenPosition.RIGHT, currentPageIndex = 0),
        )
        assertNull(
            WorkspacePagerPolicy.resolveBackToHomeTargetPage(WidgetScreenPosition.RIGHT, currentPageIndex = 0),
        )

        // DISABLED: never intercepts back or home
        assertNull(
            WorkspacePagerPolicy.resolveBackPressedTargetPage(WidgetScreenPosition.DISABLED, currentPageIndex = 0),
        )
        assertNull(
            WorkspacePagerPolicy.resolveBackToHomeTargetPage(WidgetScreenPosition.DISABLED, currentPageIndex = 0),
        )
    }

    @Test
    fun `ForkHomeTouchDecorator clears pressed state only on ACTION_CANCEL`() {
        assertTrue(ForkHomeTouchDecorator.shouldClearPressedOnAction(MotionEvent.ACTION_CANCEL))
        assertFalse(ForkHomeTouchDecorator.shouldClearPressedOnAction(MotionEvent.ACTION_DOWN))
        assertFalse(ForkHomeTouchDecorator.shouldClearPressedOnAction(MotionEvent.ACTION_MOVE))
        assertFalse(ForkHomeTouchDecorator.shouldClearPressedOnAction(MotionEvent.ACTION_UP))
    }
}
