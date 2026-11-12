package app.chatra.local

import android.content.Context
import android.util.AtomicFile
import app.chatra.core.model.*
import app.chatra.di.PreferenceStore
import app.chatra.ui.extras.DefaultTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun AtomicFile.replace(bytes: ByteArray) {
    val output = startWrite()
    try { output.write(bytes); finishWrite(output) }
    catch (e: Exception) { failWrite(output); throw e }
}

class LocalTemplates(context: Context) : TemplateRepository {
    private val file = AtomicFile(File(context.filesDir, "templates.json"))
    private val mutex = Mutex()
    private fun read(): List<Template> {
        if (!file.baseFile.exists()) return DefaultTemplates
        val array = JSONArray(file.readFully().toString(Charsets.UTF_8))
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { Template(TemplateId(it.getString("id")), it.getString("title"), it.getString("body"), i) } }
    }
    override val items = MutableStateFlow(read())
    private suspend fun change(transform: (List<Template>) -> List<Template>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(items.value)
            require(next.size <= 100)
            val array = JSONArray(next.map { JSONObject().put("id", it.id.value).put("title", it.title).put("body", it.body) })
            file.replace(array.toString().toByteArray())
            items.value = next
        }
    }
    override suspend fun save(template: Template) {
        require(template.title.isNotBlank() && template.title.length <= 100 && template.body.isNotBlank() && template.body.length <= 16384 && '\u0000' !in template.body)
        change { list -> list.filterNot { it.id == template.id } + template.copy(sortOrder = list.size) }
    }
    override suspend fun delete(id: TemplateId) { change { it.filterNot { t -> t.id == id } } }
}

class LocalNotificationPrefs(context: Context) : PreferenceStore<NotificationPrefs> {
    private val file = AtomicFile(File(context.filesDir, "notification-prefs.json"))
    private val mutex = Mutex()
    private fun read(): NotificationPrefs {
        if (!file.baseFile.exists()) return NotificationPrefs()
        val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
        val muted = json.getJSONObject("muted")
        return NotificationPrefs(json.optBoolean("previews", false), muted.keys().asSequence().associateWith { muted.getLong(it) })
    }
    override val value = MutableStateFlow(read())
    override suspend fun update(transform: (NotificationPrefs) -> NotificationPrefs) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(value.value)
            file.replace(JSONObject().put("previews", next.previews).put("muted", JSONObject(next.mutedUntil)).toString().toByteArray())
            value.value = next
        }
    }
}
