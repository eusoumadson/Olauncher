package app.olauncher.fork.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.ui.BaseFragment

/**
 * Dedicated Widget Screen fragment displaying pinned widgets inside a transparent [BentoGridLayoutView]
 * with gesture-only interaction (no static add/edit buttons).
 */
class WidgetsFragment : BaseFragment() {

    private var _binding: FragmentWidgetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var hostManager: ForkWidgetHostManager

    var onEditInteractionActiveChanged: ((Boolean) -> Unit)? = null
    var onEditModeChanged: ((Boolean) -> Unit)? = null

    private val widgetsChangedListener: () -> Unit = {
        if (_binding != null && isAdded) {
            renderWidgets()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hostManager = ForkWidgetHostManager.getInstance(requireContext()).apply {
            setHostViewFactory { ctx, _, _ -> ForkAppWidgetHostView(ctx) }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyGridInsets(insets)
            insets
        }
        applyCurrentWindowInsets()
        binding.root.post { applyCurrentWindowInsets() }

        binding.bentoGridLayout.apply {
            onEmptySpaceLongPressListener = {
                activity?.let { act ->
                    ForkWidgetPinDelegate.startWidgetPicker(act, hostManager)
                }
            }
            onWidgetRemovedListener = { appWidgetId ->
                BentoGridEditActions.removeWidgetAndPersist(
                    appWidgetId = appWidgetId,
                    prefs = hostManager.widgetPrefs,
                    deleteHostWidgetId = { id -> hostManager.deleteAppWidgetId(id) },
                )
                hostManager.syncListeningState()
            }
            onWidgetsPersistedListener = { updated ->
                BentoGridEditActions.persistOrderAndSpans(updated, hostManager.widgetPrefs)
            }
            onEditModeChangedListener = { editing ->
                onEditModeChanged?.invoke(editing)
            }
            onEditInteractionActiveChangedListener = { interacting ->
                onEditInteractionActiveChanged?.invoke(interacting)
            }
        }

        hostManager.addWidgetsChangedListener(widgetsChangedListener)
        renderWidgets()
    }

    override fun onResume() {
        super.onResume()
        applyCurrentWindowInsets()
        renderWidgets()
    }

    private fun applyCurrentWindowInsets() {
        val root = _binding?.root ?: return
        val insets = ViewCompat.getRootWindowInsets(root)
            ?: activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
        applyGridInsets(insets)
        ViewCompat.requestApplyInsets(root)
    }

    private fun applyGridInsets(insets: WindowInsetsCompat?) {
        val grid = _binding?.bentoGridLayout ?: return
        val basePaddingPx = BASE_GRID_PADDING_DP.dpToPx()
        val visible = insets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val persistentMask = if (Prefs(requireContext()).showStatusBar) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        } else {
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
        }
        val persistent = insets?.getInsetsIgnoringVisibility(persistentMask)
        val topInset = maxOf(visible?.top ?: 0, persistent?.top ?: 0)
        val bottomInset = maxOf(visible?.bottom ?: 0, persistent?.bottom ?: 0)
        val leftInset = maxOf(visible?.left ?: 0, persistent?.left ?: 0)
        val rightInset = maxOf(visible?.right ?: 0, persistent?.right ?: 0)
        if (grid.paddingLeft != basePaddingPx + leftInset ||
            grid.paddingTop != basePaddingPx + topInset ||
            grid.paddingRight != basePaddingPx + rightInset ||
            grid.paddingBottom != basePaddingPx + bottomInset
        ) {
            grid.setPadding(
                basePaddingPx + leftInset,
                basePaddingPx + topInset,
                basePaddingPx + rightInset,
                basePaddingPx + bottomInset,
            )
        }
    }

    override fun onPause() {
        binding.bentoGridLayout.exitEditModeIfActive()
        super.onPause()
    }

    override fun onDestroyView() {
        hostManager.removeWidgetsChangedListener(widgetsChangedListener)
        _binding = null
        super.onDestroyView()
    }

    fun exitEditModeIfActive(): Boolean {
        return _binding?.bentoGridLayout?.exitEditModeIfActive() ?: false
    }

    fun renderWidgets() {
        val binding = _binding ?: return
        val prefs = hostManager.widgetPrefs
        val entries = hostManager.pruneOrphanedWidgets()
        binding.bentoGridLayout.bindWidgets(
            widgetEntries = entries,
            allowMinWidth1 = prefs.allowMinWidth1,
            allowMinHeight1 = prefs.allowMinHeight1,
            hostViewProvider = { entry ->
                val info = hostManager.getAppWidgetInfo(entry.appWidgetId) ?: return@bindWidgets null
                runCatching {
                    hostManager.createView(requireContext(), entry.appWidgetId, info)
                }.getOrNull()
            },
            providerInfoLookup = { appWidgetId -> hostManager.getAppWidgetInfo(appWidgetId) },
        )
    }

    companion object {
        private const val BASE_GRID_PADDING_DP = 16
    }
}
