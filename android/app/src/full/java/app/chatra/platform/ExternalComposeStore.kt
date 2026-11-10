package app.chatra.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.AtomicFile
import app.chatra.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

fun parseComposeIntent(intent: Intent): ExternalCompose? {
    if (intent.action !in setOf(Intent.ACTION_SENDTO, TelephonyManager.ACTION_RESPOND_VIA_MESSAGE)) return null
    val uri = intent.data ?: return null
    if (uri.scheme?.lowercase() !in setOf("sms", "smsto", "mms", "mmsto")) return null
    val part = uri.encodedSchemeSpecificPart ?: return null
    val recipients = Uri.decode(part.substringBefore('?')).split(',', ';').map { it.trim() }
    val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: part.substringAfter('?', "").split('&').firstOrNull { it.startsWith("body=") || it.startsWith("sms_body=") }?.substringAfter('=')?.let(Uri::decode).orEmpty()
    return externalCompose(recipients, body, UUID.randomUUID().toString())
}

/** Trwały, prywatny szkic wejścia systemowego. Żadna operacja nie uruchamia transportu. */
class ExternalComposeStore(context: Context) {
    private val folder = File(context.filesDir, "external-compose").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("external-compose", Context.MODE_PRIVATE)
    fun save(draft: ExternalCompose) {
        val json = JSONObject().put("id", draft.id).put("recipients", JSONArray(draft.recipients.map { it.raw })).put("text", draft.text)
        val file = AtomicFile(File(folder, "${draft.id}.json")); val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream); preferences.edit().putString("pending", draft.id).commit() }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    fun pendingId(): String? = preferences.getString("pending", null)
    fun acknowledge(id: String) { if (pendingId() == id) preferences.edit().remove("pending").commit() }
    fun read(id: String): ExternalCompose? {
        if (!id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) return null
        return runCatching {
            val json = JSONObject(AtomicFile(File(folder, "$id.json")).readFully().toString(Charsets.UTF_8))
            val recipients = json.getJSONArray("recipients")
            externalCompose((0 until recipients.length()).map { recipients.getString(it) }, json.getString("text"), id)
        }.getOrNull()
    }
}
