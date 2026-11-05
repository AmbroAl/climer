package io.github.ambroal.climer.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Before

abstract class DbTestBase {
    protected lateinit var db: AppDatabase

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    // Nessuna chiusura dopo il test: i ViewModel dei test osservano ancora il database e, chiudendolo,
    // un'ultima emissione fallirebbe venendo attribuita al test successivo. Ogni test ne crea uno nuovo in memoria.
}
