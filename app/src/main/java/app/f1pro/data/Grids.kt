package app.f1pro.data

/** One car on a starting grid. [slot] is "P1"… or "PL" for a pit-lane start. */
data class GridEntry(
    val slot: String,
    val code: String,
    val givenName: String,
    val familyName: String,
    val constructorId: String,
    val constructorName: String,
)

/** Which grid it is, and so how sure we can be of it. */
enum class GridKind { Official, Provisional, Sprint }

data class StartingGrid(val kind: GridKind, val cars: List<GridEntry>)

/**
 * The grids a weekend has. The race results carry the grid the race actually started from
 * (penalties and pit-lane starts included), so once they're out they win over qualifying,
 * which is only the grid as it stood on Saturday.
 */
object Grids {
    fun fromResults(results: List<RaceResult>): List<GridEntry> {
        if (results.none { (it.grid ?: 0) > 0 }) return emptyList()
        val front = results.filter { (it.grid ?: 0) > 0 }.sortedBy { it.grid }
        val pitLane = results.filter { (it.grid ?: 0) <= 0 }
        return front.map { it.toEntry("P${it.grid}") } + pitLane.map { it.toEntry("PL") }
    }

    fun fromQuali(quali: List<QualiResult>): List<GridEntry> =
        quali.filter { it.position != null }.sortedBy { it.position }.map {
            GridEntry("P${it.position}", it.code.ifBlank { it.familyName.take(3).uppercase() }, it.givenName, it.familyName, it.constructorId, it.constructorName)
        }

    /** The race grid (official, or provisional from qualifying) and the sprint's when it ran. */
    fun of(race: List<RaceResult>, sprint: List<RaceResult>, quali: List<QualiResult>): List<StartingGrid> {
        val main = fromResults(race).takeIf { it.isNotEmpty() }?.let { StartingGrid(GridKind.Official, it) }
            ?: fromQuali(quali).takeIf { it.isNotEmpty() }?.let { StartingGrid(GridKind.Provisional, it) }
        val sp = fromResults(sprint).takeIf { it.isNotEmpty() }?.let { StartingGrid(GridKind.Sprint, it) }
        return listOfNotNull(main, sp)
    }

    private fun RaceResult.toEntry(slot: String) =
        GridEntry(slot, code.ifBlank { familyName.take(3).uppercase() }, givenName, familyName, constructorId, constructorName)
}
