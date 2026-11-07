package app.chatra.core.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsSetupTest {
    private class Gate(
        var capability: SmsCapability = SmsCapability.Available,
        var held: Boolean = false,
        var result: RoleRequestResult = RoleRequestResult.Granted,
    ) : SmsRoleGate {
        var requests = 0
        var settings = 0
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun capability() = capability
        override fun isHeld() = held
        override suspend fun request(): RoleRequestResult {
            requests++
            pending?.await()
            if (result == RoleRequestResult.Granted) held = true
            return result
        }
        override fun openSystemSettings() {
            settings++
        }
    }

    private suspend fun ready(gate: Gate) = SmsSetup(gate).also { it.check() }

    @Test
    fun alreadyHeldSkipsStart() = runTest {
        assertEquals(SetupState.Done, ready(Gate(held = true)).state.value)
    }

    @Test
    fun noTelephonyAndMissingRoleBlock() = runTest {
        assertEquals(SetupState.Blocked(SmsCapability.NoTelephony), ready(Gate(SmsCapability.NoTelephony)).state.value)
        assertEquals(SetupState.Blocked(SmsCapability.RoleUnavailable), ready(Gate(SmsCapability.RoleUnavailable)).state.value)
    }

    @Test
    fun grantedFinishes() = runTest {
        val setup = ready(Gate())
        assertEquals(SetupState.Ready(), setup.state.value)
        setup.primary()
        assertEquals(SetupState.Done, setup.state.value)
    }

    @Test
    fun cancelledAllowsRetry() = runTest {
        val gate = Gate(result = RoleRequestResult.Cancelled)
        val setup = ready(gate)
        setup.primary()
        assertEquals(SetupState.Ready(SetupState.Notice.Cancelled), setup.state.value)
        gate.result = RoleRequestResult.Granted
        setup.primary()
        assertEquals(SetupState.Done, setup.state.value)
        assertEquals(2, gate.requests)
    }

    @Test
    fun deniedSendsToSettingsAndRecheckFinishes() = runTest {
        val gate = Gate(result = RoleRequestResult.Denied)
        val setup = ready(gate)
        setup.primary()
        assertEquals(SetupState.Ready(SetupState.Notice.Denied), setup.state.value)
        setup.primary()
        assertEquals(1, gate.requests)
        assertEquals(1, gate.settings)
        gate.held = true // użytkownik wybrał Chatrę w ustawieniach systemu
        setup.recheck()
        assertEquals(SetupState.Done, setup.state.value)
    }

    @Test
    fun unavailableDuringRequestBlocks() = runTest {
        val setup = ready(Gate(result = RoleRequestResult.Unavailable))
        setup.primary()
        assertEquals(SetupState.Blocked(SmsCapability.RoleUnavailable), setup.state.value)
    }

    @Test
    fun repeatedTapDuringRequestIsIgnored() = runTest {
        val gate = Gate().apply { pending = CompletableDeferred() }
        val setup = ready(gate)
        launch { setup.primary() }
        runCurrent()
        assertEquals(SetupState.Requesting, setup.state.value)
        setup.primary()
        gate.pending!!.complete(Unit)
        runCurrent()
        assertEquals(1, gate.requests)
        assertEquals(SetupState.Done, setup.state.value)
    }
}
