package app.olauncher.fork.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.LayoutInflater
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Pure Kotlin contract abstracting [AppWidgetHost] and [AppWidgetManager] operations
 * so lifecycle state transitions and orphan cleanup remain unit-testable on the JVM.
 */
interface ForkWidgetHostBackend {
    fun startListening()
    fun stopListening()
    fun allocateAppWidgetId(): Int
    fun deleteAppWidgetId(appWidgetId: Int)
    fun hasProviderInfo(appWidgetId: Int): Boolean
}

/**
 * Pure Kotlin coordinator for idempotent [AppWidgetHost] listening state and orphan cleanup.
 */
class ForkWidgetHostCoordinator(
    private val prefs: ForkWidgetPrefs,
    private val backend: ForkWidgetHostBackend,
) {
    var isListening: Boolean = false
        private set

    fun onStart(): List<ForkWidgetEntry> {
        val activeWidgets = pruneOrphanedWidgets()
        if (shouldStartListening(prefs.widgetScreenPosition, activeWidgets.isNotEmpty(), isListening)) {
            backend.startListening()
            isListening = true
        }
        return activeWidgets
    }

    fun onStop() {
        if (shouldStopListening(isListening)) {
            backend.stopListening()
            isListening = false
        }
    }

    fun syncListeningState(): List<ForkWidgetEntry> {
        val activeWidgets = pruneOrphanedWidgets()
        val position = prefs.widgetScreenPosition
        if (shouldStartListening(position, activeWidgets.isNotEmpty(), isListening)) {
            backend.startListening()
            isListening = true
        } else if (isListening && (!position.isEnabled || activeWidgets.isEmpty())) {
            backend.stopListening()
            isListening = false
        }
        return activeWidgets
    }

    fun pruneOrphanedWidgets(): List<ForkWidgetEntry> {
        val persisted = prefs.getWidgets()
        if (persisted.isEmpty()) return emptyList()

        val orphanIds = findOrphanAppWidgetIds(persisted) { appWidgetId ->
            backend.hasProviderInfo(appWidgetId)
        }
        if (orphanIds.isEmpty()) return persisted

        orphanIds.forEach { backend.deleteAppWidgetId(it) }
        return prefs.removeWidgets(orphanIds)
    }

    companion object {
        fun shouldStartListening(
            position: WidgetScreenPosition,
            hasWidgets: Boolean,
            currentlyListening: Boolean,
        ): Boolean = position.isEnabled && hasWidgets && !currentlyListening

        fun shouldStopListening(currentlyListening: Boolean): Boolean = currentlyListening

        fun findOrphanAppWidgetIds(
            entries: List<ForkWidgetEntry>,
            hasProviderInfo: (Int) -> Boolean,
        ): List<Int> = entries.asSequence()
            .map { it.appWidgetId }
            .distinct()
            .filterNot(hasProviderInfo)
            .toList()
    }
}

/**
 * Process-scoped manager encapsulating Android's [AppWidgetHost] (`FORK_WIDGET_HOST_ID = 1024`),
 * economical start/stop listening, orphan cleanup, and change notifications for the Widget Screen.
 */
class ForkWidgetHostManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val widgetPrefs: ForkWidgetPrefs = ForkWidgetPrefs(appContext)

    private var hostViewFactory: ((Context, Int, AppWidgetProviderInfo?) -> AppWidgetHostView)? = null

    private val appWidgetHost: AppWidgetHost = object : AppWidgetHost(appContext, FORK_WIDGET_HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView {
            return hostViewFactory?.invoke(context, appWidgetId, appWidget)
                ?: super.onCreateView(context, appWidgetId, appWidget)
        }
    }

    private val backend = object : ForkWidgetHostBackend {
        override fun startListening() {
            runCatching { appWidgetHost.startListening() }
        }

        override fun stopListening() {
            runCatching { appWidgetHost.stopListening() }
        }

        override fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

        override fun deleteAppWidgetId(appWidgetId: Int) {
            runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
        }

        override fun hasProviderInfo(appWidgetId: Int): Boolean =
            appWidgetManager.getAppWidgetInfo(appWidgetId) != null
    }

    private val coordinator = ForkWidgetHostCoordinator(widgetPrefs, backend)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    val isListening: Boolean
        get() = coordinator.isListening

    fun setHostViewFactory(factory: ((Context, Int, AppWidgetProviderInfo?) -> AppWidgetHostView)?) {
        hostViewFactory = factory
    }

    fun onStart(): List<ForkWidgetEntry> = coordinator.onStart()

    fun onStop() {
        coordinator.onStop()
    }

    fun syncListeningState(): List<ForkWidgetEntry> = coordinator.syncListeningState()

    fun pruneOrphanedWidgets(): List<ForkWidgetEntry> = coordinator.pruneOrphanedWidgets()

    fun allocateAppWidgetId(): Int = backend.allocateAppWidgetId()

    fun deleteAppWidgetId(appWidgetId: Int) {
        backend.deleteAppWidgetId(appWidgetId)
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    fun bindAppWidgetIdIfAllowed(
        appWidgetId: Int,
        provider: android.content.ComponentName,
        options: android.os.Bundle? = null,
    ): Boolean = if (options != null) {
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider, options)
    } else {
        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
    }

    fun createView(
        context: Context,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo? = getAppWidgetInfo(appWidgetId),
    ): AppWidgetHostView = appWidgetHost.createView(
        createRemoteViewsSafeContext(context),
        appWidgetId,
        appWidgetInfo,
    )

    /**
     * Shields [RemoteViews] inflation from [androidx.appcompat.app.AppCompatViewInflater] installed
     * on `AppCompatActivity`'s LayoutInflater (which converts `<ImageView>`/`<TextView>` into
     * non-`@RemoteView` AppCompat subclasses and crashes third-party widgets), while preserving
     * Activity theming, configuration, and PendingIntent launch capabilities.
     */
    private fun createRemoteViewsSafeContext(context: Context): Context {
        if (context === appContext) return context
        val configContext = appContext.createConfigurationContext(context.resources.configuration)
        val platformInflater = LayoutInflater.from(configContext)
        return object : ContextWrapper(context) {
            private var safeInflater: LayoutInflater? = null

            override fun getSystemService(name: String): Any? {
                if (name == Context.LAYOUT_INFLATER_SERVICE) {
                    return safeInflater ?: platformInflater.cloneInContext(this).also {
                        safeInflater = it
                    }
                }
                return super.getSystemService(name)
            }
        }
    }

    fun startConfigureActivity(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle? = null,
    ) {
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            activity,
            appWidgetId,
            0,
            requestCode,
            options,
        )
    }

    fun notifyWidgetsChanged() {
        syncListeningState()
        listeners.forEach { it.invoke() }
    }

    fun addWidgetsChangedListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeWidgetsChangedListener(listener: () -> Unit) {
        listeners -= listener
    }

    companion object {
        const val FORK_WIDGET_HOST_ID = 1024

        @Volatile
        private var instance: ForkWidgetHostManager? = null

        fun getInstance(context: Context): ForkWidgetHostManager {
            return instance ?: synchronized(this) {
                instance ?: ForkWidgetHostManager(context).also { instance = it }
            }
        }
    }
}
