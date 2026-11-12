package app.chatra.core.model

import org.junit.Assert.*
import org.junit.Test

class AppearanceInheritanceTest {
    @Test fun globalChangesPreserveExplicitCompositionWhileAccessibilityAlwaysWins() {
        val custom = AppearanceOverride(composition = Composition.Graphite, textStep = 1)
        val global = AppearancePrefs(mode = ThemeMode.Auto, textStep = 7, reduceTransparency = true, increaseContrast = true)
        val actual = resolveAppearance(global, custom)
        assertEquals(Composition.Graphite, actual.composition)
        assertEquals(ThemeMode.Auto, actual.mode)
        assertEquals(7, actual.textStep)
        assertTrue(actual.reduceTransparency); assertTrue(actual.increaseContrast)
        assertEquals(Composition.Graphite, resolveAppearance(AppearancePrefs.Default, custom).composition)
        assertEquals(global, resolveAppearance(global, null))
    }
}
