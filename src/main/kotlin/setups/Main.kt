package setups

import setups.load.DataLoader
import setups.load.Validator
import setups.render.Site
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.system.exitProcess

/**
 * Generates the static site.
 *
 * Usage: Main <dataDir> <staticDir> <outDir>
 * Set SITE_BASE (e.g. "/nascar-thunder-setups/") when the site isn't served from the domain root;
 * only the 404 page needs it, every other link is relative.
 */
@OptIn(ExperimentalPathApi::class)
fun main(args: Array<String>) {
    val dataDir = Path(args.getOrElse(0) { "data" })
    val staticDir = Path(args.getOrElse(1) { "site-static" })
    val outDir = Path(args.getOrElse(2) { "build/site" })
    val basePath = System.getenv("SITE_BASE")?.takeIf { it.isNotBlank() }?.let { if (it.endsWith("/")) it else "$it/" } ?: "/"

    val data = DataLoader.load(dataDir)
    val errors = Validator.validate(data)
    if (errors.isNotEmpty()) {
        System.err.println("Found ${errors.size} problem(s) in $dataDir:")
        errors.forEach { System.err.println("  - $it") }
        exitProcess(1)
    }

    if (outDir.exists()) outDir.deleteRecursively()
    outDir.createDirectories()

    val pages = Site(data, basePath).pages()
    pages.forEach { page ->
        val file = outDir / page.path
        file.createParentDirectories()
        file.writeText(page.html)
    }
    staticDir.walk().filter { it.isRegularFile() }.forEach { src ->
        val dest = outDir / src.relativeTo(staticDir).toString()
        dest.createParentDirectories()
        src.copyTo(dest, overwrite = true)
    }
    // Tell GitHub Pages not to run Jekyll over the output.
    (outDir / ".nojekyll").writeText("")

    val setups = data.setupFiles.sumOf { it.setups.size }
    println("Generated ${pages.size} pages ($setups setups) into $outDir")
}
