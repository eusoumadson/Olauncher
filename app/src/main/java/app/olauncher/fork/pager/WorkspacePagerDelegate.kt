package app.olauncher.fork.pager

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.fork.widgets.ForkWidgetPrefs
import app.olauncher.fork.widgets.WidgetScreenPosition
import app.olauncher.fork.widgets.WidgetsFragment

enum class WorkspacePage(val stableId: Long) {
    HOME(1L),
    WIDGETS(2L),
}

/**
 * Pure Kotlin policy governing page ordering, user swipe availability, and back/home return
 * behavior for the ViewPager2 workspace.
 */
object WorkspacePagerPolicy {

    fun pagesFor(position: WidgetScreenPosition): List<WorkspacePage> = when (position) {
        WidgetScreenPosition.DISABLED -> listOf(WorkspacePage.HOME)
        WidgetScreenPosition.LEFT -> listOf(WorkspacePage.WIDGETS, WorkspacePage.HOME)
        WidgetScreenPosition.RIGHT -> listOf(WorkspacePage.HOME, WorkspacePage.WIDGETS)
    }

    fun homePageIndex(position: WidgetScreenPosition): Int =
        pagesFor(position).indexOf(WorkspacePage.HOME)

    fun widgetsPageIndex(position: WidgetScreenPosition): Int =
        pagesFor(position).indexOf(WorkspacePage.WIDGETS)

    fun isUserInputEnabled(
        position: WidgetScreenPosition,
        currentDestinationId: Int?,
        homeDestinationId: Int,
        isWidgetEditInteractionActive: Boolean,
    ): Boolean {
        if (!position.isEnabled) return false
        if (currentDestinationId != homeDestinationId) return false
        if (isWidgetEditInteractionActive) return false
        return true
    }

    fun shouldReturnToHome(
        position: WidgetScreenPosition,
        currentPageIndex: Int,
    ): Boolean {
        if (!position.isEnabled) return false
        val widgetsIndex = widgetsPageIndex(position)
        return widgetsIndex >= 0 && currentPageIndex == widgetsIndex
    }

    fun resolveBackPressedTargetPage(
        position: WidgetScreenPosition,
        currentPageIndex: Int,
    ): Int? = if (shouldReturnToHome(position, currentPageIndex)) homePageIndex(position) else null

    fun resolveBackToHomeTargetPage(
        position: WidgetScreenPosition,
        currentPageIndex: Int,
    ): Int? = if (shouldReturnToHome(position, currentPageIndex)) homePageIndex(position) else null
}

/**
 * Coordinates the overlay [androidx.viewpager2.widget.ViewPager2] in `activity_main.xml` without
 * displacing `NavHostFragment` during initial `findNavController` resolution or when disabled.
 */
class WorkspacePagerDelegate(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val navController: NavController,
    private val widgetPrefs: ForkWidgetPrefs,
) {
    private val navHostView: View = activity.findViewById(R.id.nav_host_fragment)
    private val sharedPreferences: SharedPreferences =
        activity.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    private var currentPosition: WidgetScreenPosition? = null
    private var currentDestinationId: Int? = navController.currentDestination?.id
    private var isWidgetEditInteractionActive: Boolean = false
    private var pagerAdapter: WorkspacePagerAdapter? = null

    private val destinationChangedListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            currentDestinationId = destination.id
            updateUserInputEnabled()
        }

    // Hold strong reference because SharedPreferences tracks listeners via WeakHashMap.
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_WIDGET_SCREEN_POSITION) {
                applyPosition(widgetPrefs.widgetScreenPosition)
            }
        }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            navController.removeOnDestinationChangedListener(destinationChangedListener)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
            owner.lifecycle.removeObserver(this)
        }
    }

    init {
        detachStaleWidgetsFragment()
        binding.workspacePager.overScrollMode = View.OVER_SCROLL_NEVER
        navController.addOnDestinationChangedListener(destinationChangedListener)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        activity.lifecycle.addObserver(lifecycleObserver)
        applyPosition(widgetPrefs.widgetScreenPosition)
    }

    fun isOnWidgetScreen(): Boolean {
        val position = currentPosition ?: widgetPrefs.widgetScreenPosition
        return WorkspacePagerPolicy.shouldReturnToHome(
            position = position,
            currentPageIndex = binding.workspacePager.currentItem,
        )
    }

    fun handleBackPressed(): Boolean {
        if (!isOnWidgetScreen()) return false
        returnToHome(smoothScroll = true)
        return true
    }

    fun returnToHome(smoothScroll: Boolean = true) {
        val position = currentPosition ?: widgetPrefs.widgetScreenPosition
        if (!position.isEnabled) return
        findWidgetsFragment()?.exitEditModeIfActive()
        isWidgetEditInteractionActive = false
        updateUserInputEnabled()
        val homeIndex = WorkspacePagerPolicy.homePageIndex(position)
        if (binding.workspacePager.currentItem != homeIndex) {
            binding.workspacePager.setCurrentItem(homeIndex, smoothScroll)
        }
    }

    private fun applyPosition(position: WidgetScreenPosition) {
        if (currentPosition == position) {
            updateUserInputEnabled()
            return
        }
        currentPosition = position
        isWidgetEditInteractionActive = false

        detachWidgetsFragment()
        restoreNavHostToRoot()

        if (!position.isEnabled) {
            binding.workspacePager.isUserInputEnabled = false
            binding.workspacePager.adapter = null
            pagerAdapter = null
            binding.workspacePager.visibility = View.GONE
            return
        }

        val pages = WorkspacePagerPolicy.pagesFor(position)
        val adapter = WorkspacePagerAdapter(pages)
        pagerAdapter = adapter
        binding.workspacePager.visibility = View.VISIBLE
        binding.workspacePager.offscreenPageLimit = 1
        binding.workspacePager.adapter = adapter
        binding.workspacePager.setCurrentItem(WorkspacePagerPolicy.homePageIndex(position), false)
        updateUserInputEnabled()
    }

    private fun updateUserInputEnabled() {
        val position = currentPosition ?: widgetPrefs.widgetScreenPosition
        binding.workspacePager.isUserInputEnabled = WorkspacePagerPolicy.isUserInputEnabled(
            position = position,
            currentDestinationId = currentDestinationId ?: navController.currentDestination?.id,
            homeDestinationId = R.id.mainFragment,
            isWidgetEditInteractionActive = isWidgetEditInteractionActive,
        )
    }

    private fun restoreNavHostToRoot() {
        if (navHostView.parent !== binding.mainActivityLayout) {
            (navHostView.parent as? ViewGroup)?.removeView(navHostView)
            binding.mainActivityLayout.addView(
                navHostView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun findWidgetsFragment(): WidgetsFragment? =
        activity.supportFragmentManager.findFragmentByTag(WIDGETS_FRAGMENT_TAG) as? WidgetsFragment

    private fun detachStaleWidgetsFragment() {
        val existing = activity.supportFragmentManager.findFragmentByTag(WIDGETS_FRAGMENT_TAG) ?: return
        activity.supportFragmentManager.beginTransaction()
            .remove(existing)
            .commitNowAllowingStateLoss()
    }

    private fun detachWidgetsFragment() {
        val existing = activity.supportFragmentManager.findFragmentByTag(WIDGETS_FRAGMENT_TAG) ?: return
        if (!activity.supportFragmentManager.isDestroyed) {
            activity.supportFragmentManager.beginTransaction()
                .remove(existing)
                .commitNowAllowingStateLoss()
        }
    }

    private fun attachHomePage(container: FrameLayout) {
        if (navHostView.parent !== container) {
            (navHostView.parent as? ViewGroup)?.removeView(navHostView)
            container.addView(
                navHostView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun attachWidgetsPage(container: FrameLayout) {
        container.id = R.id.fork_widgets_page_container
        val fm = activity.supportFragmentManager
        if (fm.isDestroyed) return

        val existing = fm.findFragmentByTag(WIDGETS_FRAGMENT_TAG) as? WidgetsFragment
        if (existing != null && existing.isAdded && existing.id == container.id && existing.view?.parent === container) {
            wireWidgetsFragment(existing)
            return
        }

        if (existing != null) {
            fm.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }

        val fragment = WidgetsFragment()
        wireWidgetsFragment(fragment)
        fm.beginTransaction()
            .replace(container.id, fragment, WIDGETS_FRAGMENT_TAG)
            .commitNowAllowingStateLoss()
    }

    private fun wireWidgetsFragment(fragment: WidgetsFragment) {
        fragment.onEditInteractionActiveChanged = { active ->
            isWidgetEditInteractionActive = active
            updateUserInputEnabled()
        }
    }

    private inner class WorkspacePagerAdapter(
        private val pages: List<WorkspacePage>,
    ) : RecyclerView.Adapter<WorkspacePageViewHolder>() {

        init {
            setHasStableIds(true)
        }

        override fun getItemCount(): Int = pages.size

        override fun getItemId(position: Int): Long = pages[position].stableId

        override fun getItemViewType(position: Int): Int = pages[position].ordinal

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkspacePageViewHolder {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return WorkspacePageViewHolder(container).apply {
                setIsRecyclable(false)
            }
        }

        override fun onBindViewHolder(holder: WorkspacePageViewHolder, position: Int) {
            when (pages[position]) {
                WorkspacePage.HOME -> attachHomePage(holder.container)
                WorkspacePage.WIDGETS -> attachWidgetsPage(holder.container)
            }
        }
    }

    private class WorkspacePageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    companion object {
        private const val PREFS_FILENAME = "app.olauncher"
        private const val KEY_WIDGET_SCREEN_POSITION = "FORK_WIDGET_SCREEN_POSITION"
        private const val WIDGETS_FRAGMENT_TAG = "fork_widgets_screen"
    }
}
