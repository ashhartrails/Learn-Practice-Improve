package com.example.learnanything.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.NextStep
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.core.ui.components.EmptyState

/**
 * Home. The primary action is Continue when there's a course in progress; otherwise
 * it's New subject. (New subject moves to the top bar once you have a course.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewSubject: () -> Unit,
    onOpenSubject: (Subject) -> Unit,
    onOpenTopic: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenReview: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready = state as? SubjectsUiState.Ready
    val hasCourses = ready?.subjects?.isNotEmpty() == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learn") },
                actions = {
                    if (hasCourses) IconButton(onClick = onNewSubject) { Icon(Icons.Default.Add, contentDescription = "New subject") }
                },
            )
        },
        floatingActionButton = {
            if (ready != null && !hasCourses) {
                ExtendedFloatingActionButton(
                    onClick = onNewSubject,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New subject") },
                )
            }
        },
    ) { padding ->
        when {
            ready == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            !hasCourses -> EmptyState(
                "What do you want to learn?",
                "Tap New subject, type a goal like \"SQL joins\", and the app builds a course for you.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ready.continueCard?.let { card ->
                    item { ContinueCardView(card, onOpenTopic, onOpenQuiz) }
                }
                if (ready.reviewsDue > 0) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Reviews due", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${ready.reviewsDue} topic${if (ready.reviewsDue == 1) "" else "s"} to refresh, about a minute each",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = onOpenReview) { Text("Review") }
                            }
                        }
                    }
                }
                item { Text("Your courses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(ready.subjects, key = { it.id }) { SubjectCard(it, onClick = { onOpenSubject(it) }) }
            }
        }
    }
}

@Composable
private fun ContinueCardView(card: ContinueCard, onOpenTopic: (String) -> Unit, onOpenQuiz: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
            Text(card.subject.title, style = MaterialTheme.typography.titleLarge)
            val (line, action) = when (val next = card.next) {
                is NextStep.OpenTopic -> "Next: ${next.title}" to { onOpenTopic(next.topicId) }
                is NextStep.TakeQuiz -> "Next: ${next.chapterTitle} quiz" to { onOpenQuiz(next.chapterId) }
                NextStep.CourseComplete -> "" to {}
            }
            Text(line, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenSubject: (Subject) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        when (val s = state) {
            SubjectsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is SubjectsUiState.Ready -> if (s.subjects.isEmpty()) {
                EmptyState("Your library is empty", "Courses you create appear here.", Modifier.padding(padding))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.subjects, key = { it.id }) { SubjectCard(it, onClick = { onOpenSubject(it) }) }
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: Subject, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(subject.title, style = MaterialTheme.typography.titleMedium)
            Text(
                subject.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (subject.status == SubjectStatus.DRAFT) "Draft · tap to review and start"
                else "${subject.level.label} · ${subject.weeklyHours} h/week",
                style = MaterialTheme.typography.labelMedium,
                color = if (subject.status == SubjectStatus.DRAFT) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
