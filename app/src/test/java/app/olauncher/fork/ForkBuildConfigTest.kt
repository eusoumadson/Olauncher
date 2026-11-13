package app.olauncher.fork

import app.olauncher.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ForkBuildConfigTest {
    @Test
    fun `debug build config uses fork applicationId and CalVer metadata`() {
        assertEquals("app.olauncher.abacate.debug", BuildConfig.APPLICATION_ID)
        assertEquals(116, BuildConfig.VERSION_CODE)
        assertEquals("2026.11.12", BuildConfig.VERSION_NAME)
    }
}
