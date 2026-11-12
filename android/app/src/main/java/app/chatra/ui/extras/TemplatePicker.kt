package app.chatra.ui.extras

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.*
import kotlinx.coroutines.launch
import java.util.UUID

/** Insertion always appends; it never replaces the current composer or changes its SIM/recipients. */
@Composable
fun TemplatePicker(onInsert: (String) -> Unit) {
    val repo = LocalContainer.current.templates
    val items by repo.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<Template?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inserting by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Szablon dopiszemy na końcu wiadomości.", style = ChatraType.Preview)
        if (editId == null) {
            TextAction("Dodaj szablon", { editId = UUID.randomUUID().toString(); title = ""; body = "" })
            if (items.isEmpty()) BasicText("Brak szablonów", style = ChatraType.Body)
            items.forEach { item ->
                BasicText(item.title, style = chatraText(17f, 600))
                BasicText(item.body, style = ChatraType.Preview)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextAction("Wstaw", { if (!inserting) { inserting = true; onInsert(item.body) } }, enabled = !inserting)
                    TextAction("Edytuj", { editId = item.id.value; title = item.title; body = item.body })
                    TextAction("Usuń", { deletion = item })
                }
            }
        } else {
            BasicTextField(title, { if (it.length <= 100) title = it }, Modifier.fillMaxWidth().padding(8.dp).semantics { contentDescription = "Nazwa szablonu" }, textStyle = ChatraType.Body)
            BasicTextField(body, { if (it.length <= 16384) body = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(8.dp).semantics { contentDescription = "Treść szablonu" }, textStyle = ChatraType.Body)
            TextAction("Zapisz szablon", {
                busy = true
                val template = Template(TemplateId(editId!!), title.trim(), body, items.size)
                scope.launch {
                    try { repo.save(template); editId = null; error = null }
                    catch (_: Exception) { error = "Nie udało się zapisać szablonu." }
                    finally { busy = false }
                }
            }, enabled = !busy && title.isNotBlank() && body.isNotBlank())
            TextAction("Anuluj edycję", { editId = null }, enabled = !busy)
        }
        error?.let { BasicText(it, style = ChatraType.Preview) }
    }
    deletion?.let { item -> ChoiceDialog("Usunąć szablon „${item.title}”?", listOf("Usuń szablon"), {
        scope.launch { try { repo.delete(item.id) } catch (_: Exception) { error = "Nie udało się usunąć szablonu." } }
    }, { deletion = null }) }
}
