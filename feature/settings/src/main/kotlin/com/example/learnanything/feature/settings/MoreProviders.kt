package com.example.learnanything.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.data.usecase.CloudProvidersUseCase
import com.example.learnanything.core.data.usecase.KeyCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderUi(
    val id: String,
    val name: String,
    val keySaved: Boolean = false,
    val lastFour: String? = null,
    val input: String = "",
    val checking: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: String? = null,
)

@HiltViewModel
class MoreProvidersViewModel @Inject constructor(private val cloud: CloudProvidersUseCase) : ViewModel() {
    private val _state = MutableStateFlow(cloud.providers.map { ProviderUi(it.id, it.displayName) })
    val state: StateFlow<List<ProviderUi>> = _state.asStateFlow()

    init {
        cloud.providers.forEach { p ->
            viewModelScope.launch {
                combine(cloud.keyState(p.id), cloud.selectedModel(p.id)) { k, m -> k to m }.collect { (key, model) ->
                    val wasSaved = item(p.id)?.keySaved == true
                    edit(p.id) { it.copy(keySaved = key.saved, lastFour = key.lastFour, selectedModel = model) }
                    if (key.saved && !wasSaved && item(p.id)?.models.isNullOrEmpty()) loadModels(p.id)
                }
            }
        }
    }

    private fun item(id: String) = _state.value.firstOrNull { it.id == id }
    private fun edit(id: String, f: (ProviderUi) -> ProviderUi) = _state.update { list -> list.map { if (it.id == id) f(it) else it } }

    fun onInput(id: String, v: String) = edit(id) { it.copy(input = v.trim(), message = null) }

    fun save(id: String) {
        val key = item(id)?.input.orEmpty()
        if (key.isBlank()) return
        edit(id) { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            when (val r = cloud.verifyAndSave(id, key)) {
                is KeyCheckResult.Saved -> edit(id) {
                    it.copy(checking = false, input = "", models = r.models, message = "Key works. ${r.models.size} free models available.", messageIsError = false)
                }
                is KeyCheckResult.SavedUnverified -> edit(id) { it.copy(checking = false, input = "", message = r.reason, messageIsError = true) }
                is KeyCheckResult.Rejected -> edit(id) { it.copy(checking = false, message = r.reason, messageIsError = true) }
            }
        }
    }

    fun loadModels(id: String) {
        viewModelScope.launch {
            try {
                val models = cloud.models(id)
                edit(id) { it.copy(models = models) }
            } catch (e: AiException) {
                edit(id) { it.copy(message = e.message, messageIsError = true) }
            }
        }
    }

    fun select(id: String, model: String?) = viewModelScope.launch { cloud.selectModel(id, model) }

    fun remove(id: String) = viewModelScope.launch {
        cloud.remove(id)
        edit(id) { it.copy(models = emptyList(), message = "Key removed.", messageIsError = false) }
    }
}

private fun signUpHint(id: String) = when (id) {
    "groq" -> "Free key: console.groq.com → API Keys → Create API Key. Very fast open models."
    "openrouter" -> "Free key: openrouter.ai → Keys → Create Key. Only its free (\":free\") models are used."
    else -> ""
}

/** Groq and OpenRouter as free fallbacks after Gemini. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreProvidersCard(viewModel: MoreProvidersViewModel = hiltViewModel()) {
    val providers by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("More free AI (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "When Gemini is busy, out of quota or its answer fails our checks, the app tries these next, in this order. Every answer is checked the same way.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            providers.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (p.keySaved) "Key saved and encrypted (ends in ${p.lastFour ?: "????"})." else signUpHint(p.id),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = p.input,
                        onValueChange = { viewModel.onInput(p.id, it) },
                        label = { Text(if (p.keySaved) "Replace ${p.name} key" else "Paste ${p.name} key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        enabled = !p.checking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    p.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = if (p.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { viewModel.save(p.id) }, enabled = p.input.isNotBlank() && !p.checking) { Text("Save and test") }
                        if (p.checking) CircularProgressIndicator()
                        if (p.keySaved && !p.checking) {
                            OutlinedButton(onClick = { picker = p.id }, enabled = p.models.isNotEmpty()) {
                                Text(p.selectedModel?.let { "Model: $it" } ?: "Model: Automatic", maxLines = 1)
                            }
                            TextButton(onClick = { viewModel.remove(p.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
    picker?.let { id ->
        val p = providers.first { it.id == id }
        ModalBottomSheet(onDismissRequest = { picker = null }) {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Automatic") },
                        supportingContent = { Text(p.models.firstOrNull()?.let { "Currently ${it.id}" } ?: "Largest free model") },
                        trailingContent = { if (p.selectedModel == null) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { viewModel.select(id, null); picker = null },
                    )
                }
                items(p.models, key = { it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.displayName) },
                        supportingContent = { Text(m.id) },
                        trailingContent = { if (p.selectedModel == m.id) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { viewModel.select(id, m.id); picker = null },
                    )
                }
            }
        }
    }
}
