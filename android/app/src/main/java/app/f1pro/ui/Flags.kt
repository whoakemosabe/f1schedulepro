package app.f1pro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.R

/** Bundled 3:2 flag images (country-flag-icons, MIT), like the PWA's flag pictures. Generated. */
object Flags {
    fun res(code: String): Int? = when (code.lowercase()) {
        "ae" -> R.drawable.flag_ae
        "ar" -> R.drawable.flag_ar
        "at" -> R.drawable.flag_at
        "au" -> R.drawable.flag_au
        "az" -> R.drawable.flag_az
        "be" -> R.drawable.flag_be
        "bh" -> R.drawable.flag_bh
        "br" -> R.drawable.flag_br
        "ca" -> R.drawable.flag_ca
        "ch" -> R.drawable.flag_ch
        "cn" -> R.drawable.flag_cn
        "co" -> R.drawable.flag_co
        "cz" -> R.drawable.flag_cz
        "de" -> R.drawable.flag_de
        "dk" -> R.drawable.flag_dk
        "ee" -> R.drawable.flag_ee
        "es" -> R.drawable.flag_es
        "fi" -> R.drawable.flag_fi
        "fr" -> R.drawable.flag_fr
        "gb" -> R.drawable.flag_gb
        "hu" -> R.drawable.flag_hu
        "id" -> R.drawable.flag_id
        "ie" -> R.drawable.flag_ie
        "il" -> R.drawable.flag_il
        "in" -> R.drawable.flag_in
        "it" -> R.drawable.flag_it
        "jp" -> R.drawable.flag_jp
        "kr" -> R.drawable.flag_kr
        "ma" -> R.drawable.flag_ma
        "mc" -> R.drawable.flag_mc
        "mx" -> R.drawable.flag_mx
        "my" -> R.drawable.flag_my
        "nl" -> R.drawable.flag_nl
        "nz" -> R.drawable.flag_nz
        "pl" -> R.drawable.flag_pl
        "pt" -> R.drawable.flag_pt
        "qa" -> R.drawable.flag_qa
        "ru" -> R.drawable.flag_ru
        "rw" -> R.drawable.flag_rw
        "sa" -> R.drawable.flag_sa
        "se" -> R.drawable.flag_se
        "sg" -> R.drawable.flag_sg
        "th" -> R.drawable.flag_th
        "tr" -> R.drawable.flag_tr
        "ua" -> R.drawable.flag_ua
        "us" -> R.drawable.flag_us
        "ve" -> R.drawable.flag_ve
        "vn" -> R.drawable.flag_vn
        "za" -> R.drawable.flag_za
        else -> null
    }
}

/** A rounded rectangular flag; a chequered flag when the country isn't known. */
@Composable
fun Flag(code: String, width: Dp, height: Dp = width * 2 / 3, radius: Dp = 3.dp) {
    val id = Flags.res(code)
    val shape = RoundedCornerShape(radius)
    if (id == null) {
        Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
            Text("\uD83C\uDFC1", style = inter((height.value * 0.8f).sp))
        }
        return
    }
    Image(
        painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width, height).clip(shape).border(0.5.dp, C.GlassBorder, shape),
    )
}
