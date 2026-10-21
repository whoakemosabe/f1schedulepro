package app.f1pro

import android.app.Application

/**
 * Runs first in every process start, including the ones with no screen: alarms, background
 * refreshes, widgets and the quick settings tile. The crash recorder goes here so a crash in
 * any of those is still shown on the next launch, not just crashes while the app is open.
 */
class F1ProApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
