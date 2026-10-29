package dev.peripheral.battery.ui

import androidx.lifecycle.ViewModel
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryUiState(val snapshot: BatterySnapshot, val refreshCount: Int = 0)
class BatteryViewModel(private val repository: BatteryRepository = DemoBatteryRepository()) : ViewModel() {
    private val mutableState = MutableStateFlow(BatteryUiState(repository.snapshot()))
    val state = mutableState.asStateFlow()
    fun refreshDemo() {
        mutableState.value = BatteryUiState(repository.snapshot(), mutableState.value.refreshCount + 1)
    }
}
