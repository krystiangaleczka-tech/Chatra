package app.chatra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.navigation.ReferenceScreens
import app.chatra.ui.ChatraApp
import app.chatra.ui.ProvideChrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val reference = BuildConfig.DEMO && intent.getBooleanExtra(EXTRA_FRAME, false)
        val container = (application as ChatraApplication).container(reference)
        val initial = intent.getStringExtra(EXTRA_SCREEN)?.let(ReferenceScreens::navigatorFor)
        if (reference) {
            WindowInsetsControllerCompat(window, window.decorView).run {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            val appearance by container.appearance.value.collectAsStateWithLifecycle()
            ProvideChrome(reference) {
                TextScale(if (reference) null else appearance.textScale) {
                    if (reference) {
                        // Obszar projektu 390 × 844 dp w lewym górnym rogu (porównanie z goldenem CH002).
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                            Box(Modifier.requiredSize(390.dp, 844.dp)) { ChatraApp(container, initial, onExit = ::finish) }
                        }
                    } else {
                        ChatraApp(container, initial, onExit = ::finish)
                    }
                }
            }
        }
    }

    companion object {
        /** Tryb referencyjny (demo): `adb shell am start … --ez frame true --es screen S02`. */
        const val EXTRA_FRAME = "frame"
        const val EXTRA_SCREEN = "screen"
    }
}

/** Mnożnik tekstu z S09 współdziała ze skalą systemową; w trybie referencyjnym fontScale = 1.0. */
@Composable
private fun TextScale(multiplier: Float?, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val scale = if (multiplier == null) 1f else d.fontScale * multiplier
    CompositionLocalProvider(LocalDensity provides Density(d.density, scale), content = content)
}
