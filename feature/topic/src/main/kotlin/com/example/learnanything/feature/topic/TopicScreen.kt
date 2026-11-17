package com.example.learnanything.feature.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.lesson.LessonCard
import com.example.learnanything.core.model.rescue.RescueRung
import com.example.learnanything.core.model.lesson.WorkedExample
import com.example.learnanything.core.ui.components.ErrorCard
import com.example.learnanything.core.ui.question.QuestionCard
import com.example.learnanything.core.ui.text.RichText

/** A lesson: why it matters, swipeable cards, worked example, try it, then check questions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topicId: String,
    onFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    onRescue: (startRung: String?) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<TopicViewModel, TopicViewModel.Factory>(
        key = "topic-$topicId",
        creationCallback = { it.create(topicId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRewrite by remember { mutableStateOf(false) }

    LaunchedEffect((state as? TopicUiState.Ready)?.finished) {
        if ((state as? TopicUiState.Ready)?.finished == true) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? TopicUiState.Ready)?.place?.topic?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state is TopicUiState.Ready) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rewrite lesson") }, onClick = { menuOpen = false; confirmRewrite = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            (state as? TopicUiState.Ready)?.let { ready ->
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!ready.allChecksAnswered) {
                            Text(
                                "Answer the check questions to finish.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = viewModel::finish, enabled = ready.allChecksAnswered, modifier = Modifier.fillMaxWidth()) {
                            Text("Finish topic")
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            is TopicUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Preparing your lesson", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(s.progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Lessons are written the first time you open a topic, then saved on your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TopicUiState.Failed -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorCard(
                    message = s.message.text,
                    actionLabel = if (s.message.needsSettings) "Open Settings" else "Try again",
                    onAction = if (s.message.needsSettings) onOpenSettings else viewModel::load,
                )
            }
            is TopicUiState.Ready -> Lesson(s, padding, viewModel, onRescue)
        }
    }

    if (confirmRewrite) {
        AlertDialog(
            onDismissRequest = { confirmRewrite = false },
            title = { Text("Rewrite this lesson?") },
            text = { Text("The AI writes a new, fuller version. This uses one AI request, and your answers on this page are cleared.") },
            confirmButton = { TextButton(onClick = { confirmRewrite = false; viewModel.rewrite() }) { Text("Rewrite") } },
            dismissButton = { TextButton(onClick = { confirmRewrite = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Lesson(s: TopicUiState.Ready, padding: PaddingValues, viewModel: TopicViewModel, onRescue: (String?) -> Unit) {
    val lesson = s.lesson.lesson
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(s.place.chapter.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Why it matters", style = MaterialTheme.typography.labelLarge)
                    RichText(lesson.whyItMatters)
                    AssistChip(
                        onClick = { onRescue(RescueRung.VIDEO.name) },
                        label = { Text("Watch a short video") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    )
                }
            }
        }
        item { Cards(lesson.cards) }
        item {
            OutlinedButton(onClick = { onRescue(null) }, modifier = Modifier.fillMaxWidth()) { Text("I don't get it") }
        }
        item { Example(lesson.workedExample) }
        item {
            val item = s.lesson.tryIt
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuestionCard(
                    question = item.question,
                    answer = s.answers[item.id],
                    result = s.results[item.id],
                    onAnswerChange = { viewModel.onAnswer(item.id, it) },
                    onCheck = { viewModel.check(item) },
                    label = "Try it (practice, doesn't count)",
                )
                if (s.results[item.id]?.correct == false) {
                    TextButton(onClick = viewModel::retryTryIt) { Text("Try again") }
                }
            }
        }
        item {
            Text("Check your understanding", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        s.lesson.checks.forEachIndexed { i, item ->
            item(key = item.id) {
                QuestionCard(
                    question = item.question,
                    answer = s.answers[item.id],
                    result = s.results[item.id],
                    onAnswerChange = { viewModel.onAnswer(item.id, it) },
                    onCheck = { viewModel.check(item) },
                    label = "Question ${i + 1} of ${s.lesson.checks.size}",
                )
            }
        }
        if (s.allChecksAnswered) {
            item {
                Text(
                    "${s.checksCorrect} of ${s.lesson.checks.size} correct.",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun Cards(cards: List<LessonCard>) {
    val pager = rememberPagerState { cards.size }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { page ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cards[page].heading, style = MaterialTheme.typography.titleMedium)
                    RichText(cards[page].body)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(cards.size) { i ->
                val color = if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.padding(4.dp).size(8.dp).background(color, CircleShape))
            }
            Text(
                "  Card ${pager.currentPage + 1} of ${cards.size}, swipe for more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Example(example: WorkedExample) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Worked example", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            RichText(example.problem)
            example.steps.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}.  ", style = MaterialTheme.typography.bodyLarge)
                    RichText(step)
                }
            }
            Text("Answer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            RichText(example.answer)
        }
    }
}
