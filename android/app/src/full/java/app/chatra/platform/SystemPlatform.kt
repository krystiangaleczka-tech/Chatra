package app.chatra.platform

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.chatra.core.model.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** Launchery rejestrowane przed STARTED; wynik zawsze sprawdza faktyczny stan systemu. */
class SystemPlatform(private val context: Context) : ActivityPlatform, ObservableSmsRoleGate {
    private val manager = context.getSystemService(RoleManager::class.java)
    private var activity = WeakReference<ComponentActivity>(null)
    private var roleLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var rolePending: CancellableContinuation<RoleRequestResult>? = null
    private var permissionPending: CancellableContinuation<AccessResult>? = null
    private var requestedFeature: AccessFeature? = null
    private val asked = context.getSharedPreferences("permission-requests", Context.MODE_PRIVATE)
    private val external = ExternalComposeStore(context)
    override val changes = MutableStateFlow(0)
    override val held = MutableStateFlow(isHeld())
    override val incomingDraftId = MutableStateFlow(external.pendingId())
    override fun acceptIntent(intent: Intent): String? = parseComposeIntent(intent)?.let { external.save(it); incomingDraftId.value = it.id; it.id }
    override fun pendingComposeId(): String? = external.pendingId()
    override fun acknowledgeCompose(id: String) { external.acknowledge(id); if (incomingDraftId.value == id) incomingDraftId.value = null }
    fun externalDraft(id: String): ExternalCompose? = external.read(id)

    override fun bindActivity(activity: ComponentActivity) {
        this.activity = WeakReference(activity)
        roleLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
            rolePending?.takeIf { it.isActive }?.resume(if (isHeld()) RoleRequestResult.Granted else RoleRequestResult.Cancelled)
            rolePending = null
        }
        permissionsLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val feature = requestedFeature
            refresh()
            permissionPending?.takeIf { it.isActive }?.resume(if (feature != null && allowed(feature)) AccessResult.Granted else AccessResult.Denied)
            permissionPending = null; requestedFeature = null
        }
    }
    override suspend fun capability(): SmsCapability = when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) -> SmsCapability.NoTelephony
        manager?.isRoleAvailable(RoleManager.ROLE_SMS) != true -> SmsCapability.RoleUnavailable
        else -> SmsCapability.Available
    }
    override fun isHeld(): Boolean = manager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && manager.isRoleHeld(RoleManager.ROLE_SMS)
    override fun refresh() {
        held.value = isHeld()
        external.pendingId()?.let { if (incomingDraftId.value == null) incomingDraftId.value = it }
        changes.value++
    }
    override suspend fun request(): RoleRequestResult {
        if (isHeld()) return RoleRequestResult.Granted
        if (capability() != SmsCapability.Available || roleLauncher == null) return RoleRequestResult.Unavailable
        if (rolePending?.isActive == true) return RoleRequestResult.Cancelled
        val roleManager = manager ?: return RoleRequestResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            rolePending = continuation
            continuation.invokeOnCancellation { if (rolePending === continuation) rolePending = null }
            try { roleLauncher?.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)) }
            catch (_: android.content.ActivityNotFoundException) { rolePending = null; continuation.resume(RoleRequestResult.Unavailable) }
        }
    }
    override fun openSystemSettings() {
        try { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: android.content.ActivityNotFoundException) { openSettings() }
    }
    override fun openSettings() { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun permissions(feature: AccessFeature): Array<String> = when (feature) {
        AccessFeature.Contacts -> arrayOf(Manifest.permission.READ_CONTACTS)
        AccessFeature.Sims -> arrayOf(Manifest.permission.READ_PHONE_STATE)
        AccessFeature.Notifications -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        AccessFeature.Sms -> arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.RECEIVE_MMS, Manifest.permission.RECEIVE_WAP_PUSH)
    }
    override fun allowed(feature: AccessFeature): Boolean = (feature != AccessFeature.Sms || isHeld()) && permissions(feature).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    override suspend fun request(feature: AccessFeature): AccessResult {
        if (feature == AccessFeature.Sms && !isHeld()) return AccessResult.RoleRequired
        if (allowed(feature)) return AccessResult.Granted
        val owner = activity.get() ?: return AccessResult.Unavailable
        val missing = permissions(feature).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.any { asked.getBoolean(it, false) && !owner.shouldShowRequestPermissionRationale(it) }) return AccessResult.SettingsRequired
        if (permissionPending?.isActive == true || permissionsLauncher == null) return AccessResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            requestedFeature = feature; permissionPending = continuation
            continuation.invokeOnCancellation { if (permissionPending === continuation) { permissionPending = null; requestedFeature = null } }
            asked.edit().apply { missing.forEach { putBoolean(it, true) } }.apply()
            permissionsLauncher?.launch(missing.toTypedArray())
        }
    }
}
