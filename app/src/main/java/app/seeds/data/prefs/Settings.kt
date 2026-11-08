package app.seeds.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.seeds.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    // Daily rhythm, minutes after midnight
    val wakeMin: Int = 6 * 60,
    val sleepMin: Int = 22 * 60 + 30,
    val workStartMin: Int = 8 * 60,
    val workEndMin: Int = 17 * 60,
    val workDays: Int = 0b0011111,
    // Honest tracking
    val checkInMin: Int = 45,
    val autoTrim: Boolean = true,
    val graceMin: Int = 15,
    // Drift guard
    val driftEnabled: Boolean = true,
    val driftThresholdMin: Int = 5,
    val distractingApps: Set<String> = DEFAULT_DISTRACTING,
    // Money
    val moneyEnabled: Boolean = true,
    val momoSenders: Set<String> = DEFAULT_SENDERS,
    val currency: String = "RWF",
    // Rituals
    val morningBrief: Boolean = true,
    val nightReview: Boolean = true,
    // App
    val theme: Int = THEME_SYSTEM,
    val autoUpdate: Boolean = true,
    val githubRepo: String = BuildConfig.GITHUB_REPO,
    val lastUpdateCheck: Long = 0,
    val notifiedVersion: String = "",
) {
    fun isWorkDay(dayOfWeekIso: Int) = workDays and (1 shl (dayOfWeekIso - 1)) != 0

    fun isWorkTime(t: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val z = Instant.ofEpochMilli(t).atZone(zone)
        if (!isWorkDay(z.dayOfWeek.value)) return false
        val m = z.hour * 60 + z.minute
        return if (workStartMin <= workEndMin) m in workStartMin until workEndMin
        else m >= workStartMin || m < workEndMin
    }

    fun isAsleep(t: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val z = Instant.ofEpochMilli(t).atZone(zone)
        val m = z.hour * 60 + z.minute
        return if (sleepMin <= wakeMin) m in sleepMin until wakeMin
        else m >= sleepMin || m < wakeMin
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        val DEFAULT_DISTRACTING = setOf(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",   // TikTok
            "com.instagram.android", "com.facebook.katana", "com.facebook.lite",
            "com.twitter.android", "com.google.android.youtube", "com.snapchat.android",
            "com.reddit.frontpage", "com.netflix.mediaclient", "com.instagram.barcelona",
        )
        /** MTN MoMo & Airtel Money sender IDs seen in Rwanda; editable in settings. */
        val DEFAULT_SENDERS = setOf("M-Money", "MTN", "MoMo", "MTNMoMo", "AirtelMoney", "Airtel")
    }
}

private val Context.dataStore by preferencesDataStore(name = "seeds_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val wake = intPreferencesKey("wake")
        val sleep = intPreferencesKey("sleep")
        val workStart = intPreferencesKey("work_start")
        val workEnd = intPreferencesKey("work_end")
        val workDays = intPreferencesKey("work_days")
        val checkIn = intPreferencesKey("check_in")
        val autoTrim = booleanPreferencesKey("auto_trim")
        val grace = intPreferencesKey("grace")
        val driftEnabled = booleanPreferencesKey("drift_enabled")
        val driftThreshold = intPreferencesKey("drift_threshold")
        val distracting = stringSetPreferencesKey("distracting")
        val moneyEnabled = booleanPreferencesKey("money_enabled")
        val senders = stringSetPreferencesKey("senders")
        val currency = stringPreferencesKey("currency")
        val morning = booleanPreferencesKey("morning")
        val night = booleanPreferencesKey("night")
        val theme = intPreferencesKey("theme")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val repo = stringPreferencesKey("repo")
        val lastCheck = longPreferencesKey("last_check")
        val notifiedVersion = stringPreferencesKey("notified_version")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name
            p[K.wake] = s.wakeMin
            p[K.sleep] = s.sleepMin
            p[K.workStart] = s.workStartMin
            p[K.workEnd] = s.workEndMin
            p[K.workDays] = s.workDays
            p[K.checkIn] = s.checkInMin
            p[K.autoTrim] = s.autoTrim
            p[K.grace] = s.graceMin
            p[K.driftEnabled] = s.driftEnabled
            p[K.driftThreshold] = s.driftThresholdMin
            p[K.distracting] = s.distractingApps
            p[K.moneyEnabled] = s.moneyEnabled
            p[K.senders] = s.momoSenders
            p[K.currency] = s.currency
            p[K.morning] = s.morningBrief
            p[K.night] = s.nightReview
            p[K.theme] = s.theme
            p[K.autoUpdate] = s.autoUpdate
            if (s.githubRepo != BuildConfig.GITHUB_REPO) p[K.repo] = s.githubRepo else p.remove(K.repo)
            p[K.lastCheck] = s.lastUpdateCheck
            p[K.notifiedVersion] = s.notifiedVersion
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            onboarded = this[K.onboarded] ?: d.onboarded,
            name = this[K.name] ?: d.name,
            wakeMin = this[K.wake] ?: d.wakeMin,
            sleepMin = this[K.sleep] ?: d.sleepMin,
            workStartMin = this[K.workStart] ?: d.workStartMin,
            workEndMin = this[K.workEnd] ?: d.workEndMin,
            workDays = this[K.workDays] ?: d.workDays,
            checkInMin = this[K.checkIn] ?: d.checkInMin,
            autoTrim = this[K.autoTrim] ?: d.autoTrim,
            graceMin = this[K.grace] ?: d.graceMin,
            driftEnabled = this[K.driftEnabled] ?: d.driftEnabled,
            driftThresholdMin = this[K.driftThreshold] ?: d.driftThresholdMin,
            distractingApps = this[K.distracting] ?: d.distractingApps,
            moneyEnabled = this[K.moneyEnabled] ?: d.moneyEnabled,
            momoSenders = this[K.senders] ?: d.momoSenders,
            currency = this[K.currency] ?: d.currency,
            morningBrief = this[K.morning] ?: d.morningBrief,
            nightReview = this[K.night] ?: d.nightReview,
            theme = this[K.theme] ?: d.theme,
            autoUpdate = this[K.autoUpdate] ?: d.autoUpdate,
            githubRepo = this[K.repo] ?: d.githubRepo,
            lastUpdateCheck = this[K.lastCheck] ?: d.lastUpdateCheck,
            notifiedVersion = this[K.notifiedVersion] ?: d.notifiedVersion,
        )
    }
}
