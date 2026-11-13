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

    @Test fun revokedProFallsBackWithoutChangingAccessibilityOrSavedSelection() {
        val global = AppearancePrefs(composition = Composition.Amber, textStep = 7, increaseContrast = true)
        val thread = AppearanceOverride(composition = Composition.Forest, reduceTransparency = true)
        assertEquals(Composition.Forest, resolveAppearance(global, thread, proAllowed = true).composition)
        val fallback = resolveAppearance(global, thread, proAllowed = false)
        assertEquals(Composition.Glass, fallback.composition)
        assertEquals(7, fallback.textStep)
        assertTrue(fallback.increaseContrast)
        assertTrue(fallback.reduceTransparency)
        assertEquals(Composition.Amber, global.composition)
        assertEquals(Composition.Forest, thread.composition)
        assertEquals(Composition.Mist, resolveAppearance(global.copy(composition = Composition.Mist), thread, proAllowed = false).composition)
    }
}
