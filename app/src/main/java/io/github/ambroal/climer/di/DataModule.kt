package io.github.ambroal.climer.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ambroal.climer.data.settings.DataStoreSettingsRepository
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.domain.time.WallClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        DataStoreSettingsRepository(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") })

    @Provides
    fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
}
