package com.example.learnanything.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.ui.components.EmptyState

/** Home: the one primary action is "New subject". The Continue card arrives in M1. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewSubject: () -> Unit,
    onOpenSubject: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Learn") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSubject,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New subject") },
            )
        },
    ) { padding ->
        SubjectList(
            state = state,
            contentPadding = padding,
            emptyTitle = "What do you want to learn?",
            emptyBody = "Tap New subject, type a goal like \"SQL joins\", and the app builds a course for you.",
            onOpenSubject = onOpenSubject,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSubject: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        SubjectList(
            state = state,
            contentPadding = padding,
            emptyTitle = "Your library is empty",
            emptyBody = "Courses you create appear here.",
            onOpenSubject = onOpenSubject,
        )
    }
}

@Composable
private fun SubjectList(
    state: SubjectsUiState,
    contentPadding: PaddingValues,
    emptyTitle: String,
    emptyBody: String,
    onOpenSubject: (String) -> Unit,
) {
    when (state) {
        SubjectsUiState.Loading -> Box(Modifier.fillMaxSize().padding(contentPadding), Alignment.Center) {
            CircularProgressIndicator()
        }
        is SubjectsUiState.Ready -> if (state.subjects.isEmpty()) {
            EmptyState(emptyTitle, emptyBody, Modifier.padding(contentPadding))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.subjects, key = { it.id }) { subject ->
                    SubjectCard(subject, onClick = { onOpenSubject(subject.id) })
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
                "${subject.level.label} · ${subject.weeklyHours} h/week",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
