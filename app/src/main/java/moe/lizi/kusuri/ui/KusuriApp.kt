package moe.lizi.kusuri.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.lizi.kusuri.R
import moe.lizi.kusuri.ui.components.OnboardingDialog
import moe.lizi.kusuri.ui.history.HistoryScreen
import moe.lizi.kusuri.ui.log.LogScreen
import moe.lizi.kusuri.ui.lanexport.LanExportScreen
import moe.lizi.kusuri.ui.medications.MedicationDetailScreen
import moe.lizi.kusuri.ui.medications.MedicationEditScreen
import moe.lizi.kusuri.ui.medications.MedicationListScreen
import moe.lizi.kusuri.ui.settings.SettingsScreen
import moe.lizi.kusuri.ui.settings.SettingsViewModel
import moe.lizi.kusuri.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val LOG = "log"
    const val MEDICATIONS = "medications"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LAN_EXPORT = "settings/lan-export"

    const val MEDICATION_DETAIL = "medications/detail/{medicationId}"
    const val MEDICATION_NEW = "medications/new"
    const val MEDICATION_EDIT = "medications/edit/{medicationId}"

    fun medicationDetail(id: Long) = "medications/detail/$id"
    fun medicationEdit(id: Long) = "medications/edit/$id"
}

private data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.TODAY, R.string.tab_today, Icons.Filled.Home),
    TopLevelDestination(Routes.LOG, R.string.tab_log, Icons.Filled.Edit),
    TopLevelDestination(Routes.MEDICATIONS, R.string.tab_medications, Icons.Filled.List),
    TopLevelDestination(Routes.HISTORY, R.string.tab_history, Icons.Filled.DateRange),
)

@Composable
fun KusuriApp(navController: NavHostController = rememberNavController()) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val onboardingDone by settingsViewModel.onboardingDone.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TOP_LEVEL_DESTINATIONS.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            KusuriTopBar(
                route = currentRoute,
                onBack = { navController.navigateUp() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                KusuriBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenMedication = { medicationId ->
                        navController.navigate(Routes.medicationDetail(medicationId))
                    },
                )
            }
            composable(Routes.LOG) {
                LogScreen()
            }
            composable(Routes.MEDICATIONS) {
                MedicationListScreen(
                    onAdd = { navController.navigate(Routes.MEDICATION_NEW) },
                    onOpen = { medicationId -> navController.navigate(Routes.medicationDetail(medicationId)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenLanExport = { navController.navigate(Routes.LAN_EXPORT) },
                )
            }
            composable(Routes.LAN_EXPORT) {
                LanExportScreen()
            }
            composable(
                route = Routes.MEDICATION_DETAIL,
                arguments = listOf(navArgument("medicationId") { type = NavType.LongType }),
            ) { entry ->
                val medicationId = entry.arguments?.getLong("medicationId") ?: 0L
                MedicationDetailScreen(
                    onEdit = { navController.navigate(Routes.medicationEdit(medicationId)) },
                    onBack = { navController.navigateUp() },
                )
            }
            composable(Routes.MEDICATION_NEW) {
                MedicationEditScreen(onSaved = { navController.popBackStack() })
            }
            composable(
                route = Routes.MEDICATION_EDIT,
                arguments = listOf(navArgument("medicationId") { type = NavType.LongType }),
            ) {
                MedicationEditScreen(onSaved = { navController.popBackStack() })
            }
        }
    }

    if (!onboardingDone) {
        OnboardingDialog(
            onDone = { settingsViewModel.completeOnboarding() },
            onDismissRequest = { /* 首次向导必须显式完成,避免误触即算跳过 */ },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KusuriTopBar(route: String?, onBack: () -> Unit, onSettings: () -> Unit) {
    val isTopLevel = TOP_LEVEL_DESTINATIONS.any { it.route == route }
    val title = when (route) {
        Routes.TODAY -> stringResource(R.string.tab_today)
        Routes.LOG -> stringResource(R.string.tab_log)
        Routes.MEDICATIONS -> stringResource(R.string.tab_medications)
        Routes.HISTORY -> stringResource(R.string.tab_history)
        Routes.SETTINGS -> stringResource(R.string.title_settings)
        Routes.LAN_EXPORT -> stringResource(R.string.title_lan_export)
        Routes.MEDICATION_NEW -> stringResource(R.string.title_medication_new)
        Routes.MEDICATION_EDIT -> stringResource(R.string.title_medication_edit)
        Routes.MEDICATION_DETAIL -> stringResource(R.string.title_medication_detail)
        else -> stringResource(R.string.app_name)
    }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (route != null && !isTopLevel) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            }
        },
        actions = {
            if (isTopLevel) {
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                    )
                }
            }
        },
    )
}

@Composable
private fun KusuriBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        TOP_LEVEL_DESTINATIONS.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { if (currentRoute != destination.route) onSelect(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
