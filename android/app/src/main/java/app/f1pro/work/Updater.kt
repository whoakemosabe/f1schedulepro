package app.f1pro.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.f1pro.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks this repo's GitHub Releases for a newer f1pro.apk, downloads it and hands it to the
 * system installer. The repo is public, so no token is needed (same as Afli and Ljós).
 */
object Updater {
    const val REPO = "whoakemosabe/f1schedulepro"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"

    data class Release(val version: String, val assetUrl: String, val sizeBytes: Long)

    sealed class Check {
        data object UpToDate : Check()
        data class Available(val release: Release) : Check()
        data class Failed(val message: String) : Check()
    }

    fun installedVersion(context: Context): String =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "0"

    /** "v2.0.12" / "2.0.12" → [2, 0, 12]. */
    fun parts(v: String): List<Int> = v.trim().removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val c = open("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404 || code == 401 || code == 403) {
                c.disconnect()
                return@withContext Check.Failed("GitHub can't see the releases (is the repo public?).")
            }
            if (code !in 200..299) throw IOException("GitHub returned $code")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) asset = a
            }
            val apk = asset ?: return@withContext Check.Failed("The latest release has no APK yet.")
            if (!isNewer(tag, installedVersion(context))) return@withContext Check.UpToDate
            Check.Available(Release(tag.removePrefix("v"), apk.getString("browser_download_url"), apk.optLong("size")))
        } catch (e: Exception) {
            Check.Failed("Couldn't reach GitHub.")
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1. */
    suspend fun download(context: Context, release: Release, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // GitHub answers with redirects to signed storage; follow them by hand (cross-host).
            var conn = open(release.assetUrl, "application/octet-stream").apply { instanceFollowRedirects = false }
            var hops = 0
            while (conn.responseCode in 300..399 && hops < 5) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Download link missing")
                conn.disconnect()
                hops++
                conn = open(next, "application/octet-stream").apply { instanceFollowRedirects = false }
            }
            if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "f1pro.apk")
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            conn.disconnect()
            out
        }

    /** True if the installer opened; false if the app first needs "install unknown apps". */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "F1Pro-updater")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        return c
    }
}

/** Where the one-tap update is: shared by the top banner and Settings → Updates. */
sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Downloading(val version: String) : UpdateUi()
    data class Ready(val file: File, val version: String, val note: String? = null) : UpdateUi()
    data class Error(val message: String) : UpdateUi()
}

/**
 * Notices new releases on its own, like Afli: the background refresh asks GitHub about every
 * 6 hours and posts one quiet notification per version; opening the app asks at most every
 * 10 minutes, and pulling down on the schedule always asks. A waiting version shows as the
 * banner across the top; tapping it downloads, tapping again installs.
 */
object UpdateWatch {
    private const val BACKGROUND_EVERY = 6 * 60 * 60_000L
    private const val OPEN_EVERY = 10 * 60_000L

    /** The newer version waiting, if any. */
    var waitingVersion by mutableStateOf<String?>(null)
        private set

    /** The version whose banner was closed with ×. */
    var bannerHidden by mutableStateOf<String?>(null)

    /** Set when the app should open Settings → Updates (e.g. from the notification). */
    val openUpdates = mutableStateOf(false)

    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
    var progress by mutableFloatStateOf(0f)

    fun waiting(context: Context): String? {
        val v = Prefs(context).availableVersion
        return v.takeIf { it.isNotBlank() && Updater.isNewer(it, Updater.installedVersion(context)) }
    }

    fun load(context: Context) {
        waitingVersion = waiting(context)
    }

    /** Asks GitHub if it's been long enough (or [force]); returns the newer version waiting. */
    suspend fun check(context: Context, background: Boolean, force: Boolean = false): String? {
        val p = Prefs(context)
        val now = System.currentTimeMillis()
        val every = if (background) BACKGROUND_EVERY else OPEN_EVERY
        if (force || now - p.lastUpdateCheck >= every) {
            p.lastUpdateCheck = now
            when (val r = Updater.check(context)) {
                is Updater.Check.Available -> p.availableVersion = r.release.version
                Updater.Check.UpToDate -> p.availableVersion = ""
                is Updater.Check.Failed -> {}
            }
        }
        val w = waiting(context)
        waitingVersion = w
        return w
    }

    /** Background: check, and notify once per new version. */
    suspend fun checkAndNotify(context: Context) {
        val v = check(context, background = true) ?: return
        val p = Prefs(context)
        if (p.notifiedVersion == v) return
        p.notifiedVersion = v
        Alerts.postUpdate(context, v)
    }

    /**
     * One tap: check → download → ready; a tap when ready installs. Returns true when the
     * download finished (for a confirm haptic).
     */
    suspend fun tap(context: Context): Boolean {
        when (val st = ui) {
            is UpdateUi.Ready -> {
                if (!Updater.install(context, st.file)) ui = st.copy(note = "Allow F1 Pro to install updates, then tap again.")
                return false
            }
            is UpdateUi.Checking, is UpdateUi.Downloading -> return false
            else -> {}
        }
        ui = UpdateUi.Checking
        return when (val r = Updater.check(context)) {
            is Updater.Check.Available -> {
                Prefs(context).availableVersion = r.release.version
                waitingVersion = waiting(context)
                ui = UpdateUi.Downloading(r.release.version)
                progress = 0f
                try {
                    val f = Updater.download(context, r.release) { progress = it }
                    ui = UpdateUi.Ready(f, r.release.version)
                    true
                } catch (e: Exception) {
                    ui = UpdateUi.Error("Download failed. Tap to try again.")
                    false
                }
            }
            Updater.Check.UpToDate -> {
                Prefs(context).availableVersion = ""
                waitingVersion = null
                ui = UpdateUi.UpToDate
                false
            }
            is Updater.Check.Failed -> {
                ui = UpdateUi.Error(r.message)
                false
            }
        }
    }
}
