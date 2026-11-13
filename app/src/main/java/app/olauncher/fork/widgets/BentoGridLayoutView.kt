package app.olauncher.fork.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.SizeF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.helper.dpToPx
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pure Kotlin edit actions (drag-and-drop reorder, resize clamping, removal, and persistence)
 * for the Bento Widget Grid, testable on the JVM without Android framework views.
 */
object BentoGridEditActions {
    const val MAX_ROW_SPAN = 8

    fun reorderToIndex(
        entries: List<ForkWidgetEntry>,
        draggedAppWidgetId: Int,
        targetIndex: Int,
    ): List<ForkWidgetEntry> {
        val sorted = entries.sortedBy { it.order }
        val fromIndex = sorted.indexOfFirst { it.appWidgetId == draggedAppWidgetId }
        if (fromIndex < 0) return sorted

        val mutable = sorted.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        val clampedTarget = targetIndex.coerceIn(0, mutable.size)
        mutable.add(clampedTarget, moved)
        return mutable.mapIndexed { index, entry -> entry.copy(order = index) }
    }

    fun reorderByDrop(
        entries: List<ForkWidgetEntry>,
        draggedAppWidgetId: Int,
        targetAppWidgetId: Int,
    ): List<ForkWidgetEntry> {
        if (draggedAppWidgetId == targetAppWidgetId) return entries.sortedBy { it.order }
        val sorted = entries.sortedBy { it.order }
        val targetIndex = sorted.indexOfFirst { it.appWidgetId == targetAppWidgetId }
        if (targetIndex < 0) return sorted
        return reorderToIndex(sorted, draggedAppWidgetId, targetIndex)
    }

    fun findDropTargetWidgetId(
        placements: List<WidgetPlacementOutput>,
        draggedAppWidgetId: Int,
        pointerX: Float,
        pointerY: Float,
        paddingLeft: Float,
        paddingTop: Float,
        cellSizePx: Float,
        gapPx: Float,
    ): Int? {
        if (cellSizePx <= 0f) return null
        val step = cellSizePx + gapPx
        for (placement in placements) {
            if (placement.appWidgetId == draggedAppWidgetId) continue
            val left = paddingLeft + placement.column * step
            val top = paddingTop + placement.row * step
            val right = left + placement.colSpan * cellSizePx + (placement.colSpan - 1) * gapPx
            val bottom = top + placement.rowSpan * cellSizePx + (placement.rowSpan - 1) * gapPx
            if (pointerX in left..right && pointerY in top..bottom) {
                return placement.appWidgetId
            }
        }
        return null
    }

    fun resizeWidget(
        entries: List<ForkWidgetEntry>,
        appWidgetId: Int,
        requestedColSpan: Int,
        requestedRowSpan: Int,
        columnCount: Int,
        allowMinWidth1: Boolean,
        allowMinHeight1: Boolean,
    ): List<ForkWidgetEntry> {
        val floorCol = if (allowMinWidth1) 1 else ForkWidgetEntry.DEFAULT_SPAN
        val floorRow = if (allowMinHeight1) 1 else ForkWidgetEntry.DEFAULT_SPAN

        return entries.map { entry ->
            if (entry.appWidgetId != appWidgetId) {
                entry
            } else if (entry.resizeMode == ForkWidgetEntry.RESIZE_NONE) {
                entry
            } else {
                val effectiveMinCol = maxOf(floorCol, entry.minColSpan.coerceAtLeast(1))
                    .coerceIn(1, columnCount.coerceAtLeast(1))
                val effectiveMinRow = maxOf(floorRow, entry.minRowSpan.coerceAtLeast(1))
                    .coerceIn(1, MAX_ROW_SPAN)

                val canResizeH = (entry.resizeMode and ForkWidgetEntry.RESIZE_HORIZONTAL) != 0
                val canResizeV = (entry.resizeMode and ForkWidgetEntry.RESIZE_VERTICAL) != 0

                val nextCol = if (canResizeH) {
                    requestedColSpan.coerceIn(effectiveMinCol, columnCount.coerceAtLeast(effectiveMinCol))
                } else {
                    entry.colSpan.coerceIn(effectiveMinCol, columnCount.coerceAtLeast(effectiveMinCol))
                }
                val nextRow = if (canResizeV) {
                    requestedRowSpan.coerceIn(effectiveMinRow, MAX_ROW_SPAN)
                } else {
                    entry.rowSpan.coerceIn(effectiveMinRow, MAX_ROW_SPAN)
                }

                entry.copy(colSpan = nextCol, rowSpan = nextRow)
            }
        }
    }

    fun removeWidgetAndPersist(
        appWidgetId: Int,
        prefs: ForkWidgetPrefs,
        deleteHostWidgetId: (Int) -> Unit,
    ): List<ForkWidgetEntry> {
        deleteHostWidgetId(appWidgetId)
        return prefs.removeWidget(appWidgetId)
    }

    fun persistOrderAndSpans(
        entries: List<ForkWidgetEntry>,
        prefs: ForkWidgetPrefs,
    ): List<ForkWidgetEntry> {
        val normalized = entries.mapIndexed { index, entry -> entry.copy(order = index) }
        prefs.saveWidgets(normalized)
        return normalized
    }
}

/**
 * Custom [ViewGroup] rendering widgets in a responsive Apple-style Bento Grid
 * ([BentoGridLayoutPlanner]) with gesture-only editing (long-press empty space to add,
 * long-press widget for drag-and-drop reorder, resize handles, and removal badge; tap outside to commit).
 */
class BentoGridLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val emptySpaceTracker = ForkLongPressTouchTracker(touchSlop)
    private val editStateMachine = ForkWidgetEditStateMachine()

    private val gapPx: Int = GRID_GAP_DP.dpToPx()
    private var lastColumnCount: Int = GridBreakpoint.COMPACT.columns
    private var lastCellSizePx: Float = 0f
    private var currentPlan: BentoGridPlan = BentoGridPlan(GridBreakpoint.COMPACT.columns, 0, emptyList())
    private var entries: MutableList<ForkWidgetEntry> = mutableListOf()
    private var allowMinWidth1: Boolean = false
    private var allowMinHeight1: Boolean = false

    private val cardByWidgetId = LinkedHashMap<Int, WidgetCardContainer>()
    private var activeDragWidgetId: Int? = null
    private var activeResizeWidgetId: Int? = null

    var onEmptySpaceLongPressListener: (() -> Unit)? = null
    var onWidgetRemovedListener: ((Int) -> Unit)? = null
    var onWidgetsPersistedListener: ((List<ForkWidgetEntry>) -> Unit)? = null
    var onEditModeChangedListener: ((Boolean) -> Unit)? = null
    var onEditInteractionActiveChangedListener: ((Boolean) -> Unit)? = null

    private val emptySpaceLongPressRunnable = Runnable {
        if (!editStateMachine.isEditing && emptySpaceTracker.markLongPressTriggered()) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            onEmptySpaceLongPressListener?.invoke()
        }
    }

    init {
        isClickable = true
        isFocusable = true
    }

    val isEditMode: Boolean
        get() = editStateMachine.isEditing

    fun bindWidgets(
        widgetEntries: List<ForkWidgetEntry>,
        allowMinWidth1: Boolean,
        allowMinHeight1: Boolean,
        hostViewProvider: (ForkWidgetEntry) -> View?,
        providerInfoLookup: (Int) -> AppWidgetProviderInfo?,
    ) {
        this.allowMinWidth1 = allowMinWidth1
        this.allowMinHeight1 = allowMinHeight1
        this.entries = widgetEntries.sortedBy { it.order }.toMutableList()

        val desiredIds = entries.map { it.appWidgetId }.toSet()
        val staleIds = cardByWidgetId.keys.filter { it !in desiredIds }
        for (staleId in staleIds) {
            cardByWidgetId.remove(staleId)?.let { removeView(it) }
        }

        for (entry in entries) {
            val existingCard = cardByWidgetId[entry.appWidgetId]
            if (existingCard == null) {
                val hostView = hostViewProvider(entry) ?: continue
                if (hostView is ForkAppWidgetHostView) {
                    hostView.onWidgetLongPressListener = {
                        enterEditMode(entry.appWidgetId)
                    }
                }
                val card = WidgetCardContainer(
                    context = context,
                    appWidgetId = entry.appWidgetId,
                    hostView = hostView,
                    providerInfo = providerInfoLookup(entry.appWidgetId),
                )
                cardByWidgetId[entry.appWidgetId] = card
                addView(card)
            } else {
                existingCard.providerInfo = providerInfoLookup(entry.appWidgetId)
            }
        }

        if (entries.isEmpty() && editStateMachine.isEditing) {
            exitEditMode(persist = false)
        } else {
            syncCardEditStates()
        }
        requestLayout()
        invalidate()
    }

    fun enterEditMode(appWidgetId: Int) {
        val wasEditing = editStateMachine.isEditing
        editStateMachine.enterEditMode(appWidgetId)
        syncCardEditStates()
        if (!wasEditing) {
            onEditModeChangedListener?.invoke(true)
        }
    }

    fun exitEditModeIfActive(): Boolean {
        if (!editStateMachine.isEditing) return false
        exitEditMode(persist = true)
        return true
    }

    private fun exitEditMode(persist: Boolean) {
        activeDragWidgetId = null
        activeResizeWidgetId = null
        onEditInteractionActiveChangedListener?.invoke(false)
        editStateMachine.exitEditMode()
        syncCardEditStates()
        onEditModeChangedListener?.invoke(false)
        if (persist) {
            persistCurrentEntries()
        }
    }

    private fun persistCurrentEntries() {
        val normalized = entries.mapIndexed { index, entry -> entry.copy(order = index) }
        entries = normalized.toMutableList()
        onWidgetsPersistedListener?.invoke(normalized)
    }

    private fun syncCardEditStates() {
        val editing = editStateMachine.isEditing
        val placementById = currentPlan.placements.associateBy { it.appWidgetId }
        for ((widgetId, card) in cardByWidgetId) {
            card.setEditState(
                editing = editing,
                selected = editStateMachine.selectedAppWidgetId == widgetId,
                placement = placementById[widgetId],
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthPx = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val widthDp = (widthPx / density).roundToInt()

        currentPlan = BentoGridLayoutPlanner.plan(
            entries = entries.map { it.toPlacementInput() },
            widthDp = widthDp,
            allowMinWidth1 = allowMinWidth1,
            allowMinHeight1 = allowMinHeight1,
        )
        lastColumnCount = currentPlan.columnCount

        val usableWidth = (widthPx - paddingLeft - paddingRight).coerceAtLeast(0)
        val totalGaps = gapPx * (currentPlan.columnCount - 1)
        val cellSize = if (currentPlan.columnCount > 0) {
            ((usableWidth - totalGaps).toFloat() / currentPlan.columnCount).coerceAtLeast(1f)
        } else {
            0f
        }
        lastCellSizePx = cellSize

        val placementById = currentPlan.placements.associateBy { it.appWidgetId }
        for ((widgetId, card) in cardByWidgetId) {
            val placement = placementById[widgetId] ?: continue
            val cardWidth = spanPixels(placement.colSpan, cellSize)
            val cardHeight = spanPixels(placement.rowSpan, cellSize)
            card.measure(
                MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY),
            )
            card.setEditState(
                editing = editStateMachine.isEditing,
                selected = editStateMachine.selectedAppWidgetId == widgetId,
                placement = placement,
            )
        }

        val gridHeight = if (currentPlan.rowCount <= 0) {
            0
        } else {
            spanPixels(currentPlan.rowCount, cellSize)
        }
        val desiredHeight = maxOf(suggestedMinimumHeight, paddingTop + paddingBottom + gridHeight)
        setMeasuredDimension(
            resolveSize(widthPx, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellSize = lastCellSizePx
        val step = cellSize + gapPx
        val density = resources.displayMetrics.density.coerceAtLeast(1f)

        for (placement in currentPlan.placements) {
            val card = cardByWidgetId[placement.appWidgetId] ?: continue
            val left = paddingLeft + (placement.column * step).roundToInt()
            val top = paddingTop + (placement.row * step).roundToInt()
            val width = spanPixels(placement.colSpan, cellSize)
            val height = spanPixels(placement.rowSpan, cellSize)
            card.layout(left, top, left + width, top + height)

            val widthDp = (width / density).roundToInt().coerceAtLeast(1)
            val heightDp = (height / density).roundToInt().coerceAtLeast(1)
            card.updateWidgetSize(widthDp, heightDp, placement)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val overAnyCard = findCardUnder(event.x, event.y) != null

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (overAnyCard) {
                    cancelEmptySpaceLongPress()
                    return false
                }
                if (editStateMachine.isEditing) {
                    return true
                }
                emptySpaceTracker.onDown(event.x, event.y)
                postDelayed(emptySpaceLongPressRunnable, longPressTimeoutMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (emptySpaceTracker.onMove(event.x, event.y)) {
                    removeCallbacks(emptySpaceLongPressRunnable)
                }
                return !overAnyCard
            }

            MotionEvent.ACTION_UP -> {
                val longPressed = emptySpaceTracker.longPressTriggered
                cancelEmptySpaceLongPress()
                if (!longPressed && !overAnyCard && editStateMachine.isEditing) {
                    exitEditMode(persist = true)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelEmptySpaceLongPress()
                return false
            }

            else -> return super.onTouchEvent(event)
        }
    }

    override fun onDetachedFromWindow() {
        cancelEmptySpaceLongPress()
        super.onDetachedFromWindow()
    }

    private fun cancelEmptySpaceLongPress() {
        removeCallbacks(emptySpaceLongPressRunnable)
        emptySpaceTracker.reset()
    }

    private fun findCardUnder(x: Float, y: Float): WidgetCardContainer? {
        for (card in cardByWidgetId.values) {
            if (card.visibility == View.VISIBLE &&
                x >= card.left && x <= card.right &&
                y >= card.top && y <= card.bottom
            ) {
                return card
            }
        }
        return null
    }

    private fun spanPixels(span: Int, cellSize: Float): Int {
        if (span <= 0) return 0
        return (span * cellSize + (span - 1) * gapPx).roundToInt().coerceAtLeast(1)
    }

    private fun handleCardDraggedTo(appWidgetId: Int, pointerGridX: Float, pointerGridY: Float) {
        val targetId = BentoGridEditActions.findDropTargetWidgetId(
            placements = currentPlan.placements,
            draggedAppWidgetId = appWidgetId,
            pointerX = pointerGridX,
            pointerY = pointerGridY,
            paddingLeft = paddingLeft.toFloat(),
            paddingTop = paddingTop.toFloat(),
            cellSizePx = lastCellSizePx,
            gapPx = gapPx.toFloat(),
        ) ?: return

        val reordered = BentoGridEditActions.reorderByDrop(entries, appWidgetId, targetId)
        if (reordered != entries) {
            entries = reordered.toMutableList()
            requestLayout()
        }
    }

    private fun handleCardResized(appWidgetId: Int, targetColSpan: Int, targetRowSpan: Int) {
        val resized = BentoGridEditActions.resizeWidget(
            entries = entries,
            appWidgetId = appWidgetId,
            requestedColSpan = targetColSpan,
            requestedRowSpan = targetRowSpan,
            columnCount = lastColumnCount,
            allowMinWidth1 = allowMinWidth1,
            allowMinHeight1 = allowMinHeight1,
        )
        if (resized != entries) {
            entries = resized.toMutableList()
            requestLayout()
        }
    }

    private fun handleCardRemoveClicked(appWidgetId: Int) {
        entries = entries.filterNot { it.appWidgetId == appWidgetId }
            .mapIndexed { index, entry -> entry.copy(order = index) }
            .toMutableList()
        cardByWidgetId.remove(appWidgetId)?.let { removeView(it) }
        editStateMachine.onWidgetRemoved(appWidgetId, entries.map { it.appWidgetId })
        if (!editStateMachine.isEditing) {
            onEditModeChangedListener?.invoke(false)
        }
        syncCardEditStates()
        requestLayout()
        onWidgetRemovedListener?.invoke(appWidgetId)
    }

    private enum class ResizeEdge {
        HORIZONTAL,
        VERTICAL,
        BOTH,
    }

    private inner class WidgetCardContainer(
        context: Context,
        val appWidgetId: Int,
        val hostView: View,
        var providerInfo: AppWidgetProviderInfo?,
    ) : FrameLayout(context) {

        private val hostWrapper = FrameLayout(context)
        private val borderOverlay = View(context)
        private val removeBadge = ImageView(context)
        private val rightResizeHandle = ImageView(context)
        private val bottomResizeHandle = ImageView(context)
        private val cornerResizeHandle = ImageView(context)

        private var isEditing = false
        private var currentPlacement: WidgetPlacementOutput? = null

        private var dragStartRawX = 0f
        private var dragStartRawY = 0f
        private var isDragging = false

        init {
            clipChildren = false
            clipToPadding = false

            addView(
                hostWrapper,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
            hostWrapper.addView(
                hostView,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER),
            )

            borderOverlay.background = ContextCompat.getDrawable(context, R.drawable.fork_widget_edit_border)
            borderOverlay.visibility = View.GONE
            addView(
                borderOverlay,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )

            val badgeSize = REMOVE_BADGE_SIZE_DP.dpToPx()
            val badgePadding = 5.dpToPx()
            removeBadge.apply {
                background = ContextCompat.getDrawable(context, R.drawable.bg_shade_circular)
                setImageResource(R.drawable.ic_close)
                setPadding(badgePadding, badgePadding, badgePadding, badgePadding)
                contentDescription = context.getString(R.string.fork_remove_widget)
                visibility = View.GONE
                setOnClickListener { handleCardRemoveClicked(appWidgetId) }
            }
            addView(
                removeBadge,
                LayoutParams(badgeSize, badgeSize, Gravity.TOP or Gravity.END).apply {
                    topMargin = 4.dpToPx()
                    marginEnd = 4.dpToPx()
                },
            )

            val handleSize = RESIZE_HANDLE_SIZE_DP.dpToPx()
            configureResizeHandle(rightResizeHandle, handleSize, Gravity.END or Gravity.CENTER_VERTICAL, ResizeEdge.HORIZONTAL)
            configureResizeHandle(bottomResizeHandle, handleSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, ResizeEdge.VERTICAL)
            configureResizeHandle(cornerResizeHandle, handleSize, Gravity.BOTTOM or Gravity.END, ResizeEdge.BOTH)
        }

        private fun configureResizeHandle(
            handle: ImageView,
            sizePx: Int,
            gravity: Int,
            edge: ResizeEdge,
        ) {
            handle.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.fork_widget_resize_handle))
            handle.scaleType = ImageView.ScaleType.CENTER
            handle.visibility = View.GONE
            val touchPadding = 6.dpToPx()
            handle.setPadding(touchPadding, touchPadding, touchPadding, touchPadding)
            val totalSize = sizePx + touchPadding * 2
            addView(handle, LayoutParams(totalSize, totalSize, gravity))

            var startRawX = 0f
            var startRawY = 0f
            var initialColSpan = 2
            var initialRowSpan = 2

            handle.setOnTouchListener { _, ev ->
                if (!isEditing) return@setOnTouchListener false
                val placement = currentPlacement ?: return@setOnTouchListener false
                if (placement.isFixedSize) return@setOnTouchListener false

                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = ev.rawX
                        startRawY = ev.rawY
                        initialColSpan = placement.colSpan
                        initialRowSpan = placement.rowSpan
                        activeResizeWidgetId = appWidgetId
                        editStateMachine.selectWidget(appWidgetId)
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onEditInteractionActiveChangedListener?.invoke(true)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val step = (lastCellSizePx + gapPx).coerceAtLeast(1f)
                        val deltaCols = if (edge == ResizeEdge.HORIZONTAL || edge == ResizeEdge.BOTH) {
                            ((ev.rawX - startRawX) / step).roundToInt()
                        } else {
                            0
                        }
                        val deltaRows = if (edge == ResizeEdge.VERTICAL || edge == ResizeEdge.BOTH) {
                            ((ev.rawY - startRawY) / step).roundToInt()
                        } else {
                            0
                        }
                        handleCardResized(
                            appWidgetId = appWidgetId,
                            targetColSpan = initialColSpan + deltaCols,
                            targetRowSpan = initialRowSpan + deltaRows,
                        )
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeResizeWidgetId = null
                        onEditInteractionActiveChangedListener?.invoke(false)
                        persistCurrentEntries()
                        true
                    }

                    else -> false
                }
            }
        }

        fun setEditState(
            editing: Boolean,
            selected: Boolean,
            placement: WidgetPlacementOutput?,
        ) {
            isEditing = editing
            currentPlacement = placement
            if (hostView is ForkAppWidgetHostView) {
                hostView.isEditMode = editing
            }

            borderOverlay.visibility = if (editing) View.VISIBLE else View.GONE
            borderOverlay.alpha = if (selected) 1f else 0.7f
            removeBadge.visibility = if (editing) View.VISIBLE else View.GONE

            val showH = editing && placement != null && !placement.isFixedSize && placement.resizableHorizontally
            val showV = editing && placement != null && !placement.isFixedSize && placement.resizableVertically
            rightResizeHandle.visibility = if (showH) View.VISIBLE else View.GONE
            bottomResizeHandle.visibility = if (showV) View.VISIBLE else View.GONE
            cornerResizeHandle.visibility = if (showH && showV) View.VISIBLE else View.GONE
        }

        fun updateWidgetSize(widthDp: Int, heightDp: Int, placement: WidgetPlacementOutput) {
            val host = hostView as? ForkAppWidgetHostView ?: return
            val lp = host.layoutParams as? LayoutParams ?: return
            if (placement.centerInCell) {
                val info = providerInfo
                val targetW = info?.minWidth?.takeIf { it > 0 }?.dpToPx()?.coerceAtMost(measuredWidth)
                    ?: LayoutParams.WRAP_CONTENT
                val targetH = info?.minHeight?.takeIf { it > 0 }?.dpToPx()?.coerceAtMost(measuredHeight)
                    ?: LayoutParams.WRAP_CONTENT
                if (lp.width != targetW || lp.height != targetH || lp.gravity != Gravity.CENTER) {
                    lp.width = targetW
                    lp.height = targetH
                    lp.gravity = Gravity.CENTER
                    host.layoutParams = lp
                }
            } else {
                if (lp.width != LayoutParams.MATCH_PARENT || lp.height != LayoutParams.MATCH_PARENT) {
                    lp.width = LayoutParams.MATCH_PARENT
                    lp.height = LayoutParams.MATCH_PARENT
                    lp.gravity = Gravity.CENTER
                    host.layoutParams = lp
                }
            }

            val options = Bundle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                host.updateAppWidgetSize(
                    options,
                    listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                )
            } else {
                @Suppress("DEPRECATION")
                host.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEditing) return super.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isTouchInChild(removeBadge, event) ||
                        isTouchInChild(rightResizeHandle, event) ||
                        isTouchInChild(bottomResizeHandle, event) ||
                        isTouchInChild(cornerResizeHandle, event)
                    ) {
                        return false
                    }
                    dragStartRawX = event.rawX
                    dragStartRawY = event.rawY
                    isDragging = false
                    editStateMachine.selectWidget(appWidgetId)
                    syncCardEditStates()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartRawX
                    val dy = event.rawY - dragStartRawY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        activeDragWidgetId = appWidgetId
                        bringToFront()
                        alpha = 0.85f
                        scaleX = 1.03f
                        scaleY = 1.03f
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onEditInteractionActiveChangedListener?.invoke(true)
                    }
                    if (isDragging) {
                        translationX = dx
                        translationY = dy
                        val centerInGridX = left + translationX + width / 2f
                        val centerInGridY = top + translationY + height / 2f
                        handleCardDraggedTo(appWidgetId, centerInGridX, centerInGridY)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        activeDragWidgetId = null
                        translationX = 0f
                        translationY = 0f
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        onEditInteractionActiveChangedListener?.invoke(false)
                        persistCurrentEntries()
                    }
                    return true
                }

                else -> return super.onTouchEvent(event)
            }
        }

        private fun isTouchInChild(child: View, event: MotionEvent): Boolean {
            if (child.visibility != View.VISIBLE) return false
            val rect = RectF(
                child.left.toFloat(),
                child.top.toFloat(),
                child.right.toFloat(),
                child.bottom.toFloat(),
            )
            return rect.contains(event.x, event.y)
        }
    }

    companion object {
        private const val GRID_GAP_DP = 12
        private const val REMOVE_BADGE_SIZE_DP = 28
        private const val RESIZE_HANDLE_SIZE_DP = 16
    }
}
