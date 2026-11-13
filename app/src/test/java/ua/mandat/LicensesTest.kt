package ua.mandat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ua.mandat.ui.licenceTexts
import ua.mandat.ui.paragraphsOf
import java.io.File

/** The licence texts some libraries ask to be shown really travel inside the app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LicensesTest {
    @Test fun everyLicenceTextTravelsWithTheApp() {
        val texts = licenceTexts(ApplicationProvider.getApplicationContext<Application>()).associate { it.title to it.paragraphs }
        assertEquals(5, texts.size)
        assertTrue(texts.values.all { it.size >= 3 })
        assertTrue(texts.getValue("SQLCipher").first().contains("ZETETIC"))
        // (this one comes from inside the library itself)
        assertTrue(texts.getValue("Bouncy Castle").joinToString(" ").contains("Legion of the Bouncy Castle"))
        assertTrue(texts.getValue("Protocol Buffers").first().contains("Google Inc."))
        assertTrue(texts.getValue("Apache License 2.0").first().contains("Apache License"))
        // Mandat's own licence in the app is the very file in the repository
        assertEquals(paragraphsOf(File("../LICENSE").readText()), texts.getValue("GNU General Public License 3.0"))
    }

    @Test fun aLongTextIsSplitAtEmptyLines() {
        assertEquals(listOf("перший\nрядок", "  другий", "третій"), paragraphsOf("перший\r\nрядок\n\n  другий\n \n\n\nтретій\n"))
        // On the screen a paragraph wraps by itself; list items keep their own lines
        assertEquals("один два\n* три чотири\n* п'ять", ua.mandat.ui.flowed("  один\nдва\n    * три\nчотири\n    * п'ять"))
    }
}
