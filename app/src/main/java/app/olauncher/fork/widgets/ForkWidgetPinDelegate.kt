package app.olauncher.fork.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import app.olauncher.R
import app.olauncher.helper.showToast
import java.util.WeakHashMap
import kotlin.math.ceil

/**
 * Pure Kotlin policy rules for external widget pinning, initial span sizing,
 * and allocated `appWidgetId` cleanup on cancellation.
 */
object ForkWidgetPinPolicy {
    private const val CELL_BASE_DP = 70.0
    private const val CELL_MARGIN_DP = 30.0
    private const val MAX_INITIAL_COLS = 4

    fun canPinExternalWidget(position: WidgetScreenPosition): Boolean = position.isEnabled

    fun shouldCleanupOnCancel(resultCodeOk: Boolean, appWidgetId: Int): Boolean =
        !resultCodeOk && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    fun cellsForDp(sizeDp: Int): Int {
        if (sizeDp <= 0) return 1
        return ceil((sizeDp + CELL_MARGIN_DP) / CELL_BASE_DP).toInt().coerceAtLeast(1)
    }

    fun calculateInitialEntry(
        appWidgetId: Int,
        minWidthDp: Int,
        minHeightDp: Int,
        minResizeWidthDp: Int,
        minResizeHeightDp: Int,
        resizeMode: Int,
        allowMinWidth1: Boolean,
        allowMinHeight1: Boolean,
        order: Int = 0,
        targetCellWidth: Int = 0,
        targetCellHeight: Int = 0,
    ): ForkWidgetEntry {
        val floorCol = if (allowMinWidth1) 1 else ForkWidgetEntry.DEFAULT_SPAN
        val floorRow = if (allowMinHeight1) 1 else ForkWidgetEntry.DEFAULT_SPAN

        val rawCols = if (targetCellWidth > 0) targetCellWidth else cellsForDp(minWidthDp)
        val rawRows = if (targetCellHeight > 0) targetCellHeight else cellsForDp(minHeightDp)

        val colSpan = maxOf(floorCol, rawCols).coerceIn(floorCol, MAX_INITIAL_COLS)
        val rowSpan = maxOf(floorRow, rawRows).coerceAtLeast(floorRow)

        val isFixedSize = resizeMode == ForkWidgetEntry.RESIZE_NONE
        val canResizeH = !isFixedSize && (resizeMode and ForkWidgetEntry.RESIZE_HORIZONTAL) != 0
        val canResizeV = !isFixedSize && (resizeMode and ForkWidgetEntry.RESIZE_VERTICAL) != 0

        val minColSpan = when {
            isFixedSize -> colSpan
            !canResizeH -> colSpan
            minResizeWidthDp > 0 -> cellsForDp(minResizeWidthDp).coerceIn(1, colSpan)
            else -> minOf(floorCol, colSpan)
        }
        val minRowSpan = when {
            isFixedSize -> rowSpan
            !canResizeV -> rowSpan
            minResizeHeightDp > 0 -> cellsForDp(minResizeHeightDp).coerceIn(1, rowSpan)
            else -> minOf(floorRow, rowSpan)
        }

        return ForkWidgetEntry(
            appWidgetId = appWidgetId,
            colSpan = colSpan,
            rowSpan = rowSpan,
            order = order,
            minColSpan = minColSpan,
            minRowSpan = minRowSpan,
            resizeMode = resizeMode,
        )
    }
}

/**
 * Coordinates the `ACTION_APPWIDGET_PICK` -> `ACTION_APPWIDGET_BIND` -> `ACTION_APPWIDGET_CONFIGURE`
 * flow for [app.olauncher.MainActivity] and external `REQUEST_TYPE_APPWIDGET` pinning in
 * [app.olauncher.helper.PinItemActivity].
 */
class ForkWidgetPinDelegate(
    private val activity: Activity,
    private val hostManager: ForkWidgetHostManager,
) {
    private var pendingAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingPinRequest: LauncherApps.PinItemRequest? = null
    private var pendingFlowCommitted: Boolean = false

    fun startWidgetPicker() {
        val appWidgetId = hostManager.allocateAppWidgetId()
        pendingAppWidgetId = appWidgetId
        pendingFlowCommitted = false
        pendingPickerIds[activity] = appWidgetId

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<AppWidgetProviderInfo>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())
        }
        runCatching {
            activity.startActivityForResult(pickIntent, REQUEST_CODE_PICK_WIDGET)
        }.onFailure {
            cleanupPendingId(appWidgetId)
        }
    }

    /**
     * Handles `REQUEST_TYPE_APPWIDGET` in [app.olauncher.helper.PinItemActivity].
     *
     * Returns `true` when the caller may finish immediately, or `false` when the flow continues
     * asynchronously via `startActivityForResult`.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun handlePinItemRequest(pinItemRequest: LauncherApps.PinItemRequest): Boolean {
        val prefs = hostManager.widgetPrefs
        if (!ForkWidgetPinPolicy.canPinExternalWidget(prefs.widgetScreenPosition)) {
            activity.showToast(R.string.widgets_not_supported)
            return true
        }

        val providerInfo = pinItemRequest.getAppWidgetProviderInfo(activity)
        if (providerInfo == null) {
            activity.showToast(R.string.invalid_pin_request)
            return true
        }

        val appWidgetId = hostManager.allocateAppWidgetId()
        pendingAppWidgetId = appWidgetId
        pendingPinRequest = pinItemRequest
        pendingFlowCommitted = false

        val extras = runCatching { pinItemRequest.extras }.getOrNull()
        val bound = runCatching {
            hostManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider, extras)
        }.getOrDefault(false)

        if (!bound) {
            launchBindActivity(
                appWidgetId = appWidgetId,
                provider = providerInfo.provider,
                profile = providerInfo.profile,
                options = extras,
                requestCode = REQUEST_CODE_PIN_BIND_WIDGET,
            )
            return false
        }

        if (providerInfo.configure != null) {
            return !launchConfigureActivity(appWidgetId, REQUEST_CODE_PIN_CONFIGURE_WIDGET, extras)
        }

        completePinRequest(pinItemRequest, appWidgetId, providerInfo)
        return true
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            REQUEST_CODE_PICK_WIDGET -> {
                handlePickResult(resultCode, data)
                true
            }
            REQUEST_CODE_BIND_WIDGET -> {
                handleBindResult(resultCode, data)
                true
            }
            REQUEST_CODE_CONFIGURE_WIDGET -> {
                handleConfigureResult(resultCode, data)
                true
            }
            REQUEST_CODE_PIN_BIND_WIDGET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    handlePinBindResult(resultCode, data)
                } else {
                    cleanupAllPending()
                    activity.finish()
                }
                true
            }
            REQUEST_CODE_PIN_CONFIGURE_WIDGET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    handlePinConfigureResult(resultCode, data)
                } else {
                    cleanupAllPending()
                    activity.finish()
                }
                true
            }
            else -> false
        }
    }

    fun onDestroy() {
        if (activity.isFinishing && !pendingFlowCommitted) {
            cleanupAllPending()
        }
    }

    private fun handlePickResult(resultCode: Int, data: Intent?) {
        val activePendingId = resolveAppWidgetId(data)
        pendingPickerIds.remove(activity)

        if (resultCode != Activity.RESULT_OK || activePendingId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            cleanupPendingId(activePendingId)
            return
        }

        val providerInfo = hostManager.getAppWidgetInfo(activePendingId)
        if (providerInfo == null) {
            @Suppress("DEPRECATION")
            val provider = data?.getParcelableExtra<ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
            if (provider == null) {
                cleanupPendingId(activePendingId)
                return
            }
            val bound = runCatching {
                hostManager.bindAppWidgetIdIfAllowed(activePendingId, provider)
            }.getOrDefault(false)
            if (!bound) {
                pendingAppWidgetId = activePendingId
                launchBindActivity(
                    appWidgetId = activePendingId,
                    provider = provider,
                    profile = null,
                    options = null,
                    requestCode = REQUEST_CODE_BIND_WIDGET,
                )
                return
            }
            val resolvedInfo = hostManager.getAppWidgetInfo(activePendingId)
            if (resolvedInfo == null) {
                cleanupPendingId(activePendingId)
                return
            }
            proceedAfterPickOrBind(activePendingId, resolvedInfo)
            return
        }

        proceedAfterPickOrBind(activePendingId, providerInfo)
    }

    private fun handleBindResult(resultCode: Int, data: Intent?) {
        val appWidgetId = resolveAppWidgetId(data)
        if (resultCode != Activity.RESULT_OK || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            cleanupPendingId(appWidgetId)
            return
        }
        val providerInfo = hostManager.getAppWidgetInfo(appWidgetId)
        if (providerInfo == null) {
            cleanupPendingId(appWidgetId)
            return
        }
        proceedAfterPickOrBind(appWidgetId, providerInfo)
    }

    private fun proceedAfterPickOrBind(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        if (providerInfo.configure != null) {
            pendingAppWidgetId = appWidgetId
            if (!launchConfigureActivity(appWidgetId, REQUEST_CODE_CONFIGURE_WIDGET, null)) {
                cleanupPendingId(appWidgetId)
            }
        } else {
            persistWidget(appWidgetId, providerInfo)
        }
    }

    private fun handleConfigureResult(resultCode: Int, data: Intent?) {
        val appWidgetId = resolveAppWidgetId(data)
        if (resultCode != Activity.RESULT_OK || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            cleanupPendingId(appWidgetId)
            return
        }
        val providerInfo = hostManager.getAppWidgetInfo(appWidgetId)
        if (providerInfo == null) {
            cleanupPendingId(appWidgetId)
            return
        }
        persistWidget(appWidgetId, providerInfo)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handlePinBindResult(resultCode: Int, data: Intent?) {
        val appWidgetId = resolveAppWidgetId(data)
        val pinRequest = pendingPinRequest
        if (resultCode != Activity.RESULT_OK ||
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            pinRequest == null
        ) {
            cleanupPendingId(appWidgetId)
            activity.finish()
            return
        }

        val providerInfo = hostManager.getAppWidgetInfo(appWidgetId)
            ?: pinRequest.getAppWidgetProviderInfo(activity)
        if (providerInfo == null) {
            cleanupPendingId(appWidgetId)
            activity.finish()
            return
        }

        if (providerInfo.configure != null) {
            val extras = runCatching { pinRequest.extras }.getOrNull()
            if (!launchConfigureActivity(appWidgetId, REQUEST_CODE_PIN_CONFIGURE_WIDGET, extras)) {
                cleanupPendingId(appWidgetId)
                activity.finish()
            }
            return
        }

        completePinRequest(pinRequest, appWidgetId, providerInfo)
        activity.finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handlePinConfigureResult(resultCode: Int, data: Intent?) {
        val appWidgetId = resolveAppWidgetId(data)
        val pinRequest = pendingPinRequest
        if (resultCode != Activity.RESULT_OK ||
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            pinRequest == null
        ) {
            cleanupPendingId(appWidgetId)
            activity.finish()
            return
        }

        val providerInfo = hostManager.getAppWidgetInfo(appWidgetId)
            ?: pinRequest.getAppWidgetProviderInfo(activity)
        if (providerInfo == null) {
            cleanupPendingId(appWidgetId)
            activity.finish()
            return
        }

        completePinRequest(pinRequest, appWidgetId, providerInfo)
        activity.finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun completePinRequest(
        pinRequest: LauncherApps.PinItemRequest,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
    ) {
        val acceptExtras = Bundle().apply {
            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val accepted = runCatching { pinRequest.accept(acceptExtras) }.getOrDefault(false)
        if (accepted) {
            persistWidget(appWidgetId, providerInfo)
        } else {
            cleanupPendingId(appWidgetId)
        }
    }

    private fun launchBindActivity(
        appWidgetId: Int,
        provider: ComponentName,
        profile: android.os.UserHandle?,
        options: Bundle?,
        requestCode: Int,
    ) {
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            if (profile != null) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
            }
            if (options != null) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }
        }
        runCatching {
            activity.startActivityForResult(bindIntent, requestCode)
        }.onFailure {
            cleanupPendingId(appWidgetId)
            if (requestCode == REQUEST_CODE_PIN_BIND_WIDGET) {
                activity.finish()
            }
        }
    }

    private fun launchConfigureActivity(
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle?,
    ): Boolean {
        return runCatching {
            hostManager.startConfigureActivity(activity, appWidgetId, requestCode, options)
            true
        }.getOrDefault(false)
    }

    private fun persistWidget(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        val prefs = hostManager.widgetPrefs
        val existing = prefs.getWidgets()
        val nextOrder = (existing.maxOfOrNull { it.order }?.plus(1)) ?: 0
        val targetCellWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providerInfo.targetCellWidth
        } else {
            0
        }
        val targetCellHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providerInfo.targetCellHeight
        } else {
            0
        }
        val entry = ForkWidgetPinPolicy.calculateInitialEntry(
            appWidgetId = appWidgetId,
            minWidthDp = providerInfo.minWidth,
            minHeightDp = providerInfo.minHeight,
            minResizeWidthDp = providerInfo.minResizeWidth,
            minResizeHeightDp = providerInfo.minResizeHeight,
            resizeMode = providerInfo.resizeMode,
            allowMinWidth1 = prefs.allowMinWidth1,
            allowMinHeight1 = prefs.allowMinHeight1,
            order = nextOrder,
            targetCellWidth = targetCellWidth,
            targetCellHeight = targetCellHeight,
        )
        prefs.upsertWidget(entry)
        pendingFlowCommitted = true
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingPinRequest = null
        hostManager.notifyWidgetsChanged()
    }

    private fun resolveAppWidgetId(data: Intent?): Int {
        val fromIntent = data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (fromIntent != AppWidgetManager.INVALID_APPWIDGET_ID) return fromIntent
        if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) return pendingAppWidgetId
        return pendingPickerIds[activity] ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun cleanupPendingId(appWidgetId: Int) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            hostManager.deleteAppWidgetId(appWidgetId)
        }
        pendingAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingPinRequest = null
        pendingPickerIds.remove(activity)
    }

    private fun cleanupAllPending() {
        val idToClean = if (pendingAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingAppWidgetId
        } else {
            pendingPickerIds.remove(activity) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        }
        cleanupPendingId(idToClean)
    }

    companion object {
        const val REQUEST_CODE_PICK_WIDGET = 1901
        const val REQUEST_CODE_BIND_WIDGET = 1902
        const val REQUEST_CODE_CONFIGURE_WIDGET = 1903
        const val REQUEST_CODE_PIN_BIND_WIDGET = 1904
        const val REQUEST_CODE_PIN_CONFIGURE_WIDGET = 1905

        private val pendingPickerIds = WeakHashMap<Activity, Int>()

        fun startWidgetPicker(activity: Activity, hostManager: ForkWidgetHostManager) {
            ForkWidgetPinDelegate(activity, hostManager).startWidgetPicker()
        }
    }
}
