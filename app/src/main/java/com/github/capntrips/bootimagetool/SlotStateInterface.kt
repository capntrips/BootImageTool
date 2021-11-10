package com.github.capntrips.bootimagetool

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface SlotStateInterface {
    var patchStatus: PatchStatus
    var sha1: String
    var backupStatus: BackupStatus
    var downloadStatus: DownloadStatus
    val isRefreshing: StateFlow<Boolean>
    fun refresh(context: Context)
    fun downloadImage(context: Context)
    fun restoreBackup(context: Context, uri: Uri)
}
