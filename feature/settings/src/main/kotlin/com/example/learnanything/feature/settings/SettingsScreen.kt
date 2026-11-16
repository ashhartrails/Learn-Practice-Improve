package com.example.learnanything.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.ai.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GeminiKeyCard(state, viewModel)
            if (state.keySaved) {
                ModelCard(state, onOpenPicker = { pickerOpen = true }, onRetry = viewModel::loadModels)
            }
            PrivacyCard()
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(onDismissRequest = { pickerOpen = false }) {
            ModelPicker(
                state = state,
                onSelect = {
                    viewModel.selectModel(it)
                    pickerOpen = false
                },
            )
        }
    }
}

@Composable
private fun GeminiKeyCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
            if (state.keySaved) {
                Text(
                    "Saved and encrypted on this phone (ends in ${state.keyLastFour ?: "????"}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Get a free key at aistudio.google.com → Get API key, then paste it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.keyInput,
                onValueChange = viewModel::onKeyInput,
                label = { Text(if (state.keySaved) "Replace key" else "Paste key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                enabled = !state.checkingKey,
                modifier = Modifier.fillMaxWidth(),
            )
            state.keyMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.keyMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = viewModel::saveKey,
                    enabled = state.keyInput.isNotBlank() && !state.checkingKey,
                ) { Text("Save and test") }
                if (state.checkingKey) CircularProgressIndicator(Modifier.padding(start = 8.dp))
                if (state.keySaved && !state.checkingKey) {
                    TextButton(onClick = viewModel::removeKey) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(state: SettingsUiState, onOpenPicker: () -> Unit, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Model", style = MaterialTheme.typography.titleMedium)
            val label = when {
                state.selectedModelId == null ->
                    "Automatic" + (state.automaticModel?.let { " (${it.displayName})" } ?: "")
                else -> state.selectedModel?.displayName ?: state.selectedModelId
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "The list comes live from Google, so new models appear here automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.modelsError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenPicker, enabled = state.models.isNotEmpty()) { Text("Change") }
                if (state.loadingModels) CircularProgressIndicator()
                else if (state.models.isEmpty()) TextButton(onClick = onRetry) { Text("Load models") }
            }
        }
    }
}

@Composable
private fun ModelPicker(state: SettingsUiState, onSelect: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            ModelRow(
                title = "Automatic",
                subtitle = state.automaticModel?.let { "Currently ${it.displayName}" } ?: "Best available model",
                selected = state.selectedModelId == null,
                onClick = { onSelect(null) },
            )
        }
        items(state.models, key = { it.id }) { model ->
            ModelRow(
                title = model.displayName,
                subtitle = model.subtitle(),
                selected = state.selectedModelId == model.id,
                onClick = { onSelect(model.id) },
            )
        }
    }
}

private fun ModelInfo.subtitle(): String = buildList {
    add(id)
    if (isPreview) add("preview")
}.joinToString(" · ")

@Composable
private fun ModelRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { if (selected) Icon(Icons.Default.Check, contentDescription = "Selected") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PrivacyCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Your API key is encrypted with a key held in this phone's secure hardware. It is never backed up or shared.",
                "Your goals and answers are sent to the AI provider you choose (Gemini today) to build your course.",
                "On Gemini's free tier, Google may use what you send to improve its products. Check Google's terms in AI Studio.",
                "Your courses and progress stay on this phone. Google Drive backup arrives in a later version.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
