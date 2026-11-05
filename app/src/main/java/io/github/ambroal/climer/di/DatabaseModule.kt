package io.github.ambroal.climer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ambroal.climer.data.db.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "timerapp.db").build()

    @Provides
    fun savedTimerDao(db: AppDatabase) = db.savedTimerDao()

    @Provides
    fun programDao(db: AppDatabase) = db.programDao()

    @Provides
    fun measureDao(db: AppDatabase) = db.measureDao()

    @Provides
    fun workoutDao(db: AppDatabase) = db.workoutDao()
}
