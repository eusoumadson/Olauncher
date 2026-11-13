package app.olauncher.fork.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        val basePaddingPx = BASE_GRID_PADDING_DP.dpToPx()
        ViewCompat.setOnApplyWindowInsetsListener(binding.bentoGridLayout) { grid, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            grid.setPadding(
                basePaddingPx + sysBars.left,
                basePaddingPx + sysBars.top,
                basePaddingPx + sysBars.right,
                basePaddingPx + sysBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.bentoGridLayout)

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
        renderWidgets()
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
