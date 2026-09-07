package com.riftcompanion.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.riftcompanion.app.security.BiometricHelper
import com.riftcompanion.app.ui.components.gradientBackground
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
import kotlinx.coroutines.launch

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

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navItems = listOf(
    NavItem("inventory", "Inventory", Icons.Default.GridView),
    NavItem("catalogue", "Catalog", Icons.Default.Apps),
    NavItem("locations", "Locations", Icons.Default.Place),
    NavItem("settings", "Settings", Icons.Default.Settings),
)

private val mainRoutes = setOf("inventory", "catalogue", "locations", "settings")

@OptIn(ExperimentalMaterial3Api::class)
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
            LaunchedEffect(Unit) {
                if (!isAuthenticating) {
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
                            authError = "Authentication failed. Tap to retry."
                            isAuthenticating = false
                        },
                    )
                }
            }
            LockScreen(
                onUnlock = {
                    if (!isAuthenticating) {
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
                                authError = "Authentication failed. Tap to retry."
                                isAuthenticating = false
                            },
                        )
                    }
                },
                error = authError,
                isAuthenticating = isAuthenticating,
            )
        }

        else -> {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.gradientBackground(),
                        drawerContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "RiftCompanion",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 12.dp),
                        )
                        navItems.forEach { item ->
                            NavigationDrawerItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                },
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "inventory?location={location}",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(
                        "inventory?location={location}",
                        arguments = listOf(
                            androidx.navigation.navArgument("location") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        ),
                    ) { backStackEntry ->
                        val locationFilter = backStackEntry.arguments?.getString("location")
                        InventoryScreen(
                            onCardClick = { nameSlug, isFromInventory ->
                                navController.navigate("cardDetail/$nameSlug/$isFromInventory")
                            },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            initialLocationFilter = locationFilter,
                        )
                    }
                    composable("catalogue") {
                        CatalogueScreen(
                            onCardClick = { nameSlug, isFromInventory ->
                                navController.navigate("cardDetail/$nameSlug/$isFromInventory")
                            },
                            onMenuClick = { scope.launch { drawerState.open() } },
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
                        LocationsScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onLocationClick = { locName ->
                                navController.navigate("inventory?location=$locName") {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                        )
                    }
                }
            }
        }
    }
}
