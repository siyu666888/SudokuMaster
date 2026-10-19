package com.example.sudokumaster.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.achievements.AchievementsScreen
import com.example.sudokumaster.ui.game.GameScreen
import com.example.sudokumaster.ui.history.HistoryScreen
import com.example.sudokumaster.ui.home.HomeScreen
import com.example.sudokumaster.ui.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // 对局为全屏，不显示底部导航栏
    val showBottomBar = navBackStackEntry?.destination?.route?.startsWith("game") != true

    Scaffold(
        bottomBar = { if (showBottomBar) AppBottomBar(navController) }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner)
        ) {

            composable(Routes.HOME) {
                HomeScreen(
                    onNewGame = { size, diff -> navController.navigate(Routes.newGame(size.id, diff)) },
                    onResume = { navController.navigate(Routes.resume()) }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onReplay = { recordId -> navController.navigate(Routes.replay(recordId)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onPlayImported = { sizeId, diffId, puzzle, solution ->
                        navController.navigate(Routes.importGame(sizeId, diffId, puzzle, solution))
                    }
                )
            }

            composable(Routes.ACHIEVEMENTS) {
                AchievementsScreen(
                    onBack = { navController.popBackStack() },
                    onStartLevel = { sizeId, diffId ->
                        navController.navigate(Routes.newGame(sizeId, diffId))
                    }
                )
            }

            composable(
                route = Routes.GAME_PATTERN,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                    navArgument("sizeId") { type = NavType.StringType; defaultValue = "9" },
                    navArgument("diffId") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("recordId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("puzzle") { type = NavType.StringType; defaultValue = "" },
                    navArgument("solution") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "new"
                val sizeId = SizeId.fromId(backStackEntry.arguments?.getString("sizeId"))
                val diffId = backStackEntry.arguments?.getInt("diffId") ?: 1
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                val importedPuzzle = backStackEntry.arguments?.getString("puzzle") ?: ""
                val importedSolution = backStackEntry.arguments?.getString("solution")?.ifBlank { null }
                GameScreen(
                    sizeId = sizeId,
                    diffId = diffId,
                    mode = mode,
                    replayRecordId = recordId,
                    importedPuzzle = importedPuzzle,
                    importedSolution = importedSolution,
                    onBack = { navController.popBackStack() },
                    onNavigateHome = {
                        navController.popBackStack(route = Routes.HOME, inclusive = false)
                    }
                )
            }
        }
    }
}
