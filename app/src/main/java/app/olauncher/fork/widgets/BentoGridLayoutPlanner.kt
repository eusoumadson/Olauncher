package app.olauncher.fork.widgets

/**
 * Apple-style responsive column breakpoints for the Bento Widget Grid.
 */
enum class GridBreakpoint(val columns: Int) {
    COMPACT(4),
    MEDIUM(6),
    EXPANDED(8),
}

data class WidgetPlacementInput(
    val appWidgetId: Int,
    val requestedColSpan: Int,
    val requestedRowSpan: Int,
    val minColSpan: Int = ForkWidgetEntry.DEFAULT_MIN_SPAN,
    val minRowSpan: Int = ForkWidgetEntry.DEFAULT_MIN_SPAN,
    val resizeMode: Int = ForkWidgetEntry.RESIZE_BOTH,
)

data class WidgetPlacementOutput(
    val appWidgetId: Int,
    val column: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val minColSpan: Int,
    val minRowSpan: Int,
    val isFixedSize: Boolean,
    val centerInCell: Boolean,
    val resizableHorizontally: Boolean,
    val resizableVertically: Boolean,
)

data class BentoGridPlan(
    val columnCount: Int,
    val rowCount: Int,
    val placements: List<WidgetPlacementOutput>,
)

fun ForkWidgetEntry.toPlacementInput(): WidgetPlacementInput = WidgetPlacementInput(
    appWidgetId = appWidgetId,
    requestedColSpan = colSpan,
    requestedRowSpan = rowSpan,
    minColSpan = minColSpan,
    minRowSpan = minRowSpan,
    resizeMode = resizeMode,
)

/**
 * Pure Kotlin deterministic first-fit Bento grid layout planner.
 */
object BentoGridLayoutPlanner {
    private const val MEDIUM_BREAKPOINT_DP = 600
    private const val EXPANDED_BREAKPOINT_DP = 840
    private const val DEFAULT_MIN_SPAN = 2

    fun breakpointForWidthDp(widthDp: Int): GridBreakpoint = when {
        widthDp >= EXPANDED_BREAKPOINT_DP -> GridBreakpoint.EXPANDED
        widthDp >= MEDIUM_BREAKPOINT_DP -> GridBreakpoint.MEDIUM
        else -> GridBreakpoint.COMPACT
    }

    fun columnCountForWidthDp(widthDp: Int): Int = breakpointForWidthDp(widthDp).columns

    fun plan(
        entries: List<WidgetPlacementInput>,
        widthDp: Int,
        allowMinWidth1: Boolean = false,
        allowMinHeight1: Boolean = false,
    ): BentoGridPlan {
        val columnCount = columnCountForWidthDp(widthDp)
        if (entries.isEmpty()) {
            return BentoGridPlan(columnCount = columnCount, rowCount = 0, placements = emptyList())
        }

        val floorCol = if (allowMinWidth1) 1 else DEFAULT_MIN_SPAN
        val floorRow = if (allowMinHeight1) 1 else DEFAULT_MIN_SPAN
        val occupancy = mutableListOf<BooleanArray>()
        val placements = ArrayList<WidgetPlacementOutput>(entries.size)

        for (entry in entries) {
            val effectiveMinCol = maxOf(floorCol, entry.minColSpan.coerceAtLeast(1)).coerceIn(1, columnCount)
            val effectiveMinRow = maxOf(floorRow, entry.minRowSpan.coerceAtLeast(1)).coerceAtLeast(1)

            val colSpan = entry.requestedColSpan.coerceIn(effectiveMinCol, columnCount)
            val rowSpan = entry.requestedRowSpan.coerceAtLeast(effectiveMinRow)

            val isFixedSize = entry.resizeMode == ForkWidgetEntry.RESIZE_NONE
            val resizableHorizontally = !isFixedSize &&
                (entry.resizeMode and ForkWidgetEntry.RESIZE_HORIZONTAL) != 0
            val resizableVertically = !isFixedSize &&
                (entry.resizeMode and ForkWidgetEntry.RESIZE_VERTICAL) != 0

            val (row, col) = findFirstFit(occupancy, columnCount, colSpan, rowSpan)
            markOccupied(occupancy, columnCount, row, col, colSpan, rowSpan)

            placements += WidgetPlacementOutput(
                appWidgetId = entry.appWidgetId,
                column = col,
                row = row,
                colSpan = colSpan,
                rowSpan = rowSpan,
                minColSpan = effectiveMinCol,
                minRowSpan = effectiveMinRow,
                isFixedSize = isFixedSize,
                centerInCell = isFixedSize,
                resizableHorizontally = resizableHorizontally,
                resizableVertically = resizableVertically,
            )
        }

        return BentoGridPlan(
            columnCount = columnCount,
            rowCount = occupancy.size,
            placements = placements,
        )
    }

    private fun findFirstFit(
        occupancy: List<BooleanArray>,
        columnCount: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Pair<Int, Int> {
        var row = 0
        while (true) {
            val maxStartCol = columnCount - colSpan
            for (col in 0..maxStartCol) {
                if (fitsAt(occupancy, row, col, colSpan, rowSpan)) {
                    return row to col
                }
            }
            row++
        }
    }

    private fun fitsAt(
        occupancy: List<BooleanArray>,
        startRow: Int,
        startCol: Int,
        colSpan: Int,
        rowSpan: Int,
    ): Boolean {
        val checkedEndRow = minOf(startRow + rowSpan, occupancy.size)
        for (r in startRow until checkedEndRow) {
            val rowCells = occupancy[r]
            for (c in startCol until startCol + colSpan) {
                if (rowCells[c]) return false
            }
        }
        return true
    }

    private fun markOccupied(
        occupancy: MutableList<BooleanArray>,
        columnCount: Int,
        startRow: Int,
        startCol: Int,
        colSpan: Int,
        rowSpan: Int,
    ) {
        val endRow = startRow + rowSpan
        while (occupancy.size < endRow) {
            occupancy += BooleanArray(columnCount)
        }
        for (r in startRow until endRow) {
            val rowCells = occupancy[r]
            for (c in startCol until startCol + colSpan) {
                rowCells[c] = true
            }
        }
    }
}
