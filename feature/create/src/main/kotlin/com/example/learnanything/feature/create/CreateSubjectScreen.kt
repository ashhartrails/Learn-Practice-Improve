package com.example.learnanything.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.ui.components.ErrorCard

/** New subject. One primary action: Build my course. Clarifying questions arrive in M1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSubjectScreen(
    onCreated: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateSubjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdSubjectId) {
        state.createdSubjectId?.let {
            viewModel.onNavigated()
            onCreated(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New subject") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = state.goal,
                onValueChange = viewModel::onGoalChange,
                label = { Text("What do you want to learn?") },
                placeholder = { Text("e.g. SQL joins, basics of thermodynamics") },
                enabled = !state.generating,
                minLines = 2,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your level", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Level.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = state.level == level,
                            onClick = { viewModel.onLevelChange(level) },
                            enabled = !state.generating,
                            shape = SegmentedButtonDefaults.itemShape(index, Level.entries.size),
                        ) { Text(level.label) }
                    }
                }
            }

            Column {
                Text("Time per week: ${state.weeklyHours} h", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = state.weeklyHours.toFloat(),
                    onValueChange = { viewModel.onHoursChange(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    enabled = !state.generating,
                )
            }

            state.error?.let { error ->
                ErrorCard(
                    message = error.message,
                    actionLabel = if (error.needsSettings) "Open Settings" else null,
                    onAction = onOpenSettings,
                )
            }

            if (state.generating) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row {
                        Text(
                            state.progress.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(top = 12.dp),
                        )
                        TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                    }
                }
            } else {
                Button(
                    onClick = viewModel::generate,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Build my course") }
            }
        }
    }
}
