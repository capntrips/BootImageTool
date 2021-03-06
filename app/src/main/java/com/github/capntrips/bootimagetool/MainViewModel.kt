package com.github.capntrips.bootimagetool

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel constructor(context: Context) : ViewModel(), MainViewModelInterface {
    private val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private lateinit var _uiState: MutableStateFlow<DeviceStateInterface>
    private var _error: String? = null

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()
    override val uiState: StateFlow<DeviceStateInterface>
        get() = _uiState.asStateFlow()
    val hasError: Boolean
        get() = _error != null
    val error: String
        get() = _error ?: "Unknown Error"

    init {
        try {
            _uiState = MutableStateFlow(DeviceState(context, _isRefreshing))
        } catch (e: Exception) {
            _error = e.message
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.emit(true)
            block()
            _isRefreshing.emit(false)
        }
    }

    override fun refresh(context: Context) {
        launch {
            try {
                uiState.value.refresh(context)
            } catch (e: Exception) {
                _error = e.message
            }
        }
    }
}
