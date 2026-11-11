package app.chatra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.pressable(onClick).padding(horizontal = 4.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        BasicText(text, style = chatraText(17f, color = ChatraColors.Link))
    }
}

/** Tymczasowy wybór poza dziesięcioma wzorcami; projekty szczegółowe w CH020. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())) {
            BasicText(title, Modifier.padding(8.dp).semantics { heading() }, style = chatraText(20f, 700))
            options.forEachIndexed { i, label -> TextAction(label, { onSelect(i); onDismiss() }, Modifier.fillMaxWidth()) }
            TextAction("Anuluj", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
