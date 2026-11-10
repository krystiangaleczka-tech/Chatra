package app.chatra.ui

import app.chatra.core.model.SetupState
import app.chatra.core.testing.FakeSmsRoleGate
import app.chatra.ui.start.StartViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleResumeTest {
    @Test fun `return after role loss does not reuse completed onboarding`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val gate = FakeSmsRoleGate(held = true)
            val model = StartViewModel(gate)
            runCurrent(); assertEquals(SetupState.Done, model.state.value)
            gate.held = false
            model.onResume(); runCurrent()
            assertTrue(model.state.value is SetupState.Ready)
            assertFalse(model.isRoleHeld())
            model.onPrimary(); runCurrent()
            assertEquals(SetupState.Done, model.state.value)
            assertEquals(1, gate.requests)
        } finally { Dispatchers.resetMain() }
    }
}
