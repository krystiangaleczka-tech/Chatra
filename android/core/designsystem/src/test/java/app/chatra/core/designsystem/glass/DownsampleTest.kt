package app.chatra.core.designsystem.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class DownsampleTest {
    @Test
    fun smallBlurStaysFullResolution() {
        assertEquals(1f, downsample(0f), 0f)
        assertEquals(1f, downsample(16f), 0f)
    }

    @Test
    fun largeBlurKeepsSixteenPixelSigmaUpToQuarter() {
        assertEquals(0.5f, downsample(32f), 1e-6f)
        assertEquals(16f, 48f * downsample(48f), 1e-4f)
        // Arkusz S04 przy DPR 3,5: σ 40 dp = 140 px → 1/4, po zmniejszeniu σ 35 px.
        assertEquals(0.25f, downsample(140f), 0f)
    }
}
