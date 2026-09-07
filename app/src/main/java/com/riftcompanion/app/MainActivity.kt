package com.riftcompanion.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.riftcompanion.app.security.BiometricHelper
import com.riftcompanion.app.ui.screens.carddetail.CardDetailScreen
import com.riftcompanion.app.ui.screens.catalogue.CatalogueScreen
import com.riftcompanion.app.ui.screens.inventory.InventoryScreen
import com.riftcompanion.app.ui.screens.locations.LocationsScreen
import com.riftcompanion.app.ui.screens.lock.LockScreen
import com.riftcompanion.app.ui.screens.settings.SettingsScreen
import com.riftcompanion.app.ui.screens.setup.SetupScreen
import com.riftcompanion.app.ui.theme.RiftCompanionTheme
import com.riftcompanion.app.ui.theme.ThemeState
import com.riftcompanion.app.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsData by settingsViewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = null)

            val themeState = remember(settingsData) {
                ThemeState(
                    appearance = settingsData?.appearance ?: com.riftcompanion.app.ui.theme.AppAppearance.System,
                    accent = settingsData?.accent ?: com.riftcompanion.app.ui.theme.AppAccentPalette.RiftBlue,
                    secondaryAccent = settingsData?.secondaryAccent,
                    backgroundTransparency = settingsData?.backgroundTransparency ?: 0f,
                )
            }

            RiftCompanionTheme(themeState = themeState) {
                AppNavigation(settingsViewModel = settingsViewModel)
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem("inventory", "Inventory", Icons.Default.GridView),
    BottomNavItem("catalogue", "Catalog", Icons.Default.Apps),
    BottomNavItem("locations", "Locations", Icons.Default.Place),
    BottomNavItem("settings", "Settings", Icons.Default.Settings),
)

private val mainRoutes = setOf("inventory", "catalogue", "locations", "settings")

@Composable
private fun AppNavigation(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val settingsData by settingsViewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var isLocked by remember { mutableStateOf(true) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val biometricEnabled = settingsData?.biometricEnabled ?: false
    val setupComplete = settingsData?.setupComplete ?: false

    when {
        !setupComplete -> {
            SetupScreen(
                biometricAvailable = BiometricHelper.isBiometricAvailable(navController.context),
                onEnableBiometric = { settingsViewModel.setBiometricEnabled(true) },
                biometricEnabled = uiState.biometricEnabled,
                onSaveApiKey = { key, callback ->
                    settingsViewModel.saveApiKey(key) { success, error ->
                        if (success) settingsViewModel.setSetupComplete(true)
                        callback(success, error)
                    }
                },
                onComplete = { settingsViewModel.setSetupComplete(true) },
            )
        }

        biometricEnabled && isLocked -> {
            LockScreen(
                onUnlock = {
                    isAuthenticating = true
                    authError = null
                    BiometricHelper.authenticate(
                        activity = navController.context as FragmentActivity,
                        onSuccess = {
                            isLocked = false
                            isAuthenticating = false
                        },
                        onError = { error ->
                            authError = error
                            isAuthenticating = false
                        },
                        onFail = {
                            authError = "Authentication failed. Try again."
                            isAuthenticating = false
                        },
                    )
                },
                error = authError,
                isAuthenticating = isAuthenticating,
            )
        }

        else -> {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val showBottomBar = currentRoute in mainRoutes

            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = "inventory",
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable("inventory") {
                        InventoryScreen(
                            onCardClick = { nameSlug, isFromInventory ->
                                navController.navigate("cardDetail/$nameSlug/$isFromInventory")
                            },
                        )
                    }
                    composable("catalogue") {
                        CatalogueScreen(
                            onCardClick = { nameSlug, isFromInventory ->
                                navController.navigate("cardDetail/$nameSlug/$isFromInventory")
                            },
                        )
                    }
                    composable("cardDetail/{nameSlug}/{isFromInventory}") { backStackEntry ->
                        val nameSlug = backStackEntry.arguments?.getString("nameSlug") ?: ""
                        val isFromInventory = backStackEntry.arguments?.getString("isFromInventory")?.toBoolean() ?: false
                        CardDetailScreen(
                            cardNameSlug = nameSlug,
                            isFromInventory = isFromInventory,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("locations") {
                        LocationsScreen()
                    }
                    composable("settings") {
                        SettingsScreen()
                    }
                }
            }
        }
    }
}
