package setups.render

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import setups.load.Dataset
import setups.model.*

/** A page to write: path relative to the site root (e.g. "thunder-2004/index.html") and its HTML. */
data class Page(val path: String, val html: String)

/** Builds every page of the site from the dataset. */
class Site(private val data: Dataset, private val basePath: String = "/") {

    private val siteName = "NASCAR Thunder Setups"

    /** A setup with the game/series it belongs to. */
    private data class Entry(val game: Game, val series: Series, val track: Track, val setup: Setup)

    private val entries: List<Entry> = data.setupFiles.flatMap { f ->
        val game = data.gamesById.getValue(f.game)
        val series = data.seriesById.getValue(f.series)
        f.resolvedSetups().map { Entry(game, series, data.tracksById.getValue(it.track), it) }
    }

    private val authors: Map<String, List<Entry>> =
        entries.groupBy { it.setup.author }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    fun pages(): List<Page> = buildList {
        add(home())
        data.games.forEach { game ->
            add(gamePage(game))
            game.series.forEach { sid ->
                val series = data.seriesById.getValue(sid)
                entries.filter { it.game == game && it.series == series }
                    .groupBy { it.track }
                    .forEach { (track, list) -> add(trackPage(game, series, track, list.map { it.setup })) }
            }
        }
        add(authorsIndex())
        authors.forEach { (name, list) -> add(authorPage(name, list)) }
        add(about())
        add(notFound())
    }

    // ---------- URLs (all relative, so the site works from any base path) ----------

    private fun gameUrl(g: Game) = "${g.id}/"
    private fun trackUrl(g: Game, s: Series, t: Track) = "${g.id}/${s.id}/${t.id}/"
    private fun authorUrl(name: String) = "authors/${Format.slug(name)}/"

    // ---------- Pages ----------

    private fun home() = page("index.html", null, "Setups for the NASCAR Thunder games") {
        section("hero") {
            h1 { +siteName }
            p("lead") {
                +"Garage setups for EA Sports' NASCAR Thunder games, collected from fan sites and forums that are mostly gone now."
            }
        }
        div("game-grid") {
            data.games.forEach { g ->
                val list = entries.filter { it.game == g }
                a(href = rel(gameUrl(g)), classes = "game-card") {
                    span("game-year") { +"${g.released}" }
                    h2 { +g.name }
                    g.description?.let { p { +it } }
                    p("game-stats") {
                        +"${list.size} setups · ${list.map { it.track }.distinct().size} tracks · ${list.map { it.setup.author }.distinct().size} authors"
                    }
                }
            }
        }
    }

    private fun gamePage(g: Game) = page("${gameUrl(g)}index.html", g.name, g.description ?: g.name, crumbs = listOf(g.name to null)) {
        h1 { +g.name }
        g.description?.let { p("lead") { +it } }
        p("muted") { +"Garage settings in this game: ${g.fields.joinToString { it.label }}." }

        val seriesWithSetups = g.series.map { data.seriesById.getValue(it) }
            .map { s -> s to entries.filter { it.game == g && it.series == s } }
            .filter { it.second.isNotEmpty() }

        if (seriesWithSetups.size > 1) {
            nav("series-tabs") {
                seriesWithSetups.forEach { (s, _) -> a(href = "#${s.id}") { +s.name } }
            }
        }
        div("filter") {
            label { htmlFor = "track-filter"; +"Find a track" }
            input(type = InputType.search) {
                id = "track-filter"
                placeholder = "e.g. Bristol, Charlotte…"
                attributes["data-filter"] = ".track-card"
                attributes["autocomplete"] = "off"
            }
        }
        seriesWithSetups.forEach { (s, list) ->
            section("series") {
                id = s.id
                h2 { +s.name }
                list.groupBy { it.track }.entries
                    .groupBy { it.key.type }
                    .toSortedMap()
                    .forEach { (type, tracks) ->
                        h3("track-type") { +type.label }
                        div("track-grid") {
                            tracks.sortedBy { it.key.name }.forEach { (t, setups) ->
                                a(href = rel(trackUrl(g, s, t)), classes = "track-card") {
                                    attributes["data-filter-text"] = (listOf(t.name) + t.aliases + listOfNotNull(t.location)).joinToString(" ")
                                    span("track-name") { +t.name }
                                    t.location?.let { span("track-loc") { +it } }
                                    span("track-count") { +plural(setups.size, "setup") }
                                }
                            }
                        }
                    }
            }
        }
        p("empty-filter hidden") { +"No tracks match." }
    }

    private fun trackPage(g: Game, s: Series, t: Track, setups: List<Setup>) = page(
        "${trackUrl(g, s, t)}index.html",
        "${t.name} — ${g.name}",
        "${setups.size} ${g.name} ${s.name} setups for ${t.name}",
        crumbs = listOf(g.name to gameUrl(g), s.name to "${gameUrl(g)}#${s.id}", t.name to null),
    ) {
        h1 { +t.name }
        p("lead") {
            +"${g.name} · ${s.name}"
            t.location?.let { +" · $it" }
        }
        // Only show rows for settings at least one setup here uses.
        val fields = g.fields.filter { f -> setups.any { Format.field(it, f) != null } }
        val ids = setups.indices.map { "setup-${it + 1}" }

        if (setups.size > 1) {
            h2 { +"Compare" }
            p("muted") { +"Rows where every setup agrees are dimmed. A dash means the source didn't give that setting." }
            div("table-wrap") {
                table("compare") {
                    thead {
                        tr {
                            th { +"Setting" }
                            setups.forEachIndexed { i, st -> th { a(href = "#${ids[i]}") { +st.author } } }
                        }
                    }
                    tbody {
                        fields.forEach { f ->
                            val values = setups.map { Format.field(it, f) }
                            tr(if (values.distinct().size == 1) "same" else null) {
                                th { +f.label }
                                values.forEach { v -> td { if (v == null) span("none") { +"—" } else +v } }
                            }
                        }
                    }
                }
            }
        }

        h2 { +plural(setups.size, "setup") }
        div("setup-list") {
            setups.forEachIndexed { i, st ->
                val twin = setups.subList(0, i).firstOrNull { it.author != st.author && sameValues(it, st) }
                article("setup-card") {
                    id = ids[i]
                    header {
                        h3 { a(href = rel(authorUrl(st.author))) { +st.author } }
                        button(classes = "copy") {
                            type = ButtonType.button
                            attributes["data-copy"] = Format.copyText(t.name, g.name, st, g.fields)
                            +"Copy"
                        }
                    }
                    twin?.let { p("badge") { +"Same values as ${it.author}'s setup" } }
                    dl {
                        fields.forEach { f ->
                            Format.field(st, f)?.let { v -> dt { +f.label }; dd { +v } }
                        }
                        st.bestLap?.let { dt { +"Best lap" }; dd { +it } }
                    }
                    st.notes?.let { p("notes") { +it } }
                    st.source?.let { p("muted") { +"Source: $it" } }
                    st.raw?.let { r ->
                        details {
                            summary { +"Original text" }
                            pre { +r }
                        }
                    }
                }
            }
        }
    }

    private fun authorsIndex() = page("authors/index.html", "Authors", "Everyone whose setups are on the site", crumbs = listOf("Authors" to null)) {
        h1 { +"Authors" }
        p("lead") { +"The people whose setups were collected here." }
        ul("author-list") {
            authors.forEach { (name, list) ->
                li {
                    a(href = rel(authorUrl(name))) { +name }
                    span("muted") { +" ${plural(list.size, "setup")} · ${list.map { it.game.name }.distinct().joinToString()}" }
                }
            }
        }
    }

    private fun authorPage(name: String, list: List<Entry>) = page(
        "${authorUrl(name)}index.html", name, "Setups by $name",
        crumbs = listOf("Authors" to "authors/", name to null),
    ) {
        h1 { +name }
        p("lead") { +"${plural(list.size, "setup")} by $name." }
        list.groupBy { it.game to it.series }.forEach { (gs, items) ->
            val (g, s) = gs
            h2 { +"${g.name} · ${s.name}" }
            ul("track-links") {
                items.sortedBy { it.track.name }.forEach { e ->
                    val index = entries.filter { it.game == g && it.series == s && it.track == e.track }.indexOf(e)
                    li { a(href = rel(trackUrl(g, s, e.track)) + "#setup-${index + 1}") { +e.track.name } }
                }
            }
        }
    }

    private fun about() = page("about/index.html", "About", "About $siteName", crumbs = listOf("About" to null)) {
        h1 { +"About" }
        p {
            +"$siteName collects garage setups for EA Sports' NASCAR Thunder games, found on fan sites and forums from the early 2000s. "
            +"Each setup is credited to the name it was posted under, and each one keeps a copy of its original text."
        }
        h2 { +"Reading a setup" }
        ul {
            li { +"Gears are either a preset number from the garage screen or individual ratios (1st / 2nd / 3rd / 4th) plus a rear end ratio." }
            li { +"\"Default\" means the author left that setting at the game's default." }
            li { +"Fender flare and rear spoiler aren't adjustable at the restrictor-plate tracks (Daytona and Talladega)." }
        }
        h2 { +"Corrections" }
        p {
            +"Some original posts had typos or jumbled lines. Where a value had to be interpreted, the setup has a note saying so, "
            +"and you can always open \"Original text\" to see exactly what was posted."
        }
        h2 { +"Credits" }
        p {
            +"Setups are the work of their authors. This site isn't affiliated with EA Sports or NASCAR, "
            +"and all trademarks belong to their owners."
        }
    }

    private fun notFound() = page("404.html", "Page not found", "Page not found", absoluteRoot = true) {
        h1 { +"Page not found" }
        p { +"That page doesn't exist. "; a(href = rel("")) { +"Back to the games" } }
    }

    // ---------- Layout ----------

    private var root = ""
    private fun rel(path: String) = root + path

    private fun page(
        path: String,
        title: String?,
        description: String,
        crumbs: List<Pair<String, String?>> = emptyList(),
        absoluteRoot: Boolean = false,
        body: MAIN.() -> Unit,
    ): Page {
        // How many directories deep this page is, so links can be relative.
        root = if (absoluteRoot) basePath else "../".repeat(path.count { it == '/' })
        val html = createHTML(prettyPrint = false).html {
            lang = "en"
            head {
                meta(charset = "utf-8")
                meta(name = "viewport", content = "width=device-width, initial-scale=1")
                meta(name = "description", content = description)
                title { +(title?.let { "$it · $siteName" } ?: siteName) }
                link(rel = "stylesheet", href = rel("site.css"))
                link(rel = "icon", href = rel("favicon.svg"), type = "image/svg+xml")
                script(src = rel("site.js")) { defer = true }
            }
            body {
                header("site-header") {
                    div("wrap") {
                        a(href = rel(""), classes = "brand") { +siteName }
                        nav {
                            data.games.forEach { g -> a(href = rel(gameUrl(g))) { +g.name.removePrefix("NASCAR ") } }
                            a(href = rel("authors/")) { +"Authors" }
                            a(href = rel("about/")) { +"About" }
                        }
                    }
                }
                main("wrap") {
                    if (crumbs.isNotEmpty()) {
                        nav("crumbs") {
                            a(href = rel("")) { +"Home" }
                            crumbs.forEach { (label, href) ->
                                span("sep") { +"›" }
                                if (href == null) span { +label } else a(href = rel(href)) { +label }
                            }
                        }
                    }
                    body()
                }
                footer("site-footer") {
                    div("wrap") {
                        p {
                            +"${entries.size} setups for ${data.games.size} games. Setups are the work of their authors. Not affiliated with EA Sports or NASCAR."
                        }
                    }
                }
            }
        }
        return Page(path, "<!DOCTYPE html>\n$html")
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    private fun sameValues(a: Setup, b: Setup) = a.valuesKey().copy(author = "") == b.valuesKey().copy(author = "")
}
