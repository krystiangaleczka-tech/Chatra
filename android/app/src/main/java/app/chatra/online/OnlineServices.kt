package app.chatra.online

import android.app.Activity
import app.chatra.core.model.ProOffer
import app.chatra.core.model.StoredMedia
import kotlinx.coroutines.flow.StateFlow

enum class ProStatus { Checking, Unavailable, Available, Pending, Active, Cancelled, Invalid, VerificationFailed }

data class ProState(
    val offer: ProOffer = ProOffer.Unavailable,
    val status: ProStatus = ProStatus.Unavailable,
    val entitled: Boolean = status == ProStatus.Active,
)

interface ProBilling {
    val state: StateFlow<ProState>
    fun refresh()
    fun buy(activity: Activity)
}

data class HdIdentity(val installation: String? = null, val challenge: String? = null, val discoverable: Boolean = false)

enum class HdStatus { Queued, Uploading, Waiting, Paused, NeedsActivation, Failed, Cancelling, Verified, Cancelled }

data class HdProgress(val id: String, val bytes: Long, val total: Long, val status: HdStatus, val detail: String? = null,
    val link: String? = null, val recipient: String? = null, val linkExpires: Long? = null)

interface HdPilot {
    val identity: StateFlow<HdIdentity>
    val transfers: StateFlow<List<HdProgress>>
    suspend fun requestCode(number: String)
    suspend fun verifyCode(code: String)
    suspend fun setDiscoverable(enabled: Boolean)
    suspend fun lookup(recipient: String): Boolean
    suspend fun detach()
    suspend fun enqueue(recipient: String, files: List<StoredMedia>, allowMetered: Boolean)
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun cancel(id: String)
    suspend fun sync() {}
    suspend fun download(id: String, allowMetered: Boolean = false) {}
    suspend fun enqueueLink(recipient: String, files: List<StoredMedia>, allowMetered: Boolean) {}
}

fun hdPhone(raw: String): String? {
    val compact = raw.trim().replace(Regex("[ ()-]"), "")
    val number = when {
        compact.startsWith("00") -> "+" + compact.drop(2)
        compact.matches(Regex("[0-9]{9}")) -> "+48$compact"
        else -> compact
    }
    return number.takeIf { it.matches(Regex("\\+[1-9][0-9]{7,14}")) }
}
