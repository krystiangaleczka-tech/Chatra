package app.chatra.acceptance

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.util.function.Consumer
import org.junit.Assert.*
import org.junit.Test

class TransportApiTest {
    @Test fun alternativeTransportApiMatchesPlatformWithoutEnablingItInChatra() {
        val service = "android.service.messaging.AlternativeMessageTransportService"
        val type = try { Class.forName(service) } catch (_: ClassNotFoundException) { null }
        if (Build.VERSION.SDK_INT >= 37) {
            assertNotNull(type)
            assertNotNull(type!!.getMethod("onMessageUpgradeRequested", Uri::class.java, Consumer::class.java))
        } else {
            assertNull(type)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageManager.queryIntentServices(Intent(service).setPackage(context.packageName), 0).isEmpty())
    }
}
