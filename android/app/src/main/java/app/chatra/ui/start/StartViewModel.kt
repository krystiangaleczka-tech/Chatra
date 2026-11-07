package app.chatra.ui.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chatra.core.model.SetupState
import app.chatra.core.model.SmsRoleGate
import app.chatra.core.model.SmsSetup
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StartViewModel(gate: SmsRoleGate) : ViewModel() {
    private val setup = SmsSetup(gate)
    val state: StateFlow<SetupState> = setup.state

    init {
        viewModelScope.launch { setup.check() }
    }

    fun onPrimary() {
        viewModelScope.launch { setup.primary() }
    }

    fun onResume() = setup.recheck()
}
