package app.olauncher.fork.settings

import app.olauncher.fork.widgets.WidgetScreenPosition

data class SwipeAppDisplayState(
    val label: String,
    val isReservedForWidgets: Boolean,
    val isDimmed: Boolean,
    val canSelectApp: Boolean,
)

/**
 * Pure policy resolving swipe-gesture availability when the dedicated Widget Screen
 * occupies the left or right side of the home screen.
 *
 * - `LEFT` places Widget Screen on the left -> swiping RIGHT enters it -> swipe-right app is disabled.
 * - `RIGHT` places Widget Screen on the right -> swiping LEFT enters it -> swipe-left app is disabled.
 */
object ForkSwipePolicy {

    fun isSwipeLeftReserved(position: WidgetScreenPosition): Boolean =
        position == WidgetScreenPosition.RIGHT

    fun isSwipeRightReserved(position: WidgetScreenPosition): Boolean =
        position == WidgetScreenPosition.LEFT

    fun isSwipeLeftEnabled(position: WidgetScreenPosition, swipeLeftEnabled: Boolean): Boolean =
        swipeLeftEnabled && !isSwipeLeftReserved(position)

    fun isSwipeRightEnabled(position: WidgetScreenPosition, swipeRightEnabled: Boolean): Boolean =
        swipeRightEnabled && !isSwipeRightReserved(position)

    fun canOpenSwipeLeftApp(position: WidgetScreenPosition, swipeLeftEnabled: Boolean): Boolean =
        isSwipeLeftEnabled(position, swipeLeftEnabled)

    fun canOpenSwipeRightApp(position: WidgetScreenPosition, swipeRightEnabled: Boolean): Boolean =
        isSwipeRightEnabled(position, swipeRightEnabled)

    fun resolveSwipeLeftDisplay(
        position: WidgetScreenPosition,
        swipeLeftEnabled: Boolean,
        appNameSwipeLeft: String,
        reservedLabel: String,
    ): SwipeAppDisplayState = resolveDisplay(
        isReserved = isSwipeLeftReserved(position),
        prefEnabled = swipeLeftEnabled,
        appName = appNameSwipeLeft,
        reservedLabel = reservedLabel,
    )

    fun resolveSwipeRightDisplay(
        position: WidgetScreenPosition,
        swipeRightEnabled: Boolean,
        appNameSwipeRight: String,
        reservedLabel: String,
    ): SwipeAppDisplayState = resolveDisplay(
        isReserved = isSwipeRightReserved(position),
        prefEnabled = swipeRightEnabled,
        appName = appNameSwipeRight,
        reservedLabel = reservedLabel,
    )

    private fun resolveDisplay(
        isReserved: Boolean,
        prefEnabled: Boolean,
        appName: String,
        reservedLabel: String,
    ): SwipeAppDisplayState {
        if (isReserved) {
            return SwipeAppDisplayState(
                label = reservedLabel,
                isReservedForWidgets = true,
                isDimmed = true,
                canSelectApp = false,
            )
        }
        return SwipeAppDisplayState(
            label = appName,
            isReservedForWidgets = false,
            isDimmed = !prefEnabled,
            canSelectApp = prefEnabled,
        )
    }
}
