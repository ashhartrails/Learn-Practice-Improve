package com.example.learnanything.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.feature.create.CreateSubjectScreen
import com.example.learnanything.feature.create.OutlineEditorScreen
import com.example.learnanything.feature.home.HomeScreen
import com.example.learnanything.feature.home.LibraryScreen
import com.example.learnanything.feature.progress.ProgressScreen
import com.example.learnanything.feature.settings.SettingsScreen
import com.example.learnanything.feature.subject.SubjectScreen
import com.example.learnanything.feature.practical.PracticalScreen
import com.example.learnanything.feature.test.QuizScreen
import com.example.learnanything.feature.topic.RescueScreen
import com.example.learnanything.feature.test.ReviewScreen
import com.example.learnanything.feature.topic.TopicScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data object LibraryKey : NavKey
@Serializable data object YouKey : NavKey
@Serializable data object CreateKey : NavKey
@Serializable data class SubjectKey(val subjectId: String) : NavKey
@Serializable data class OutlineEditorKey(val subjectId: String) : NavKey
@Serializable data class TopicKey(val topicId: String) : NavKey
@Serializable data class QuizKey(val chapterId: String) : NavKey
@Serializable data class PracticalKey(val chapterId: String) : NavKey
@Serializable data class RescueKey(val topicId: String, val startRung: String? = null) : NavKey
@Serializable data object ReviewKey : NavKey
@Serializable data object SettingsKey : NavKey

private data class Tab(val key: NavKey, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeKey, "Home", Icons.Default.Home),
    Tab(LibraryKey, "Library", Icons.AutoMirrored.Filled.List),
    Tab(YouKey, "You", Icons.Default.Person),
)

@Composable
fun LearnApp() {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()
    val showTabs = TABS.any { it.key == current }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.key == current,
                            onClick = { backStack.switchTab(tab.key) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    val openSubject: (Subject) -> Unit = { subject ->
                        backStack.add(if (subject.status == SubjectStatus.DRAFT) OutlineEditorKey(subject.id) else SubjectKey(subject.id))
                    }
                    entry<HomeKey> {
                        HomeScreen(
                            onNewSubject = { backStack.add(CreateKey) },
                            onOpenSubject = openSubject,
                            onOpenTopic = { backStack.add(TopicKey(it)) },
                            onOpenQuiz = { backStack.add(QuizKey(it)) },
                            onOpenPractical = { backStack.add(PracticalKey(it)) },
                            onOpenReview = { backStack.add(ReviewKey) },
                        )
                    }
                    entry<LibraryKey> { LibraryScreen(onOpenSubject = openSubject) }
                    entry<YouKey> {
                        ProgressScreen(
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onOpenReview = { backStack.add(ReviewKey) },
                            onOpenSubject = { backStack.add(SubjectKey(it)) },
                        )
                    }
                    entry<SettingsKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<CreateKey> {
                        CreateSubjectScreen(
                            onCreated = { id -> backStack.replaceTop(OutlineEditorKey(id)) },
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<OutlineEditorKey> { key ->
                        OutlineEditorScreen(
                            subjectId = key.subjectId,
                            onStarted = { id -> backStack.replaceTop(SubjectKey(id)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<SubjectKey> { key ->
                        SubjectScreen(
                            subjectId = key.subjectId,
                            onOpenTopic = { backStack.add(TopicKey(it)) },
                            onOpenQuiz = { backStack.add(QuizKey(it)) },
                            onOpenPractical = { backStack.add(PracticalKey(it)) },
                            onEditOutline = { backStack.add(OutlineEditorKey(it)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<TopicKey> { key ->
                        TopicScreen(
                            topicId = key.topicId,
                            onFinished = { backStack.removeLastOrNull() },
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onRescue = { rung -> backStack.add(RescueKey(key.topicId, rung)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<RescueKey> { key ->
                        RescueScreen(
                            topicId = key.topicId,
                            startRung = key.startRung,
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<QuizKey> { key ->
                        QuizScreen(
                            chapterId = key.chapterId,
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<PracticalKey> { key ->
                        PracticalScreen(
                            chapterId = key.chapterId,
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<ReviewKey> { ReviewScreen(onBack = { backStack.removeLastOrNull() }) }
                },
            )
        }
    }
}

/** Replaces the current screen, e.g. New subject → editor → course, so Back skips finished steps. */
private fun NavBackStack<NavKey>.replaceTop(key: NavKey) {
    removeLastOrNull()
    add(key)
}

/** Tabs are roots: switching tab replaces the whole back stack. */
private fun NavBackStack<NavKey>.switchTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(tab)
}
