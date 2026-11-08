package app.chatra.core.testing

import app.chatra.core.model.ExactAlarmGate

/** Fałszywa zgoda na dokładne alarmy (CH014). Demo: `--ez exactAlarms false` pokazuje ścieżkę bez zgody. */
class FakeExactAlarmGate(var allowed: Boolean = true) : ExactAlarmGate {
    var settingsOpened = 0
        private set

    override fun canScheduleExact() = allowed

    override fun openSettings() {
        settingsOpened++
    }
}
