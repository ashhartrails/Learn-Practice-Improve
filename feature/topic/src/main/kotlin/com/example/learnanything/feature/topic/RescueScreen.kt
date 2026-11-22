package com.example.learnanything.feature.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.data.usecase.RescueContent
import com.example.learnanything.core.data.usecase.TeachBackOutcome
import com.example.learnanything.core.model.question.toQuestion
import com.example.learnanything.core.model.rescue.RescueRung
import com.example.learnanything.core.model.resources.PhetCatalog
import com.example.learnanything.core.ui.components.ErrorCard
import com.example.learnanything.core.ui.content.ContentHost
import com.example.learnanything.core.ui.content.ContentUrls
import com.example.learnanything.core.ui.content.openInBrowser
import com.example.learnanything.core.ui.question.QuestionCard
import com.example.learnanything.core.ui.question.TrustLabel
import com.example.learnanything.core.ui.text.RichText
import kotlin.math.roundToInt

/**
 * "I don't get it": one rung at a time. After each rung the learner tries a lesson question;
 * a right answer stops the ladder and is remembered as what helped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueScreen(topicId: String, startRung: String?, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val viewModel = hiltViewModel<RescueViewModel, RescueViewModel.Factory>(
        key = "rescue-$topicId-$startRung",
        creationCallback = { it.create(topicId, startRung) },
    )
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Another way") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        bottomBar = {
            if (s.loading == null && !s.outOfRungs) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (s.helped) {
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to the lesson") }
                        } else {
                            val next = s.order.getOrNull(s.index + 1)
                            OutlinedButton(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                                Text(if (next != null) "Show me another way: ${next.label.lowercase()}" else "I've tried everything")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            s.outOfRungs -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("That's every way we have for now.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Try the lesson again, or use ⋮ → Rewrite lesson for a fresh explanation. What you tried here is remembered, so next time starts with what's most likely to help.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onBack) { Text("Back to the lesson") }
            }
            s.loading != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                s.rung?.let { Text(it.label, style = MaterialTheme.typography.titleMedium) }
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(s.loading.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        "Step ${s.index + 1} of ${s.order.size}: ${s.rung?.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                content(s, viewModel, onOpenSettings)
                checkSection(s, viewModel)
            }
        }
    }
}

private fun LazyListScope.content(s: RescueUiState, viewModel: RescueViewModel, onOpenSettings: () -> Unit) {
    when (val c = s.content) {
        is RescueContent.Failed -> item {
            ErrorCard(
                c.message.text,
                actionLabel = if (c.message.needsSettings) "Open Settings" else null,
                onAction = onOpenSettings,
            )
        }
        is RescueContent.Video -> item { VideoCard(c, viewModel) }
        is RescueContent.VideoSearchChip -> item { SearchChipCard(c) }
        is RescueContent.Simulation -> item { SimulationCard(c.sim) }
        is RescueContent.Diagram -> item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Picture it", style = MaterialTheme.typography.titleMedium)
                    Text("No simulation fits this topic. Sketch this on paper:", style = MaterialTheme.typography.bodySmall)
                    RichText(c.description)
                }
            }
        }
        is RescueContent.Written -> written(c, s, viewModel)
        null -> Unit
    }
}

private fun LazyListScope.written(c: RescueContent.Written, s: RescueUiState, viewModel: RescueViewModel) {
    val r = c.content
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                RichText(r.body)
            }
        }
    }
    when (c.rung) {
        RescueRung.BLANKS -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.blanks.indices.forEach { i ->
                    val ok = s.blankResults?.getOrNull(i)
                    OutlinedTextField(
                        value = s.blanks.getOrElse(i) { "" },
                        onValueChange = { viewModel.onBlank(i, it) },
                        label = { Text("[${i + 1}]") },
                        singleLine = true,
                        supportingText = when (ok) {
                            true -> { { Text("Correct") } }
                            false -> { { Text("Not quite. Accepted: ${r.blanks[i].accepted.first()}") } }
                            null -> null
                        },
                        isError = ok == false,
                        modifier = Modifier.fillMaxWidth().testTag("blank-$i"),
                    )
                }
                Button(onClick = viewModel::checkBlanks, enabled = s.blanks.any { it.isNotBlank() }) { Text("Check blanks") }
                if (s.blankResults != null) TrustLabel()
            }
        }
        RescueRung.SOCRATIC -> r.questions.forEachIndexed { i, q ->
            item {
                QuestionCard(
                    question = q.toQuestion(),
                    answer = s.inner["s$i"],
                    result = s.innerResults["s$i"],
                    onAnswerChange = { viewModel.onInnerAnswer("s$i", it) },
                    onCheck = { viewModel.checkInner("s$i", q) },
                    label = "Step ${i + 1}",
                )
            }
        }
        RescueRung.PREREQUISITE -> r.prerequisites.forEachIndexed { i, p ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.concept, style = MaterialTheme.typography.titleSmall)
                    RichText(p.explanation)
                    QuestionCard(
                        question = p.question.toQuestion(),
                        answer = s.inner["p$i"],
                        result = s.innerResults["p$i"],
                        onAnswerChange = { viewModel.onInnerAnswer("p$i", it) },
                        onCheck = { viewModel.checkInner("p$i", p.question) },
                    )
                }
            }
        }
        RescueRung.TEACH_BACK -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.teachBack,
                    onValueChange = viewModel::onTeachBack,
                    label = { Text("Your explanation") },
                    minLines = 5,
                    enabled = !s.teachBackGrading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = viewModel::gradeTeachBack, enabled = s.teachBack.isNotBlank() && !s.teachBackGrading) { Text("Get feedback") }
                if (s.teachBackGrading) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (val o = s.teachBackOutcome) {
                    is TeachBackOutcome.Graded -> Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${o.score.earned} of ${o.score.total} points (${(o.score.score * 100).roundToInt()}%)", style = MaterialTheme.typography.titleSmall)
                            TrustLabel(aiGraded = true)
                            o.grade.criteria.sortedBy { it.index }.forEach { g ->
                                val criterion = r.rubric.getOrNull(g.index)?.criterion ?: "Criterion ${g.index + 1}"
                                Text((if (g.met) "✓ " else "✗ ") + criterion, style = MaterialTheme.typography.bodyMedium)
                                if (g.met && g.evidence.isNotBlank()) Text("“${g.evidence}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(g.feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(o.grade.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is TeachBackOutcome.Failed -> ErrorCard(o.message.text)
                    null -> Unit
                }
            }
        }
        else -> Unit
    }
}

private fun LazyListScope.checkSection(s: RescueUiState, viewModel: RescueViewModel) {
    val item = s.check ?: return
    if (s.content is RescueContent.Failed) return
    item {
        Text("Did that help? Try this", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    }
    item {
        QuestionCard(
            question = item.question,
            answer = s.checkAnswer,
            result = s.checkResult,
            onAnswerChange = viewModel::onCheckAnswer,
            onCheck = viewModel::submitCheck,
            label = "From your lesson",
        )
    }
    if (s.helped) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    "It clicked. Next time you're stuck, we'll start with ${s.rung?.label?.lowercase()}.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun VideoCard(c: RescueContent.Video, viewModel: RescueViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentHost(
            url = ContentUrls.youTubeEmbed(c.resource.sourceRef),
            allowedHosts = setOf(ContentUrls.YOUTUBE_EMBED_HOST),
            headers = ContentUrls.refererFor(context),
        )
        Text(c.resource.title, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Was this useful?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.rateVideo(true) }) { Icon(Icons.Filled.ThumbUp, contentDescription = "Useful") }
            TextButton(onClick = { viewModel.rateVideo(false) }) { Text("Find another") }
        }
    }
}

@Composable
private fun SearchChipCard(c: RescueContent.VideoSearchChip) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Find a short video", style = MaterialTheme.typography.titleMedium)
            Text(
                c.note?.let { "$it Search YouTube instead:" }
                    ?: "Add a YouTube key in Settings to play videos here. For now, search YouTube:",
                style = MaterialTheme.typography.bodySmall,
            )
            AssistChip(
                onClick = { openInBrowser(context, ContentUrls.youTubeSearch(c.query)) },
                label = { Text(c.query) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SimulationCard(sim: PhetCatalog.Sim) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(sim.title, style = MaterialTheme.typography.titleMedium)
        Text("Play with it: change one thing at a time and watch what happens.", style = MaterialTheme.typography.bodySmall)
        ContentHost(url = sim.url, allowedHosts = setOf(PhetCatalog.HOST), aspectRatio = 4f / 3f)
        Text(PhetCatalog.ATTRIBUTION, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { openInBrowser(context, sim.url) }) { Text("Open full screen") }
    }
}
