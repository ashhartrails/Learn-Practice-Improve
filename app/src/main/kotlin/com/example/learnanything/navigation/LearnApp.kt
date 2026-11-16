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
import com.example.learnanything.feature.create.CreateSubjectScreen
import com.example.learnanything.feature.home.HomeScreen
import com.example.learnanything.feature.home.LibraryScreen
import com.example.learnanything.feature.settings.SettingsScreen
import com.example.learnanything.feature.subject.SubjectScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey
@Serializable data object LibraryKey : NavKey
@Serializable data object YouKey : NavKey
@Serializable data object CreateKey : NavKey
@Serializable data class SubjectKey(val subjectId: String) : NavKey

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
                    entry<HomeKey> {
                        HomeScreen(
                            onNewSubject = { backStack.add(CreateKey) },
                            onOpenSubject = { backStack.add(SubjectKey(it)) },
                        )
                    }
                    entry<LibraryKey> {
                        LibraryScreen(onOpenSubject = { backStack.add(SubjectKey(it)) })
                    }
                    entry<YouKey> { SettingsScreen() }
                    entry<CreateKey> {
                        CreateSubjectScreen(
                            onCreated = { id ->
                                backStack.removeLastOrNull()
                                backStack.add(SubjectKey(id))
                            },
                            onOpenSettings = { backStack.switchTab(YouKey) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<SubjectKey> { key ->
                        SubjectScreen(subjectId = key.subjectId, onBack = { backStack.removeLastOrNull() })
                    }
                },
            )
        }
    }
}

/** Tabs are roots: switching tab replaces the whole back stack. */
private fun NavBackStack<NavKey>.switchTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(tab)
}
