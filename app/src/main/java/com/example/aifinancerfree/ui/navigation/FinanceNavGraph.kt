package com.example.aifinancerfree.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.example.aifinancerfree.data.ThemeMode
import com.example.aifinancerfree.data.local.ConsentPreferences
import com.example.aifinancerfree.ui.screens.*
import com.example.aifinancerfree.ui.viewmodel.AuthViewModel
import com.example.aifinancerfree.ui.viewmodel.FinanceViewModel
import com.example.aifinancerfree.ui.viewmodel.ProfileViewModel
import com.example.aifinancerfree.ui.viewmodel.ProfileUiState
import com.example.aifinancerfree.ui.viewmodel.AdvisorViewModel
import com.example.aifinancerfree.ui.viewmodel.GoalsViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@Composable
fun FinanceNavGraph(
    nav: NavHostController,
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    financeViewModel: FinanceViewModel,
    advisorViewModel: AdvisorViewModel,
    goalsViewModel: GoalsViewModel,
    consentPrefs: ConsentPreferences,
    mode: ThemeMode,
    selectTheme: (ThemeMode) -> Unit,
    startDestination: String
) {
    val isLoggedIn by profileViewModel.isLoggedIn.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val username = when (val state = profileState) {
        is ProfileUiState.Success -> state.user.username
        else -> "User"
    }

    // Global listener for logout
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            nav.navigate(Routes.UnauthGraph) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = startDestination
    ) {
        // Unauthenticated Nested Graph
        navigation(
            startDestination = Routes.Welcome,
            route = Routes.UnauthGraph
        ) {
            composable(Routes.Welcome) {
                WelcomeScreen(
                    login = { nav.go(Routes.Login) },
                    register = { nav.go(Routes.Register) }
                )
            }
            composable(Routes.Login) {
                LoginScreen(
                    viewModel = authViewModel,
                    onSuccess = {
                        nav.openDemo()
                        profileViewModel.fetchProfile()
                    },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.Register) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onSuccess = {
                        nav.openDemo()
                        profileViewModel.fetchProfile()
                    },
                    onBack = { nav.popBackStack() }
                )
            }
        }

        // Authenticated Nested Graph
        navigation(
            startDestination = Routes.Dashboard,
            route = Routes.AuthGraph
        ) {
            composable(Routes.Dashboard) {
                MainScaffold(nav) {
                    DashboardScreen(
                        viewModel = financeViewModel,
                        username = username,
                        advisor = { nav.go(Routes.Advisor) }
                    )
                }
            }
            composable(Routes.Transactions) {
                MainScaffold(nav) {
                    TransactionsScreen(
                        viewModel = financeViewModel,
                        addTx = { nav.go(Routes.Add) }
                    )
                }
            }
            composable(Routes.Goals) {
                var selectedTab by remember { mutableStateOf(0) }
                MainScaffold(nav) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Budgets", fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Savings Goals", fontWeight = FontWeight.Bold) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (selectedTab == 0) {
                                BudgetsScreen(viewModel = financeViewModel)
                            } else {
                                GoalsScreen(viewModel = goalsViewModel)
                            }
                        }
                    }
                }
            }
            composable(Routes.Profile) {
                MainScaffold(nav) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        mode = mode,
                        select = selectTheme,
                        onConsent = { nav.go(Routes.Consent) },
                        logout = {
                            profileViewModel.logout()
                        }
                    )
                }
            }
            composable(Routes.Add) {
                AddTransactionScreen(
                    viewModel = financeViewModel,
                    back = { nav.popBackStack() }
                )
            }
            composable(Routes.Advisor) {
                AdvisorChatScreen(
                    viewModel = advisorViewModel,
                    back = { nav.popBackStack() }
                )
            }
            composable(Routes.Consent) {
                ConsentScreen(
                    prefs = consentPrefs,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}

