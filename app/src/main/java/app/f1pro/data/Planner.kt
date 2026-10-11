package app.f1pro.data

/** A session together with the weekend it belongs to. */
data class Slot(val race: Race, val session: Session) {
    val key: String get() = "${race.key}-${session.name}"
}

/** Pure scheduling logic: which season to show, what's next, and when the app next needs to wake. */
object Planner {

    /** The season to show: the first one not finished yet, or else the latest. */
    fun currentSeason(seasons: List<SeasonCalendar>, now: Long): SeasonCalendar? {
        val sorted = seasons.filter { it.races.isNotEmpty() }.sortedBy { it.year }
        return sorted.firstOrNull { s -> s.races.any { it.endsAt > now } } ?: sorted.lastOrNull()
    }

    fun slots(seasons: List<SeasonCalendar>): List<Slot> =
        seasons.sortedBy { it.year }
            .flatMap { s -> s.races.flatMap { r -> r.sessions.map { Slot(r, it) } } }
            .sortedBy { it.session.start }

    /** The session on track right now, if any. */
    fun live(seasons: List<SeasonCalendar>, now: Long): Slot? =
        slots(seasons).firstOrNull { it.session.isLive(now) }

    /** The next session to start (with a known time). */
    fun next(seasons: List<SeasonCalendar>, now: Long): Slot? =
        slots(seasons).firstOrNull { !it.session.tbc && it.session.start > now }

    /** The weekend to show in the weekend widget: the one under way, or else the next one. */
    fun weekend(seasons: List<SeasonCalendar>, now: Long): Race? {
        val races = seasons.sortedBy { it.year }.flatMap { it.races }.sortedBy { it.first }
        return races.firstOrNull { it.status(now) == RaceStatus.ACTIVE }
            ?: races.firstOrNull { it.status(now) == RaceStatus.UPCOMING }
    }

    /**
     * Race, sprint and qualifying sessions that ended in the last [windowMs]: the ones whose
     * results we still want to report.
     */
    fun finishedForReport(seasons: List<SeasonCalendar>, now: Long, windowMs: Long = 12 * HOUR_MS): List<Slot> =
        slots(seasons).filter { s ->
            !s.session.tbc && s.session.name in Report.SESSIONS && !s.race.isTesting &&
                s.session.end <= now && s.session.end > now - windowMs
        }

    /** Reminders due now that haven't been sent yet. */
    fun dueReminders(
        seasons: List<SeasonCalendar>,
        now: Long,
        leadMs: Long,
        which: String,
        sent: Set<String>,
        bells: Set<String> = emptySet(),
        alertsOn: Boolean = true,
    ): List<Slot> = slots(seasons).filter { s ->
        val st = s.session.start
        !s.session.tbc && ((alertsOn && Sessions.wanted(s.session.name, which)) || s.key in bells) &&
            st - leadMs <= now + 30_000 && st > now - 10 * MIN_MS && s.key !in sent
    }

    /**
     * The next moment something changes: a reminder is due, a session starts or ends, or the
     * widget's whole-day countdown ticks over. Null when nothing is left on any calendar.
     */
    fun nextWake(
        seasons: List<SeasonCalendar>,
        now: Long,
        alerts: Boolean,
        leadMs: Long,
        which: String,
        bells: Set<String> = emptySet(),
    ): Long? {
        var best = Long.MAX_VALUE
        val all = slots(seasons).filter { !it.session.tbc }
        for (s in all) {
            val st = s.session.start
            if (((alerts && Sessions.wanted(s.session.name, which)) || s.key in bells) && st - leadMs > now) best = minOf(best, st - leadMs)
            if (st > now) best = minOf(best, st)
            if (s.session.end > now) best = minOf(best, s.session.end + 1000)
        }
        all.firstOrNull { it.session.start > now }?.let { n ->
            val rem = n.session.start - now
            val days = rem / DAY_MS
            if (days >= 1) {
                val t = n.session.start - days * DAY_MS
                if (t > now) best = minOf(best, t)
            }
        }
        return best.takeIf { it != Long.MAX_VALUE }
    }
}
