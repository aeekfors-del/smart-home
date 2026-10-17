package com.besenok.smarthome.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.besenok.smarthome.domain.model.TimeInfo
import com.besenok.smarthome.domain.usecase.time.GetTimeUseCase
import com.besenok.smarthome.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimeViewModel @Inject constructor(
    private val getTimeUseCase: GetTimeUseCase
) : ViewModel() {

    private val _time = MutableStateFlow<UiState<TimeInfo>>(UiState.Loading)
    val time: StateFlow<UiState<TimeInfo>> = _time.asStateFlow()

    init {
        loadTime()
    }

    fun loadTime() {
        viewModelScope.launch {
            try {
                _time.value = getTimeUseCase()
            } catch (e: Exception) {
                _time.value = UiState.Error(e.message ?: "Failed to load time")
            }
        }
    }

    fun syncTime() {
        loadTime()
    }

    val currentTime: String
        get() = time.value.let {
            when (it) {
                is UiState.Success -> it.data.dateTime.substring(11, 19)
                else -> "--:--:--"
            }
        }

    val currentDate: String
        get() = time.value.let {
            when (it) {
                is UiState.Success -> it.data.dateTime.substring(0, 10)
                else -> "----/--/--"
            }
        }

    val timezone: String
        get() = time.value.let {
            when (it) {
                is UiState.Success -> it.data.timezone
                else -> ""
            }
        }
}
