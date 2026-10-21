package com.ignacio.rutina.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ignacio.rutina.ui.detail.TaskDetailScreen
import com.ignacio.rutina.ui.edit.EditTaskScreen
import com.ignacio.rutina.ui.tasks.TasksScreen
import com.ignacio.rutina.ui.today.TodayScreen

private object Routes {
    const val TODAY = "hoy"
    const val TASKS = "tareas"
    const val DETAIL = "tarea/{id}"
    const val EDIT = "editar?id={id}"

    fun detail(id: Long) = "tarea/$id"

    /** id 0 = tarea nueva. */
    fun edit(id: Long) = "editar?id=$id"
}

@Composable
fun RutinaNavHost() {
    val nav = rememberNavController()
    val bottomBar: @Composable () -> Unit = { MainBottomBar(nav) }
    NavHost(navController = nav, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) {
            TodayScreen(
                onOpenTask = { nav.navigate(Routes.detail(it)) },
                onNewTask = { nav.navigate(Routes.edit(0)) },
                bottomBar = bottomBar,
            )
        }
        composable(Routes.TASKS) {
            TasksScreen(
                onOpenTask = { nav.navigate(Routes.detail(it)) },
                onNewTask = { nav.navigate(Routes.edit(0)) },
                bottomBar = bottomBar,
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            TaskDetailScreen(
                taskId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            EditTaskScreen(
                taskId = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
                onDeleted = {
                    // El detalle de la tarea borrada ya no tiene sentido: se vuelve a lo anterior.
                    if (!nav.popBackStack(Routes.DETAIL, inclusive = true)) nav.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun MainBottomBar(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar {
        listOf(
            Triple(Routes.TODAY, "Hoy", Icons.Default.Home),
            Triple(Routes.TASKS, "Tareas", Icons.AutoMirrored.Filled.List),
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = {
                    if (current != route) {
                        nav.navigate(route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}
