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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.learnanything.core.model.Topic
import com.example.learnanything.core.ui.components.EmptyState

/** Subject map: chapters and topics. Topic details open in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(subjectId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<SubjectViewModel, SubjectViewModel.Factory>(
        key = subjectId,
        creationCallback = { factory -> factory.create(subjectId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openTopic by remember { mutableStateOf<Topic?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? SubjectUiState.Ready)?.outline?.subject?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            SubjectUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            SubjectUiState.NotFound -> EmptyState("Course not found", "It may have been deleted.", Modifier.padding(padding))
            is SubjectUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        s.outline.subject.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(s.outline.chapters, key = { it.id }) { chapter ->
                    ChapterCard(chapter, onTopicClick = { openTopic = it })
                }
            }
        }
    }

    openTopic?.let { topic ->
        ModalBottomSheet(onDismissRequest = { openTopic = null }) {
            TopicDetails(topic)
        }
    }
}

@Composable
private fun ChapterCard(chapter: Chapter, onTopicClick: (Topic) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Chapter ${chapter.order + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    chapter.goal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            chapter.topics.forEachIndexed { i, topic ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onTopicClick(topic) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(topic.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${topic.estimatedMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicDetails(topic: Topic) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(topic.title, style = MaterialTheme.typography.titleLarge)
        Text("About ${topic.estimatedMinutes} minutes", style = MaterialTheme.typography.labelLarge)
        Text("You will be able to", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        topic.objectives.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (topic.prerequisites.isNotEmpty()) {
            Text("Builds on", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            topic.prerequisites.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        Text(
            "The lesson is written the first time you open this topic (next milestone).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
