package com.example.learnanything.feature.practical

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.data.usecase.PracticalOutcome
import com.example.learnanything.core.data.usecase.Trust
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.PracticalRules
import com.example.learnanything.core.ui.components.ErrorCard
import com.example.learnanything.core.ui.question.TrustLabel
import com.example.learnanything.core.ui.text.RichText
import kotlin.math.roundToInt

/** Chapter practical: one task, one primary action (run or check), results below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticalScreen(chapterId: String, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val viewModel = hiltViewModel<PracticalViewModel, PracticalViewModel.Factory>(
        key = "practical-$chapterId",
        creationCallback = { it.create(chapterId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var kindSheet by remember { mutableStateOf(false) }
    val working = state as? PracticalUiState.Working

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(working?.place?.chapter?.title?.let { "$it practical" } ?: "Practical", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (working != null && !working.checking) {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("New task") }, onClick = { menuOpen = false; viewModel.load(newTask = true, kind = null) })
                            DropdownMenuItem(text = { Text("Change type") }, onClick = { menuOpen = false; kindSheet = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is PracticalUiState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Preparing your practical", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(s.progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Code tasks are tested before you see them: the AI's own solution must pass its tests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is PracticalUiState.Failed -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorCard(
                    message = s.message.text,
                    actionLabel = if (s.message.needsSettings) "Open Settings" else "Try again",
                    onAction = if (s.message.needsSettings) onOpenSettings else { { viewModel.load(newTask = true, kind = null) } },
                )
                TextButton(onClick = { kindSheet = true }) { Text("Pick another type") }
            }
            is PracticalUiState.Working -> Workspace(s, padding, viewModel, onBack, onOpenSettings)
        }
    }

    if (kindSheet) {
        ModalBottomSheet(onDismissRequest = { kindSheet = false }) {
            Text("Practical type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Changing the type writes a new task.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            PracticalKind.entries.forEach { kind ->
                ListItem(
                    headlineContent = { Text(kind.label) },
                    supportingContent = { Text(kindDescription(kind)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    trailingContent = {
                        TextButton(onClick = { kindSheet = false; viewModel.load(newTask = true, kind = kind) }) { Text("Use") }
                    },
                )
            }
        }
    }
}

private fun kindDescription(kind: PracticalKind) = when (kind) {
    PracticalKind.PYTHON -> "Write Python. Hidden tests check it. Exact."
    PracticalKind.JAVASCRIPT -> "Write JavaScript. Hidden tests check it. Exact."
    PracticalKind.SQL -> "Query a sample database. Results are compared. Exact."
    PracticalKind.NUMERIC -> "Work out a number. Checked with a tolerance. Exact."
    PracticalKind.WRITTEN -> "Write an answer. Graded against a rubric by AI."
}

@Composable
private fun Workspace(
    s: PracticalUiState.Working,
    padding: PaddingValues,
    viewModel: PracticalViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = s.stored.practical
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(p.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        item { RichText(p.task) }
        if (p.kind == PracticalKind.SQL) {
            item {
                Text("Sample tables", style = MaterialTheme.typography.titleSmall)
                CodeBlock(p.sqlSetup)
            }
        }
        item { AnswerField(p, s.answer, enabled = !s.checking, onChange = viewModel::onAnswer) }
        item {
            Button(onClick = viewModel::submit, enabled = !s.checking && s.answer.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (p.kind) {
                        PracticalKind.WRITTEN -> "Submit for grading"
                        PracticalKind.NUMERIC -> "Check answer"
                        PracticalKind.SQL -> "Run query"
                        else -> "Run tests"
                    },
                )
            }
        }
        if (s.checking) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(s.checkingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        s.error?.let { e ->
            item {
                ErrorCard(e.text, actionLabel = if (e.needsSettings) "Open Settings" else null, onAction = onOpenSettings)
            }
        }
        s.outcome?.let { o -> resultItems(o, p, onBack) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(o: PracticalOutcome.Graded, p: PracticalResponse, onBack: () -> Unit) {
    item {
        Card(
            Modifier.fillMaxWidth().testTag("practical-result"),
            colors = CardDefaults.cardColors(
                containerColor = if (o.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (o.passed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (o.passed) "Passed. Chapter complete!" else "Not yet",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(summary(o, p), style = MaterialTheme.typography.bodyMedium)
                TrustLabel(aiGraded = o.trust == Trust.AI_GRADED)
                Text(
                    if (o.trust == Trust.AI_GRADED) "An AI graded this against the rubric, quoting your words as evidence."
                    else "Checked by code, not by the AI.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (o.passed) Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to course") }
            }
        }
    }
    o.report?.let { r ->
        r.error?.let { item { ErrorCard(it) } }
        items(r.tests) { t ->
            Column {
                Text((if (t.passed) "✓ " else "✗ ") + t.name, style = MaterialTheme.typography.bodyLarge)
                if (t.message.isNotBlank()) {
                    Text(t.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (r.output.isNotBlank()) {
            item {
                Text("Output", style = MaterialTheme.typography.titleSmall)
                CodeBlock(r.output)
            }
        }
    }
    o.grade?.let { g ->
        items(g.criteria.sortedBy { it.index }) { c ->
            val criterion = p.rubric.getOrNull(c.index)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (c.met) "✓ " else "✗ ") + (criterion?.criterion ?: "Criterion ${c.index + 1}") +
                        (criterion?.let { " (${it.points} pt)" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (c.met && c.evidence.isNotBlank()) {
                    Text("“${c.evidence}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(c.feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Text(g.summary, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun summary(o: PracticalOutcome.Graded, p: PracticalResponse): String = when {
    o.report != null -> {
        val r = o.report!!
        if (r.error != null) "Your code didn't run." else "${r.tests.count { it.passed }} of ${r.tests.size} checks passed." +
            if (!o.passed) " Every check must pass." else ""
    }
    o.rubricScore != null -> {
        val r = o.rubricScore!!
        "${r.earned} of ${r.total} points (${(r.score * 100).roundToInt()}%). You need ${(PracticalRules.WRITTEN_PASS_MARK * 100).roundToInt()}% to pass."
    }
    o.passed -> "Correct: ${o.expected}."
    else -> "That's not within the allowed range. Check your working and try again."
}

@Composable
private fun AnswerField(p: PracticalResponse, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val code = p.kind.runsCode
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = {
            Text(
                when (p.kind) {
                    PracticalKind.WRITTEN -> "Your answer"
                    PracticalKind.NUMERIC -> "Your answer" + (p.unit?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                    PracticalKind.SQL -> "Your query"
                    else -> "Your code"
                },
            )
        },
        textStyle = if (code) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        minLines = when (p.kind) {
            PracticalKind.NUMERIC -> 1
            PracticalKind.SQL -> 4
            else -> 8
        },
        singleLine = p.kind == PracticalKind.NUMERIC,
        keyboardOptions = KeyboardOptions(
            capitalization = if (code) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !code,
            keyboardType = when {
                p.kind == PracticalKind.NUMERIC -> KeyboardType.Decimal
                code -> KeyboardType.Ascii
                else -> KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth().testTag("practical-answer"),
    )
}

@Composable
private fun CodeBlock(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
