package com.example.learnanything.feature.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.quiz.QuizRules
import com.example.learnanything.core.ui.components.ErrorCard
import com.example.learnanything.core.ui.question.Feedback
import com.example.learnanything.core.ui.question.QuestionInput
import com.example.learnanything.core.ui.question.TrustLabel
import com.example.learnanything.core.ui.text.RichText
import kotlin.math.roundToInt

/** Chapter quiz: one question per page, graded by code on submit, pass mark 80%. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(chapterId: String, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val viewModel = hiltViewModel<QuizViewModel, QuizViewModel.Factory>(
        key = "quiz-$chapterId",
        creationCallback = { it.create(chapterId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = when (val s = state) {
        is QuizUiState.Taking -> s.place.chapter.title
        is QuizUiState.Done -> s.place.chapter.title
        else -> "Chapter quiz"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (val s = state) {
            is QuizUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Preparing your quiz", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(s.progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is QuizUiState.Failed -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorCard(
                    message = s.message.text,
                    actionLabel = if (s.message.needsSettings) "Open Settings" else "Try again",
                    onAction = if (s.message.needsSettings) onOpenSettings else { { viewModel.load(newQuestions = false) } },
                )
            }
            is QuizUiState.Taking -> Taking(s, padding, viewModel)
            is QuizUiState.Done -> Done(s, padding, onBack, onRetake = { viewModel.load(newQuestions = true) })
        }
    }
}

@Composable
private fun Taking(s: QuizUiState.Taking, padding: PaddingValues, viewModel: QuizViewModel) {
    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Question ${s.index + 1} of ${s.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (s.index + 1).toFloat() / s.items.size }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            QuestionInput(s.current.question, s.answers[s.current.id], viewModel::onAnswer, enabled = !s.submitting)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.index > 0) OutlinedButton(onClick = viewModel::previous, enabled = !s.submitting) { Text("Back") }
            if (s.isLast) {
                Button(onClick = viewModel::submit, enabled = !s.submitting, modifier = Modifier.weight(1f)) {
                    val unanswered = s.items.count { it.id !in s.answers }
                    Text(if (unanswered > 0) "Submit ($unanswered unanswered)" else "Submit")
                }
            } else {
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) { Text("Next") }
            }
        }
    }
}

@Composable
private fun Done(s: QuizUiState.Done, padding: PaddingValues, onBack: () -> Unit, onRetake: () -> Unit) {
    val percent = (s.outcome.score * 100).roundToInt()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", style = MaterialTheme.typography.displayMedium)
                    Text(
                        if (s.outcome.passed) "Passed. Chapter complete!" else "Not yet. You need ${(QuizRules.PASS_MARK * 100).roundToInt()}% to pass.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TrustLabel()
                    Text("Every answer was checked by code, not by the AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.outcome.passed) {
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to course") }
                    } else {
                        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("Retake with new questions") }
                        TextButton(onClick = onBack) { Text("Review the topics first") }
                    }
                }
            }
        }
        item { Text("Your answers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        itemsIndexed(s.outcome.results) { i, (item, result) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.labelLarge)
                RichText(item.question.prompt, style = MaterialTheme.typography.bodyLarge)
                Feedback(result)
            }
        }
    }
}
