package app.chatra.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.chatra.core.model.*
import app.chatra.di.createContainer
import app.chatra.ui.conversation.AttachmentStack
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class AttachmentStackTest {
    @get:Rule val ui = createComposeRule()

    @Test fun oneToFourMmsFilesKeepOrderAndExposeAnUnavailableOriginal() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val container = createContainer(context, referenceFrame = true)
        ui.setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (count in 1..4) {
                        val message = Message(MessageRef.Provider(ProviderRef(ProviderTransport.MMS, count.toLong())),
                            ConversationId("qa"), Direction.Incoming, null, "", Instant.EPOCH, true,
                            attachments = List(count) { index -> AttachmentSummary(AttachmentId("part-$count-$index"), "image/png", "Zdjęcie ${index + 1}") })
                        AttachmentStack(message)
                    }
                }
            }
        }
        ui.onNodeWithContentDescription("Otwórz załącznik 1 z 1").assertIsDisplayed()
        ui.onNodeWithContentDescription("Otwórz załącznik 2 z 2").assertIsDisplayed()
        ui.onNodeWithContentDescription("Otwórz załącznik 3 z 3").assertIsDisplayed()
        ui.onNodeWithContentDescription("Otwórz załącznik 3 i kolejne 1").performClick()
        ui.onNodeWithText("Załącznik 3 z 4").assertIsDisplayed()
        ui.onNodeWithText("Następny").performClick()
        ui.onNodeWithText("Załącznik 4 z 4").assertIsDisplayed()
        ui.onNodeWithText("Załącznik jest niedostępny.").assertIsDisplayed()
        ui.onNodeWithText("Następny").assertIsNotEnabled()
        ui.onNodeWithText("Poprzedni").performClick()
        ui.onNodeWithText("Załącznik 3 z 4").assertIsDisplayed()
    }
}
