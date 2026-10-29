package dev.peripheral.battery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryUiState(val snapshot: BatterySnapshot, val refreshCount: Int = 0) {
    val connection: ConnectionPresentation get() = snapshot.connection.present()
}

class BatteryViewModel(
    private val repository: BatteryRepository = DemoBatteryRepository(),
    observationScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BatteryUiState(repository.snapshot().forUi()))
    val state = mutableState.asStateFlow()
    init {
        // viewModelScope cancels collection when Android clears the ViewModel.
        // Tests can supply a controlled scope without an Android Main dispatcher.
        (observationScope ?: viewModelScope).launch(start = CoroutineStart.UNDISPATCHED) {
            repository.snapshots.collect { snapshot ->
                mutableState.value = mutableState.value.copy(snapshot = snapshot.forUi())
            }
        }
    }
    fun refreshDemo() {
        if (!state.value.snapshot.synthetic) return
        repository.refreshDemo()
        mutableState.value = mutableState.value.copy(refreshCount = mutableState.value.refreshCount + 1)
    }
}
