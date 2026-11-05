package io.github.ambroal.climer.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.backup.BackupRepository
import io.github.ambroal.climer.data.backup.ImportResult
import io.github.ambroal.climer.ui.components.ConfirmDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupMessage { EXPORTED, EXPORT_FAILED, IMPORTED, IMPORT_INVALID }

@HiltViewModel
class BackupViewModel @Inject constructor(private val repo: BackupRepository, @param:ApplicationContext private val context: Context) :
    ViewModel() {
    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { repo.export(it) } ?: error("stream")
            }.fold({ BackupMessage.EXPORTED }, { BackupMessage.EXPORT_FAILED })
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching { context.contentResolver.openInputStream(uri)?.use { repo.import(it) } }.getOrNull()
            _message.value = if (result is ImportResult.Success) BackupMessage.IMPORTED else BackupMessage.IMPORT_INVALID
        }
    }
}

@Composable
fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri?.toString() }
    Column(Modifier.padding(bottom = 32.dp)) {
        SectionTitle(R.string.backup)
        Text(
            stringResource(R.string.backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = {
                exporter.launch("timer-arrampicata-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
            }) { Text(stringResource(R.string.export_data)) }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text(stringResource(R.string.import_data))
            }
        }
        message?.let {
            val text = when (it) {
                BackupMessage.EXPORTED -> R.string.export_done
                BackupMessage.EXPORT_FAILED -> R.string.export_failed
                BackupMessage.IMPORTED -> R.string.import_done
                BackupMessage.IMPORT_INVALID -> R.string.import_invalid
            }
            val isError = it == BackupMessage.EXPORT_FAILED || it == BackupMessage.IMPORT_INVALID
            Text(
                stringResource(text),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            stringResource(R.string.import_confirm_title),
            stringResource(R.string.import_confirm_text),
            stringResource(R.string.import_data),
            { viewModel.import(uri.toUri()) },
            { pendingImport = null }
        )
    }
}
