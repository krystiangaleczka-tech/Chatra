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
        // API 36 builds can ship an internal stub whose initialization fails.
        if (Build.VERSION.SDK_INT >= 37) {
            val type = Class.forName(service)
            assertNotNull(type.getMethod("onMessageUpgradeRequested", Uri::class.java, Consumer::class.java))
            assertEquals(service, type.getField("SERVICE_INTERFACE").get(null))
            assertEquals(1, type.getField("UPGRADE_STATUS_ACCEPTED").getInt(null))
            assertEquals(2, type.getField("UPGRADE_STATUS_REJECTED").getInt(null))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageManager.queryIntentServices(Intent(service).setPackage(context.packageName), 0).isEmpty())
    }
}
