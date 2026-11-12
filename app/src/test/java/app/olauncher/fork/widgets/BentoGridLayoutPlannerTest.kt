package app.olauncher.fork.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BentoGridLayoutPlannerTest {

    @Test
    fun `columnCountForWidthDp follows Apple responsive breakpoints`() {
        assertEquals(4, BentoGridLayoutPlanner.columnCountForWidthDp(360))
        assertEquals(4, BentoGridLayoutPlanner.columnCountForWidthDp(599))
        assertEquals(6, BentoGridLayoutPlanner.columnCountForWidthDp(600))
        assertEquals(6, BentoGridLayoutPlanner.columnCountForWidthDp(700))
        assertEquals(6, BentoGridLayoutPlanner.columnCountForWidthDp(839))
        assertEquals(8, BentoGridLayoutPlanner.columnCountForWidthDp(840))
        assertEquals(8, BentoGridLayoutPlanner.columnCountForWidthDp(900))
    }

    @Test
    fun `extreme narrow and zero widths clamp safely to compact 4 columns`() {
        assertEquals(4, BentoGridLayoutPlanner.columnCountForWidthDp(0))
        assertEquals(4, BentoGridLayoutPlanner.columnCountForWidthDp(-120))

        val plan = BentoGridLayoutPlanner.plan(
            entries = listOf(
                WidgetPlacementInput(appWidgetId = 1, requestedColSpan = 10, requestedRowSpan = 0)
            ),
            widthDp = 0,
        )

        assertEquals(4, plan.columnCount)
        assertEquals(1, plan.placements.size)
        assertEquals(4, plan.placements[0].colSpan)
        assertEquals(2, plan.placements[0].rowSpan)
        assertEquals(0, plan.placements[0].column)
        assertEquals(0, plan.placements[0].row)
    }

    @Test
    fun `first fit fills earlier compatible gaps without overlaps`() {
        val entries = listOf(
            WidgetPlacementInput(appWidgetId = 1, requestedColSpan = 3, requestedRowSpan = 2),
            WidgetPlacementInput(appWidgetId = 2, requestedColSpan = 2, requestedRowSpan = 2),
            WidgetPlacementInput(appWidgetId = 3, requestedColSpan = 1, requestedRowSpan = 2),
            WidgetPlacementInput(appWidgetId = 4, requestedColSpan = 2, requestedRowSpan = 2),
        )

        val plan = BentoGridLayoutPlanner.plan(
            entries = entries,
            widthDp = 360,
            allowMinWidth1 = true,
            allowMinHeight1 = false,
        )

        val byId = plan.placements.associateBy { it.appWidgetId }
        // Item 1 takes (col 0..2, row 0..1), leaving col 3 open in rows 0..1
        assertEquals(0, byId[1]!!.column)
        assertEquals(0, byId[1]!!.row)

        // Item 2 (2x2) does not fit in remaining 1 col at row 0..1, so goes to row 2
        assertEquals(0, byId[2]!!.column)
        assertEquals(2, byId[2]!!.row)

        // Item 3 (1x2) MUST backfill the earlier gap at (col=3, row=0)
        assertEquals(3, byId[3]!!.column)
        assertEquals(0, byId[3]!!.row)

        // Item 4 (2x2) fits next to item 2 at (col=2, row=2)
        assertEquals(2, byId[4]!!.column)
        assertEquals(2, byId[4]!!.row)

        assertNoOverlaps(plan)
    }

    @Test
    fun `default minimum 2x2 is enforced unless 1x toggles are enabled`() {
        val smallRequest = listOf(
            WidgetPlacementInput(appWidgetId = 10, requestedColSpan = 1, requestedRowSpan = 1)
        )

        val defaultPlan = BentoGridLayoutPlanner.plan(smallRequest, widthDp = 360)
        assertEquals(2, defaultPlan.placements[0].colSpan)
        assertEquals(2, defaultPlan.placements[0].rowSpan)
        assertEquals(2, defaultPlan.placements[0].minColSpan)
        assertEquals(2, defaultPlan.placements[0].minRowSpan)

        val width1Only = BentoGridLayoutPlanner.plan(
            smallRequest,
            widthDp = 360,
            allowMinWidth1 = true,
            allowMinHeight1 = false,
        )
        assertEquals(1, width1Only.placements[0].colSpan)
        assertEquals(2, width1Only.placements[0].rowSpan)

        val height1Only = BentoGridLayoutPlanner.plan(
            smallRequest,
            widthDp = 360,
            allowMinWidth1 = false,
            allowMinHeight1 = true,
        )
        assertEquals(2, height1Only.placements[0].colSpan)
        assertEquals(1, height1Only.placements[0].rowSpan)

        val both1x = BentoGridLayoutPlanner.plan(
            smallRequest,
            widthDp = 360,
            allowMinWidth1 = true,
            allowMinHeight1 = true,
        )
        assertEquals(1, both1x.placements[0].colSpan)
        assertEquals(1, both1x.placements[0].rowSpan)
    }

    @Test
    fun `resize none marks fixed size centered and disables resize handles while respecting min bounds`() {
        val plan = BentoGridLayoutPlanner.plan(
            entries = listOf(
                WidgetPlacementInput(
                    appWidgetId = 55,
                    requestedColSpan = 1,
                    requestedRowSpan = 1,
                    resizeMode = ForkWidgetEntry.RESIZE_NONE,
                ),
                WidgetPlacementInput(
                    appWidgetId = 56,
                    requestedColSpan = 2,
                    requestedRowSpan = 2,
                    resizeMode = ForkWidgetEntry.RESIZE_HORIZONTAL,
                ),
            ),
            widthDp = 700,
        )

        assertEquals(6, plan.columnCount)
        val fixed = plan.placements[0]
        assertTrue(fixed.isFixedSize)
        assertTrue(fixed.centerInCell)
        assertFalse(fixed.resizableHorizontally)
        assertFalse(fixed.resizableVertically)
        assertEquals(2, fixed.colSpan)
        assertEquals(2, fixed.rowSpan)

        val horiz = plan.placements[1]
        assertFalse(horiz.isFixedSize)
        assertFalse(horiz.centerInCell)
        assertTrue(horiz.resizableHorizontally)
        assertFalse(horiz.resizableVertically)
        assertNoOverlaps(plan)
    }

    @Test
    fun `dense mixed widget set across 4 6 and 8 columns produces zero intersections`() {
        val entries = listOf(
            WidgetPlacementInput(1, 2, 3),
            WidgetPlacementInput(2, 4, 2),
            WidgetPlacementInput(3, 2, 2),
            WidgetPlacementInput(4, 3, 2),
            WidgetPlacementInput(5, 2, 4),
            WidgetPlacementInput(6, 5, 2),
            WidgetPlacementInput(7, 2, 2),
        )

        for (widthDp in listOf(360, 700, 900)) {
            val plan = BentoGridLayoutPlanner.plan(entries, widthDp = widthDp)
            assertNoOverlaps(plan)
            for (placement in plan.placements) {
                assertTrue(placement.column >= 0)
                assertTrue(placement.column + placement.colSpan <= plan.columnCount)
                assertTrue(placement.row >= 0)
                assertTrue(placement.row + placement.rowSpan <= plan.rowCount)
            }
        }
    }

    private fun assertNoOverlaps(plan: BentoGridPlan) {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        for (placement in plan.placements) {
            for (r in placement.row until placement.row + placement.rowSpan) {
                for (c in placement.column until placement.column + placement.colSpan) {
                    val cell = r to c
                    assertTrue("Overlap at cell $cell for widget ${placement.appWidgetId}", occupied.add(cell))
                }
            }
        }
    }
}
