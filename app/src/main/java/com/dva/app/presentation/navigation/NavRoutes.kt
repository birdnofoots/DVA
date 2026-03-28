package com.dva.app.presentation.navigation

sealed class NavRoutes(val route: String) {
    object VideoList : NavRoutes("video_list")
    object Analysis : NavRoutes("analysis/{videoId}") {
        fun createRoute(videoId: String) = "analysis/$videoId"
    }
    object Report : NavRoutes("report/{taskId}") {
        fun createRoute(taskId: String) = "report/$taskId"
    }
    object ViolationDetail : NavRoutes("violation/{violationId}") {
        fun createRoute(violationId: String) = "violation/$violationId"
    }
    object Settings : NavRoutes("settings")
}
