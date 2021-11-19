package com.github.capntrips.bootimagetool

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.capntrips.bootimagetool.ui.theme.BootImageToolTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainContent(viewModel: MainViewModelInterface) {
    val uiState by viewModel.uiState.collectAsState()
    val slotA by uiState.slotA.collectAsState()
    val slotB by uiState.slotB.collectAsState()
    Column {
        DataCard (title = stringResource(R.string.device)) {
            DataRow(
                label = stringResource(R.string.model),
                value = "${Build.MODEL} (${Build.DEVICE})"
            )
            DataRow(
                label = stringResource(R.string.build_number),
                value = Build.ID
            )
            DataRow(
                label = stringResource(R.string.slot_suffix),
                value = uiState.slotSuffix
            )
        }
        Spacer(Modifier.height(16.dp))
        SlotCard(
            title = "boot_a",
            slotStateFlow = uiState.slotA,
            isActive = uiState.slotSuffix == "_a",
            isFallback = slotB.patchStatus != PatchStatus.Stock || slotB.sha1 != slotA.sha1
        )
        Spacer(Modifier.height(16.dp))
        SlotCard(
            title = "boot_b",
            slotStateFlow = uiState.slotB,
            isActive = uiState.slotSuffix == "_b",
            isFallback = slotA.patchStatus != PatchStatus.Stock || slotA.sha1 != slotB.sha1
        )
    }
}

@Composable
fun SlotCard(
    title: String,
    slotStateFlow: StateFlow<SlotStateInterface>,
    isActive: Boolean,
    isFallback: Boolean
) {
    // TODO: hoist state?
    val slot by slotStateFlow.collectAsState()
    val isRefreshing by slot.isRefreshing.collectAsState()
    DataCard (
        title = title,
        button = {
            if (!isRefreshing) {
                if (isActive) {
                    if (slot.patchStatus == PatchStatus.Patched && slot.backupStatus != BackupStatus.Found) {
                        RestoreButton(slot)
                    } else {
                        if (isFallback && slot.downloadStatus != DownloadStatus.Found) {
                            DownloadButton(slot)
                        }
                    }
                } else {
                    if (slot.patchStatus == PatchStatus.Stock && slot.downloadStatus != DownloadStatus.Found) {
                        DownloadButton(slot)
                    }
                }
            }
        }
    ) {
        IsPatchedDataRow(
            label = stringResource(R.string.status),
            isActive = isActive,
            isPatched = slot.patchStatus == PatchStatus.Patched
        )
        DataRow(
            label = "SHA1",
            value = slot.sha1.substring(0, 8),
            valueStyle = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Thin
            )
        )
        if (isActive && slot.patchStatus == PatchStatus.Patched) {
            BackupStatusDataRow(
                label = stringResource(R.string.backup),
                backupStatus = slot.backupStatus
            )
        }
    }
}

@Composable
fun DownloadButton(slot: SlotStateInterface) {
    val context = LocalContext.current
    TextButton(
        modifier = Modifier.padding(0.dp),
        shape = RoundedCornerShape(4.0.dp),
        contentPadding = PaddingValues(
            horizontal = ButtonDefaults.ContentPadding.calculateLeftPadding(LayoutDirection.Ltr) - (6.667).dp,
            vertical = ButtonDefaults.ContentPadding.calculateTopPadding()
        ),
        onClick = { slot.downloadImage(context) }
    ) {
        Text(stringResource(R.string.download))
    }
}

@Composable
fun RestoreButton(slot: SlotStateInterface) {
    val result = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        result.value = it
    }
    Button(
        modifier = Modifier.padding(0.dp),
        shape = RoundedCornerShape(4.0.dp),
        onClick = { launcher.launch("*/*") }
    ) {
        Text(stringResource(R.string.restore))
    }
    result.value?.let {uri ->
        val context = LocalContext.current
        slot.restoreBackup(context, uri)
    }
}

@Composable
fun DataCard(
    title: String,
    button: @Composable (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(0.dp, 9.dp, 8.dp, 9.dp),
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge
            )
            if (button != null) {
                button()
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// TODO: Remove when card is supported in material3: https://m3.material.io/components/cards/implementation/android
@Composable
fun Card(
    shape: Shape = RoundedCornerShape(4.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 2.dp,
    shadowElevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp, (13.788).dp, 18.dp, 18.dp),
            content = content
        )
    }
}

@Composable
fun DataRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    valueStyle: TextStyle = MaterialTheme.typography.titleSmall
) {
    Row {
        Text(
            modifier = Modifier.alignByBaseline(),
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer(Modifier.alignByBaseline()) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = value,
                color = valueColor,
                style = valueStyle
            )
        }
    }
}

@Composable
fun HasStatusDataRow(
    label: String,
    value: String,
    hasStatus: Boolean
) {
    DataRow(
        label = label,
        value = value,
        valueColor = if (hasStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

@Composable
fun IsPatchedDataRow(
    label: String,
    isActive: Boolean,
    isPatched: Boolean
) {
    HasStatusDataRow(
        label = label,
        value = stringResource(if (isPatched) R.string.patched else R.string.stock),
        hasStatus = if (isActive) isPatched else !isPatched
    )
}

@Composable
fun BackupStatusDataRow(
    label: String,
    backupStatus: BackupStatus
) {
    HasStatusDataRow(
        label = label,
        value = stringResource(
            when (backupStatus) {
                BackupStatus.Found -> R.string.found
                BackupStatus.Missing -> R.string.missing
                else -> R.string.invalid
            }
        ),
        hasStatus = backupStatus == BackupStatus.Found
    )
}

@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MainContentPreviewDark() {
    MainContentPreviewLight()
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun MainContentPreviewLight() {
    BootImageToolTheme {
        Scaffold {
            val viewModel: MainViewModelPreview = viewModel()
            MainContent(viewModel)
        }
    }
}
