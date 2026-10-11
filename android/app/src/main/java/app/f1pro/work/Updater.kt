package app.f1pro.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
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
 * system installer. The repo is private, so this needs a read-only token pasted in Settings;
 * without one, Settings offers the releases page instead.
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

    suspend fun check(context: Context, token: String): Check = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Check.Failed("Add a GitHub token to check from the app.")
        try {
            val c = open("https://api.github.com/repos/$REPO/releases/latest", token, "application/vnd.github+json")
            val code = c.responseCode
            if (code == 401 || code == 403) { c.disconnect(); return@withContext Check.Failed("GitHub didn't accept the token.") }
            if (code == 404) { c.disconnect(); return@withContext Check.Failed("No release found (or the token can't see the repo).") }
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
            Check.Available(Release(tag.removePrefix("v"), apk.getString("url"), apk.optLong("size")))
        } catch (e: Exception) {
            Check.Failed("Couldn't reach GitHub.")
        }
    }

    /** Downloads the APK into the app cache, reporting progress 0..1. */
    suspend fun download(context: Context, release: Release, token: String, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // The asset API answers with a redirect to signed storage, fetched without the token.
            val first = open(release.assetUrl, token, "application/octet-stream")
            first.instanceFollowRedirects = false
            val conn = if (first.responseCode in 300..399) {
                val location = first.getHeaderField("Location") ?: throw IOException("Download link missing")
                first.disconnect()
                open(location, "", "application/octet-stream")
            } else {
                first
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

    private fun open(url: String, token: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "F1Pro-updater")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        return c
    }
}

/** Background checks (at most every 6 hours) and one quiet notification per new version. */
object UpdateWatch {
    private const val EVERY = 6 * 60 * 60_000L

    /** Set when the app should open Settings → Updates (e.g. from the notification). */
    val openUpdates = mutableStateOf(false)

    fun waiting(context: Context): String? {
        val v = Prefs(context).availableVersion
        return v.takeIf { it.isNotBlank() && Updater.isNewer(it, Updater.installedVersion(context)) }
    }

    suspend fun check(context: Context, force: Boolean): Updater.Check? {
        val p = Prefs(context)
        val now = System.currentTimeMillis()
        if (p.githubToken.isBlank()) return null
        if (!force && now - p.lastUpdateCheck < EVERY) return null
        p.lastUpdateCheck = now
        val r = Updater.check(context, p.githubToken)
        when (r) {
            is Updater.Check.Available -> p.availableVersion = r.release.version
            Updater.Check.UpToDate -> p.availableVersion = ""
            is Updater.Check.Failed -> {}
        }
        return r
    }

    suspend fun checkAndNotify(context: Context) {
        check(context, force = false)
        val v = waiting(context) ?: return
        val p = Prefs(context)
        if (p.notifiedVersion == v) return
        p.notifiedVersion = v
        Alerts.postUpdate(context, v)
    }
}
