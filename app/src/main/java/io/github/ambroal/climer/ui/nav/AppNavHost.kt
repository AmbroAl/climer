package io.github.ambroal.climer.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ambroal.climer.R
import io.github.ambroal.climer.data.db.entity.WorkoutStatus
import io.github.ambroal.climer.timer.RunMode
import io.github.ambroal.climer.ui.components.isLandscape
import io.github.ambroal.climer.ui.history.HistoryListScreen
import io.github.ambroal.climer.ui.history.ResultsScreen
import io.github.ambroal.climer.ui.history.WorkoutDetailScreen
import io.github.ambroal.climer.ui.measures.MeasureDetailScreen
import io.github.ambroal.climer.ui.measures.MeasureListScreen
import io.github.ambroal.climer.ui.measures.MeasureTypeEditorScreen
import io.github.ambroal.climer.ui.programs.BlockEditorScreen
import io.github.ambroal.climer.ui.programs.ProgramDetailScreen
import io.github.ambroal.climer.ui.programs.ProgramListScreen
import io.github.ambroal.climer.ui.running.RunningScreen
import io.github.ambroal.climer.ui.session.SessionScreen
import io.github.ambroal.climer.ui.settings.AudioSettingsScreen
import io.github.ambroal.climer.ui.settings.BackupSection
import io.github.ambroal.climer.ui.settings.SettingsScreen
import io.github.ambroal.climer.ui.stopwatch.StopwatchScreen
import io.github.ambroal.climer.ui.timers.TimerEditorScreen
import io.github.ambroal.climer.ui.timers.TimerListScreen
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(TimersRoute, TimersRoute::class, R.string.tab_timers, Icons.Default.Timer),
    TopLevel(StopwatchRoute, StopwatchRoute::class, R.string.stopwatch_tab, Icons.Default.AvTimer),
    TopLevel(ProgramsRoute, ProgramsRoute::class, R.string.tab_programs, Icons.Default.FitnessCenter),
    TopLevel(MeasuresRoute, MeasuresRoute::class, R.string.tab_measures, Icons.AutoMirrored.Filled.ShowChart),
    TopLevel(HistoryRoute, HistoryRoute::class, R.string.tab_history, Icons.Default.History)
)

@Composable
fun AppNavHost(
    openRunningRequests: Flow<Unit> = emptyFlow(),
    nav: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel()
) {
    val openRunning = {
        if (appViewModel.activeMode.value == RunMode.STOPWATCH) {
            nav.navigate(StopwatchRoute) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            nav.navigate(RunningRoute) { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) {
        if (appViewModel.onAppStart()) openRunning()
    }
    LaunchedEffect(openRunningRequests) { openRunningRequests.collect { if (appViewModel.timerActive.value) openRunning() } }
    val timerActive by appViewModel.timerActive.collectAsStateWithLifecycle()
    val activeMode by appViewModel.activeMode.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = topLevels.any { destination?.hasRoute(it.routeClass) == true }
    val onRunning = destination?.hasRoute(RunningRoute::class) == true ||
        (activeMode == RunMode.STOPWATCH && destination?.hasRoute(StopwatchRoute::class) == true)
    val goTo = { item: TopLevel ->
        nav.navigate(item.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // In orizzontale le schede vanno in una colonna a sinistra: in basso toglierebbero troppa altezza.
    val rail = showBar && isLandscape()
    Row {
        if (rail) {
            NavigationRail(Modifier.fillMaxHeight()) {
                Spacer(Modifier.weight(1f))
                topLevels.forEach { item ->
                    NavigationRailItem(
                        selected = destination?.hasRoute(item.routeClass) == true,
                        onClick = { goTo(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
        AppScaffold(
            nav,
            runningBar = timerActive && !onRunning && destination != null,
            stopwatch = activeMode == RunMode.STOPWATCH,
            onOpenRunning = openRunning,
            bottomBar = showBar && !rail,
            isSelected = { destination?.hasRoute(it.routeClass) == true },
            onSelect = goTo
        )
    }
}

@Composable
private fun AppScaffold(
    nav: NavHostController,
    runningBar: Boolean,
    stopwatch: Boolean,
    onOpenRunning: () -> Unit,
    bottomBar: Boolean,
    isSelected: (TopLevel) -> Boolean,
    onSelect: (TopLevel) -> Unit
) {
    Scaffold(
        bottomBar = {
            Column {
                if (runningBar) RunningTimerBar(stopwatch = stopwatch, onOpen = onOpenRunning)
                if (bottomBar) {
                    NavigationBar {
                        topLevels.forEach { item ->
                            NavigationBarItem(
                                selected = isSelected(item),
                                onClick = { onSelect(item) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = TimersRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable<TimersRoute> {
                TimerListScreen(
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onNew = { nav.navigate(TimerEditorRoute()) },
                    onEdit = { nav.navigate(TimerEditorRoute(it)) },
                    onStarted = onOpenRunning
                )
            }
            composable<TimerEditorRoute> { TimerEditorScreen(onClose = { nav.popBackStack() }) }
            composable<StopwatchRoute> { StopwatchScreen(onOpenSettings = { nav.navigate(SettingsRoute) }) }
            composable<RunningRoute> {
                RunningScreen(
                    onExit = { nav.popBackStack() },
                    onEditSounds = { nav.navigate(AudioSettingsRoute) }
                )
            }
            composable<AudioSettingsRoute> { AudioSettingsScreen(onBack = { nav.popBackStack() }) }
            composable<ProgramsRoute> {
                ProgramListScreen(onOpenSettings = { nav.navigate(SettingsRoute) }, onOpen = { nav.navigate(ProgramDetailRoute(it)) })
            }
            composable<ProgramDetailRoute> {
                ProgramDetailScreen(onBack = { nav.popBackStack() }, onOpenSession = { nav.navigate(SessionRoute(it)) })
            }
            composable<SessionRoute> {
                SessionScreen(
                    onBack = { nav.popBackStack() },
                    onAddBlock = { nav.navigate(BlockEditorRoute(it)) },
                    onEditBlock = { s, b -> nav.navigate(BlockEditorRoute(s, b)) },
                    onBlockStarted = onOpenRunning,
                    onFinish = { nav.navigate(ResultsRoute(it)) }
                )
            }
            composable<BlockEditorRoute> { BlockEditorScreen(onClose = { nav.popBackStack() }) }
            composable<MeasuresRoute> {
                MeasureListScreen(
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onNewType = { nav.navigate(MeasureTypeEditorRoute()) },
                    onOpen = { nav.navigate(MeasureDetailRoute(it)) }
                )
            }
            composable<MeasureTypeEditorRoute> { MeasureTypeEditorScreen(onClose = { nav.popBackStack() }) }
            composable<MeasureDetailRoute> {
                MeasureDetailScreen(onBack = { nav.popBackStack() }, onEditType = { nav.navigate(MeasureTypeEditorRoute(it)) })
            }
            composable<HistoryRoute> {
                HistoryListScreen(
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                    onOpen = { w ->
                        val sessionId = w.sourceSessionId
                        if (w.status == WorkoutStatus.IN_PROGRESS && sessionId != null) {
                            nav.navigate(SessionRoute(sessionId))
                        } else {
                            nav.navigate(WorkoutDetailRoute(w.id))
                        }
                    }
                )
            }
            composable<WorkoutDetailRoute> {
                WorkoutDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ResultsRoute(it)) })
            }
            composable<ResultsRoute> {
                ResultsScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = {
                        nav.navigate(HistoryRoute) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onAudio = { nav.navigate(AudioSettingsRoute) },
                    backupSection = { BackupSection() }
                )
            }
        }
    }
}
