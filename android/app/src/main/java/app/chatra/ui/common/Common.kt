package app.chatra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.component.AvatarContent
import app.chatra.core.designsystem.component.AvatarTone
import app.chatra.core.designsystem.component.AvatarTones
import app.chatra.core.designsystem.component.Dock
import app.chatra.core.designsystem.component.DockTab
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.model.Address
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.Participant
import app.chatra.core.model.participantsKey
import app.chatra.navigation.Tab
import app.chatra.ui.LocalChrome
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

/** Odcień awatara z danych (fixture) albo z hasha uczestników. */
val LocalAvatarTones = staticCompositionLocalOf<(ConversationId) -> String?> { { null } }

@Composable
fun avatarFor(row: ConversationSummary): Pair<AvatarContent, AvatarTone> {
    val tone = AvatarTones.named(LocalAvatarTones.current(row.id))
        ?: AvatarTones.forKey(participantsKey(row.participants.map { it.address }))
    val content = when {
        row.isGroupMms || row.participants.size > 1 -> AvatarContent.Glyph(ChatraIcons.Group)
        ConversationSummary.Tag.Code in row.tags || row.participants.firstOrNull()?.address?.kind == Address.Kind.Alphanumeric ->
            AvatarContent.Glyph(ChatraIcons.Parcel)
        row.participants.firstOrNull()?.displayName == null -> AvatarContent.Glyph(ChatraIcons.Person)
        else -> AvatarContent.Initials(initials(row.title))
    }
    return content to tone
}

/** „Ola Nowak” → „ON”, „Mama” → „M”, „Marek (praca)” → „M”. */
fun initials(name: String): String =
    name.replace(Regex("\\(.*?\\)"), "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it[0].isLetter() }
        .take(2).joinToString("") { it.first().uppercase() }

fun Participant.initials(): String = initials(label)

fun Modifier.drawTopLine(color: Color) = drawBehind {
    drawLine(color, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
}

/** Dock z trzema sekcjami i akcją Nowa wiadomość; pozycja: left/right 16, bottom 26. */
class MainDock(private val selected: Tab, private val onSelect: (Tab) -> Unit, private val onCompose: () -> Unit) {
    @Composable
    fun Render(modifier: Modifier) {
        val chrome = LocalChrome.current
        Dock(
            listOf(DockTab("Rozmowy", ChatraIcons.Chat), DockTab("Zaplanowane", ChatraIcons.Clock), DockTab("Ustawienia", ChatraIcons.Sliders)),
            selected.ordinal,
            { onSelect(Tab.entries[it]) },
            onCompose,
            modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = chrome.bottom(26.dp)),
        )
    }
}
