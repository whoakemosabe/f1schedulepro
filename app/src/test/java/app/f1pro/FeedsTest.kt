package app.f1pro

import app.f1pro.data.Countries
import app.f1pro.data.Parsers
import app.f1pro.data.Teams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedsTest {
    @Test
    fun rssItemsParseAndMerge() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>Feed</title>
            <item><title>Antonelli wins &amp; extends lead</title><link>https://example.com/a</link>
              <pubDate>Sun, 11 Oct 2026 14:05:00 +0000</pubDate>
              <description><![CDATA[<p>The Mercedes driver &#8217;s <b>eighth</b> win.</p>]]></description></item>
            <item><title>Older story</title><link>https://example.com/b</link>
              <pubDate>Sat, 10 Oct 2026 09:00:00 GMT</pubDate><description>Plain</description></item>
            <item><title>No link</title></item>
            </channel></rss>"""
        val items = Parsers.rss(xml, "RACER")
        assertEquals(2, items.size)
        assertEquals("Antonelli wins & extends lead", items[0].title)
        assertEquals("The Mercedes driver ’s eighth win.", items[0].excerpt)
        assertEquals(Instant.parse("2026-10-11T14:05:00Z").toEpochMilli(), items[0].published)

        val dup = Parsers.rss(xml, "Autosport")
        val merged = Parsers.mergeNews(listOf(items, dup))
        assertEquals(2, merged.size)
        assertTrue(merged[0].published > merged[1].published)
    }

    @Test
    fun atomEntriesParse() {
        val xml = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom">
            <entry><title>Atom story</title><link rel="alternate" href="https://example.com/c"/>
            <updated>2026-10-11T10:00:00Z</updated><summary>Short</summary></entry></feed>"""
        val items = Parsers.rss(xml, "X")
        assertEquals(1, items.size)
        assertEquals("https://example.com/c", items[0].link)
    }

    @Test
    fun datesInTheFormatsFeedsUse() {
        val t = Instant.parse("2026-10-11T14:05:00Z").toEpochMilli()
        assertEquals(t, Parsers.date("Sun, 11 Oct 2026 14:05:00 +0000"))
        assertEquals(t, Parsers.date("Sun, 11 Oct 2026 14:05:00 GMT"))
        assertEquals(t, Parsers.date("2026-10-11T14:05:00Z"))
        assertEquals(t, Parsers.date("Sun, 11 Oct 2026 10:05:00 EDT"))
        assertEquals(0L, Parsers.date("whenever"))
    }

    @Test
    fun standingsParse() {
        val drivers = """{"season":2026,"drivers_championship":[
            {"classificationId":1,"driverId":"antonelli","teamId":"mercedes","points":320,"position":1,"wins":8,
             "driver":{"name":"Andrea Kimi","surname":"Antonelli","nationality":"Italy","number":12,"shortName":"ANT"},
             "team":{"teamId":"mercedes","teamName":"Mercedes Formula 1 Team"}},
            {"classificationId":2,"driverId":"russell","teamId":"mercedes","points":236.5,"position":2,"wins":3,
             "driver":{"name":"George","surname":"Russell","nationality":"Great Britain","number":63},
             "team":{"teamId":"mercedes","teamName":"Mercedes Formula 1 Team"}}]}"""
        val (season, list) = Parsers.drivers(drivers)
        assertEquals(2026, season)
        assertEquals(2, list.size)
        assertEquals("Andrea Kimi", list[0].name)
        assertEquals(236.5, list[1].points, 0.0)
        assertEquals("https://www.formula1.com/en/drivers/andrea-kimi-antonelli", list[0].url)
        val teams = Parsers.teamsFromDrivers(list)
        assertEquals(556.5, teams.single().points, 0.0)

        val cons = """{"constructors_championship":[{"teamId":"ferrari","points":405,"position":2,"wins":2,"team":{"teamName":"Scuderia Ferrari"}},
            {"teamId":"mercedes","points":556,"position":1,"wins":11,"team":{"teamName":"Mercedes Formula 1 Team"}}]}"""
        val t = Parsers.teams(cons)
        assertEquals(listOf("mercedes", "ferrari"), t.map { it.teamId })
    }

    @Test
    fun flagsAndTeams() {
        assertEquals("🇮🇸", Countries.flag("is"))
        assertEquals("it", Countries.code("Italy"))
        assertEquals("gb", Countries.code("British"))
        assertEquals("🏁", Countries.flag(Countries.code("Atlantis")))
        assertEquals("https://www.formula1.com/en/teams/racing-bulls", Teams.url("rb"))
        assertEquals("https://www.formula1.com/en/drivers/nico-hulkenberg", Teams.driverUrl("Nico", "Hülkenberg"))
    }
}
