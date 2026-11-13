package app.chatra.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.chatra.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal class OnlineError(val status: Int, val code: String, val retryAfter: Long) : IOException(code)

internal fun newOnlineId(): String = UUID.randomUUID().toString().replace("-", "")
internal fun unixTime(): Long = System.currentTimeMillis() / 1000
internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

internal class InstallationKey {
    private val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "chatra-hd-signing-v1"

    @Synchronized
    fun publicKey(): String {
        if (!keys.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
            }.generateKeyPair()
        }
        return keys.getCertificate(alias).publicKey.encoded.base64Url()
    }

    @Synchronized
    fun sign(message: String): String {
        val key = keys.getKey(alias, null) as? java.security.PrivateKey ?: throw OnlineError(401, "binding_required", 0)
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message.toByteArray(Charsets.US_ASCII))
            sign().base64Url()
        }
    }

    @Synchronized
    fun delete() = keys.deleteEntry(alias)
}

internal interface OnlineTransport {
    suspend fun signed(
        installation: String, method: String, path: String, body: ByteArray = byteArrayOf(),
        headers: Map<String, String> = emptyMap(), unmetered: Boolean = false,
    ): JSONObject

    suspend fun request(
        method: String, path: String, body: ByteArray = byteArrayOf(), credential: String? = null,
        bearer: Boolean = false, headers: Map<String, String> = emptyMap(), unmetered: Boolean = false,
    ): JSONObject

    suspend fun download(installation: String, path: String, unmetered: Boolean): ByteArray = throw IOException("download_unavailable")

    suspend fun forget()
}

internal class OnlineApi(context: Context, private val key: InstallationKey? = null) : OnlineTransport {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val sessionLock = Mutex()
    private var session: Session? = null
    private data class Session(val owner: String, val token: String, val expires: Long)

    override suspend fun signed(
        installation: String,
        method: String,
        path: String,
        body: ByteArray,
        headers: Map<String, String>,
        unmetered: Boolean,
    ): JSONObject {
        val token = tokenFor(installation, unmetered)
        return try {
            request(method, path, body, token, true, headers, unmetered)
        } catch (e: OnlineError) {
            if (e.status != 401 || e.code != "session_expired") throw e
            val renewed = tokenFor(installation, unmetered, token)
            request(method, path, body, renewed, true, headers, unmetered)
        }
    }

    override suspend fun download(installation: String, path: String, unmetered: Boolean): ByteArray {
        val token = tokenFor(installation, unmetered)
        return try { wire("GET", path, byteArrayOf(), token, true, emptyMap(), unmetered, true) }
        catch (e: OnlineError) {
            if (e.status != 401 || e.code != "session_expired") throw e
            wire("GET", path, byteArrayOf(), tokenFor(installation, unmetered, token), true, emptyMap(), unmetered, true)
        }
    }

    override suspend fun forget() = sessionLock.withLock { session = null }

    private suspend fun tokenFor(owner: String, unmetered: Boolean, rejected: String? = null): String = sessionLock.withLock {
        session?.takeIf { it.owner == owner && it.expires > unixTime() + 30 && it.token != rejected }?.let { return@withLock it.token }
        val data = request("POST", "/v1/installations/$owner/sessions", credential = owner, unmetered = unmetered)
        data.getString("token").also { session = Session(owner, it, data.getLong("expiresAt")) }
    }

    override suspend fun request(
        method: String,
        path: String,
        body: ByteArray,
        credential: String?,
        bearer: Boolean,
        headers: Map<String, String>,
        unmetered: Boolean,
    ): JSONObject {
        val bytes = wire(method, path, body, credential, bearer, headers, unmetered, false)
        return if (bytes.isEmpty()) JSONObject() else JSONObject(bytes.toString(Charsets.UTF_8))
    }

    private suspend fun wire(method: String, path: String, body: ByteArray, credential: String?,
        bearer: Boolean, headers: Map<String, String>, unmetered: Boolean, binary: Boolean): ByteArray = withContext(Dispatchers.IO) {
        require(path.startsWith("/v1/") && !path.contains('#'))
        val url = URL(BuildConfig.BACKEND_ORIGIN + path)
        val connection = if (unmetered) {
            val network = connectivity.activeNetwork ?: throw IOException("no_network")
            val caps = connectivity.getNetworkCapabilities(network)
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true) throw IOException("metered_network")
            network.openConnection(url) as HttpsURLConnection
        } else {
            url.openConnection() as HttpsURLConnection
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", if (binary) "application/octet-stream" else "application/json")
            if (credential != null) {
                val timestamp = unixTime().toString()
                val nonce = newOnlineId()
                val canonical = listOf(
                    "CHATRA-HD-1", method, path, body.sha256(), timestamp, nonce,
                    credential.toByteArray(Charsets.US_ASCII).sha256(),
                    headers["Idempotency-Key"].orEmpty(), headers["Upload-Offset"].orEmpty(),
                    headers["X-Chunk-SHA256"].orEmpty(),
                ).joinToString("\n")
                connection.setRequestProperty("X-Chatra-Time", timestamp)
                connection.setRequestProperty("X-Chatra-Nonce", nonce)
                connection.setRequestProperty("X-Chatra-Signature", checkNotNull(key).sign(canonical))
                if (bearer) connection.setRequestProperty("Authorization", "Bearer $credential")
            }
            headers.forEach(connection::setRequestProperty)
            if (body.isNotEmpty()) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", if (method == "PUT" && path.contains("/chunks?")) "application/octet-stream" else "application/json")
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val success = status in 200..299
            val stream = if (success) connection.inputStream else connection.errorStream
            val data = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > if (binary && success) 262_144 else 32_768) throw IOException("invalid_response")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val result = data?.takeIf { it.isNotEmpty() }?.let {
                try { JSONObject(it.toString(Charsets.UTF_8)) } catch (_: org.json.JSONException) { null }
            }
            if (!success) throw OnlineError(
                status, result?.optString("code") ?: "http_error",
                (connection.getHeaderField("Retry-After")?.toLongOrNull() ?: 0).coerceIn(0, 86_400),
            )
            if (!binary && result == null && status != 204) throw IOException("invalid_response")
            data ?: byteArrayOf()
        } finally {
            connection.disconnect()
        }
    }
}

internal fun JSONObject.bytes(): ByteArray = toString().toByteArray(Charsets.UTF_8)
