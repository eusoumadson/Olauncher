package app.olauncher.fork.pager

import app.olauncher.R
import app.olauncher.fork.widgets.WidgetScreenPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePagerPolicyTest {

    @Test
    fun `pagesFor and page index helpers match LEFT RIGHT and DISABLED layouts`() {
        assertEquals(listOf(WorkspacePage.HOME), WorkspacePagerPolicy.pagesFor(WidgetScreenPosition.DISABLED))
        assertEquals(0, WorkspacePagerPolicy.homePageIndex(WidgetScreenPosition.DISABLED))
        assertEquals(-1, WorkspacePagerPolicy.widgetsPageIndex(WidgetScreenPosition.DISABLED))

        assertEquals(
            listOf(WorkspacePage.WIDGETS, WorkspacePage.HOME),
            WorkspacePagerPolicy.pagesFor(WidgetScreenPosition.LEFT),
        )
        assertEquals(1, WorkspacePagerPolicy.homePageIndex(WidgetScreenPosition.LEFT))
        assertEquals(0, WorkspacePagerPolicy.widgetsPageIndex(WidgetScreenPosition.LEFT))

        assertEquals(
            listOf(WorkspacePage.HOME, WorkspacePage.WIDGETS),
            WorkspacePagerPolicy.pagesFor(WidgetScreenPosition.RIGHT),
        )
        assertEquals(0, WorkspacePagerPolicy.homePageIndex(WidgetScreenPosition.RIGHT))
        assertEquals(1, WorkspacePagerPolicy.widgetsPageIndex(WidgetScreenPosition.RIGHT))
    }

    @Test
    fun `isUserInputEnabled only allows horizontal paging on mainFragment when enabled and not editing`() {
        for (position in listOf(WidgetScreenPosition.LEFT, WidgetScreenPosition.RIGHT)) {
            assertTrue(
                WorkspacePagerPolicy.isUserInputEnabled(
                    position = position,
                    currentDestinationId = R.id.mainFragment,
                    homeDestinationId = R.id.mainFragment,
                    isWidgetEditInteractionActive = false,
                )
            )
            assertFalse(
                WorkspacePagerPolicy.isUserInputEnabled(
                    position = position,
                    currentDestinationId = R.id.appListFragment,
                    homeDestinationId = R.id.mainFragment,
                    isWidgetEditInteractionActive = false,
                )
            )
            assertFalse(
                WorkspacePagerPolicy.isUserInputEnabled(
                    position = position,
                    currentDestinationId = R.id.settingsFragment,
                    homeDestinationId = R.id.mainFragment,
                    isWidgetEditInteractionActive = false,
                )
            )
            assertFalse(
                WorkspacePagerPolicy.isUserInputEnabled(
                    position = position,
                    currentDestinationId = null,
                    homeDestinationId = R.id.mainFragment,
                    isWidgetEditInteractionActive = false,
                )
            )
            assertFalse(
                WorkspacePagerPolicy.isUserInputEnabled(
                    position = position,
                    currentDestinationId = R.id.mainFragment,
                    homeDestinationId = R.id.mainFragment,
                    isWidgetEditInteractionActive = true,
                )
            )
        }

        assertFalse(
            WorkspacePagerPolicy.isUserInputEnabled(
                position = WidgetScreenPosition.DISABLED,
                currentDestinationId = R.id.mainFragment,
                homeDestinationId = R.id.mainFragment,
                isWidgetEditInteractionActive = false,
            )
        )
    }
}
