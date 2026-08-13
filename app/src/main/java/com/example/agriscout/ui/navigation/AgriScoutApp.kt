package com.example.agriscout.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agriscout.ui.screens.AuthScreen
import com.example.agriscout.ui.screens.DashboardScreen
import com.example.agriscout.ui.screens.DiseaseCatalogScreen
import com.example.agriscout.ui.screens.FarmDetailScreen
import com.example.agriscout.ui.screens.FarmFormScreen
import com.example.agriscout.ui.screens.FarmListScreen
import com.example.agriscout.ui.screens.MapScreen
import com.example.agriscout.ui.screens.OfficerAccessGateScreen
import com.example.agriscout.ui.screens.ReportDetailScreen
import com.example.agriscout.ui.screens.ReportFormScreen
import com.example.agriscout.ui.screens.ReportListScreen
import com.example.agriscout.ui.screens.SettingsScreen
import com.example.agriscout.ui.screens.SplashScreen
import com.example.agriscout.ui.screens.SyncStatusScreen
import com.example.agriscout.ui.screens.WeatherWarningsScreen
import com.example.agriscout.ui.screens.field.FarmVisitLogScreen
import com.example.agriscout.ui.screens.field.HarvestFollowUpScreen
import com.example.agriscout.ui.screens.field.InventoryRequestScreen
import com.example.agriscout.ui.screens.field.RecommendationsScreen
import com.example.agriscout.ui.screens.field.SensorDashboardScreen
import com.example.agriscout.ui.screens.field.SupplierRequestsScreen
import com.example.agriscout.ui.viewmodel.AgriScoutViewModel
import kotlinx.coroutines.delay

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ACCESS_GATE = "accessGate"
    const val DASHBOARD = "dashboard"
    const val FARMS = "farms"
    const val FARM_FORM = "farmForm"
    const val FARM_DETAIL = "farmDetail"
    const val REPORTS = "reports"
    const val REPORT_FORM = "reportForm"
    const val REPORT_DETAIL = "reportDetail"
    const val CATALOG = "catalog"
    const val MAP = "map"
    const val SENSOR_DASHBOARD = "sensorDashboard"
    const val FARM_VISITS = "farmVisits"
    const val INVENTORY = "inventory"
    const val SUPPLIER_REQUESTS = "supplierRequests"
    const val HARVEST_LISTINGS = "harvestListings"
    const val RECOMMENDATIONS = "recommendations"
    const val WARNINGS = "warnings"
    const val SYNC = "sync"
    const val SETTINGS = "settings"
    const val MORE = "more"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, "Home", Icons.Filled.Dashboard),
    TopLevelDestination(Routes.FARMS, "Farms", Icons.Filled.Agriculture),
    TopLevelDestination(Routes.REPORTS, "Reports", Icons.AutoMirrored.Filled.Article),
    TopLevelDestination(Routes.INVENTORY, "Inventory", Icons.Filled.Inventory),
    TopLevelDestination(Routes.MORE, "More", Icons.Filled.MoreHoriz)
)

private val secondaryDestinations = listOf(
    TopLevelDestination(Routes.FARM_VISITS, "Visit Log", Icons.AutoMirrored.Filled.EventNote),
    TopLevelDestination(Routes.SUPPLIER_REQUESTS, "Supplier Requests", Icons.Filled.Store),
    TopLevelDestination(Routes.HARVEST_LISTINGS, "Harvest Listings", Icons.Filled.ShoppingBasket),
    TopLevelDestination(Routes.MAP, "Map", Icons.Filled.Map),
    TopLevelDestination(Routes.CATALOG, "Catalog", Icons.AutoMirrored.Filled.MenuBook),
    TopLevelDestination(Routes.SYNC, "Sync", Icons.Filled.Sync),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

private val navigationRoutes = topLevelDestinations.map { it.route } + secondaryDestinations.map { it.route }

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AgriScoutApp(viewModel: AgriScoutViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val officerAccess by viewModel.officerAccess.collectAsState()
    val officerProfile by viewModel.officerProfile.collectAsState()
    val showNavigation = currentRoute in navigationRoutes

    LaunchedEffect(officerProfile.isLoggedIn, officerAccess.canOperate, officerAccess.loaded, currentRoute) {
        if (!officerProfile.isLoggedIn) return@LaunchedEffect
        if (!officerAccess.loaded && officerAccess.loading) return@LaunchedEffect
        val onAuthScreens = currentRoute in setOf(Routes.LOGIN, Routes.REGISTER, Routes.SPLASH, null)
        if (onAuthScreens) return@LaunchedEffect
        if (!officerAccess.canOperate && currentRoute != Routes.ACCESS_GATE) {
            navController.navigate(Routes.ACCESS_GATE) {
                popUpTo(Routes.DASHBOARD) { inclusive = true }
                launchSingleTop = true
            }
        } else if (officerAccess.canOperate && currentRoute == Routes.ACCESS_GATE) {
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.ACCESS_GATE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showNavigation && !useRail) {
                    AppNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigateTopLevel(route)
                        }
                    )
                }
            }
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (showNavigation && useRail) {
                    AppNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navController.navigateTopLevel(route) }
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.weight(1f)
                ) {
            composable(Routes.SPLASH) {
                SplashScreen()
                LaunchedEffect(Unit) {
                    delay(600)
                    if (!viewModel.officerProfile.value.isLoggedIn) {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    } else {
                        viewModel.refreshOfficerAccess { access ->
                            navController.navigate(
                                if (access.canOperate) Routes.DASHBOARD else Routes.ACCESS_GATE
                            ) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    }
                }
            }
            composable(Routes.LOGIN) {
                AuthScreen(
                    title = "Officer Login",
                    actionText = "Login",
                    alternateText = "Create account",
                    viewModel = viewModel,
                    onAction = {
                        viewModel.login {
                            val destination = if (viewModel.officerAccess.value.canOperate) {
                                Routes.DASHBOARD
                            } else {
                                Routes.ACCESS_GATE
                            }
                            navController.navigate(destination) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    },
                    onAlternate = { navController.navigate(Routes.REGISTER) }
                )
            }
            composable(Routes.REGISTER) {
                AuthScreen(
                    title = "Register Officer",
                    actionText = "Register",
                    alternateText = "Back to login",
                    viewModel = viewModel,
                    onAction = {
                        viewModel.register {
                            navController.navigate(Routes.ACCESS_GATE) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    },
                    onAlternate = { navController.popBackStack() }
                )
            }
            composable(Routes.ACCESS_GATE) {
                OfficerAccessGateScreen(
                    viewModel = viewModel,
                    onLogout = {
                        viewModel.logout {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(viewModel) { route ->
                    navController.navigate(route)
                }
            }
            composable(Routes.MORE) {
                MoreScreen(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateTopLevel(route) }
                )
            }
            composable(Routes.FARMS) {
                FarmListScreen(
                    viewModel = viewModel,
                    onAdd = { viewModel.editFarm(null); navController.navigate(Routes.FARM_FORM) },
                    onEdit = { viewModel.editFarm(it); navController.navigate(Routes.FARM_FORM) },
                    onOpen = { navController.navigate("${Routes.FARM_DETAIL}/${it.id}") },
                    onOpenIoT = { farm -> navController.navigate("${Routes.SENSOR_DASHBOARD}/${farm.id}") },
                    onBack = null
                )
            }
            composable(Routes.FARM_FORM) {
                FarmFormScreen(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(
                "${Routes.FARM_DETAIL}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                FarmDetailScreen(
                    farmId = it.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onAddReport = { farm ->
                        viewModel.editReport(null, farmId = farm.id)
                        navController.navigate(Routes.REPORT_FORM)
                    },
                    onOpenReport = { report -> navController.navigate("${Routes.REPORT_DETAIL}/${report.id}") },
                    onEditReport = { report ->
                        viewModel.editReport(report)
                        navController.navigate(Routes.REPORT_FORM)
                    },
                    onDeleteReport = { report -> viewModel.deleteReport(report) },
                    onOpenSensors = { farm -> navController.navigate("${Routes.SENSOR_DASHBOARD}/${farm.id}") },
                    onOpenVisits = { farm ->
                        viewModel.prepareFarmVisit(farm.id)
                        navController.navigate("${Routes.FARM_VISITS}/${farm.id}")
                    },
                    onOpenInventory = { farm ->
                        viewModel.updateInventoryRequestForm { copy(farmId = farm.id) }
                        navController.navigate(Routes.INVENTORY)
                    },
                    onOpenRecommendations = { farm -> navController.navigate("${Routes.RECOMMENDATIONS}/${farm.id}") }
                )
            }
            composable(Routes.REPORTS) {
                ReportListScreen(
                    viewModel = viewModel,
                    onAdd = { viewModel.editReport(null); navController.navigate(Routes.REPORT_FORM) },
                    onEdit = { viewModel.editReport(it); navController.navigate(Routes.REPORT_FORM) },
                    onOpen = { navController.navigate("${Routes.REPORT_DETAIL}/${it.id}") },
                    onBack = null
                )
            }
            composable(Routes.REPORT_FORM) {
                ReportFormScreen(viewModel, onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
            }
            composable(
                "${Routes.REPORT_DETAIL}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                ReportDetailScreen(
                    reportId = it.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { report ->
                        viewModel.editReport(report)
                        navController.navigate(Routes.REPORT_FORM)
                    }
                )
            }
            composable(Routes.CATALOG) { DiseaseCatalogScreen(viewModel, onBack = null) }
            composable(
                "${Routes.SENSOR_DASHBOARD}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                SensorDashboardScreen(
                    farmId = it.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onRecommendations = { farmId -> navController.navigate("${Routes.RECOMMENDATIONS}/$farmId") }
                )
            }
            composable(Routes.INVENTORY) {
                InventoryRequestScreen(viewModel = viewModel, onBack = null)
            }
            composable(Routes.SUPPLIER_REQUESTS) {
                SupplierRequestsScreen(viewModel = viewModel, onBack = null)
            }
            composable(Routes.HARVEST_LISTINGS) {
                HarvestFollowUpScreen(viewModel = viewModel, onBack = null)
            }
            composable(Routes.FARM_VISITS) {
                FarmVisitLogScreen(farmId = null, viewModel = viewModel, onBack = null)
            }
            composable(
                "${Routes.FARM_VISITS}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                FarmVisitLogScreen(
                    farmId = it.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "${Routes.RECOMMENDATIONS}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                RecommendationsScreen(
                    farmId = it.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSensors = { farmId -> navController.navigate("${Routes.SENSOR_DASHBOARD}/$farmId") }
                )
            }
            composable(Routes.MAP) { MapScreen(viewModel, onBack = null) }
            composable(Routes.WARNINGS) { WeatherWarningsScreen(viewModel, onBack = { navController.popBackStack() }) }
            composable(Routes.SYNC) { SyncStatusScreen(viewModel, onBack = null) }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel, onBack = null) {
                    viewModel.logout {
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
            NavigationBarItem(
                selected = isDestinationSelected(currentRoute, destination.route),
                onClick = { onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationRail {
        topLevelDestinations.forEach { destination ->
            NavigationRailItem(
                selected = isDestinationSelected(currentRoute, destination.route),
                onClick = { onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun MoreScreen(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("More", modifier = Modifier.padding(bottom = 16.dp))
                secondaryDestinations.forEach { destination ->
                    Button(
                        onClick = { onNavigate(destination.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(destination.icon, contentDescription = null)
                        Text(
                            text = destination.label,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isDestinationSelected(currentRoute: String?, destinationRoute: String): Boolean {
    return currentRoute == destinationRoute ||
        (destinationRoute == Routes.MORE && currentRoute in secondaryDestinations.map { it.route })
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        if (route == Routes.DASHBOARD) {
            popUpTo(Routes.DASHBOARD) {
                inclusive = true
                saveState = false
            }
            restoreState = false
        } else {
            popUpTo(Routes.DASHBOARD) {
                saveState = true
            }
            restoreState = true
        }
        launchSingleTop = true
    }
}
