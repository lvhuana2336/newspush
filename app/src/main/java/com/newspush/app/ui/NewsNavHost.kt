package com.newspush.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.newspush.app.ui.detail.DetailScreen
import com.newspush.app.ui.home.HomeScreen
import com.newspush.app.ui.settings.SettingsScreen

@Composable
fun NewsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenArticle = { id -> navController.navigate("detail/$id") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "detail/{articleId}",
            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
