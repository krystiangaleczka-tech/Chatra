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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
@Stable
class Chrome(val statusBar: Dp, val navigationBar: Dp, private val imeHeight: State<Dp>) {
    constructor(statusBar: Dp, navigationBar: Dp, ime: Dp) : this(statusBar, navigationBar, mutableStateOf(ime))

    /**
     * Wysokość klawiatury czytana dopiero przy użyciu: animacja IME unieważnia tylko te zakresy,
     * które ją odczytują, a nie całe drzewo pod [LocalChrome] (lokalna statyczna przebudowywała
     * w każdej klatce całą aplikację, z listą rozmów włącznie).
     */
    val ime: Dp get() = imeHeight.value
    fun top(design: Dp): Dp = max(design, statusBar + 8.dp)
    fun bottom(design: Dp): Dp = max(design, max(navigationBar, ime) + 8.dp)
}

val LocalChrome = staticCompositionLocalOf { Chrome(0.dp, 0.dp, 0.dp) }

@Composable
fun ProvideChrome(referenceFrame: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val ime = remember(imeInsets, density) { derivedStateOf { with(density) { imeInsets.getBottom(this).toDp() } } }
    val statusBar = if (referenceFrame) 0.dp else with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navigationBar = if (referenceFrame) 0.dp else with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val chrome = remember(statusBar, navigationBar, ime) { Chrome(statusBar, navigationBar, ime) }
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
    val tokens = app.chatra.core.designsystem.theme.LocalAppearanceTokens.current
    val resolvedBackground = if (tokens.dark || tokens.contrast || tokens.composition != "Glass") CssBackground(tokens.background, emptyList()) else background
    val scene = rememberBackdrop()
    val page = rememberBackdrop()
    // Tło z warstwami radialnymi to kilka pełnoekranowych wypełnień gradientem w każdej klatce, w której
    // przesuwa się treść (na A15 ≈ 2,5 ms GPU na klatkę). Jest statyczne, więc HWUI trzyma je jako gotową
    // teksturę i tylko ją kopiuje. Szkło nadal próbkuje nagranie sceny, nie tę teksturę.
    val cacheScene = resolvedBackground.layers.isNotEmpty()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (cacheScene) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
                    .backdropSource(scene)
                    .drawBehind { drawCssBackground(resolvedBackground); sceneDecoration() },
            )
            ProvideBackdrop(scene) { content() }
        }
        ProvideBackdrop(page) { floating() }
    }
}
