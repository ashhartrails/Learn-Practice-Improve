package com.example.learnanything.feature.subject

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.NextStep
import com.example.learnanything.core.ui.components.EmptyState
import kotlin.math.roundToInt

/** Subject map: progress, chapters, topics, quizzes and practicals. The one primary action is Continue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    subjectId: String,
    onOpenTopic: (String) -> Unit,
    onOpenQuiz: (String) -> Unit,
    onOpenPractical: (String) -> Unit,
    onEditOutline: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<SubjectViewModel, SubjectViewModel.Factory>(
        key = "subject-$subjectId",
        creationCallback = { it.create(subjectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? SubjectUiState.Ready)?.outline?.subject?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit outline") }, onClick = { menuOpen = false; onEditOutline(subjectId) })
                        DropdownMenuItem(text = { Text("Delete course") }, onClick = { menuOpen = false; confirmDelete = true })
                    }
                },
            )
        },
        floatingActionButton = {
            when (val next = (state as? SubjectUiState.Ready)?.next) {
                is NextStep.OpenTopic -> ExtendedFloatingActionButton(
                    onClick = { onOpenTopic(next.topicId) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Continue: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                is NextStep.TakeQuiz -> ExtendedFloatingActionButton(
                    onClick = { onOpenQuiz(next.chapterId) },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    text = { Text("Take the chapter quiz") },
                )
                is NextStep.DoPractical -> ExtendedFloatingActionButton(
                    onClick = { onOpenPractical(next.chapterId) },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    text = { Text("Do the practical") },
                )
                else -> Unit
            }
        },
    ) { padding ->
        when (val s = state) {
            SubjectUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            SubjectUiState.NotFound -> EmptyState("Course not found", "It may have been deleted.", Modifier.padding(padding))
            is SubjectUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(s) }
                items(s.outline.chapters, key = { it.id }) { chapter ->
                    ChapterCard(
                        chapter = chapter,
                        nextTopicId = (s.next as? NextStep.OpenTopic)?.topicId,
                        onTopicClick = onOpenTopic,
                        onQuizClick = { onOpenQuiz(chapter.id) },
                        onPracticalClick = { onOpenPractical(chapter.id) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this course?") },
            text = { Text("Its lessons and progress are removed from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(s: SubjectUiState.Ready) {
    val total = s.outline.topicCount
    val done = s.outline.completedTopicCount
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.outline.subject.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Text(
            if (s.next is NextStep.CourseComplete) "Course complete. Well done!" else "$done of $total topics done",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ChapterCard(
    chapter: Chapter,
    nextTopicId: String?,
    onTopicClick: (String) -> Unit,
    onQuizClick: () -> Unit,
    onPracticalClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapter ${chapter.order + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (chapter.isFinished) Text("Finished", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                Text(chapter.goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
            chapter.topics.forEach { topic ->
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { onTopicClick(topic.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (topic.isComplete) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    } else if (topic.id == nextTopicId) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Next", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    } else {
                        Box(Modifier.size(20.dp))
                    }
                    Text(topic.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Text("${topic.estimatedMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val gate = chapter.gate
            val quizLabel = when {
                gate?.passed == true -> "Chapter quiz: passed (${(gate.score * 100).roundToInt()}%)"
                gate != null -> "Chapter quiz: best ${(gate.score * 100).roundToInt()}%, 80% to pass"
                chapter.topicsDone -> "Chapter quiz: ready"
                else -> "Chapter quiz: after the topics"
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = chapter.topicsDone, onClick = onQuizClick).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (gate?.passed == true) Icons.Default.CheckCircle else Icons.Default.Star,
                    contentDescription = null,
                    tint = if (chapter.topicsDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    quizLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (chapter.topicsDone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val practical = chapter.practical
            val practicalOpen = chapter.quizPassed
            val practicalLabel = when {
                practical?.passed == true -> "Practical: passed" + if (practical.trust == "AI_GRADED") " (AI-graded)" else ""
                practical != null -> "Practical: not passed yet"
                !chapter.practicalRequired && chapter.quizPassed -> "Practical: optional"
                practicalOpen -> "Practical: ready"
                else -> "Practical: after the quiz"
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = practicalOpen, onClick = onPracticalClick).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (practical?.passed == true) Icons.Default.CheckCircle else Icons.Default.Build,
                    contentDescription = null,
                    tint = if (practicalOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    practicalLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (practicalOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
