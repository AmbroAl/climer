package io.github.ambroal.climer.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ambroal.climer.data.repo.WorkoutRepository
import io.github.ambroal.climer.data.repo.toActiveRun
import io.github.ambroal.climer.timer.ActiveRunStore
import io.github.ambroal.climer.timer.BlockCompletionSink
import io.github.ambroal.climer.timer.Clock
import io.github.ambroal.climer.timer.CuePlayer
import io.github.ambroal.climer.timer.CueSink
import io.github.ambroal.climer.timer.DataStoreActiveRunStore
import io.github.ambroal.climer.timer.NextBlockStarter
import io.github.ambroal.climer.timer.ServiceLauncher
import io.github.ambroal.climer.timer.SystemElapsedClock
import io.github.ambroal.climer.timer.TimerService
import io.github.ambroal.climer.ui.settings.TonePreview
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimerModule {
    @Provides
    fun clock(@ApplicationContext context: Context): Clock = SystemElapsedClock(context)

    @Provides
    fun appContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun activeRunStore(@ApplicationContext context: Context): ActiveRunStore =
        DataStoreActiveRunStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("active_run") })

    @Provides
    fun serviceLauncher(@ApplicationContext context: Context): ServiceLauncher = ServiceLauncher {
        ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
    }

    @Provides
    fun completion(workouts: WorkoutRepository): BlockCompletionSink = BlockCompletionSink { workouts.markCompleted(it) }

    @Provides
    fun cueSink(player: CuePlayer): CueSink = player

    @Provides
    fun nextBlock(workouts: WorkoutRepository): NextBlockStarter = NextBlockStarter { workoutId, blockId ->
        runCatching { workouts.startBlock(workoutId, blockId).toActiveRun(workoutId) }.getOrNull()
    }

    @Provides
    fun tonePreview(player: CuePlayer): TonePreview = TonePreview { player.preview(it) }
}
