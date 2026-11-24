package app.chatra.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** UTP may uninstall the test app before the host collects its files. */
internal fun writeQaCapture(image: Bitmap, name: String) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val path = "/data/local/tmp/$name"
    val (stdout, stdin) = automation.executeShellCommandRw("dd of=$path")
    ParcelFileDescriptor.AutoCloseInputStream(stdout).use { output ->
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { input ->
            assertTrue("Could not encode $name", image.compress(Bitmap.CompressFormat.PNG, 100, input))
        }
        output.readBytes()
    }
    val stat = automation.executeShellCommand("stat -c %s $path")
    val size = ParcelFileDescriptor.AutoCloseInputStream(stat).use { it.bufferedReader().readText().trim().toLongOrNull() }
    assertTrue("The exported PNG is empty or missing: $name", size != null && size > 0L)
}
