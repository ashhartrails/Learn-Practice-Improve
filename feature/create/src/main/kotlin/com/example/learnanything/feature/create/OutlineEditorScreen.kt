package com.example.learnanything.feature.create

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.core.model.Topic

private sealed interface EditTarget {
    data class ChapterTarget(val chapter: Chapter, val index: Int, val count: Int) : EditTarget
    data class TopicTarget(val topic: Topic, val index: Int, val count: Int) : EditTarget
}

private sealed interface Dialog {
    data class RenameChapter(val chapter: Chapter) : Dialog
    data class RenameTopic(val topic: Topic) : Dialog
    data class AddTopic(val chapter: Chapter) : Dialog
}

/**
 * Outline editor. One primary action: Start course (or Done for a course already started).
 * Row actions live in a bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineEditorScreen(subjectId: String, onStarted: (String) -> Unit, onBack: () -> Unit) {
    val viewModel = hiltViewModel<OutlineEditorViewModel, OutlineEditorViewModel.Factory>(
        key = "editor-$subjectId",
        creationCallback = { it.create(subjectId) },
    )
    val outline by viewModel.outline.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<EditTarget?>(null) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    LaunchedEffect(started) { if (started) onStarted(subjectId) }

    val isDraft = outline?.subject?.status == SubjectStatus.DRAFT
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isDraft) "Review your course" else "Edit outline") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { if (isDraft) viewModel.start() else onBack() },
                    enabled = (outline?.topicCount ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(if (isDraft) "Start course" else "Done") }
            }
        },
    ) { padding ->
        val o = outline
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(o.subject.title, style = MaterialTheme.typography.headlineSmall)
                    Text(o.subject.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Tap any chapter or topic to rename, move or remove it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(o.chapters, key = { it.id }) { chapter ->
                val index = o.chapters.indexOf(chapter)
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { sheet = EditTarget.ChapterTarget(chapter, index, o.chapters.size) }
                            .padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Chapter ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Default.MoreVert, contentDescription = "Chapter actions", Modifier.padding(12.dp))
                    }
                    chapter.topics.forEachIndexed { ti, topic ->
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { sheet = EditTarget.TopicTarget(topic, ti, chapter.topics.size) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(topic.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("${topic.estimatedMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { dialog = Dialog.AddTopic(chapter) }, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add topic", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    sheet?.let { target ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                fun act(block: () -> Unit) { block(); sheet = null }
                when (target) {
                    is EditTarget.ChapterTarget -> {
                        Text(target.chapter.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        SheetAction("Rename", Icons.Default.Edit) { act { dialog = Dialog.RenameChapter(target.chapter) } }
                        if (target.index > 0) SheetAction("Move up", Icons.Default.KeyboardArrowUp) { act { viewModel.moveChapter(target.chapter.id, up = true) } }
                        if (target.index < target.count - 1) SheetAction("Move down", Icons.Default.KeyboardArrowDown) { act { viewModel.moveChapter(target.chapter.id, up = false) } }
                        SheetAction("Delete chapter and its topics", Icons.Default.Delete) { act { viewModel.deleteChapter(target.chapter.id) } }
                    }
                    is EditTarget.TopicTarget -> {
                        Text(target.topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        SheetAction("Rename", Icons.Default.Edit) { act { dialog = Dialog.RenameTopic(target.topic) } }
                        if (target.index > 0) SheetAction("Move up", Icons.Default.KeyboardArrowUp) { act { viewModel.moveTopic(target.topic.id, up = true) } }
                        if (target.index < target.count - 1) SheetAction("Move down", Icons.Default.KeyboardArrowDown) { act { viewModel.moveTopic(target.topic.id, up = false) } }
                        SheetAction("Delete topic", Icons.Default.Delete) { act { viewModel.deleteTopic(target.topic.id) } }
                    }
                }
            }
        }
    }

    dialog?.let { d ->
        val (title, initial) = when (d) {
            is Dialog.RenameChapter -> "Rename chapter" to d.chapter.title
            is Dialog.RenameTopic -> "Rename topic" to d.topic.title
            is Dialog.AddTopic -> "Add a topic to “${d.chapter.title}”" to ""
        }
        TextDialog(title, initial, onDismiss = { dialog = null }) { text ->
            when (d) {
                is Dialog.RenameChapter -> viewModel.renameChapter(d.chapter.id, text)
                is Dialog.RenameTopic -> viewModel.renameTopic(d.topic.id, text)
                is Dialog.AddTopic -> viewModel.addTopic(d.chapter.id, text)
            }
            dialog = null
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(120) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
