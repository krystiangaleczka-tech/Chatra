package app.chatra.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.di.createContainer
import app.chatra.navigation.ReferenceScreens
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ReferenceCaptureTest {
    @get:Rule val ui = createComposeRule()

    @Test fun capturesEveryReferenceAfterItsFixtureIsVisible() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureChatraReferences") == "true")
        val checkpoints = linkedMapOf(
            "S01" to "Chatra",
            "S02" to "Ola Nowak",
            "S03" to "Pasuje, do zobaczenia!",
            "S04" to "Wybrane: 4 zdjęcia",
            "S05" to "Zaplanuj wysyłkę",
            "S06" to "Tomek Wiśniewski",
            "S07" to "482 915",
            "S08" to "Nowa wiadomość",
            "S09" to "Wygląd",
            "S10" to "Karty SIM",
        )
        val selection = mutableStateOf(checkpoints.keys.first())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = createContainer(app, referenceFrame = true)
        ui.setContent {
            Box(Modifier.requiredSize(390.dp, 844.dp).testTag("reference-frame")) {
                val screen = selection.value
                key(screen) {
                    val navigator = remember { requireNotNull(ReferenceScreens.navigatorFor(screen)) }
                    ProvideChrome(true) { ChatraApp(container, navigator, onExit = {}) }
                }
            }
        }
        checkpoints.forEach { (screen, marker) ->
            ui.runOnIdle { selection.value = screen }
            ui.waitUntil(12_000) {
                ui.onAllNodesWithText(marker, substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().any { !it.boundsInRoot.isEmpty }
            }
            val image = ui.onNodeWithTag("reference-frame").captureToImage().asAndroidBitmap()
            assertEquals("Width of $screen", 780, image.width)
            assertEquals("Height of $screen", 1688, image.height)
            writeQaCapture(image, "android-$screen.png")
        }
    }
}
