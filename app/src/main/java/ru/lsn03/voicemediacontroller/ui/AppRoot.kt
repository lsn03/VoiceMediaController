package ru.lsn03.voicemediacontroller.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.lsn03.voicemediacontroller.ui.screens.commands.ActionDetailScreen
import ru.lsn03.voicemediacontroller.ui.screens.commands.CommandsScreen
import ru.lsn03.voicemediacontroller.ui.screens.home.HomeScreen
import ru.lsn03.voicemediacontroller.ui.screens.settings.SettingsScreen

@Composable
fun AppRoot(
    recognizedStatus: String,
) {
    val navController = rememberNavController()

    val topLevel = listOf(
        TopLevelDestination.Home,
        TopLevelDestination.Commands,
        TopLevelDestination.Settings
    )

    Scaffold(
        bottomBar = { BottomBar(navController, topLevel) }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Home.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(TopLevelDestination.Home.route) {
                HomeScreen(recognizedStatus = recognizedStatus)
            }
            composable(TopLevelDestination.Commands.route) {
                CommandsScreen(
                    onOpenAction = { actionName ->
                        navController.navigate("${Routes.ActionDetail}/$actionName")
                    }
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen()
            }
            composable("${Routes.ActionDetail}/{action}") { backStack ->
                val action = backStack.arguments?.getString("action").orEmpty()
                ActionDetailScreen(
                    actionName = action,
                    navUp = { navController.popBackStack() }
                )
            }
        }
    }
}

private enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Главная"),
    Commands("commands", "Команды"),
    Settings("settings", "Настройки");
}


private object Routes {
    const val ActionDetail = "action"
}


@Composable
private fun BottomBar(
    navController: NavHostController,
    items: List<TopLevelDestination>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        items.forEach { dest ->
            val selected =
                currentDestination?.hierarchy?.any { it.route == dest.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            // Если у тебя было saveState/restoreState и оно ломало — выключи:
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                icon = {
                    val icon = when (dest) {
                        TopLevelDestination.Home -> Icons.Default.Home
                        TopLevelDestination.Commands -> Icons.Default.List
                        TopLevelDestination.Settings -> Icons.Default.Settings
                    }
                    Icon(icon, contentDescription = dest.label)
                },
                label = { Text(dest.label) }
            )
        }
    }
}
