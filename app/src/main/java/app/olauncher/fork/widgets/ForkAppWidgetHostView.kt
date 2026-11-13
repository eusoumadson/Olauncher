package app.olauncher.fork.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Immutable snapshot of the Widget Screen edit-mode state machine.
 */
data class ForkWidgetEditSnapshot(
    val isEditing: Boolean = false,
    val selectedAppWidgetId: Int? = null,
)

/**
 * Pure Kotlin state machine managing Widget Screen edit mode, active widget selection,
 * widget removal updates, and dismissal when tapping outside widget edit controls.
 */
class ForkWidgetEditStateMachine(
    initialState: ForkWidgetEditSnapshot = ForkWidgetEditSnapshot(),
) {
    var snapshot: ForkWidgetEditSnapshot = initialState
        private set

    val isEditing: Boolean
        get() = snapshot.isEditing

    val selectedAppWidgetId: Int?
        get() = snapshot.selectedAppWidgetId

    fun enterEditMode(appWidgetId: Int): ForkWidgetEditSnapshot {
        snapshot = ForkWidgetEditSnapshot(
            isEditing = true,
            selectedAppWidgetId = appWidgetId,
        )
        return snapshot
    }

    fun selectWidget(appWidgetId: Int): ForkWidgetEditSnapshot {
        if (!snapshot.isEditing) return snapshot
        if (snapshot.selectedAppWidgetId != appWidgetId) {
            snapshot = snapshot.copy(selectedAppWidgetId = appWidgetId)
        }
        return snapshot
    }

    fun exitEditMode(): ForkWidgetEditSnapshot {
        if (!snapshot.isEditing && snapshot.selectedAppWidgetId == null) return snapshot
        snapshot = ForkWidgetEditSnapshot(isEditing = false, selectedAppWidgetId = null)
        return snapshot
    }

    /**
     * Handles a tap outside any widget card or resize/remove control.
     *
     * Returns `true` if edit mode was active and has now been exited.
     */
    fun onTapOutside(): Boolean {
        if (!snapshot.isEditing) return false
        exitEditMode()
        return true
    }

    fun onWidgetRemoved(appWidgetId: Int, remainingWidgetIds: List<Int>): ForkWidgetEditSnapshot {
        if (remainingWidgetIds.isEmpty()) {
            return exitEditMode()
        }
        if (snapshot.isEditing && snapshot.selectedAppWidgetId == appWidgetId) {
            snapshot = snapshot.copy(selectedAppWidgetId = null)
        }
        return snapshot
    }
}

/**
 * Pure Kotlin touch-slop tracker used by [ForkAppWidgetHostView] to detect long-press
 * without interfering with normal taps or widget internal scrolling.
 */
class ForkLongPressTouchTracker(private val touchSlop: Int) {
    private var downX = 0f
    private var downY = 0f
    var isPending: Boolean = false
        private set
    var longPressTriggered: Boolean = false
        private set

    fun onDown(x: Float, y: Float) {
        downX = x
        downY = y
        isPending = true
        longPressTriggered = false
    }

    /**
     * Returns `true` if movement exceeded [touchSlop] and cancelled a pending long-press timer.
     */
    fun onMove(x: Float, y: Float): Boolean {
        if (!isPending) return false
        val dx = abs(x - downX)
        val dy = abs(y - downY)
        if (dx > touchSlop || dy > touchSlop) {
            isPending = false
            return true
        }
        return false
    }

    fun markLongPressTriggered(): Boolean {
        if (!isPending) return false
        isPending = false
        longPressTriggered = true
        return true
    }

    fun reset() {
        isPending = false
        longPressTriggered = false
    }
}

/**
 * Custom [AppWidgetHostView] that intercepts long-press gestures respecting `touchSlop`,
 * triggers [HapticFeedbackConstants.LONG_PRESS], enters Widget Screen edit mode, and blocks
 * accidental clicks on inner widget views while edit mode is active.
 */
class ForkAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private val touchTracker = ForkLongPressTouchTracker(
        ViewConfiguration.get(context).scaledTouchSlop
    )
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

    var isEditMode: Boolean = false
    var onWidgetLongPressListener: ((ForkAppWidgetHostView) -> Unit)? = null

    private val longPressRunnable = Runnable {
        if (touchTracker.markLongPressTriggered()) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            onWidgetLongPressListener?.invoke(this)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isEditMode) {
            cancelPendingLongPress()
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingLongPress()
                touchTracker.onDown(ev.x, ev.y)
                postDelayed(longPressRunnable, longPressTimeoutMs)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchTracker.longPressTriggered) {
                    return true
                }
                if (touchTracker.onMove(ev.x, ev.y)) {
                    removeCallbacks(longPressRunnable)
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val triggered = touchTracker.longPressTriggered
                cancelPendingLongPress()
                return triggered
            }

            else -> return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEditMode || touchTracker.longPressTriggered) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                cancelPendingLongPress()
            }
            // Do not consume here so the parent widget card / BentoGridLayoutView handles edit gestures.
            return false
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        cancelPendingLongPress()
        super.onDetachedFromWindow()
    }

    private fun cancelPendingLongPress() {
        removeCallbacks(longPressRunnable)
        touchTracker.reset()
    }
}
