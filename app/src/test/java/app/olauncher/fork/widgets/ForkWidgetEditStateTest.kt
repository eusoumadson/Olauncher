package app.olauncher.fork.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkWidgetEditStateTest {

    @Test
    fun `edit state machine starts inactive and enters edit mode with active selection`() {
        val state = ForkWidgetEditStateMachine()

        assertFalse(state.isEditing)
        assertNull(state.selectedAppWidgetId)

        val entered = state.enterEditMode(appWidgetId = 101)
        assertTrue(entered.isEditing)
        assertEquals(101, entered.selectedAppWidgetId)
    }

    @Test
    fun `selectWidget only updates selection while in edit mode`() {
        val state = ForkWidgetEditStateMachine()

        state.selectWidget(202)
        assertFalse(state.isEditing)
        assertNull(state.selectedAppWidgetId)

        state.enterEditMode(101)
        state.selectWidget(202)
        assertTrue(state.isEditing)
        assertEquals(202, state.selectedAppWidgetId)
    }

    @Test
    fun `onTapOutside exits edit mode clears selection and returns false when already inactive`() {
        val state = ForkWidgetEditStateMachine()

        assertFalse(state.onTapOutside())

        state.enterEditMode(101)
        assertTrue(state.onTapOutside())
        assertFalse(state.isEditing)
        assertNull(state.selectedAppWidgetId)

        assertFalse(state.onTapOutside())
    }

    @Test
    fun `onWidgetRemoved clears selection or exits edit mode when last widget is removed`() {
        val state = ForkWidgetEditStateMachine()
        state.enterEditMode(10)

        val afterFirstRemoval = state.onWidgetRemoved(appWidgetId = 10, remainingWidgetIds = listOf(20, 30))
        assertTrue(afterFirstRemoval.isEditing)
        assertNull(afterFirstRemoval.selectedAppWidgetId)

        state.selectWidget(20)
        val afterUnrelatedRemoval = state.onWidgetRemoved(appWidgetId = 30, remainingWidgetIds = listOf(20))
        assertTrue(afterUnrelatedRemoval.isEditing)
        assertEquals(20, afterUnrelatedRemoval.selectedAppWidgetId)

        val afterLastRemoval = state.onWidgetRemoved(appWidgetId = 20, remainingWidgetIds = emptyList())
        assertFalse(afterLastRemoval.isEditing)
        assertNull(afterLastRemoval.selectedAppWidgetId)
    }

    @Test
    fun `long press touch tracker respects touchSlop and cancels on movement`() {
        val tracker = ForkLongPressTouchTracker(touchSlop = 16)

        tracker.onDown(100f, 200f)
        assertTrue(tracker.isPending)
        assertFalse(tracker.onMove(110f, 212f))
        assertTrue(tracker.isPending)

        assertTrue(tracker.markLongPressTriggered())
        assertTrue(tracker.longPressTriggered)
        assertFalse(tracker.isPending)

        tracker.reset()
        assertFalse(tracker.longPressTriggered)
        assertFalse(tracker.isPending)

        // Moving beyond touchSlop cancels long-press timer
        tracker.onDown(100f, 200f)
        assertTrue(tracker.onMove(125f, 200f))
        assertFalse(tracker.isPending)
        assertFalse(tracker.markLongPressTriggered())
        assertFalse(tracker.longPressTriggered)
    }
}
