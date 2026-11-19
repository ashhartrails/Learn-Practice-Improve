package com.example.learnanything.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import com.example.learnanything.core.model.clarify.ClarifyQuestion
import com.example.learnanything.core.ui.components.ErrorCard

/** New subject: goal → 2 or 3 clarifying questions → outline (opens in the editor). */
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
            when (state.step) {
                CreateStep.GOAL -> GoalStep(state, viewModel)
                CreateStep.CLARIFY -> ClarifyStep(state, viewModel)
                CreateStep.ASKING, CreateStep.BUILDING -> Working(state.progress.orEmpty(), viewModel::cancel)
            }
            state.error?.let { error ->
                ErrorCard(
                    message = error.text,
                    actionLabel = when {
                        error.needsSettings -> "Open Settings"
                        state.canSkipQuestions -> "Build without questions"
                        else -> null
                    },
                    onAction = if (error.needsSettings) onOpenSettings else viewModel::skipQuestions,
                )
            }
        }
    }
}

@Composable
private fun GoalStep(state: CreateUiState, viewModel: CreateSubjectViewModel) {
    OutlinedTextField(
        value = state.goal,
        onValueChange = viewModel::onGoalChange,
        label = { Text("What do you want to learn?") },
        placeholder = { Text("e.g. SQL joins, basics of thermodynamics") },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your level", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Level.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = state.level == level,
                    onClick = { viewModel.onLevelChange(level) },
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
        )
    }
    Button(onClick = viewModel::askQuestions, enabled = state.canContinue, modifier = Modifier.fillMaxWidth()) {
        Text("Next")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarifyStep(state: CreateUiState, viewModel: CreateSubjectViewModel) {
    Text("A few quick questions", style = MaterialTheme.typography.titleLarge)
    Text(
        "Your answers shape the course. Skip any you like.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.questions.forEach { q -> ClarifyField(q, state.answers[q.id].orEmpty()) { viewModel.onAnswer(q.id, it) } }
    Button(onClick = viewModel::buildCourse, modifier = Modifier.fillMaxWidth()) { Text("Build my course") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarifyField(question: ClarifyQuestion, answer: String, onAnswer: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.question, style = MaterialTheme.typography.titleSmall)
        if (question.kind == "choice" && question.options.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    FilterChip(selected = answer == option, onClick = { onAnswer(option) }, label = { Text(option) })
                }
            }
        } else {
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswer,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Working(progress: String, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Row {
            Text(progress, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
