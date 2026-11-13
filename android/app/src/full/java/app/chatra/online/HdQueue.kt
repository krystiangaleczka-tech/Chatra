package app.chatra.online

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.chatra.BuildConfig
import app.chatra.ChatraApplication
import app.chatra.core.model.AttachmentId
import app.chatra.core.model.AttachmentStore
import app.chatra.core.model.StoredMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.TimeUnit

private val inProgress = setOf(HdStatus.Queued, HdStatus.Uploading, HdStatus.Waiting, HdStatus.Cancelling)
private val terminal = setOf(HdStatus.Verified, HdStatus.Cancelled)
private const val MIB = 1024L * 1024L
private const val CHUNK = 256 * 1024

internal class HdQueue(
    private val context: Context,
    private val media: AttachmentStore,
    transport: OnlineTransport? = null,
    private val linkOrigin: String = BuildConfig.BACKEND_ORIGIN,
) : HdPilot {
    private val key by lazy { InstallationKey() }
    private val api by lazy { transport ?: OnlineApi(context, key) }
    internal var mailbox: HdMailbox? = null
    internal val transport: OnlineTransport get() = api
    private val lock = Mutex()
    private val identityLock = Mutex()
    private val store = AtomicFile(File(context.noBackupFilesDir, "hd-transfers.json"))
    private val binding = context.getSharedPreferences("hd-binding", Context.MODE_PRIVATE)
    private val currentIdentity = MutableStateFlow(readIdentity())
    override val identity = currentIdentity.asStateFlow()

    private var entries: List<Upload>? = null
    private val progress = MutableStateFlow<List<HdProgress>>(emptyList())
    override val transfers = progress.asStateFlow()

    private data class Upload(
        val id: String, val owner: String, val recipient: String, val attachments: List<AttachmentId>,
        val total: Long, val metered: Boolean, val status: HdStatus = HdStatus.Queued,
        val generation: Int = 0, val bytes: Long = 0, val nextAttempt: Long = 0, val detail: String? = null,
        val linkDelivery: Boolean = false, val link: String? = null, val completedAt: Long = 0, val linkExpires: Long = 0,
    ) {
        fun json() = JSONObject().put("id", id).put("owner", owner).put("recipient", recipient)
            .put("attachments", JSONArray(attachments.map { it.value })).put("total", total).put("metered", metered)
            .put("status", status.name).put("generation", generation).put("bytes", bytes).put("retry", nextAttempt).put("detail", detail).put("linkDelivery", linkDelivery).put("link", link)
            .put("completedAt", completedAt).put("linkExpires", linkExpires)

        companion object {
            fun read(json: JSONObject): Upload {
                val attachments = json.getJSONArray("attachments")
                return Upload(
                    json.getString("id"), json.getString("owner"), json.getString("recipient"),
                    List(attachments.length()) { AttachmentId(attachments.getString(it)) },
                    json.getLong("total"), json.getBoolean("metered"), HdStatus.valueOf(json.getString("status")),
                    json.getInt("generation"), json.getLong("bytes"), json.getLong("retry"),
                    json.optString("detail").takeIf { it.isNotEmpty() && it != "null" },
                    json.optBoolean("linkDelivery"), json.optString("link").takeIf { it.startsWith("https://") },
                    json.optLong("completedAt"), json.optLong("linkExpires"),
                )
            }
        }
    }

    private fun readIdentity() = HdIdentity(
        binding.getString("installation", null),
        binding.getString("challenge", null).takeIf { binding.getLong("challengeExpiry", 0) > unixTime() },
        binding.getBoolean("discoverable", false),
    )

    private fun saveIdentity(installation: String?, challenge: String? = null, expiry: Long = 0, discoverable: Boolean = false) {
        check(binding.edit().putString("installation", installation).putString("challenge", challenge)
            .putLong("challengeExpiry", expiry).putBoolean("discoverable", discoverable).commit())
        currentIdentity.value = readIdentity()
    }

    override suspend fun requestCode(number: String) = withContext(Dispatchers.IO) {
        identityLock.withLock {
            val phone = requireNotNull(hdPhone(number)) { "Wpisz poprawny numer telefonu z kodem kraju." }
            val result = api.request(
                "POST", "/v1/number-challenges",
                JSONObject().put("number", phone).put("publicKey", key.publicKey()).bytes(), credential = "",
            )
            val old = currentIdentity.value
            saveIdentity(old.installation, result.getString("challengeId"), result.getLong("expiresAt"), old.discoverable)
        }
    }

    override suspend fun verifyCode(code: String) = withContext(Dispatchers.IO) {
        identityLock.withLock {
            require(code.matches(Regex("[0-9]{4,10}"))) { "Wpisz kod z SMS." }
            val old = readIdentity()
            val challenge = old.challenge ?: throw OnlineError(401, "invalid_code", 0)
            val owner = try {
                api.request("POST", "/v1/number-challenges/$challenge/verification", JSONObject().put("code", code).bytes(),
                    credential = challenge).getString("installationId")
            } catch (e: IOException) {
                // A successful first activation may have committed while its response was lost.
                try { api.request("POST", "/v1/installations/$challenge/sessions", credential = challenge) }
                catch (_: IOException) { throw e }
                challenge
            }
            saveIdentity(owner, discoverable = owner == old.installation && old.discoverable)
            api.forget()
            if (old.installation != null && old.installation != owner) discard(old.installation)
            HdPush.signal(context)
        }
    }

    override suspend fun setDiscoverable(enabled: Boolean) = withContext(Dispatchers.IO) {
        identityLock.withLock {
            val old = readIdentity()
            val owner = old.installation ?: throw OnlineError(401, "binding_required", 0)
            api.signed(owner, "PUT", "/v1/discovery", JSONObject().put("enabled", enabled).bytes())
            saveIdentity(owner, old.challenge, binding.getLong("challengeExpiry", 0), enabled)
        }
    }

    override suspend fun lookup(recipient: String): Boolean {
        val owner = identity.value.installation ?: throw OnlineError(401, "binding_required", 0)
        val number = requireNotNull(hdPhone(recipient)) { "Nie można sprawdzić tego numeru." }
        return api.signed(owner, "POST", "/v1/capabilities/lookup", JSONObject().put("number", number).bytes()).getBoolean("available")
    }

    override suspend fun detach() = withContext(Dispatchers.IO) {
        identityLock.withLock {
            val owner = identity.value.installation
            if (owner != null) {
                try { api.signed(owner, "DELETE", "/v1/binding") }
                catch (e: OnlineError) { if (e.code != "binding_required") throw e }
            }
            saveIdentity(null)
            api.forget()
            key.delete()
            if (owner != null) discard(owner)
        }
    }

    private suspend fun discard(owner: String) {
        lock.withLock {
            val old = all()
            val discarded = old.filter { it.owner == owner }
            if (discarded.isNotEmpty()) {
                save(old - discarded.toSet())
                discarded.forEach { WorkManager.getInstance(context).cancelUniqueWork(workName(it)) }
            }
        }
        mailbox?.detached(owner)
    }

    private fun all(): List<Upload> {
        entries?.let { return it }
        val array = try { JSONArray(store.readFully().toString(Charsets.UTF_8)) }
        catch (_: FileNotFoundException) { JSONArray() }
        return List(array.length()) { Upload.read(array.getJSONObject(it)) }.also { entries = it; publish(it) }
    }

    private fun publish(rows: List<Upload>) {
        progress.value = rows.map { HdProgress(it.id, it.bytes, it.total, it.status, it.detail, it.link, it.recipient, it.linkExpires.takeIf { expiry -> expiry > 0 }) }
    }

    private fun save(rows: List<Upload>) {
        val file = store.startWrite()
        try {
            file.write(JSONArray(rows.map(Upload::json)).toString().toByteArray(Charsets.UTF_8))
            store.finishWrite(file)
        } catch (e: Exception) {
            store.failWrite(file)
            throw e
        }
        entries = rows
        publish(rows)
    }

    suspend fun protectedIds(): Set<AttachmentId> = withContext(Dispatchers.IO) {
        lock.withLock { all().filter { it.status != HdStatus.Cancelled }.flatMap { it.attachments }.toSet() } + mailbox?.protectedIds().orEmpty()
    }

    suspend fun recover() = withContext(Dispatchers.IO) {
        lock.withLock { all().filter { it.status in inProgress }.forEach { schedule(it) } }
        mailbox?.recover()
    }

    fun registerRepair() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "hd-queue-recovery", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<HdRecoveryWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    override suspend fun sync() { mailbox?.sync(); kotlinx.coroutines.withTimeoutOrNull(10_000) { registerPush() } }
    internal suspend fun registerPush() {
        val owner = identity.value.installation ?: return
        val token = HdPush.token(context) ?: return
        api.signed(owner, "PUT", "/v1/push-registration", JSONObject().put("token", token).bytes())
    }
    override suspend fun download(id: String, allowMetered: Boolean) { mailbox?.enqueue(id, allowMetered) }
    override suspend fun enqueue(recipient: String, files: List<StoredMedia>, allowMetered: Boolean) = enqueueFiles(recipient, files, allowMetered, false)
    override suspend fun enqueueLink(recipient: String, files: List<StoredMedia>, allowMetered: Boolean) = enqueueFiles(recipient, files, allowMetered, true)

    private suspend fun enqueueFiles(recipient: String, files: List<StoredMedia>, allowMetered: Boolean, link: Boolean) = withContext(Dispatchers.IO) {
        identityLock.withLock {
            val state = currentIdentity.value
            val owner = state.installation ?: throw OnlineError(401, "binding_required", 0)
            check(link || state.discoverable) { "Włącz odkrywalność w ustawieniach Chatra HD." }
            val number = requireNotNull(hdPhone(recipient))
            val total = files.sumOf { it.bytes }
            require(files.size in 1..5 && files.all { it.bytes in 1..(25 * MIB) } && total <= 50 * MIB) {
                "HD obsługuje do 5 plików, po 25 MiB każdy i łącznie do 50 MiB."
            }
            val ids = files.map { it.id }
            lock.withLock queue@{
                val old = all()
                if (old.any { it.owner == owner && it.recipient == number && it.attachments == ids && it.linkDelivery == link && it.status !in setOf(HdStatus.Failed, HdStatus.Cancelled) && (!link || it.linkExpires == 0L || it.linkExpires > unixTime()) }) return@queue
                require(old.count { it.status !in terminal } < 16) { "Kolejka HD jest pełna." }
                val row = Upload(newOnlineId(), owner, number, ids, total, allowMetered, linkDelivery = link)
                val recent = old.filter { it.status == HdStatus.Verified && it.completedAt > unixTime() - 7 * 86400 }.toSet()
                require(recent.size < 128) { "Lista ostatnich transferów jest pełna. Usuń zbędne transfery z serwera." }
                save(old.filter { it.status !in terminal || it in recent } + row)
                schedule(row)
            }
        }
    }

    override suspend fun pause(id: String) = action(id) { row ->
        row.takeIf { it.status in setOf(HdStatus.Queued, HdStatus.Uploading, HdStatus.Waiting) }?.copy(status = HdStatus.Paused)
    }

    override suspend fun resume(id: String) = action(id) { row ->
        row.takeIf {
            it.status in setOf(HdStatus.Paused, HdStatus.NeedsActivation) && it.owner == currentIdentity.value.installation
        }?.copy(status = HdStatus.Queued, nextAttempt = maxOf(unixTime(), row.nextAttempt), detail = null)
    }

    override suspend fun cancel(id: String) = action(id) { row ->
        row.takeIf { it.status !in setOf(HdStatus.Cancelled, HdStatus.Cancelling) }?.copy(
            status = HdStatus.Cancelling, nextAttempt = 0, detail = null,
        )
    }

    private suspend fun action(id: String, change: (Upload) -> Upload?) = withContext(Dispatchers.IO) {
        lock.withLock {
            val old = all()
            val row = old.firstOrNull { it.id == id } ?: return@withLock
            val update = change(row)?.copy(generation = row.generation + 1) ?: return@withLock
            save(old.map { if (it.id == id) update else it })
            WorkManager.getInstance(context).cancelUniqueWork(workName(row))
            if (update.status in inProgress) schedule(update)
            Unit
        }
    }

    private fun workName(row: Upload) = "hd-transfer:${row.id}:${row.generation}"

    private fun schedule(row: Upload) {
        val network = if (row.metered || row.status == HdStatus.Cancelling) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<HdUploadWorker>()
            .setInputData(workDataOf("id" to row.id, "generation" to row.generation))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInitialDelay((row.nextAttempt - unixTime()).coerceAtLeast(0), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(row), ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun update(row: Upload, change: (Upload) -> Upload): Boolean {
        currentCoroutineContext().ensureActive()
        return lock.withLock {
            val old = all()
            val live = old.firstOrNull { it.id == row.id && it.generation == row.generation } ?: return@withLock false
            save(old.map { if (it.id == live.id) change(live) else it })
            true
        }
    }

    suspend fun run(id: String, generation: Int): Boolean = withContext(Dispatchers.IO) {
        val row = lock.withLock { all().firstOrNull { it.id == id && it.generation == generation } }
            ?.takeIf { it.status in inProgress } ?: return@withContext true
        if (row.nextAttempt > unixTime()) return@withContext false
        try {
            if (row.status == HdStatus.Cancelling) {
                try { api.signed(row.owner, "DELETE", "/v1/transfer-requests/${row.id}") }
                catch (e: OnlineError) { if (e.code != "binding_required") throw e }
                update(row) { it.copy(status = HdStatus.Cancelled) }
                return@withContext true
            }
            if (row.owner != currentIdentity.value.installation) throw OnlineError(401, "binding_required", 0)
            if (!update(row) { it.copy(status = HdStatus.Uploading, detail = null) }) return@withContext true
            upload(row)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                update(row) { if (it.status == HdStatus.Uploading) it.copy(status = HdStatus.Waiting) else it }
            }
            throw e
        } catch (e: OnlineError) {
            when {
                e.status == 429 || e.status >= 500 -> retry(row, e.code, e.retryAfter)
                e.code in setOf("wrong_offset", "invalid_upload_ticket") -> retry(row, e.code)
                e.status == 401 -> update(row) { it.copy(status = HdStatus.NeedsActivation, detail = e.code) }.let { true }
                else -> update(row) { it.copy(status = HdStatus.Failed, detail = e.code) }.let { true }
            }
        } catch (_: FileNotFoundException) {
            update(row) { it.copy(status = HdStatus.Failed, detail = "file_unavailable") }
            true
        } catch (_: IOException) {
            retry(row, "network")
        } catch (_: org.json.JSONException) {
            retry(row, "invalid_response")
        } catch (_: IllegalArgumentException) {
            retry(row, "invalid_response")
        } catch (_: IllegalStateException) {
            retry(row, "invalid_response")
        }
    }

    private suspend fun retry(row: Upload, detail: String, delay: Long = 0): Boolean =
        !update(row) { it.copy(status = if (it.status == HdStatus.Cancelling) it.status else HdStatus.Waiting,
            detail = detail, nextAttempt = unixTime() + delay) }

    private suspend fun upload(row: Upload): Boolean {
        val files = row.attachments.map { media.get(it) ?: throw FileNotFoundException() }
        val manifest = JSONArray(files.map {
            JSONObject().put("size", it.bytes).put("mime", it.mime).put("sha256", it.sha256)
                .put("variant", if (it.sanitized) "no_location" else "original")
        })
        val unmetered = !row.metered
        val created = api.signed(
            row.owner, "POST", "/v1/transfers", JSONObject().put("recipient", if (row.linkDelivery) JSONObject.NULL else row.recipient).put("delivery", if (row.linkDelivery) "link" else "hd").put("files", manifest).bytes(),
            headers = mapOf("Idempotency-Key" to row.id), unmetered = unmetered,
        )
        val transferId = created.getString("transferId")
        val path = "/v1/transfers/$transferId"
        if (created.getString("state") == "finalized") return markVerified(row, created)
        if (created.getString("state") == "invalid") throw OnlineError(422, "file_checksum", 0)
        val serverFiles = created.getJSONArray("files")
        require(serverFiles.length() == files.size)
        val offsets = LongArray(files.size) { serverFiles.getJSONObject(it).getLong("offset") }
        if (!update(row) { it.copy(bytes = offsets.sum()) }) return true
        val deadline = SystemClock.elapsedRealtime() + 120_000
        for ((position, file) in files.withIndex()) {
            if (offsets[position] == file.bytes) continue
            val filePath = "$path/files/$position"
            var ticket = api.signed(row.owner, "POST", "$filePath/ticket", unmetered = unmetered)
            require(ticket.getInt("chunkSize") == CHUNK)
            var offset = ticket.getLong("offset")
            require(offset in 0..file.bytes)
            context.contentResolver.openFileDescriptor(Uri.parse(file.uri), "r")?.use { source ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(source).channel.use { channel ->
                    channel.position(offset)
                    while (offset < file.bytes) {
                        currentCoroutineContext().ensureActive()
                        if (SystemClock.elapsedRealtime() >= deadline) return retry(row, "continuing")
                        if (ticket.getLong("expiresAt") <= unixTime() + 20) {
                            ticket = api.signed(row.owner, "POST", "$filePath/ticket", unmetered = unmetered)
                            if (ticket.getLong("offset") != offset) return retry(row, "wrong_offset")
                        }
                        val uploadPath = ticket.getString("path")
                        require(uploadPath.startsWith("$filePath/chunks?"))
                        val body = ByteArray(minOf(CHUNK.toLong(), file.bytes - offset).toInt())
                        val buffer = java.nio.ByteBuffer.wrap(body)
                        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw FileNotFoundException()
                        val sent = api.signed(
                            row.owner, "PUT", uploadPath, body,
                            mapOf("Upload-Offset" to offset.toString(), "X-Chunk-SHA256" to body.sha256()), unmetered,
                        ).getLong("offset")
                        if (sent != offset + body.size) return retry(row, "wrong_offset")
                        offset = sent
                        offsets[position] = offset
                        if (!update(row) { it.copy(bytes = offsets.sum()) }) return true
                    }
                }
            } ?: throw FileNotFoundException()
        }
        return markVerified(row, api.signed(row.owner, "POST", "$path/finalize", unmetered = unmetered))
    }

    private suspend fun markVerified(row: Upload, result: JSONObject): Boolean {
        check(result.getString("state") == "finalized" && !result.isNull("envelopeId"))
        var expiry = 0L
        val link = if (row.linkDelivery) {
            val transfer = result.getString("transferId")
            val shared = api.signed(row.owner, "POST", "/v1/transfers/$transfer/shares",
                JSONObject().put("consent", true).put("ttlSeconds", 86400).bytes(),
                mapOf("Idempotency-Key" to row.id), !row.metered)
            val path = shared.getString("path")
            expiry = shared.getLong("expiresAt")
            require(path.matches(Regex("/r/#[0-9a-f]{32}\\.[0-9a-f]{64}")))
            linkOrigin + path
        } else {
            mailbox?.sent(result.getString("envelopeId"), row.attachments)
            null
        }
        update(row) { it.copy(status = HdStatus.Verified, bytes = row.total, detail = null, link = link, completedAt = unixTime(), linkExpires = expiry) }
        return true
    }
}

class HdUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.HD_PILOT) return Result.failure()
        val queue = (applicationContext as ChatraApplication).container(false).hd as? HdQueue ?: return Result.failure()
        val id = inputData.getString("id") ?: return Result.failure()
        return if (queue.run(id, inputData.getInt("generation", -1))) Result.success() else Result.retry()
    }
}

class HdRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.HD_PILOT) return Result.success()
        val queue = (applicationContext as ChatraApplication).container(false).hd as? HdQueue ?: return Result.failure()
        queue.recover()
        return try { queue.sync(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
