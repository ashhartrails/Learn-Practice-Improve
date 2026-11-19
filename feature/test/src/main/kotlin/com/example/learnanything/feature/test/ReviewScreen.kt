package com.example.learnanything.feature.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.ui.components.EmptyState
import com.example.learnanything.core.ui.question.Feedback
import com.example.learnanything.core.ui.question.QuestionInput
import com.example.learnanything.core.ui.question.isFilled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val current = state.current
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            state.items.isEmpty() -> EmptyState("Nothing to review", "Reviews appear here when it's time to refresh a topic.", Modifier.padding(padding))
            state.done || current == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("All caught up", style = MaterialTheme.typography.headlineSmall)
                Text("${state.correctCount} of ${state.items.size} correct", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack) { Text("Done") }
            }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "${state.index + 1} of ${state.items.size} · ${current.topicTitle}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuestionInput(current.item.question, state.answer, viewModel::onAnswer, enabled = state.result == null)
                    state.result?.let { Feedback(it) }
                }
                if (state.result == null) {
                    Button(onClick = viewModel::check, enabled = state.answer.isFilled(), modifier = Modifier.fillMaxWidth()) { Text("Check") }
                } else {
                    Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                }
            }
        }
    }
}
