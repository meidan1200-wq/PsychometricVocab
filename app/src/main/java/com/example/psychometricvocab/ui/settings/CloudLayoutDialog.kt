package com.example.psychometricvocab.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.data.sync.SyncCoordinator
import com.example.psychometricvocab.data.sync.VocabMigration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The developer-only screen for moving the cloud from the flat layout to v2 (see [VocabMigration]).
 * Opened by a long press on the "Cloud backup" card while signed in; the owner never sees it by
 * accident. The dry run writes nothing; each write needs its confirmation typed in full.
 */
class LayoutMigrationViewModel(app: Application) : AndroidViewModel(app) {
    private val tool = SyncCoordinator.get(app).migration()

    private val _text = MutableStateFlow("Start with the dry run: it reads the cloud and writes nothing.")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun dryRun() = run { tool.dryRun().text }

    fun writeBesideV1(confirmation: String) = run { tool.applyBesideV1(confirmation).text }

    fun setDual(confirmation: String) = run { tool.setDual(confirmation).text }

    private fun run(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _text.value = try {
                block()
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            _busy.value = false
        }
    }
}

@Composable
fun CloudLayoutDialog(onDismiss: () -> Unit, vm: LayoutMigrationViewModel = viewModel()) {
    val text by vm.text.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud layout (developer)") },
        text = {
            Column {
                Text(
                    text = if (busy) "Working..." else text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    singleLine = true,
                    label = { Text("Confirmation") },
                    supportingText = { Text("Write: ${VocabMigration.CONFIRM_WRITE}. Switch: ${VocabMigration.CONFIRM_DUAL}.") }
                )
                TextButton(enabled = !busy, onClick = vm::dryRun) { Text("1. Dry run (writes nothing)") }
                TextButton(enabled = !busy && confirmation.isNotEmpty(), onClick = { vm.writeBesideV1(confirmation) }) {
                    Text("2. Write v2 beside v1")
                }
                TextButton(enabled = !busy && confirmation.isNotEmpty(), onClick = { vm.setDual(confirmation) }) {
                    Text("3. Set layout to dual")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
