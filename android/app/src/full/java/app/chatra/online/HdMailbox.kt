package app.chatra.online

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.room.withTransaction
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.core.database.*
import app.chatra.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Authenticated metadata sync and explicit, resumable private downloads. */
internal class HdMailbox(
    private val context: Context, private val db: ChatraDatabase, private val media: AttachmentStore,
    private val history: ConversationRepository, private val api: OnlineTransport, private val owner: () -> String?,
) {
    private val syncLock = Mutex()
    private val downloads = Mutex()
    private val dao get() = db.hd()
    private val stageDir = File(context.noBackupFilesDir, "hd-downloads").apply { mkdirs() }

    suspend fun sync() = withContext(Dispatchers.IO) { syncLock.withLock {
        val installation = owner() ?: return@withLock
        repeat(40) {
            currentCoroutineContext().ensureActive()
            val cursor = dao.cursor(installation)
            val page = try { api.signed(installation, "GET", "/v1/inbox" + (cursor?.let { "?cursor=$it" } ?: "")) }
            catch (e: OnlineError) {
                if (e.code != "cursor_expired") throw e
                db.withTransaction { dao.cursor(HdCursor(installation, "")) }
                return@repeat
            }
            val items = page.getJSONArray("items")
            val rows = mutableListOf<HdRecord>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getString("envelope")
                require(id.matches(Regex("[0-9a-f]{32}")))
                if (dao.dismissed(id) != 0) continue
                val old = dao.get(id)
                if (item.getString("kind") == "revoked") {
                    if (old != null) rows += old.copy(state = if (JSONArray(old.localFiles).length() > 0) "LOCAL" else "EXPIRED")
                    continue
                }
                try { rows += metadata(installation, id, old) }
                catch (e: OnlineError) {
                    if (e.status != 404) throw e
                    if (old != null) rows += old.copy(state = if (JSONArray(old.localFiles).length() > 0) "LOCAL" else "EXPIRED")
                }
            }
            check(owner() == installation) { "binding_changed" }
            db.withTransaction {
                // Re-read local state inside the transaction: download/read/trash may have changed.
                for (row in rows) if (dao.dismissed(row.id) == 0) {
                    val current = dao.get(row.id)
                    dao.put(if (current == null) row else row.copy(localFiles = current.localFiles,
                        read = current.read, bytes = current.bytes, metered = current.metered,
                        downloadedAt = current.downloadedAt ?: row.downloadedAt,
                        state = if (row.state in setOf("EXPIRED", "LOCAL")) {
                            if (ids(current.localFiles).isEmpty()) "EXPIRED" else "LOCAL"
                        } else current.state))
                }
                dao.cursor(HdCursor(installation, page.getString("cursor")))
            }
            if (!page.getBoolean("hasMore")) return@withLock
        }
        throw IOException("inbox_budget")
    } }

    private suspend fun metadata(installation: String, id: String, old: HdRecord?): HdRecord {
        val data = api.signed(installation, "GET", "/v1/envelopes/$id")
        check(data.getString("id") == id)
        val incoming = data.getBoolean("incoming")
        val sender = requireNotNull(hdPhone(data.getString("sender")))
        val recipient = requireNotNull(hdPhone(data.getString("recipient")))
        val files = data.getJSONArray("files")
        require(files.length() in 1..5)
        var total = 0L
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            require(f.getInt("position") == i && f.getLong("size") in 1..(25L * 1024 * 1024))
            require(f.getString("sha256").matches(Regex("[0-9a-f]{64}")))
            total += f.getLong("size")
        }
        require(total <= 50L * 1024 * 1024)
        val conversation = old?.conversationId ?: history.conversationFor(listOf(Address(if (incoming) sender else recipient))).value
        return HdRecord(id, installation, conversation, sender, recipient, incoming,
            data.getLong("createdAt") * 1000, data.getLong("expiresAt") * 1000, files.toString(),
            downloadedAt = if (data.isNull("downloadedAt")) null else data.getLong("downloadedAt") * 1000,
            read = !incoming)
    }

    suspend fun sent(id: String, local: List<AttachmentId>) = withContext(Dispatchers.IO) { syncLock.withLock {
        val installation = owner() ?: throw IOException("binding_changed")
        val row = metadata(installation, id, dao.get(id))
        db.withTransaction {
            if (dao.dismissed(id) == 0) dao.put(row.copy(localFiles = JSONArray(local.map { it.value }).toString(), state = "SENT", read = true))
        }
    } }

    suspend fun enqueue(id: String, metered: Boolean) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = dao.get(id) ?: error("Nie ma tej wiadomości.")
            check(row.incoming && row.owner == owner() && row.state !in setOf("LOCAL", "READY", "EXPIRED"))
            check(row.expiresAt > System.currentTimeMillis()) { "Pliki wygasły." }
            dao.put(row.copy(state = if (row.state == "RECEIPT") row.state else "QUEUED", metered = metered))
        }
        schedule(id, metered)
    }

    private fun schedule(id: String, metered: Boolean) {
        val request = OneTimeWorkRequestBuilder<HdDownloadWorker>()
            .setInputData(workDataOf("envelope" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (metered) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("hd-download-$id", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun recover() {
        for (row in dao.pending()) if (row.owner == owner()) schedule(row.id, row.metered)
        val keep = dao.all().filter { it.owner == owner() && it.state in setOf("QUEUED", "DOWNLOADING", "RECEIPT") && it.expiresAt > System.currentTimeMillis() }.map { it.id }.toSet()
        stageDir.listFiles()?.filter { file -> file.name.substringBefore('-') !in keep }?.forEach { it.delete() }
    }

    suspend fun protectedIds(): Set<AttachmentId> = dao.all().flatMap { ids(it.localFiles) }.toSet()

    suspend fun detached(installation: String) = withContext(Dispatchers.IO) { syncLock.withLock {
        val rows = dao.all().filter { it.owner == installation }
        db.withTransaction {
            for (old in rows) dao.get(old.id)?.let { row ->
                dao.put(row.copy(state = if (ids(row.localFiles).isEmpty()) "EXPIRED" else "LOCAL"))
            }
        }
        for (row in rows) {
            WorkManager.getInstance(context).cancelUniqueWork("hd-download-${row.id}")
            stageDir.listFiles()?.filter { it.name.startsWith("${row.id}-") }?.forEach { it.delete() }
        }
    } }

    suspend fun run(id: String): Boolean = withContext(Dispatchers.IO) { downloads.withLock {
        val initial = dao.get(id) ?: return@withLock true
        if (initial.state !in setOf("QUEUED", "DOWNLOADING", "RECEIPT") || initial.owner != owner()) return@withLock true
        try {
            // Refresh authorization before each attempt, including receipt-only retries.
            api.signed(initial.owner, "GET", "/v1/envelopes/$id")
            val manifest = JSONArray(initial.manifest)
            val saved = ids(initial.localFiles)
            val local = mutableListOf<AttachmentId>()
            for (attachment in saved) {
                if (media.get(attachment) == null) break
                local += attachment
            }
            if (local.size != saved.size) db.withTransaction {
                dao.get(id)?.let { dao.put(it.copy(localFiles = JSONArray(local.map { file -> file.value }).toString(), state = "DOWNLOADING")) }
            }
            val deadline = SystemClock.elapsedRealtime() + 120_000
            for (position in local.size until manifest.length()) {
                val spec = manifest.getJSONObject(position)
                val file = File(stageDir, "$id-$position")
                val size = spec.getLong("size")
                if (file.length() > size || file.length() % 262_144 != 0L && file.length() != size) file.delete()
                while (file.length() < size) {
                    currentCoroutineContext().ensureActive()
                    if (SystemClock.elapsedRealtime() > deadline) return@withLock false
                    check(owner() == initial.owner) { "binding_changed" }
                    val bytes = api.download(initial.owner, "/v1/envelopes/$id/files/$position/chunks?offset=${file.length()}", !initial.metered)
                    require(bytes.size.toLong() == minOf(262_144L, size - file.length()))
                    java.io.FileOutputStream(file, true).use { it.write(bytes); it.fd.sync() }
                    val previous = (0 until position).sumOf { manifest.getJSONObject(it).getLong("size") }
                    db.withTransaction { dao.get(id)?.takeIf { it.state !in setOf("EXPIRED", "LOCAL") }?.let { dao.put(it.copy(state = "DOWNLOADING", bytes = previous + file.length())) } }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != spec.getString("sha256")) { file.delete(); throw OnlineError(422, "file_checksum", 0) }
                val uri = Uri.parse(media.cameraUri())
                context.contentResolver.openOutputStream(uri, "w")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
                val imported = try { media.importUri(uri.toString()) }
                finally { context.contentResolver.delete(uri, null, null) }
                require(imported.sha256 == hash && imported.bytes == size && imported.mime == spec.getString("mime"))
                local += imported.id
                db.withTransaction {
                    val row = dao.get(id) ?: throw IOException("message_removed")
                    check(dao.dismissed(id) == 0 && row.state !in setOf("LOCAL", "EXPIRED"))
                    dao.put(row.copy(localFiles = JSONArray(local.map { it.value }).toString(),
                        state = if (local.size == manifest.length()) "RECEIPT" else "DOWNLOADING"))
                }
                file.delete()
            }
            // A crash after this POST retries the same receipt. Local files already exist durably.
            for (attachment in local) check(media.get(attachment) != null) { "file_unavailable" }
            val response = api.signed(initial.owner, "POST", "/v1/envelopes/$id/receipt",
                JSONObject().put("files", JSONArray((0 until manifest.length()).map { manifest.getJSONObject(it).getString("sha256") })).bytes())
            db.withTransaction { dao.get(id)?.let { dao.put(it.copy(state = "READY", downloadedAt = response.getLong("downloadedAt") * 1000)) } }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: OnlineError) {
            if (e.status in setOf(404, 410)) {
                db.withTransaction { dao.get(id)?.let { dao.put(it.copy(state = if (ids(it.localFiles).isEmpty()) "EXPIRED" else "LOCAL")) } }
                stageDir.listFiles()?.filter { it.name.startsWith("$id-") }?.forEach { it.delete() }
                true
            } else if (e.status in setOf(401, 403, 422)) {
                fail(id, if (e.status == 422) "FAILED" else "AUTH")
                true
            } else false
        } catch (_: IOException) { false }
        catch (_: IllegalArgumentException) { fail(id, "FAILED"); true }
        catch (_: IllegalStateException) { fail(id, "FAILED"); true }
        catch (_: org.json.JSONException) { fail(id, "FAILED"); true }
    } }

    private suspend fun fail(id: String, state: String) = db.withTransaction {
        dao.get(id)?.takeIf { it.state !in setOf("LOCAL", "EXPIRED") }?.let { dao.put(it.copy(state = state)) }
    }

    companion object {
        fun ids(json: String): List<AttachmentId> = JSONArray(json).let { a -> List(a.length()) { AttachmentId(a.getString(it)) } }
    }
}

class HdDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val queue = (applicationContext as ChatraApplication).container(false).hd as? HdQueue ?: return Result.failure()
        val id = inputData.getString("envelope") ?: return Result.failure()
        return if (queue.mailbox?.run(id) == true) Result.success() else Result.retry()
    }
}
