package app.f1pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.Forecast
import app.f1pro.data.Race
import app.f1pro.data.Sky
import app.f1pro.data.WeatherHour
import app.f1pro.data.Wmo
import java.time.format.DateTimeFormatter
import java.util.Locale

fun skyIcon(sky: Sky): Int = when (sky) {
    Sky.CLEAR -> R.drawable.lc_sun
    Sky.PARTLY -> R.drawable.lc_cloud_sun
    Sky.CLOUDY -> R.drawable.lc_cloud
    Sky.FOG -> R.drawable.lc_cloud_fog
    Sky.DRIZZLE -> R.drawable.lc_cloud_drizzle
    Sky.RAIN -> R.drawable.lc_cloud_rain
    Sky.SNOW -> R.drawable.lc_cloud_snow
    Sky.STORM -> R.drawable.lc_cloud_lightning
}

fun skyColor(sky: Sky): Color = when (sky) {
    Sky.CLEAR -> C.Warning
    Sky.PARTLY -> C.Warning.copy(alpha = 0.85f)
    Sky.DRIZZLE, Sky.RAIN, Sky.STORM -> C.Highlight
    else -> C.TextSecondary
}

@Composable
fun SkyIcon(sky: Sky, size: Dp) {
    Ic(skyIcon(sky), skyColor(sky), size)
}

/** "☁ 24° · 10%" for one session row. Rain chance shown when it's worth knowing. */
@Composable
fun WeatherChip(state: AppState, hour: WeatherHour) {
    val wet = Wmo.wet(hour.sky) || (hour.rainChance ?: 0) >= 40
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SkyIcon(hour.sky, 14.dp)
        Text(state.temp(hour.tempC), style = inter(12.sp, FontWeight.SemiBold, C.Text))
        hour.rainChance?.takeIf { it >= 10 }?.let {
            Text("$it%", style = inter(11.sp, FontWeight.SemiBold, if (wet) C.Highlight else C.TextMuted))
        }
    }
}

/** Fri / Sat / Sun at the track: sky, high and low, chance of rain. Race day is outlined. */
@Composable
fun WeatherCard(state: AppState, race: Race, forecast: Forecast) {
    val dates = race.sessions.filter { !it.tbc }.map { forecast.localDate(it.start) }.distinct()
    val days = dates.mapNotNull { forecast.day(it) }
    if (days.isEmpty()) return
    val raceDay = race.raceSession?.let { forecast.localDate(it.start) }
    val dayF = DateTimeFormatter.ofPattern("EEE d", Locale.US)

    Column(
        Modifier.fillMaxWidth().glassCard().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.lc_cloud, C.Highlight, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text("WEEKEND AT THE TRACK", style = SectionLabel)
            Spacer(Modifier.weight(1f))
            Text("Updated ${Fmt.ago(forecast.fetchedAt, System.currentTimeMillis())}", style = inter(10.sp, color = C.TextMuted))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in days) {
                val isRaceDay = d.date == raceDay
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (isRaceDay) C.Highlight.copy(alpha = 0.08f) else C.SurfaceLight)
                        .border(1.dp, if (isRaceDay) C.Highlight.copy(alpha = 0.35f) else Color.Transparent, shape)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(dayF.format(d.date).uppercase(), style = inter(11.sp, FontWeight.SemiBold, C.TextSecondary, 0.04f))
                    SkyIcon(d.sky, 26.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.temp(d.maxC), style = inter(14.sp, FontWeight.Bold, C.Text))
                        Spacer(Modifier.width(4.dp))
                        Text(state.temp(d.minC), style = inter(12.sp, FontWeight.Medium, C.TextMuted))
                    }
                    val rain = d.rainChance
                    Text(
                        if (rain == null) Wmo.label(d.sky) else "Rain $rain%",
                        style = inter(11.sp, if ((rain ?: 0) >= 40) FontWeight.SemiBold else FontWeight.Normal,
                            if ((rain ?: 0) >= 40) C.Highlight else C.TextMuted),
                        maxLines = 1,
                    )
                }
            }
        }
        Text("Days in track time · forecast by Open-Meteo", style = inter(10.sp, color = C.TextMuted))
    }
}
