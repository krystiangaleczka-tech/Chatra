package app.chatra.local

import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.model.*
import app.chatra.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemContactsTest {
    @Test fun providerChangesMultipleNumbersAndPermissionDenialUpdateDirectory() = runBlocking {
        assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
        val context=instrumentation.targetContext
        val resolver=context.contentResolver
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        var allowed=true
        val gate=object : PlatformAccess {
            override val changes=MutableStateFlow(0)
            override fun allowed(feature: AccessFeature)=allowed
            override suspend fun request(feature: AccessFeature)=AccessResult.Granted
            override fun openSettings() {}
            override fun refresh() {changes.value++}
        }
        val raw=checkNotNull(resolver.insert(ContactsContract.RawContacts.CONTENT_URI,ContentValues().apply {
            putNull(ContactsContract.RawContacts.ACCOUNT_TYPE);putNull(ContactsContract.RawContacts.ACCOUNT_NAME)
        }))
        val rawId=raw.lastPathSegment!!.toLong()
        fun insert(mime: String,value: String,normalized: String?=null) {
            resolver.insert(ContactsContract.Data.CONTENT_URI,ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID,rawId);put(ContactsContract.Data.MIMETYPE,mime)
                put(ContactsContract.Data.DATA1,value)
                if(normalized!=null) put(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,normalized)
            })
        }
        try {
            val name="Chatra QA ${System.nanoTime()}"
            insert(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,name)
            insert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,"600 100 200","+48600100200")
            insert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,"+48600100200","+48600100200")
            insert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,"+48600100201","+48600100201")
            val directory=SystemContacts(context,gate,scope)
            val initial=withTimeout(15000) {directory.suggestions().first {rows->rows.count {it.displayName==name}==2}}.filter {it.displayName==name}
            assertEquals(2,initial.map {recipientKey(it.address.raw)}.distinct().size)
            assertEquals(name,directory.lookup(Address("600100200"))?.displayName)
            insert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,"+48600100202","+48600100202")
            withTimeout(15000) {directory.suggestions().first {rows->rows.count {it.displayName==name}==3}}
            allowed=false;gate.refresh()
            withTimeout(15000) {directory.suggestions().first {it.isEmpty()}}
            assertNull(directory.lookup(Address("600100200")))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            resolver.delete(raw,null,null)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
