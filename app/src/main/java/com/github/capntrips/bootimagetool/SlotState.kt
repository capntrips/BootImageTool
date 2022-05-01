package com.github.capntrips.bootimagetool

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SlotState(context: Context, private val boot: File, private val _isRefreshing : MutableStateFlow<Boolean>?, private val isImage: Boolean = false) : ViewModel(), SlotStateInterface {
    companion object {
        const val TAG: String = "BootImageTool/SlotState"
    }

    override lateinit var patchStatus: PatchStatus
    override lateinit var sha1: String
    override lateinit var backupStatus: BackupStatus
    override lateinit var downloadStatus: DownloadStatus

    override val isRefreshing: StateFlow<Boolean>
        get() = _isRefreshing!!.asStateFlow()

    init {
        refresh(context)
    }

    override fun refresh(context: Context) {
        // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L86
        // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/uninstaller.sh#L82
        Shell.su("/data/adb/magisk/magiskboot unpack $boot").exec()

        // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L110-L112
        val ramdisk = File(context.filesDir, "ramdisk.cpio")
        if (!isImage || ramdisk.exists()) {
            when (Shell.su("/data/adb/magisk/magiskboot cpio ramdisk.cpio test").exec().code) {
                0 -> {
                    // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L118-L121
                    patchStatus = PatchStatus.Stock
                    sha1 = Shell.su("/data/adb/magisk/magiskboot sha1 $boot").exec().out[0]
                }
                1 -> {
                    // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L124-L127
                    patchStatus = PatchStatus.Patched
                    sha1 = Shell.su("/data/adb/magisk/magiskboot cpio ramdisk.cpio sha1").exec().out[0]
                }
                else -> {
                    log(context, "Invalid boot.img", shouldThrow = true)
                }
            }
            ramdisk.delete()
            if (!isImage) {
                refreshBackupStatus()
                refreshDownloadStatus(context)
            }
        } else {
            log(context, "Invalid boot.img", shouldThrow = true)
        }
    }

    private fun refreshBackupStatus() {
        // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/util_functions.sh#L599-L603
        val backup = File("/data/magisk_backup_$sha1/boot.img.gz")
        backupStatus = if (backup.exists()) {
            if (Shell.su("gunzip -c $backup | sha1sum | awk '{ print $1 }'").exec().out[0] == sha1) {
                BackupStatus.Found
            } else {
                BackupStatus.Invalid
            }
        } else {
            BackupStatus.Missing
        }
    }

    private fun refreshDownloadStatus(context: Context) {
        data class Download(
            val uri: Uri,
            val name: String
        )
        val downloadList = mutableListOf<Download>()
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.DownloadColumns._ID,
            MediaStore.DownloadColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.DownloadColumns.DISPLAY_NAME} LIKE ?"
        val suffix = if (patchStatus == PatchStatus.Patched) "-patched" else ""
        val selectionArgs = arrayOf("boot_${sha1.substring(0, 8)}${suffix}%.img")
        val sortOrder = "${MediaStore.DownloadColumns.DISPLAY_NAME} ASC"
        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
            null
        )
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.DownloadColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.DownloadColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    id
                )
                downloadList += Download(contentUri, name)
            }
        }
        if (downloadList.size > 0) {
            val file = File(context.filesDir, "boot.img")
            val inputStream = context.contentResolver.openInputStream(downloadList[0].uri)
            file.writeBytes(inputStream!!.readBytes())
            inputStream.close()
            val state = SlotState(context, file, null, true)
            downloadStatus = if (state.sha1 == sha1) {
                DownloadStatus.Found
            } else {
                DownloadStatus.Invalid
            }
            file.delete()
        } else {
            downloadStatus = DownloadStatus.Missing
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing!!.emit(true)
            block()
            _isRefreshing.emit(false)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun downloadImage(context: Context) {
        launch {
            val contentResolver: ContentResolver = context.contentResolver
            val contentValues = ContentValues()
            val suffix = if (patchStatus == PatchStatus.Patched) "-patched" else ""
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "boot_${sha1.substring(0, 8)}${suffix}.img")
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            val uri = contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), contentValues)
            val outputStream = contentResolver.openOutputStream(uri!!)
            if (outputStream != null) {
                val inputStream = SuFileInputStream.open(boot)
                outputStream.write(inputStream.readBytes())
                inputStream.close()
                outputStream.close()
                contentResolver.notifyChange(uri, null, ContentResolver.NOTIFY_INSERT)
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(intent)
                refreshDownloadStatus(context)
            } else {
                log(context, "Failed to download image")
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun restoreBackup(context: Context, uri: Uri) {
        launch {
            val file = File(context.filesDir, "boot.img")
            val inputStream = context.contentResolver.openInputStream(uri)
            file.writeBytes(inputStream!!.readBytes())
            inputStream.close()
            if (file.exists()) {
                if (Shell.su("/data/adb/magisk/magiskboot sha1 boot.img").exec().out[0] == sha1) {
                    // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L86
                    // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/uninstaller.sh#L82
                    Shell.su("/data/adb/magisk/magiskboot unpack boot.img").exec()

                    // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/boot_patch.sh#L110-L112
                    val ramdisk = File(context.filesDir, "ramdisk.cpio")
                    val kernel = File(context.filesDir, "kernel")
                    if (ramdisk.exists()) {
                        val status = Shell.su("/data/adb/magisk/magiskboot cpio ramdisk.cpio test").exec().code
                        if (status == 0) {
                            // https://github.com/topjohnwu/Magisk/blob/d232cba02ded4dff4c1ccdc9e6180622926f49c8/scripts/util_functions.sh#L594-L606
                            Shell.su("mkdir /data/magisk_backup_$sha1").exec()
                            Shell.su("cp $file /data/magisk_backup_$sha1/boot.img").exec()
                            Shell.su("gzip -9f /data/magisk_backup_$sha1/boot.img").exec()
                            refreshBackupStatus()
                            if (backupStatus == BackupStatus.Found) {
                                log(context, "Backup restored")
                            } else {
                                log(context, "Failed to restore backup")
                            }
                        } else {
                            log(context, "Invalid boot.img: already patched")
                        }
                    } else {
                        log(context, "Invalid boot.img: ramdisk missing")
                    }
                    if (ramdisk.exists()) {
                        ramdisk.delete()
                    }
                    if (kernel.exists()) {
                        kernel.delete()
                    }
                } else {
                    log(context, "Invalid boot.img: wrong sha1")
                }
            } else {
                log(context, "No boot.img provided")
            }
            file.delete()
        }
    }
    
    private fun log(context: Context, message: String, shouldThrow: Boolean = false) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, message)
        if (shouldThrow) {
            throw Exception(message)
        }
    }
}
