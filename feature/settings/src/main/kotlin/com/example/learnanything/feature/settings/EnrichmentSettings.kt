package com.example.learnanything.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.EnrichmentRepository
import com.example.learnanything.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnrichmentSettingsState(
    val background: String = "",
    val knownSkills: String = "",
    val profileDirty: Boolean = false,
    val profileMessage: String? = null,
    val youTubeSaved: Boolean = false,
    val youTubeLastFour: String? = null,
    val youTubeInput: String = "",
    val youTubeMessage: String? = null,
)

@HiltViewModel
class EnrichmentSettingsViewModel @Inject constructor(
    private val enrichment: EnrichmentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EnrichmentSettingsState())
    val state: StateFlow<EnrichmentSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = enrichment.observeProfile().first()
            _state.update { it.copy(background = profile.background, knownSkills = profile.knownSkills) }
        }
        viewModelScope.launch {
            settings.youTubeKeyState.collect { key -> _state.update { it.copy(youTubeSaved = key.saved, youTubeLastFour = key.lastFour) } }
        }
    }

    fun onBackground(v: String) = _state.update { it.copy(background = v, profileDirty = true, profileMessage = null) }
    fun onKnownSkills(v: String) = _state.update { it.copy(knownSkills = v, profileDirty = true, profileMessage = null) }

    fun saveProfile() {
        val s = _state.value
        viewModelScope.launch {
            enrichment.saveProfile(s.background, s.knownSkills)
            _state.update { it.copy(profileDirty = false, profileMessage = "Saved.") }
        }
    }

    fun onYouTubeInput(v: String) = _state.update { it.copy(youTubeInput = v.trim(), youTubeMessage = null) }

    fun saveYouTubeKey() {
        val key = _state.value.youTubeInput
        if (key.isBlank()) return
        viewModelScope.launch {
            settings.saveYouTubeKey(key)
            _state.update { it.copy(youTubeInput = "", youTubeMessage = "Saved. It's used the first time you ask for a video.") }
        }
    }

    fun removeYouTubeKey() {
        viewModelScope.launch {
            settings.clearYouTubeKey()
            _state.update { it.copy(youTubeMessage = "Key removed. Videos will open as a YouTube search instead.") }
        }
    }
}

/** "About you" (for analogies) and the optional YouTube key. */
@Composable
fun EnrichmentSettingsCards(viewModel: EnrichmentSettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("About you", style = MaterialTheme.typography.titleMedium)
            Text(
                "Used when you tap \"I don't get it\", so analogies come from your world. Stays on this phone, and is sent to the AI with rescue requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = s.background,
                onValueChange = viewModel::onBackground,
                label = { Text("Your work or background") },
                placeholder = { Text("e.g. Salesforce architect, enjoys cycling") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.knownSkills,
                onValueChange = viewModel::onKnownSkills,
                label = { Text("Things you already know") },
                placeholder = { Text("e.g. Apex, SOQL, basic statistics") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::saveProfile, enabled = s.profileDirty) { Text("Save") }
                s.profileMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YouTube key (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (s.youTubeSaved) "Saved and encrypted on this phone (ends in ${s.youTubeLastFour ?: "????"}). Short videos play inside the app."
                else "Without a key, \"A short video\" opens a YouTube search instead. With one, the app finds a short, embeddable video and plays it here. See docs/SETUP.md for the steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = s.youTubeInput,
                onValueChange = viewModel::onYouTubeInput,
                label = { Text(if (s.youTubeSaved) "Replace key" else "Paste key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            s.youTubeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveYouTubeKey, enabled = s.youTubeInput.isNotBlank()) { Text("Save") }
                if (s.youTubeSaved) TextButton(onClick = viewModel::removeYouTubeKey) { Text("Remove") }
            }
        }
    }
}
