package com.example.aifinancerfree.ui.navigation

import androidx.navigation.NavHostController

object Routes {
    const val Welcome = "welcome"
    const val Login = "login"
    const val Register = "register"
    const val Dashboard = "dashboard"
    const val Transactions = "transactions"
    const val Goals = "goals"
    const val Profile = "profile"
    const val Add = "add_transaction"
    const val Advisor = "advisor"
    const val Consent = "consent"

    // Subgraph routes
    const val UnauthGraph = "unauth_graph"
    const val AuthGraph = "auth_graph"
}

fun NavHostController.go(route: String) {
    navigate(route) { launchSingleTop = true }
}

fun NavHostController.openDemo() {
    navigate(Routes.AuthGraph) {
        popUpTo(Routes.UnauthGraph) { inclusive = true }
        launchSingleTop = true
    }
}
