package app.chatra.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Szerokość obramowania CSS po przyciągnięciu do pikseli urządzenia, jak w Chrome:
 * 1 px przy DPR 3.5 to 3 piksele (0.857 dp), nie 3.5. Liczy się w układzie (content-box, border-box).
 */
@Composable
@ReadOnlyComposable
fun cssBorder(css: Float = 1f): Dp {
    val d = LocalDensity.current.density
    return (floor(css * d).coerceAtLeast(1f) / d).dp
}
