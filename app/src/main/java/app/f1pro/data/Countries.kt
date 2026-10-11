package app.f1pro.data

/** Country and nationality names (from the calendar and standings feeds) to ISO codes and flags. */
object Countries {
    private val CODES = mapOf(
        "Australia" to "au", "Austria" to "at", "Austrian" to "at", "Azerbaijan" to "az",
        "Bahrain" to "bh", "Belgium" to "be", "Belgian" to "be", "Brazil" to "br", "Brazilian" to "br",
        "British" to "gb", "UK" to "gb", "United Kingdom" to "gb", "Great Britain" to "gb",
        "Canada" to "ca", "Canadian" to "ca", "China" to "cn", "Chinese" to "cn",
        "Danish" to "dk", "Denmark" to "dk", "Dutch" to "nl", "Netherlands" to "nl",
        "Finland" to "fi", "Finnish" to "fi", "France" to "fr", "French" to "fr",
        "German" to "de", "Germany" to "de", "Hungary" to "hu", "Hungarian" to "hu",
        "India" to "in", "Indian" to "in", "Italy" to "it", "Italian" to "it",
        "Japan" to "jp", "Japanese" to "jp", "Malaysia" to "my", "Malaysian" to "my",
        "Mexico" to "mx", "Mexican" to "mx", "Monaco" to "mc", "Monegasque" to "mc",
        "New Zealand" to "nz", "New Zealander" to "nz", "Portugal" to "pt", "Portuguese" to "pt",
        "Qatar" to "qa", "Saudi Arabia" to "sa", "Singapore" to "sg", "Spain" to "es", "Spanish" to "es",
        "Swedish" to "se", "Sweden" to "se", "Swiss" to "ch", "Switzerland" to "ch",
        "Thailand" to "th", "Thai" to "th", "Turkey" to "tr", "Turkish" to "tr",
        "UAE" to "ae", "United Arab Emirates" to "ae", "Emirati" to "ae",
        "USA" to "us", "United States" to "us", "American" to "us",
        "Argentina" to "ar", "Argentinian" to "ar", "Argentine" to "ar", "Colombian" to "co",
        "Czech" to "cz", "Indonesian" to "id", "Irish" to "ie", "Polish" to "pl", "Russian" to "ru",
        "Russia" to "ru", "South Africa" to "za", "South African" to "za", "Korea" to "kr",
        "South Korean" to "kr", "Ukrainian" to "ua", "Venezuelan" to "ve", "Vietnam" to "vn",
        "Morocco" to "ma", "Rwanda" to "rw", "Estonian" to "ee", "Israeli" to "il",
    )

    fun code(name: String): String = CODES[name.trim()] ?: "un"

    /** Emoji flag from a two-letter code; a chequered flag when unknown. */
    fun flag(code: String): String {
        if (code.length != 2 || code == "un") return "🏁"
        val up = code.uppercase()
        val a = Character.toChars(0x1F1E6 + (up[0] - 'A'))
        val b = Character.toChars(0x1F1E6 + (up[1] - 'A'))
        return String(a) + String(b)
    }
}

object Teams {
    private val COLORS = mapOf(
        "red_bull" to 0xFF3671C6, "ferrari" to 0xFFE8002D, "mercedes" to 0xFF27F4D2,
        "mclaren" to 0xFFFF8000, "aston_martin" to 0xFF229971, "alpine" to 0xFFFF87BC,
        "williams" to 0xFF64C4FF, "sauber" to 0xFF52E252, "haas" to 0xFFB6BABD, "rb" to 0xFF6692FF,
        "audi" to 0xFFBB0A30, "cadillac" to 0xFFC9B37E,
    )
    private val SLUGS = mapOf(
        "red_bull" to "red-bull-racing", "ferrari" to "ferrari", "mercedes" to "mercedes",
        "mclaren" to "mclaren", "aston_martin" to "aston-martin", "alpine" to "alpine",
        "williams" to "williams", "sauber" to "kick-sauber", "haas" to "haas", "rb" to "racing-bulls",
        "audi" to "audi", "cadillac" to "cadillac",
    )

    /**
     * The two feeds (Jolpica's constructorId, f1api's teamId) have named some teams differently
     * over the years. Every id read from either goes through here, so "racing_bulls" and "rb"
     * are one team. Only spellings of the same team: Sauber and Audi stay apart (different seasons).
     */
    private val ALIASES = mapOf(
        "racing_bulls" to "rb", "visa_rb" to "rb", "rb_f1_team" to "rb", "visa_cash_app_rb" to "rb",
        "redbull" to "red_bull", "red_bull_racing" to "red_bull",
        "kick_sauber" to "sauber", "stake" to "sauber", "stake_f1_team" to "sauber",
        "aston" to "aston_martin", "astonmartin" to "aston_martin",
        "haas_f1_team" to "haas", "alpine_f1_team" to "alpine", "bwt_alpine" to "alpine",
        "audi_f1_team" to "audi", "audi_revolut" to "audi",
        "cadillac_f1_team" to "cadillac", "cadillac_formula_1_team" to "cadillac",
        "mercedes_amg" to "mercedes", "scuderia_ferrari" to "ferrari",
    )

    /** One id per team whichever feed it came from ("Racing-Bulls" → "rb"). */
    fun canonical(id: String): String {
        val k = id.trim().lowercase().replace('-', '_').replace(' ', '_')
        return ALIASES[k] ?: k
    }

    /** Ids this app knows (for the release check's mismatch list). */
    val known: Set<String> get() = COLORS.keys

    /** Official broadcast colours from OpenF1, when fetched; they win over the built-in ones. */
    @Volatile var live: Map<String, Long> = emptyMap()

    fun color(teamId: String): Long = live[teamId] ?: COLORS[teamId] ?: 0xFF4DB8FF

    /** OpenF1's team names → our team ids ("Racing Bulls" before "Red Bull": both have "bull"). */
    fun idFor(teamName: String): String? {
        val n = teamName.lowercase()
        return when {
            "racing bulls" in n || n == "rb" || "visa" in n || "alphatauri" in n -> "rb"
            "red bull" in n -> "red_bull"
            "mercedes" in n -> "mercedes"
            "ferrari" in n -> "ferrari"
            "mclaren" in n -> "mclaren"
            "aston" in n -> "aston_martin"
            "alpine" in n -> "alpine"
            "williams" in n -> "williams"
            "haas" in n -> "haas"
            "audi" in n -> "audi"
            "sauber" in n || "kick" in n -> "sauber"
            "cadillac" in n -> "cadillac"
            else -> null
        }
    }

    fun url(teamId: String): String? = SLUGS[teamId]?.let { "https://www.formula1.com/en/teams/$it" }

    fun driverUrl(name: String, surname: String): String {
        val slug = "$name-$surname".lowercase().trim()
            .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), "-")
        return "https://www.formula1.com/en/drivers/$slug"
    }

    /** "Mercedes Formula 1 Team" → "Mercedes", for tight spaces. */
    fun shortName(teamName: String): String = teamName
        .replace(Regex("\\b(Formula 1 Team|F1 Team|Racing|Team|Scuderia)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { teamName }
}
