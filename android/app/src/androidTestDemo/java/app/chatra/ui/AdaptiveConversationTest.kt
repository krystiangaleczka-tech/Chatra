package app.chatra.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.chatra.core.model.AppearancePrefs
import app.chatra.core.model.AppearanceOverride
import app.chatra.core.model.NotificationPrefs
import app.chatra.core.testing.Fixture
import app.chatra.di.AppContainer
import app.chatra.di.InMemoryStore
import app.chatra.di.createContainer
import app.chatra.navigation.Navigator
import app.chatra.navigation.Route
import org.junit.Rule
import org.junit.Test

class AdaptiveConversationTest {
    @get:Rule val ui = createComposeRule()
    private val id = Fixture.Ids.Ola

    private fun launch(width: Int, scale: Float, routes: List<Route>): AppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val fixture = createContainer(app, referenceFrame = true)
        val container = object : AppContainer by fixture {
            override val referenceFrame = false
            override val appearance = InMemoryStore(AppearancePrefs(increaseContrast = true, reduceTransparency = true))
            override val conversationAppearance = InMemoryStore(emptyMap<String, AppearanceOverride>())
            override val notifications = InMemoryStore(NotificationPrefs())
        }
        val navigator = Navigator(initialStack = routes)
        ui.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Box(Modifier.width(width.dp).fillMaxHeight()) {
                    ProvideChrome(false) { ChatraApp(container, navigator, onExit = {}) }
                }
            }
        }
        return container
    }

    @Test fun narrowGalleryWithDoubleTextKeepsFilterAndReturnActionReachable() {
        launch(360, 2f, listOf(Route.Inbox, Route.Conversation(id), Route.ConversationDetails(id), Route.ConversationGallery(id)))
        val links = ui.onNode(hasText("Linki") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        links.performScrollTo().assertIsDisplayed().performClick()
        links.assertIsSelected()
        ui.onNodeWithText("Brak pozycji w tej zakładce.").assertIsDisplayed()
        ui.onNodeWithContentDescription("Wróć do szczegółów rozmowy").assertIsDisplayed().performClick()
        ui.onNodeWithText("Otwórz galerię").performScrollTo().assertIsDisplayed()
    }

    @Test fun individualAppearanceControlsRemainReachableAtDoubleText() {
        val container = launch(390, 2f, listOf(Route.Inbox, Route.Conversation(id), Route.ConversationDetails(id), Route.ThreadAppearance(id)))
        ui.onNodeWithText("Dymki · globalne").performScrollTo().assertIsDisplayed().performClick()
        ui.onNodeWithText("Śliwkowy").performScrollTo().assertHasClickAction().performClick()
        ui.waitUntil(5_000) { container.conversationAppearance!!.value.value[id.value]?.bubble != null }
        ui.onNodeWithText("Dymki · Śliwkowy").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Przywróć ustawienia globalne").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(5_000) { id.value !in container.conversationAppearance!!.value.value }
    }

    @Test fun accessibilityActionProvidesAlternativeToInboxSwipes() {
        launch(430, 1.3f, listOf(Route.Inbox))
        val row = ui.onNode(hasText("Ola Nowak") and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val menu = row.assertIsDisplayed().fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Akcje rozmowy" }
        ui.runOnIdle { check(menu.action()) }
        ui.onNodeWithText("Wycisz do odwołania").performScrollTo().assertHasClickAction()
        ui.onNodeWithText("Szczegóły rozmowy").performScrollTo().assertHasClickAction()
    }
}
