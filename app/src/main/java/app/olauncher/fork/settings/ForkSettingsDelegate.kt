package app.olauncher.fork.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.ForkSettingsSectionBinding
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.fork.widgets.WidgetScreenPosition
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.openUrl
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast

/**
 * Injects the "Abacate Lançador" section into [app.olauncher.ui.SettingsFragment]
 * and applies fork swipe-precedence rules without altering upstream layout XMLs.
 */
class ForkSettingsDelegate(
    private val context: Context,
    private val prefs: Prefs,
) {
    private var sectionBinding: ForkSettingsSectionBinding? = null

    fun applyIdentityAndSection(
        binding: FragmentSettingsBinding,
        onSwipePolicyChanged: () -> Unit,
    ) {
        binding.olauncherHiddenApps.text = context.getString(R.string.fork_app_name)

        if (binding.scrollLayout.findViewById<View>(R.id.forkSettingsSection) != null) {
            return
        }

        val inflated = ForkSettingsSectionBinding.inflate(
            LayoutInflater.from(context),
            binding.scrollLayout,
            false,
        )
        sectionBinding = inflated

        // Insert directly after the Gestures card and before the bottom social/privacy links row.
        val insertIndex = (binding.scrollLayout.childCount - 2).coerceAtLeast(0)
        binding.scrollLayout.addView(inflated.root, insertIndex)

        populateSection(inflated)

        val positionClickListener = View.OnClickListener { anchor ->
            showWidgetPositionMenu(anchor, inflated, onSwipePolicyChanged)
        }
        inflated.forkWidgetPositionRow.setOnClickListener(positionClickListener)
        inflated.forkWidgetPositionValue.setOnClickListener(positionClickListener)

        val minWidthClickListener = View.OnClickListener {
            prefs.allowMinWidth1 = !prefs.allowMinWidth1
            populateMinSpanToggles(inflated)
        }
        inflated.forkAllowMinWidth1Row.setOnClickListener(minWidthClickListener)
        inflated.forkAllowMinWidth1Value.setOnClickListener(minWidthClickListener)

        val minHeightClickListener = View.OnClickListener {
            prefs.allowMinHeight1 = !prefs.allowMinHeight1
            populateMinSpanToggles(inflated)
        }
        inflated.forkAllowMinHeight1Row.setOnClickListener(minHeightClickListener)
        inflated.forkAllowMinHeight1Value.setOnClickListener(minHeightClickListener)

        inflated.forkGithubLink.setOnClickListener { context.openUrl(Constants.URL_FORK_GITHUB) }
        inflated.forkIssuesLink.setOnClickListener { context.openUrl(Constants.URL_FORK_ISSUES) }
        inflated.forkUpstreamCredit.setOnClickListener { context.openUrl(Constants.URL_OLAUNCHER_GITHUB) }
    }

    fun populateSwipeApps(binding: FragmentSettingsBinding) {
        val reservedLabel = context.getString(R.string.fork_swipe_reserved_for_widgets)
        val position = prefs.widgetScreenPosition

        val leftState = ForkSwipePolicy.resolveSwipeLeftDisplay(
            position = position,
            swipeLeftEnabled = prefs.swipeLeftEnabled,
            appNameSwipeLeft = prefs.appNameSwipeLeft,
            reservedLabel = reservedLabel,
        )
        val rightState = ForkSwipePolicy.resolveSwipeRightDisplay(
            position = position,
            swipeRightEnabled = prefs.swipeRightEnabled,
            appNameSwipeRight = prefs.appNameSwipeRight,
            reservedLabel = reservedLabel,
        )

        val activeColor = context.getColorFromAttr(R.attr.primaryColor)
        val dimmedColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        binding.swipeLeftApp.text = leftState.label
        binding.swipeLeftApp.setTextColor(if (leftState.isDimmed) dimmedColor else activeColor)

        binding.swipeRightApp.text = rightState.label
        binding.swipeRightApp.setTextColor(if (rightState.isDimmed) dimmedColor else activeColor)
    }

    /**
     * Returns `true` if the swipe direction identified by [flag] is reserved by the Widget Screen
     * and therefore blocked from app selection or toggling.
     */
    fun handleSwipeAppClickIfReserved(flag: Int): Boolean {
        val position = prefs.widgetScreenPosition
        val reserved = when (flag) {
            Constants.FLAG_SET_SWIPE_LEFT_APP -> ForkSwipePolicy.isSwipeLeftReserved(position)
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> ForkSwipePolicy.isSwipeRightReserved(position)
            else -> false
        }
        if (reserved) {
            context.showToast(R.string.fork_swipe_reserved_for_widgets)
        }
        return reserved
    }

    private fun populateSection(inflated: ForkSettingsSectionBinding) {
        populateWidgetPosition(inflated)
        populateMinSpanToggles(inflated)
        inflated.forkVersionValue.text = context.getString(
            R.string.fork_version_label,
            BuildConfig.VERSION_NAME,
        )
    }

    private fun populateWidgetPosition(inflated: ForkSettingsSectionBinding) {
        val labelRes = when (prefs.widgetScreenPosition) {
            WidgetScreenPosition.DISABLED -> R.string.fork_widget_position_disabled
            WidgetScreenPosition.LEFT -> R.string.fork_widget_position_left
            WidgetScreenPosition.RIGHT -> R.string.fork_widget_position_right
        }
        inflated.forkWidgetPositionValue.text = context.getString(labelRes)
    }

    private fun populateMinSpanToggles(inflated: ForkSettingsSectionBinding) {
        inflated.forkAllowMinWidth1Value.text =
            context.getString(if (prefs.allowMinWidth1) R.string.on else R.string.off)
        inflated.forkAllowMinHeight1Value.text =
            context.getString(if (prefs.allowMinHeight1) R.string.on else R.string.off)
    }

    private fun showWidgetPositionMenu(
        anchor: View,
        inflated: ForkSettingsSectionBinding,
        onSwipePolicyChanged: () -> Unit,
    ) {
        val positions = listOf(
            WidgetScreenPosition.DISABLED to R.string.fork_widget_position_disabled,
            WidgetScreenPosition.LEFT to R.string.fork_widget_position_left,
            WidgetScreenPosition.RIGHT to R.string.fork_widget_position_right,
        )
        anchor.showPopupMenu(
            configure = { menu ->
                positions.forEachIndexed { index, (_, labelRes) ->
                    menu.add(Menu.NONE, index, index, context.getString(labelRes))
                }
            }
        ) { item ->
            val selected = positions.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            if (prefs.widgetScreenPosition != selected) {
                prefs.widgetScreenPosition = selected
                populateWidgetPosition(inflated)
                onSwipePolicyChanged()
            }
        }
    }
}
