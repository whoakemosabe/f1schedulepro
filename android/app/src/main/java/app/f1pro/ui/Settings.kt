package app.f1pro.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.Planner
import app.f1pro.widget.NextWidgetReceiver
import app.f1pro.widget.WeekendWidgetReceiver
import app.f1pro.work.Alerts
import app.f1pro.work.UpdateWatch
import app.f1pro.work.Updater
import kotlinx.coroutines.launch
import java.time.Year

private val ZONES = listOf(
    "Atlantic/Reykjavik" to "GMT",
    "America/New_York" to "Eastern (ET)",
    "America/Chicago" to "Central (CT)",
    "America/Denver" to "Mountain (MT)",
    "America/Los_Angeles" to "Pacific (PT)",
    "Europe/London" to "London (GMT/BST)",
    "Europe/Paris" to "Central Europe (CET)",
    "Europe/Helsinki" to "Eastern Europe (EET)",
    "Asia/Dubai" to "Dubai (GST)",
    "Asia/Kolkata" to "India (IST)",
    "Asia/Singapore" to "Singapore (SGT)",
    "Asia/Tokyo" to "Japan (JST)",
    "Australia/Sydney" to "Sydney (AEST)",
)

@Composable
private fun SettingsCard(title: String, icon: Int, iconColor: Color = C.Highlight, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glassCard(C.Highlight.copy(alpha = 0.3f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, iconColor, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = SectionLabel)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun Switch(on: Boolean, onClick: () -> Unit) {
    val x by animateDpAsState(if (on) 24.dp else 4.dp, tween(200), label = "knob")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (on) C.Accent else C.SurfaceLight)
            .border(1.dp, if (on) C.Accent else C.Border, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.offset(x = x, y = 4.dp).size(20.dp).clip(CircleShape).background(C.Text))
    }
}

@Composable
private fun Segments(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((k, label) in options) {
            Chip(label, Modifier.weight(1f), selected = k == selected) { onSelect(k) }
        }
    }
}

@Composable
private fun Small(text: String, color: Color = C.TextMuted) {
    Text(text, style = inter(11.sp, color = color).copy(lineHeight = 16.sp))
}

@Composable
private fun ActionButton(text: String, icon: Int?, color: Color = C.Highlight, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.SurfaceLight)
            .border(1.dp, C.GlassBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Ic(icon, if (enabled) color else C.TextMuted, 13.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = inter(12.sp, FontWeight.SemiBold, if (enabled) color else C.TextMuted, 0.03f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(state: AppState) {
    val context = LocalContext.current
    val fmt = LocalFmt.current
    val scope = rememberCoroutineScope()
    var updatesHighlighted by remember { mutableStateOf(false) }
    val openUpdates by UpdateWatch.openUpdates
    val scroll = rememberScrollState()
    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            UpdateWatch.openUpdates.value = false
            updatesHighlighted = true
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Time zone & clock ──
        var zoneOpen by remember { mutableStateOf(false) }
        SettingsCard("Timezone", R.drawable.lc_globe) {
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.SurfaceLight)
                        .border(1.dp, C.Border, RoundedCornerShape(8.dp))
                        .clickable { zoneOpen = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = if (state.timezone.isBlank()) "Phone's time zone" else ZONES.firstOrNull { it.first == state.timezone }?.second ?: state.timezone
                    Text(label, style = inter(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text(fmt.abbr(), style = inter(11.sp, FontWeight.Medium, C.Accent))
                    Spacer(Modifier.width(8.dp))
                    Ic(R.drawable.lc_chevron_down, C.TextMuted, 14.dp)
                }
                DropdownMenu(
                    expanded = zoneOpen,
                    onDismissRequest = { zoneOpen = false },
                    containerColor = C.Surface,
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    val options = listOf("" to "Phone's time zone") + ZONES
                    for ((id, label) in options) {
                        val active = id == state.timezone
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(label, style = inter(14.sp, FontWeight.Medium, if (active) C.Accent else C.Text))
                                    Text(if (id.isBlank()) java.time.ZoneId.systemDefault().id else id, style = inter(11.sp, color = C.TextMuted))
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (id.isBlank()) Fmt.zoneLabel(java.time.ZoneId.systemDefault().id) else Fmt.zoneLabel(id),
                                        style = inter(11.sp, color = if (active) C.Accent else C.TextSecondary),
                                    )
                                    if (active) {
                                        Spacer(Modifier.width(6.dp))
                                        Ic(R.drawable.lc_check, C.Accent, 14.dp)
                                    }
                                }
                            },
                            onClick = {
                                state.setZone(id)
                                zoneOpen = false
                            },
                        )
                    }
                }
            }
            Small("Clock")
            Segments(listOf("auto" to "Phone", "12" to "12-hour", "24" to "24-hour"), state.clock) { state.setClock(it) }
        }

        // ── Reminders ──
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                state.setAlerts(true)
                Alerts.postTest(context, null)
            }
        }
        val canNotify = Alerts.canNotify(context)
        val active = state.alertsOn && canNotify
        SettingsCard("Notifications", if (active) R.drawable.lc_bell else R.drawable.lc_bell_off, if (active) C.Highlight else C.TextMuted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Session reminders", style = inter(14.sp, FontWeight.Medium))
                    Text("Get notified before sessions start", style = inter(12.sp, color = C.TextSecondary))
                }
                Switch(active) {
                    when {
                        active -> state.setAlerts(false)
                        canNotify -> state.setAlerts(true)
                        Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> state.setAlerts(true)
                    }
                }
            }
            if (state.alertsOn && !canNotify) {
                Small("Notifications are blocked for F1 Pro. Allow them in system settings to get reminders.", C.Warning)
                ActionButton("Open notification settings", R.drawable.lc_settings) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            if (active) {
                Small("Remind me before:")
                Segments(listOf("15" to "15 min", "30" to "30 min", "60" to "1 hour"), state.leadMinutes.toString()) { state.setLead(it.toInt()) }
                Small("For:")
                Segments(listOf("all" to "All", "quali" to "Quali + races", "race" to "Races"), state.alertWhich) { state.setWhich(it) }
                ActionButton("Send a test reminder", R.drawable.lc_bell) {
                    val next = Planner.next(state.seasons, System.currentTimeMillis())
                    Alerts.postTest(context, next)
                }
                Small("Reminders are scheduled on the phone, so they arrive even when the app is closed.")
            }
        }

        // ── Widgets ──
        SettingsCard("Widgets", R.drawable.lc_timer) {
            Small("A next-session countdown (2×2) and the full race weekend (4×2). Long-press your home screen → Widgets → F1 Pro, or add one here:")
            val awm = AppWidgetManager.getInstance(context)
            if (awm.isRequestPinAppWidgetSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ActionButton("Next session", R.drawable.lc_timer) {
                            awm.requestPinAppWidget(ComponentName(context, NextWidgetReceiver::class.java), null, null)
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        ActionButton("Race weekend", R.drawable.lc_calendar) {
                            awm.requestPinAppWidget(ComponentName(context, WeekendWidgetReceiver::class.java), null, null)
                        }
                    }
                }
            }
        }

        // ── Updates ──
        UpdatesCard(state, updatesHighlighted)

        // ── About ──
        SettingsCard("About", R.drawable.lc_info, trailing = {
            Text("v" + Updater.installedVersion(context), style = inter(10.sp, color = C.TextMuted.copy(alpha = 0.6f), tracking = 0.05f))
        }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("F1 Schedule Pro", style = inter(14.sp, FontWeight.SemiBold))
                Text(" — ${state.seasonFor(System.currentTimeMillis())?.year ?: ""} Season", style = inter(14.sp, color = C.TextMuted))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (f in listOf("Auto timezone conversion", "Works offline", "Session reminders", "Live standings", "Home-screen widgets")) {
                    Text(f, style = inter(11.sp, color = C.TextSecondary))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.5f)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in listOf("Jolpica F1 API", "f1api.dev", "RACER RSS", "Autosport RSS", "Motorsport RSS", "The Race RSS")) {
                    Text(
                        s,
                        style = inter(10.sp, color = C.TextMuted, tracking = 0.03f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.SurfaceLight)
                            .border(1.dp, C.GlassBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.5f)))
            KorbenTag()
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The PWA's "Made with ♥ by Korben" credit, with its slow colour wash. */
@Composable
private fun KorbenTag() {
    val t = rememberInfiniteTransition(label = "korben")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse), label = "k")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x1FC8A0FF))
                .border(1.dp, Color(0x668A7FCA), RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRect(Color(0x998A7FCA), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                    val w = size.width
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color(0x33FF0066), Color(0x3300FFCC), Color(0x336600FF), Color(0x33FF0066)),
                            start = androidx.compose.ui.geometry.Offset(-w + 2 * w * shift, 0f),
                            end = androidx.compose.ui.geometry.Offset(w * shift + w, size.height),
                        )
                    )
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mono = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xD9FFFFFF))
            Text("Made with ", style = mono)
            Text("♥", style = mono.copy(color = Color(0xFFFF69B4)))
            Text(" by Korben // ${Year.now().value}", style = mono)
        }
    }
}

@Composable
private fun UpdatesCard(state: AppState, highlighted: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(state.prefs.githubToken) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    val waiting = UpdateWatch.waiting(context)

    SettingsCard("Updates", R.drawable.lc_download, if (highlighted || waiting != null) C.Success else C.Highlight) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Installed", style = inter(13.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
            Text("v" + Updater.installedVersion(context), style = inter(13.sp, FontWeight.SemiBold))
        }
        if (waiting != null && release == null) {
            Small("Version $waiting is out.", C.Success)
        }
        Small("New versions are published as releases on GitHub. Your repo is private, so checking from the app needs a read-only GitHub token (fine-grained, Contents: read on f1schedulepro).")
        BasicTextField(
            value = token,
            onValueChange = {
                token = it.trim()
                state.prefs.githubToken = it.trim()
            },
            singleLine = true,
            textStyle = inter(13.sp, color = C.Text),
            cursorBrush = SolidColor(C.Accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.SurfaceLight)
                        .border(1.dp, C.Border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (token.isEmpty()) Text("GitHub token (optional)", style = inter(13.sp, color = C.TextMuted))
                    inner()
                }
            },
        )
        val r = release
        when {
            progress >= 0f -> {
                Small("Downloading… ${(progress * 100).toInt()}%")
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.SurfaceLight)) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(C.Accent))
                }
            }
            r != null -> ActionButton("Download and install v${r.version}", R.drawable.lc_download, C.Success) {
                progress = 0f
                scope.launch {
                    try {
                        val f = Updater.download(context, r, state.prefs.githubToken) { progress = it }
                        progress = -1f
                        if (!Updater.install(context, f)) status = "Allow F1 Pro to install apps, then tap again."
                    } catch (e: Exception) {
                        progress = -1f
                        status = e.message ?: "Download failed"
                    }
                }
            }
            else -> ActionButton(if (checking) "Checking…" else "Check for updates", R.drawable.lc_refresh_cw, enabled = !checking && token.isNotBlank()) {
                checking = true
                status = null
                scope.launch {
                    when (val c = UpdateWatch.check(context, force = true)) {
                        is Updater.Check.Available -> release = c.release
                        Updater.Check.UpToDate -> status = "You're on the latest version."
                        is Updater.Check.Failed -> status = c.message
                        null -> status = "Add a token first."
                    }
                    checking = false
                }
            }
        }
        status?.let { Small(it, C.TextSecondary) }
        ActionButton("Open releases on GitHub", R.drawable.lc_external_link, C.TextSecondary) {
            openUrl(context, Updater.RELEASES_URL)
        }
    }
}
