package app.f1pro

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every vector icon must parse: a bad path only fails when a screen draws it, on the phone. */
class ResourcesTest {
    private val valid = Regex("^[MmLlHhVvCcSsQqTtAaZz0-9eE.,\\s+-]+$")

    @Test
    fun vectorPathsAreValid() {
        val files = File("src/main/res").walkTopDown().filter { it.extension == "xml" && it.readText().contains("pathData") }.toList()
        assertTrue("no vectors found", files.size > 30)
        for (f in files) {
            for (m in Regex("pathData=\"([^\"]*)\"").findAll(f.readText())) {
                val d = m.groupValues[1].trim()
                assertTrue("${f.name}: bad path '$d'", d.matches(valid) && d.first() in "Mm")
            }
        }
    }
}
