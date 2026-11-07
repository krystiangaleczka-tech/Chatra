package app.chatra.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import app.chatra.core.designsystem.background.CssBackground
import app.chatra.core.designsystem.background.drawCssBackground
import app.chatra.core.designsystem.component.ProvideBackdrop
import app.chatra.core.designsystem.glass.backdropSource
import app.chatra.core.designsystem.glass.rememberBackdrop

/**
 * Systemowe paski i wycięcia (specyfikacja 3): wzorzec ma u góry 50–60 jednostek pustego miejsca,
 * a dock 26 od dołu. Jeden mechanizm insets — górny odstęp to większa z wartości: projekt albo
 * pasek stanu + 8; bez podwójnego paddingu. W trybie referencyjnym paski są ukryte (0).
 */
@Immutable
data class Chrome(val statusBar: Dp, val navigationBar: Dp, val ime: Dp) {
    fun top(design: Dp): Dp = max(design, statusBar + 8.dp)
    fun bottom(design: Dp): Dp = max(design, max(navigationBar, ime) + 8.dp)
}

val LocalChrome = staticCompositionLocalOf { Chrome(0.dp, 0.dp, 0.dp) }

@Composable
fun ProvideChrome(referenceFrame: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val chrome = if (referenceFrame) Chrome(0.dp, 0.dp, with(density) { WindowInsets.ime.getBottom(this).toDp() }) else with(density) {
        Chrome(
            WindowInsets.statusBars.getTop(this).toDp(),
            WindowInsets.navigationBars.getBottom(this).toDp(),
            WindowInsets.ime.getBottom(this).toDp(),
        )
    }
    CompositionLocalProvider(LocalChrome provides chrome, content = content)
}

/**
 * Ekran ze sceną dla szkła na dwóch poziomach: [content] (karty, dymki) próbkuje samo tło,
 * a [floating] (nagłówek, dock, kompozytor, arkusz) próbkuje tło razem z treścią pod sobą.
 * Panel nigdy nie należy do sceny, którą próbkuje.
 */
@Composable
fun ChatraScreen(
    background: CssBackground,
    modifier: Modifier = Modifier,
    /** Elementy sceny rysowane nad tłem (np. dekoracyjne koła S01) — próbkowane przez szkło. */
    sceneDecoration: DrawScope.() -> Unit = {},
    floating: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scene = rememberBackdrop()
    val page = rememberBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            Box(Modifier.fillMaxSize().backdropSource(scene).drawBehind { drawCssBackground(background); sceneDecoration() })
            ProvideBackdrop(scene) { content() }
        }
        ProvideBackdrop(page) { floating() }
    }
}
