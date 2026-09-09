package com.riftcompanion.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riftcompanion.app.security.BiometricHelper
import com.riftcompanion.app.ui.components.gradientBackground
import com.riftcompanion.app.ui.screens.carddetail.CardDetailScreen
import com.riftcompanion.app.ui.screens.catalogue.CatalogueScreen
import com.riftcompanion.app.ui.screens.decks.DeckDetailScreen
import com.riftcompanion.app.ui.screens.decks.DeckFromLocationScreen
import com.riftcompanion.app.ui.screens.decks.DeckImportScreen
import com.riftcompanion.app.ui.screens.decks.DeckListScreen
import com.riftcompanion.app.ui.screens.inventory.InventoryScreen
import com.riftcompanion.app.ui.screens.locations.LocationsScreen
import com.riftcompanion.app.ui.screens.lock.LockScreen
import com.riftcompanion.app.ui.screens.settings.SettingsScreen
import com.riftcompanion.app.ui.screens.setup.SetupScreen
import com.riftcompanion.app.ui.theme.RiftCompanionTheme
import com.riftcompanion.app.ui.theme.ThemeState
import com.riftcompanion.app.ui.theme.currentThemeState
import com.riftcompanion.app.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

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
    val route: String,          // pattern for currentRoute matching
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val navigateRoute: String = route,  // actual route to navigate to
)

private val navItems = listOf(
    NavItem("decks", "Decks", Icons.Default.Style),
    NavItem("inventory?location={location}", "Inventory", Icons.Default.GridView, "inventory"),
    NavItem("catalogue", "Catalog", Icons.Default.Apps),
    NavItem("locations", "Locations", Icons.Default.Place),
    NavItem("settings", "Settings", Icons.Default.Settings),
)

private val mainRoutes = setOf("decks", "inventory?location={location}", "catalogue", "locations", "settings")

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
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val showBottomBar = currentRoute in mainRoutes

            // Haze state for backdrop blur — the content is the source,
            // the nav bar is the child that shows the blurred background
            val hazeState = remember { HazeState() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gradientBackground(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "decks",
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                ) {
                    composable(
                        "inventory?location={location}",
                        arguments = listOf(
                            navArgument("location") {
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
                            initialLocationFilter = locationFilter,
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
                        LocationsScreen(
                            onLocationClick = { locName ->
                                navController.navigate("inventory?location=$locName") {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen()
                    }
                    composable("decks") {
                        DeckListScreen(
                            onDeckClick = { deckId ->
                                navController.navigate("deckDetail/$deckId")
                            },
                            onImportClick = {
                                navController.navigate("deckImport")
                            },
                            onCreateFromLocation = {
                                navController.navigate("deckFromLocation")
                            },
                        )
                    }
                    composable("deckImport") {
                        DeckImportScreen(
                            onBack = { navController.popBackStack() },
                            onImported = { deckId ->
                                navController.navigate("deckDetail/$deckId") {
                                    popUpTo("decks")
                                }
                            },
                        )
                    }
                    composable("deckFromLocation") {
                        DeckFromLocationScreen(
                            onBack = { navController.popBackStack() },
                            onCreated = { deckId ->
                                navController.navigate("deckDetail/$deckId") {
                                    popUpTo("decks")
                                }
                            },
                        )
                    }
                    composable("deckDetail/{deckId}") { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
                        DeckDetailScreen(
                            deckId = deckId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                if (showBottomBar) {
                    FloatingPillNav(
                        items = navItems,
                        currentRoute = currentRoute,
                        onNavigate = { item ->
                            navController.navigate(item.navigateRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingPillNav(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (NavItem) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val themeState = currentThemeState()
    val accentColor = if (themeState.isDark) themeState.accent.colors.dark else themeState.accent.colors.light
    val isDark = themeState.isDark

    val pillShape = RoundedCornerShape(28.dp)
    val glassOverlay = if (isDark) Color(0x4D1A1A22) else Color(0x4DF5F5F8)
    val glassGradient = Brush.linearGradient(
        if (isDark) listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.01f))
        else listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.1f)),
    )
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f)
    val glassTint = accentColor.copy(alpha = if (isDark) 0.06f else 0.04f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(
                elevation = 12.dp,
                shape = pillShape,
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.08f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.04f),
            ),
    ) {
        // Background layer: Haze backdrop blur — transparent background so blur shows through
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(pillShape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        blurRadius = 25.dp,
                        backgroundColor = Color.Transparent,
                        tints = listOf(HazeTint(accentColor.copy(alpha = if (isDark) 0.06f else 0.04f))),
                    ),
                )
                .border(1.dp, borderColor, pillShape),
        )

        // Content layer: icons and text
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                val tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNavigate(item) }
                        .padding(vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
