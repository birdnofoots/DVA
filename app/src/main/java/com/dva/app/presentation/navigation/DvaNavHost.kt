package com.dva.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dva.app.presentation.analysis.AnalysisScreen
import com.dva.app.presentation.report.ReportScreen
import com.dva.app.presentation.report.ViolationDetailScreen
import com.dva.app.presentation.settings.SettingsScreen
import com.dva.app.presentation.video.VideoListScreen

@Composable
fun DvaNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.VideoList.route
    ) {
        composable(NavRoutes.VideoList.route) {
            VideoListScreen(
                onNavigateToAnalysis = { videoId ->
                    navController.navigate(NavRoutes.Analysis.createRoute(videoId))
                },
                onNavigateToSettings = {
                    navController.navigate(NavRoutes.Settings.route)
                }
            )
        }

        composable(
            route = NavRoutes.Analysis.route,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
            AnalysisScreen(
                videoId = videoId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReport = { taskId ->
                    navController.navigate(NavRoutes.Report.createRoute(taskId)) {
                        popUpTo(NavRoutes.VideoList.route)
                    }
                }
            )
        }

        composable(
            route = NavRoutes.Report.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            ReportScreen(
                taskId = taskId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToViolation = { violationId ->
                    navController.navigate(NavRoutes.ViolationDetail.createRoute(violationId))
                }
            )
        }

        composable(
            route = NavRoutes.ViolationDetail.route,
            arguments = listOf(navArgument("violationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val violationId = backStackEntry.arguments?.getString("violationId") ?: return@composable
            ViolationDetailScreen(
                violationId = violationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
