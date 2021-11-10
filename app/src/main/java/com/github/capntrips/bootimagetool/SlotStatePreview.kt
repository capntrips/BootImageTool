package com.github.capntrips.bootimagetool

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SlotStatePreview constructor(private val _isRefreshing : MutableStateFlow<Boolean>, isActive: Boolean) : ViewModel(), SlotStateInterface {
    override var patchStatus: PatchStatus = if (isActive) PatchStatus.Patched else PatchStatus.Stock
    override var sha1: String = "0a1b2c3d"
    override var backupStatus: BackupStatus = BackupStatus.Missing
    override var downloadStatus: DownloadStatus = DownloadStatus.Missing

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing.asStateFlow()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isRefreshing.emit(true)
            block()
            _isRefreshing.emit(false)
        }
    }

    override fun refresh(context: Context) {
        launch {
            delay(500)
        }
    }

    override fun downloadImage(context: Context) {
        launch {
            delay(500)
            downloadStatus = DownloadStatus.Found
        }
    }

    override fun restoreBackup(context: Context, uri: Uri) {
        launch {
            delay(500)
            backupStatus = BackupStatus.Found
        }
    }
}
