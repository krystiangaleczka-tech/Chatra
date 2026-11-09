package app.chatra.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Kliknięcie bez rippla Material (plansze nie mają efektu falowania). Stan naciśnięcia
 * pokazują komponenty same, jeśli projekt go przewiduje.
 */
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
}
