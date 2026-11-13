package app.chatra.online

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chatra.core.model.AttachmentId
import app.chatra.core.model.AttachmentStore
import app.chatra.core.model.MediaVariant
import app.chatra.core.model.StoredMedia
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class HdQueueTest {
    @Test
    fun resumesOwnedFileFromServerOffsetAfterReopeningQueue() = runBlocking {
        withQueue { context, store, remote, file ->
            val queue = activated(context, store, remote)
            queue.enqueue("+48222111222", listOf(file), false)
            val id = queue.transfers.value.single().id
            remote.breakAfterChunk = true
            assertFalse(queue.run(id, 0))
            assertEquals(HdStatus.Waiting, queue.transfers.value.single().status)
            assertEquals(262_144L, queue.transfers.value.single().bytes)
            assertTrue(file.id in queue.protectedIds())
            assertTrue(remote.unmetered)

            val reopened = HdQueue(context, store, remote)
            reopened.recover()
            assertEquals(HdStatus.Waiting, reopened.transfers.value.single().status)
            assertTrue(reopened.run(id, 0))
            assertEquals(HdStatus.Verified, reopened.transfers.value.single().status)
            assertEquals(file.bytes, reopened.transfers.value.single().bytes)
            assertEquals(listOf(0L, 262_144L), remote.requests.getValue(id).offsets)
            assertEquals(file.sha256, remote.requests.getValue(id).content.toByteArray().sha256())
            assertFalse(file.id in reopened.protectedIds())
        }
    }

    @Test
    fun pausesAndCancelsByRequestKeyAndRejectsStaleWorker() = runBlocking {
        withQueue { context, store, remote, file ->
            val queue = activated(context, store, remote)
            queue.enqueue("+48222111222", listOf(file), true)
            val id = queue.transfers.value.single().id
            queue.pause(id)
            assertEquals(HdStatus.Paused, queue.transfers.value.single().status)
            assertTrue(queue.run(id, 0))
            assertTrue(remote.requests.isEmpty())
            queue.resume(id)
            assertEquals(HdStatus.Queued, queue.transfers.value.single().status)
            assertTrue(queue.run(id, 0))
            assertTrue(remote.requests.isEmpty())
            queue.cancel(id)
            assertTrue(queue.run(id, 3))
            assertEquals(HdStatus.Cancelled, queue.transfers.value.single().status)
            assertEquals(setOf(id), remote.cancelled)
            assertNotNull(store.get(file.id))
        }
    }

    @Test
    fun distinguishesRateLimitAuthorizationAndSizeFailures() = runBlocking {
        withQueue { context, store, remote, file ->
            val queue = activated(context, store, remote)
            queue.enqueue("+48222111222", listOf(file), false)
            val id = queue.transfers.value.single().id
            remote.failure = OnlineError(429, "rate_limited", 3600)
            assertFalse(queue.run(id, 0))
            assertEquals("rate_limited", queue.transfers.value.single().detail)
            val previousCalls = remote.createCalls
            assertFalse(queue.run(id, 0))
            assertEquals(previousCalls, remote.createCalls)

            queue.enqueue("+48222111223", listOf(file), false)
            val second = queue.transfers.value.last().id
            remote.failure = OnlineError(401, "binding_required", 0)
            assertTrue(queue.run(second, 0))
            assertEquals(HdStatus.NeedsActivation, queue.transfers.value.last().status)
            queue.resume(second)
            remote.failure = OnlineError(413, "transfer_too_large", 0)
            assertTrue(queue.run(second, 1))
            assertEquals(HdStatus.Failed, queue.transfers.value.last().status)
            assertTrue(file.id in queue.protectedIds())
        }
    }

    @Test
    fun acceptsTwoConcurrentTransfersAndSurvivesServiceFailure() = runBlocking {
        withQueue { context, store, remote, file ->
            val queue = activated(context, store, remote)
            queue.enqueue("+48222111222", listOf(file), true)
            queue.enqueue("+48222111223", listOf(file), true)
            val ids = queue.transfers.value.map { it.id }
            remote.failure = OnlineError(503, "storage_unavailable", 0)
            assertFalse(queue.run(ids.first(), 0))
            remote.failure = null
            coroutineScope {
                val first = async { queue.run(ids.first(), 0) }
                val second = async { queue.run(ids.last(), 0) }
                assertTrue(first.await())
                assertTrue(second.await())
            }
            assertEquals(listOf(HdStatus.Verified, HdStatus.Verified), queue.transfers.value.map { it.status })
            assertEquals(ids.toSet(), remote.requests.keys)
        }
    }

    private suspend fun activated(context: Context, store: AttachmentStore, remote: Remote): HdQueue =
        HdQueue(context, store, remote).also {
            it.requestCode("+48222111221")
            it.verifyCode("123456")
            it.setDiscoverable(true)
        }

    private suspend fun withQueue(block: suspend (Context, AttachmentStore, Remote, StoredMedia) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "hd-test-${newOnlineId()}"
        val root = File(app.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("$prefix-$name", mode)
        }
        val source = File(root, "owned").apply { writeBytes(ByteArray(350_000) { (it % 251).toByte() }) }
        val file = StoredMedia(AttachmentId.new(), Uri.fromFile(source).toString(), "image/png",
            source.length(), source.readBytes().sha256(), 0)
        val store = object : AttachmentStore {
            override suspend fun get(id: AttachmentId) = file.takeIf { it.id == id && source.exists() }
            override suspend fun importUri(uri: String) = error("Unused")
            override suspend fun derive(id: AttachmentId, variant: MediaVariant, maxBytes: Long?) = error("Unused")
            override suspend fun collectUnused(protectedIds: Set<AttachmentId>, olderThan: Long) = Unit
            override fun cameraUri() = error("Unused")
        }
        try { block(context, store, Remote(), file) }
        finally {
            app.deleteSharedPreferences("$prefix-hd-binding")
            root.deleteRecursively()
        }
    }

    private class Remote : OnlineTransport {
        data class Upload(val size: Long, val digest: String, val content: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream(),
            val offsets: MutableList<Long> = mutableListOf(), var ready: Boolean = false)

        val requests = ConcurrentHashMap<String, Upload>()
        val cancelled = mutableSetOf<String>()
        var breakAfterChunk = false
        var unmetered = false
        var failure: OnlineError? = null
        var createCalls = 0
        private val owner = newOnlineId()
        private val challenge = newOnlineId()

        override suspend fun forget() = Unit

        override suspend fun request(method: String, path: String, body: ByteArray, credential: String?, bearer: Boolean,
            headers: Map<String, String>, unmetered: Boolean): JSONObject = when (path) {
            "/v1/number-challenges" -> JSONObject().put("challengeId", challenge).put("expiresAt", unixTime() + 600)
            "/v1/number-challenges/$challenge/verification" -> JSONObject().put("installationId", owner)
            else -> error("Unexpected path $path")
        }

        override suspend fun signed(installation: String, method: String, path: String, body: ByteArray,
            headers: Map<String, String>, unmetered: Boolean): JSONObject {
            assertEquals(owner, installation)
            if (path == "/v1/discovery") return JSONObject()
            if (path.startsWith("/v1/transfer-requests/")) {
                cancelled += path.substringAfterLast('/')
                return JSONObject()
            }
            if (path == "/v1/transfers") {
                this.unmetered = unmetered
                synchronized(this) { createCalls++ }
                failure?.let { throw it }
                val id = headers.getValue("Idempotency-Key")
                val file = JSONObject(body.toString(Charsets.UTF_8)).getJSONArray("files").getJSONObject(0)
                val upload = requests.computeIfAbsent(id) { Upload(file.getLong("size"), file.getString("sha256")) }
                return response(id, upload)
            }
            val id = path.substringAfter("/v1/transfers/").substringBefore('/')
            val upload = requests.getValue(id)
            return when {
                path.endsWith("/ticket") -> JSONObject().put("offset", upload.content.size())
                    .put("path", "/v1/transfers/$id/files/0/chunks?ticket=test")
                    .put("chunkSize", 262_144).put("expiresAt", unixTime() + 300)
                method == "PUT" -> {
                    val offset = headers.getValue("Upload-Offset").toLong()
                    if (breakAfterChunk && offset > 0) {
                        breakAfterChunk = false
                        throw IOException("Synthetic disconnect")
                    }
                    assertEquals(upload.content.size().toLong(), offset)
                    assertEquals(body.sha256(), headers["X-Chunk-SHA256"])
                    upload.offsets += offset
                    upload.content.write(body)
                    JSONObject().put("offset", upload.content.size())
                }
                path.endsWith("/finalize") -> {
                    assertEquals(upload.size, upload.content.size().toLong())
                    assertEquals(upload.digest, upload.content.toByteArray().sha256())
                    upload.ready = true
                    response(id, upload)
                }
                else -> error("Unexpected path $path")
            }
        }

        private fun response(id: String, upload: Upload) = JSONObject()
            .put("transferId", id).put("state", if (upload.ready) "finalized" else "uploading")
            .put("envelopeId", if (upload.ready) id else JSONObject.NULL)
            .put("files", JSONArray().put(JSONObject().put("offset", upload.content.size())))
    }
}
