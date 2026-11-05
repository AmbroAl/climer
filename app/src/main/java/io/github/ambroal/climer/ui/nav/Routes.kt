package io.github.ambroal.climer.ui.nav

import kotlinx.serialization.Serializable

// Gli id a 0 indicano "nuovo elemento".

@Serializable
object TimersRoute

@Serializable
data class TimerEditorRoute(val id: Long = 0)

@Serializable
object RunningRoute

@Serializable
object StopwatchRoute

@Serializable
object ProgramsRoute

@Serializable
data class ProgramDetailRoute(val id: Long)

@Serializable
data class SessionRoute(val sessionId: Long)

@Serializable
data class BlockEditorRoute(val sessionId: Long, val blockId: Long = 0)

@Serializable
object MeasuresRoute

@Serializable
data class MeasureDetailRoute(val typeId: Long)

@Serializable
data class MeasureTypeEditorRoute(val id: Long = 0)

@Serializable
object HistoryRoute

@Serializable
data class WorkoutDetailRoute(val id: Long)

@Serializable
data class ResultsRoute(val workoutId: Long)

@Serializable
object SettingsRoute

@Serializable
object AudioSettingsRoute
