package com.stocksense.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.stocksense.app.ui.alertas.AlertasScreen
import com.stocksense.app.ui.dashboard.DashboardScreen
import com.stocksense.app.ui.historial.HistorialScreen
import com.stocksense.app.ui.login.LoginScreen
import com.stocksense.app.ui.login.RegisterScreen

object AppRoutes {
    const val LOGIN     = "login"
    const val REGISTER  = "register"
    const val DASHBOARD = "dashboard"
    const val ALERTAS   = "alertas"
    const val HISTORIAL = "historial"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser != null) AppRoutes.DASHBOARD else AppRoutes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {

        composable(AppRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(AppRoutes.REGISTER)
                }
            )
        }

        composable(AppRoutes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                onLogout = {
                    auth.signOut()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.DASHBOARD) { inclusive = true }
                    }
                },
                onNavigateToAlertas = {
                    navController.navigate(AppRoutes.ALERTAS)
                },
                onNavigateToHistorial = {
                    navController.navigate(AppRoutes.HISTORIAL)
                }
            )
        }

        composable(AppRoutes.ALERTAS) {
            AlertasScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.HISTORIAL) {
            HistorialScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}