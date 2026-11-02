package com.kaz229.fittrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaz229.fittrack.ui.screens.AddCardioDialog
import com.kaz229.fittrack.ui.screens.AddMealDialog
import com.kaz229.fittrack.ui.screens.ExerciseDetailScreen
import com.kaz229.fittrack.ui.screens.ExercisesScreen
import com.kaz229.fittrack.ui.screens.FoodSearchScreen
import com.kaz229.fittrack.ui.screens.HealthConnectScreen
import com.kaz229.fittrack.ui.screens.HomeScreen
import com.kaz229.fittrack.ui.screens.KazisScreen
import com.kaz229.fittrack.ui.screens.MealsScreen
import com.kaz229.fittrack.ui.screens.NeedsScreen
import com.kaz229.fittrack.ui.screens.ProfileScreen
import com.kaz229.fittrack.ui.screens.ScanScreen
import com.kaz229.fittrack.ui.screens.SessionDetailScreen
import com.kaz229.fittrack.ui.screens.SessionsScreen

/** Les quatre onglets de la barre du bas. Les autres écrans s'empilent par-dessus. */
private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Accueil", Icons.Filled.Home),
    MEALS("meals", "Repas", Icons.Filled.Restaurant),
    SESSIONS("sessions", "Séances", Icons.Filled.FitnessCenter),
    EXERCISES("exercises", "Exercices", Icons.Filled.MenuBook),
}

private const val SESSION_DETAIL = "session"
private const val EXERCISE_DETAIL = "exercise"
private const val PROFILE = "profile"
private const val SCAN = "scan"
private const val HEALTH = "health"
private const val KAZIS = "kazis"
private const val FOOD_SEARCH = "food-search"
private const val NEEDS = "needs"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitTrackApp(viewModel: FitTrackViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
    }

    // Le rappel de séance oubliée ne sert à rien sans autorisation de notifier :
    // on la demande au moment où elle prend son sens, en démarrant une séance.
    val context = LocalContext.current
    val notificationsGranted = remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    var showCardioDialog by remember { mutableStateOf(false) }
    var showMealDialog by remember { mutableStateOf(false) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val summary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val lastWeek by viewModel.lastWeek.collectAsStateWithLifecycle()
    val volumeToday by viewModel.todayVolume.collectAsStateWithLifecycle()
    val setCountToday by viewModel.todaySetCount.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val meals by viewModel.todayMeals.collectAsStateWithLifecycle()
    val macros by viewModel.todayMacros.collectAsStateWithLifecycle()
    val scanState by viewModel.scan.collectAsStateWithLifecycle()
    val healthState by viewModel.health.collectAsStateWithLifecycle()
    val kazisState by viewModel.kazis.collectAsStateWithLifecycle()
    val foodSearchState by viewModel.foodSearch.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val needsAnswers by viewModel.needsProfile.collectAsStateWithLifecycle()
    val needs by viewModel.needs.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val exercisesById by viewModel.exercisesById.collectAsStateWithLifecycle()
    val openSession by viewModel.openSession.collectAsStateWithLifecycle()
    val openSessionBlocks by viewModel.openSessionBlocks.collectAsStateWithLifecycle()
    val exerciseDetail by viewModel.exerciseDetail.collectAsStateWithLifecycle()

    val isTopLevel = currentTab != null
    val title = when (currentRoute) {
        SESSION_DETAIL -> "Séance"
        EXERCISE_DETAIL -> "Exercice"
        PROFILE -> "Profil"
        SCAN -> "Scanner un produit"
        HEALTH -> "Health Connect"
        KAZIS -> "Repas dictés"
        FOOD_SEARCH -> "Chercher un aliment"
        NEEDS -> "Mes besoins"
        else -> currentTab?.label ?: "FitTrack"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Les onglets affichent leur propre en-tête : un titre ici ferait doublon
                title = { if (!isTopLevel) Text(title) },
                // Sur un onglet la barre ne porte plus que l'accès au profil : lui laisser
                // la couleur de surface dessinait un bandeau en haut de l'écran.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isTopLevel) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                            )
                        }
                    }
                },
                actions = {
                    if (isTopLevel) {
                        IconButton(onClick = { navController.navigate(PROFILE) }) {
                            Icon(Icons.Filled.Person, contentDescription = "Profil")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when (currentTab) {
                Tab.SESSIONS -> Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = { showCardioDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = "Ajouter du cardio",
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!notificationsGranted) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.startGymSession { navController.navigate(SESSION_DETAIL) }
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Séance salle") },
                    )
                }
                Tab.MEALS -> Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(onClick = { showMealDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Saisir un aliment à la main")
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            viewModel.onFoodQueryChange("")
                            navController.navigate(FOOD_SEARCH)
                        },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Chercher un aliment")
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.resetScan()
                            navController.navigate(SCAN)
                        },
                        icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                        text = { Text("Scanner") },
                    )
                }
                else -> Unit
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    summary = summary,
                    profile = profile,
                    macros = macros,
                    lastWeek = lastWeek,
                    volumeToday = volumeToday,
                    setCountToday = setCountToday,
                    today = viewModel.todayLabel,
                )
            }
            composable(Tab.SESSIONS.route) {
                SessionsScreen(
                    sessions = sessions,
                    onOpen = { session ->
                        viewModel.openSession(session.id)
                        navController.navigate(SESSION_DETAIL)
                    },
                    onDelete = viewModel::deleteSession,
                )
            }
            composable(Tab.EXERCISES.route) {
                ExercisesScreen(
                    state = catalog,
                    onQueryChange = viewModel::onQueryChange,
                    onBodyPartFilter = viewModel::onBodyPartFilter,
                    onEquipmentFilter = viewModel::onEquipmentFilter,
                    onSelect = { exercise ->
                        viewModel.selectExercise(exercise.id)
                        navController.navigate(EXERCISE_DETAIL)
                    },
                    showHeader = true,
                )
            }
            composable(Tab.MEALS.route) {
                MealsScreen(
                    meals = meals,
                    macros = macros,
                    macroGoals = profile.macroGoals,
                    onDelete = viewModel::deleteMeal,
                    onMove = viewModel::moveMeal,
                )
            }
            composable(FOOD_SEARCH) {
                FoodSearchScreen(
                    state = foodSearchState,
                    onQueryChange = viewModel::onFoodQueryChange,
                    onRepeat = { meal, mealType ->
                        viewModel.repeatMeal(meal, mealType)
                        navController.popBackStack()
                    },
                    onPickProduct = { product ->
                        viewModel.selectSearchedProduct(product)
                        navController.navigate(SCAN)
                    },
                )
            }
            composable(NEEDS) {
                NeedsScreen(
                    answers = needsAnswers,
                    needs = needs,
                    weightKg = profile.weightKg,
                    onChange = viewModel::setNeedsProfile,
                    onApply = {
                        viewModel.applyComputedNeeds()
                        navController.popBackStack()
                    },
                    onRefreshWeight = viewModel::refreshWeightFromHealth,
                )
            }
            composable(KAZIS) {
                KazisScreen(
                    state = kazisState,
                    onSave = viewModel::saveKazisSettings,
                    onFetch = viewModel::fetchKazisMeals,
                    onBackup = viewModel::backupToKazis,
                    onRestore = viewModel::restoreFromKazis,
                )
            }
            composable(HEALTH) {
                HealthConnectScreen(
                    state = healthState,
                    onSync = viewModel::syncHealthConnect,
                    onPermissionsChanged = viewModel::refreshHealthPermissions,
                    onUseWeight = viewModel::applyWeightFromHealth,
                )
            }
            composable(SCAN) {
                ScanScreen(
                    state = scanState,
                    onBarcode = viewModel::onBarcodeScanned,
                    onConfirm = { grams, mealType ->
                        viewModel.addScannedFood(grams, mealType)
                        navController.popBackStack()
                    },
                    onRetry = viewModel::resetScan,
                    onManualEntry = {
                        navController.popBackStack()
                        showMealDialog = true
                    },
                )
            }
            composable(SESSION_DETAIL) {
                SessionDetailScreen(
                    session = openSession,
                    blocks = openSessionBlocks,
                    exercisesById = exercisesById,
                    catalog = catalog,
                    onQueryChange = viewModel::onQueryChange,
                    onBodyPartFilter = viewModel::onBodyPartFilter,
                    onEquipmentFilter = viewModel::onEquipmentFilter,
                    onAddSet = viewModel::addSet,
                    onDeleteSet = viewModel::deleteSet,
                    onFinish = { duration, note ->
                        viewModel.finishGymSession(duration, note)
                        navController.popBackStack()
                    },
                )
            }
            composable(EXERCISE_DETAIL) {
                ExerciseDetailScreen(detail = exerciseDetail)
            }
            composable(PROFILE) {
                ProfileScreen(
                    profile = profile,
                    theme = theme,
                    onThemeChange = viewModel::setTheme,
                    onOpenHealthConnect = { navController.navigate(HEALTH) },
                    onOpenKazis = { navController.navigate(KAZIS) },
                    onOpenNeeds = { navController.navigate(NEEDS) },
                    onSave = { weight, goal, macroGoals ->
                        viewModel.setWeight(weight)
                        viewModel.setDailyCalorieGoal(goal)
                        viewModel.setMacroGoals(macroGoals)
                    },
                )
            }
        }
    }

    if (showCardioDialog) {
        AddCardioDialog(
            onDismiss = { showCardioDialog = false },
            onConfirm = { activityId, duration, note ->
                viewModel.addCardioSession(activityId, duration, note)
                showCardioDialog = false
            },
        )
    }
    if (showMealDialog) {
        AddMealDialog(
            onDismiss = { showMealDialog = false },
            onConfirm = { name, calories, mealType, protein, carbs, fat ->
                viewModel.addMeal(name, calories, mealType, protein, carbs, fat)
                showMealDialog = false
            },
        )
    }
}

/** Bascule d'onglet sans empiler les destinations, en gardant l'état de chaque onglet. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
