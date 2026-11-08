package app.seeds

import android.app.Application
import android.content.Context
import app.seeds.data.db.SeedsDatabase
import app.seeds.data.prefs.SettingsRepository
import app.seeds.data.repo.MoneyRepository
import app.seeds.data.repo.TrackingRepository
import app.seeds.notify.Alarms
import app.seeds.notify.Notifier
import app.seeds.tracking.Effects
import app.seeds.update.UpdateManager
import app.seeds.update.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SeedsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.scope.launch {
            container.money.ensureDefaults()
            Alarms.scheduleAll(this@SeedsApp, container.settings.current())
            Effects.refresh(this@SeedsApp)
        }
        UpdateWorker.schedule(this)
    }
}

class AppContainer(app: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: SeedsDatabase = SeedsDatabase.build(app)
    val settings = SettingsRepository(app)
    val tracking = TrackingRepository(db) { Effects.refresh(app) }
    val money = MoneyRepository(db)
    val updates = UpdateManager(app, settings)
}

val Context.seeds: AppContainer get() = (applicationContext as SeedsApp).container
