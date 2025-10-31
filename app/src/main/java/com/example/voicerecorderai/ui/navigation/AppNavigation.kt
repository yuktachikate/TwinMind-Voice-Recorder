package com.example.voicerecorderai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.voicerecorderai.ui.screen.DashboardScreen
import com.example.voicerecorderai.ui.screen.RecordingScreen
import com.example.voicerecorderai.ui.screen.SummaryScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = {
                    navController.navigate(Screen.Recording.route)
                },
                onNavigateToSummary = { recordingId ->
                    navController.navigate(Screen.Summary.createRoute(recordingId))
                }
            )
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSummary = { recordingId ->
                    // Navigate to Summary and remove Recording from back stack
                    navController.navigate(Screen.Summary.createRoute(recordingId)) {
                        popUpTo(Screen.Recording.route) { inclusive = true }
                    }
                }
            )
        }


        composable(
            route = Screen.Summary.route,
            arguments = listOf(
                navArgument("recordingId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val recordingId = backStackEntry.arguments?.getLong("recordingId") ?: 0L
            SummaryScreen(
                recordingId = recordingId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

