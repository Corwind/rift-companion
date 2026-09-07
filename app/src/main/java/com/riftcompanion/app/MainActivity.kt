package com.riftcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

@Composable
private fun AppNavigation(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val settingsData by settingsViewModel.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // App lock state
    var isLocked by remember { mutableStateOf(true) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val biometricEnabled = settingsData?.biometricEnabled ?: false
    val setupComplete = settingsData?.setupComplete ?: false

    // Determine which screen to show
    when {
        // First-time setup
        setupComplete.not() -> {
            SetupScreen(
                biometricAvailable = BiometricHelper.isBiometricAvailable(navController.context),
                onEnableBiometric = {
                    settingsViewModel.setBiometricEnabled(true)
                },
                biometricEnabled = uiState.biometricEnabled,
                onSaveApiKey = { key, callback ->
                    settingsViewModel.saveApiKey(key) { success, error ->
                        if (success) settingsViewModel.setSetupComplete(true)
                        callback(success, error)
                    }
                },
                onComplete = {
                    settingsViewModel.setSetupComplete(true)
                },
            )
        }

        // Biometric lock
        biometricEnabled && isLocked -> {
            LaunchedEffect(Unit) {
                // Don't auto-prompt — let the user tap to unlock (saves battery, avoids unexpected prompts)
            }
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

        // Main app
        else -> {
            NavHost(navController = navController, startDestination = "inventory") {
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
