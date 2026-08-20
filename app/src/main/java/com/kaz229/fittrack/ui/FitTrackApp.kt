package com.kaz229.fittrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kaz229.fittrack.ui.screens.HomeScreen
import com.kaz229.fittrack.ui.screens.MealsScreen
import com.kaz229.fittrack.ui.screens.ProfileScreen
import com.kaz229.fittrack.ui.screens.SessionDetailScreen
import com.kaz229.fittrack.ui.screens.SessionsScreen

/** Les quatre onglets de la barre du bas. Les autres écrans s'empilent par-dessus. */
private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Accueil", Icons.Filled.Home),
    SESSIONS("sessions", "Séances", Icons.Filled.FitnessCenter),
    EXERCISES("exercises", "Exercices", Icons.Filled.MenuBook),
    MEALS("meals", "Repas", Icons.Filled.Restaurant),
}

private const val SESSION_DETAIL = "session"
private const val EXERCISE_DETAIL = "exercise"
private const val PROFILE = "profile"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitTrackApp(viewModel: FitTrackViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
    }

    var showCardioDialog by remember { mutableStateOf(false) }
    var showMealDialog by remember { mutableStateOf(false) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val summary by viewModel.todaySummary.collectAsStateWithLifecycle()
    val lastWeek by viewModel.lastWeek.collectAsStateWithLifecycle()
    val volumeToday by viewModel.todayVolume.collectAsStateWithLifecycle()
    val setCountToday by viewModel.todaySetCount.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val meals by viewModel.todayMeals.collectAsStateWithLifecycle()
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
        else -> currentTab?.label ?: "FitTrack"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                            viewModel.startGymSession { navController.navigate(SESSION_DETAIL) }
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Séance salle") },
                    )
                }
                Tab.MEALS -> FloatingActionButton(onClick = { showMealDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter un repas")
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
                    lastWeek = lastWeek,
                    volumeToday = volumeToday,
                    setCountToday = setCountToday,
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
                )
            }
            composable(Tab.MEALS.route) {
                MealsScreen(meals = meals, onDelete = viewModel::deleteMeal)
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
                    onSave = { weight, goal ->
                        viewModel.setWeight(weight)
                        viewModel.setDailyCalorieGoal(goal)
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
            onConfirm = { name, calories, mealType ->
                viewModel.addMeal(name, calories, mealType)
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
