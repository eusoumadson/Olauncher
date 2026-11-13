package app.olauncher.fork

import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class ForkIdentityConstantsTest {
    @Test
    fun `fork repository and issues URLs match expected upstream fork location`() {
        assertEquals(
            "https://github.com/eusoumadson/abacate-launcher",
            Constants.URL_FORK_GITHUB,
        )
        assertEquals(
            "https://github.com/eusoumadson/abacate-launcher/issues",
            Constants.URL_FORK_ISSUES,
        )
    }

    @Test
    fun `upstream Olauncher URLs remain intact`() {
        assertEquals(
            "https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=olauncher",
            Constants.URL_ABOUT_OLAUNCHER,
        )
        assertEquals(
            "https://www.github.com/tanujnotes/Olauncher",
            Constants.URL_OLAUNCHER_GITHUB,
        )
        assertEquals(
            "https://x.com/tanujnotes",
            Constants.URL_TWITTER_TANUJ,
        )
        assertEquals(
            "https://tanujnotes.notion.site/Olauncher-Privacy-Policy-dd6ac5101ddd4b3da9d27057889d44ab",
            Constants.URL_OLAUNCHER_PRIVACY,
        )
    }
}
