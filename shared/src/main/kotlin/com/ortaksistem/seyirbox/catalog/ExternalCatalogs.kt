package com.ortaksistem.seyirbox.catalog

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

internal val filmCatalogs: List<WebCatalog> = listOf(
    FilmCehennemi,
    DiziFilmIzle(series = false),
    JetFilmIzle,
    HtmlCatalog("SinemaGG", "https://sinemacc.com/", "film-arsivi/", ".film_kutusu > a[href]:has(img)", filmPath, ".title .text"),
    WebteIzle,
    RareFilmm,
)

internal val seriesCatalogs: List<WebCatalog> = listOf(
    DiziBox,
    DiziFilmIzle(series = true),
    HtmlCatalog("Dizirella", "https://dizirella.one/", "dizi-arsivi/", "a.r-card[href], .single-item .categorytitle a[href]", Regex("/dizi/[^/]+/?"), ".r-cap-t", posterContainer = ".single-item", searchPath = "?s=", episodePath = { path ->
        Regex("""/${Regex.escape(path.substringAfterLast('/'))}-(\d+)-sezon-(\d+)-bolum/?""")
    }),
    BroadcasterCatalog("DMAX", "https://www.dmax.com.tr/", "https://www.dmax.com.tr/img/logo.svg"),
    BroadcasterCatalog("TLC", "https://www.tlctv.com.tr/", "https://img-tlctv.mncdn.com/logo.png?v=1.1"),
)

private object WebteIzle : HtmlCatalog(
    "Webteİzle", "https://webteizle.info/", "film-izle/", "div.golgever a.image[href]",
    Regex("/hakkinda/[^/]+/?"), responseCharset = "windows-1254",
) {
    override fun playbackPage(document: Document): String =
        document.select("a[href^=/izle/altyazi/], a[href^=/izle/dublaj/]").asSequence()
            .map { it.absUrl("href") }.firstOrNull(::supports) ?: document.location()
}

private val filmPath get() = Regex("/film/[^/]+/?")

internal object JetFilmIzle : HtmlCatalog(
    "Jetfilmizle", "https://jetfilmizle.app/", "filmler", "a[href]:has(.fc-title)", filmPath,
    ".fc-title", searchPath = "arama?q=",
) {
    private val playerEndpoint = home + "jetplayer"
    private val playerIndex = Regex("[0-9]{1,3}")
    private val filmIdentifier = Regex("[0-9]{1,12}")

    internal fun playerForms(document: Document): List<Map<String, String>> {
        val filmId = document.selectFirst("input[name=film_id]")?.attr("value")
            ?.takeIf(filmIdentifier::matches) ?: return emptyList()
        return document.select(".player-source-btn[data-source-index][data-player-type]").asSequence()
            .mapNotNull { button ->
                val index = button.attr("data-source-index").takeIf(playerIndex::matches) ?: return@mapNotNull null
                val type = button.attr("data-player-type")
                    .takeIf { it in setOf("dublaj", "altyazili", "genel") } ?: return@mapNotNull null
                mapOf("film_id" to filmId, "source_index" to index, "player_type" to type)
            }.distinct().take(6).toList()
    }

    override suspend fun extraPlayerDocuments(document: Document): List<Document> {
        if (!supports(document.location())) return emptyList()
        return coroutineScope {
            playerForms(document).map { form ->
                async {
                    val response = read(playerEndpoint, referer = document.location(), form = form)
                    response?.let { Jsoup.parseBodyFragment(it.body, it.url) }
                }
            }.awaitAll().filterNotNull()
        }
    }
}

internal class BroadcasterCatalog(name: String, home: String, logo: String) : HtmlCatalog(
    name, home, "kesfet", "section.grid.dyn-content .poster a[href]:has(img)",
    Regex("/[a-z0-9][a-z0-9-]+/?"), usePathTitle = true, episodePath = { path ->
        Regex("""${Regex.escape(path)}/(\d+)-sezon-(\d+)-bolum/?""")
    },
) {
    override val menuLabel = "$name Program Arşivi"
    val liveCard = Card("$name Canlı Yayın", home + "canli-izle", logo)

    fun isLivePage(url: String): Boolean = supports(url) &&
        URI(url).path.trimEnd('/') == URI(liveCard.url).path

    override suspend fun embeddedMedia(document: Document): List<Media> {
        val metaPath = playerMetadata.find(document.select("script:not([src])").joinToString("\n") { it.data() })
            ?.groupValues?.get(1) ?: return emptyList()
        val endpoint = URI(home).resolve(metaPath).toString()
        val response = read(endpoint, searchRequest = true, referer = document.location()) ?: return emptyList()
        return jsonMedia(jsonValue(response.body), response.url, document.location())
    }

    private val playerMetadata = Regex("""\bmetaUrl\s*:\s*['"](/player/info\?referenceId=[A-Za-z0-9_-]{1,80})['"]""")

    override suspend fun search(query: String): List<Card> {
        val term = query.trim()
        val channels = if (term.equals(label, ignoreCase = true) || liveCard.title.equals(term, ignoreCase = true)) {
            listOf(liveCard)
        } else {
            emptyList()
        }
        return channels + super.search(query)
    }
}

internal open class HtmlCatalog(
    label: String,
    home: String,
    archive: String,
    private val cardSelector: String,
    private val cardPath: Regex,
    private val titleSelector: String? = null,
    private val posterContainer: String? = null,
    private val searchPath: String? = null,
    override val responseCharset: String = "UTF-8",
    private val episodePath: ((String) -> Regex)? = null,
    private val usePathTitle: Boolean = false,
) : WebCatalog(label, home) {
    override val firstPage = home + archive

    override fun isDetailUrl(url: String): Boolean = handles(url) && cardPath.matches(URI(url).path.orEmpty())

    override fun searchPage(query: String): String {
        val path = searchPath ?: return firstPage
        return home + path + encode(query)
    }

    override suspend fun search(query: String): List<Card> {
        val results = if (searchPath == null) emptyList() else super.search(query)
        if (results.isNotEmpty()) return results
        return searchFirstPage(query)
    }

    override fun cards(document: Document): List<Card> =
        document.select(cardSelector).mapNotNull { link ->
            val url = link.absUrl("href").substringBefore('#')
            if (!supports(url) || !cardPath.matches(URI(url).path.orEmpty())) return@mapNotNull null
            val container = posterContainer?.let(link::closest) ?: link
            val image = container.selectFirst("img")
            val title = sequenceOf(
                titleSelector?.let { link.selectFirst(it)?.text() },
                link.attr("aria-label"),
                link.attr("title"),
                image?.attr("alt"),
                link.text(),
                if (usePathTitle) URI(url).path.trimEnd('/').substringAfterLast('/').replace('-', ' ')
                    .replaceFirstChar { it.titlecase(Locale.ROOT) } else null,
            ).filterNotNull().firstOrNull(String::isNotBlank) ?: return@mapNotNull null
            val poster = imageOf(image) ?: backgroundPoster(container.selectFirst(".featured-image"), document)
            Card(cleanTitle(title), url, poster).takeIf { it.valid() }
        }.distinctBy { URI(it.url).path.trimEnd('/') }

    override suspend fun detail(document: Document): Detail? {
        val path = URI(document.location()).path.orEmpty().trimEnd('/')
        if (!cardPath.matches(path)) return null
        val title = cleanTitle(document.selectFirst("main h1, article h1, h1")?.text().orEmpty())
            .ifBlank { cleanTitle(document.meta("property", "og:title").orEmpty()) }
            .takeIf(String::isNotBlank) ?: return null
        val poster = document.selectFirst("meta[property=og:image]")?.absUrl("content")?.takeIf(::isHttpsUrl)
            ?: imageOf(document.selectFirst("article img"))
        val description = document.meta("name", "description") ?: document.meta("property", "og:description")
        val chapterPattern = episodePath?.invoke(path)
        val chapters = if (chapterPattern == null) emptyList() else chaptersOn(document, chapterPattern)
        return Detail(title, poster, description, chapters)
    }

    protected open fun episodeUrls(document: Document): Sequence<String> =
        document.select("a[href*=bolum]").asSequence().map { it.absUrl("href") }

    private fun chaptersOn(document: Document, pattern: Regex): List<Chapter> =
        episodeUrls(document).map { it.substringBefore('#').substringBefore('?') }.mapNotNull { url ->
            if (!supports(url)) return@mapNotNull null
            val match = pattern.matchEntire(URI(url).path.orEmpty()) ?: return@mapNotNull null
            val season = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val episode = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            Chapter("$season. Sezon $episode. Bölüm", url, season, episode)
        }.distinctBy { it.season to it.number }.sortedWith(compareBy(Chapter::season, Chapter::number)).toList()

    private fun backgroundPoster(element: Element?, document: Document): String? {
        val style = element?.attr("style") ?: return null
        val url = Regex("""background-image:\s*url\(([^)]+)\)""").find(style)?.groupValues?.get(1)
            ?.trim()?.trim('"', '\'') ?: return null
        return runCatching { URI(document.location()).resolve(url).toString() }.getOrNull()?.takeIf(::isHttpsUrl)
    }

    private fun cleanTitle(title: String): String =
        title.replace('\u00a0', ' ').replace(Regex("""(?:\s*[-|]?\s*(?:Full Türkçe Dublaj|\d{3,4}p))?\s+izle$""", RegexOption.IGNORE_CASE), "").trim()

    private fun Document.meta(attribute: String, value: String): String? =
        selectFirst("meta[$attribute=$value]")?.attr("content")?.trim()?.takeIf(String::isNotBlank)
}

private class DiziFilmIzle(private val series: Boolean) : HtmlCatalog(
    "DiziFilmizle",
    "https://dizifilmizle.to/",
    if (series) "yabanci-dizi-izle" else "",
    "a.media-card__link[href]",
    if (series) Regex("/dizi/[^/]+/?") else filmPath,
    posterContainer = ".apple-card",
    episodePath = if (series) {
        { path -> Regex("""${Regex.escape(path)}/sezon-(\d+)/bolum-(\d+)/?""") }
    } else {
        null
    },
) {
    override fun handles(url: String): Boolean =
        supports(url) && URI(url).path.orEmpty().startsWith(if (series) "/dizi/" else "/film/")

    override fun embeddedPlayers(document: Document): List<String> {
        val field = if (series) "episode" else "movie"
        val script = document.select("script:not([src])").asSequence().map(Element::data)
            .filter { it.startsWith("self.__next_f.push(") }
        val players = script.mapNotNull { encoded ->
            (jsonValue(encoded.removePrefix("self.__next_f.push(").removeSuffix(")")) as? JSONArray)?.optString(1)
        }.flatMap { flight ->
            Regex("\"$field\"\\s*:\\s*(?=\\{)").findAll(flight).mapNotNull { match ->
                jsonValue(flight.substring(match.range.last + 1)) as? JSONObject
            }
        }.flatMap { item ->
            sequenceOf("embed_player_url_1", "embed_player_url_2").map(item::optString)
        }.filter(::isHttpsUrl).distinct().take(4).toList()
        return players + super.embeddedPlayers(document)
    }

    override fun episodeUrls(document: Document): Sequence<String> =
        super.episodeUrls(document) + document.select("script[type=application/ld+json]").asSequence()
            .flatMap { scriptJson(it).episodeUrls(0) }

    private fun scriptJson(script: Element): Any? = try {
        JSONTokener(script.data()).nextValue()
    } catch (_: JSONException) {
        null
    }

    private fun Any?.episodeUrls(depth: Int): Sequence<String> {
        if (depth > 8) return emptySequence()
        return when (this) {
            is JSONObject -> if (optString("@type") == "TVEpisode") {
                sequenceOf(optString("url"))
            } else {
                sequenceOf("@graph", "containsSeason", "season", "episode", "episodes")
                    .flatMap { opt(it).episodeUrls(depth + 1) }
            }
            is JSONArray -> (0 until length()).asSequence().flatMap { opt(it).episodeUrls(depth + 1) }
            else -> emptySequence()
        }
    }
}

private object RareFilmm : HtmlCatalog(
    "RareFilmm",
    "https://rarefilmm.com/",
    "",
    ".excerpt-title a[href]",
    Regex("""/\d{4}/\d{2}/[^/]+/?"""),
    ".entry-title-primary",
    posterContainer = ".post",
    searchPath = "?s=",
) {
    override fun embeddedPlayers(document: Document): List<String> =
        document.select(".entry-content iframe[src]").mapNotNull { element ->
            val url = element.absUrl("src")
            if (!isHttpsUrl(url)) return@mapNotNull null
            val uri = URI(url)
            if (uri.host != "ok.ru" || uri.port !in setOf(-1, 443)) return@mapNotNull null
            if (!Regex("/videoembed/[0-9]+/?").matches(uri.path.orEmpty())) return@mapNotNull null
            "https://ok.ru" + uri.path.trimEnd('/')
        }.distinct()

    override suspend fun embeddedMedia(document: Document): List<Media> =
        embeddedPlayers(document).flatMap { url ->
            OkPublicPlayer.fetch(url)?.let(OkPublicPlayer::streams).orEmpty()
        }

}


internal object OkPublicPlayer : WebCatalog("OK.ru", "https://ok.ru/") {
    override val firstPage = home
    override fun searchPage(query: String) = home
    override fun cards(document: Document) = emptyList<Card>()
    override suspend fun detail(document: Document): Detail? = null

    private val quality = mapOf(
        "mobile" to 144, "lowest" to 240, "low" to 360, "sd" to 480,
        "hd" to 720, "full" to 1080, "quad" to 1440, "ultra" to 2160,
    )

    fun streams(document: Document): List<Media> =
        document.select("[data-options]").flatMap { player ->
            try {
                val flash = JSONObject(player.attr("data-options")).optJSONObject("flashvars")
                val metadata = when (val value = flash?.opt("metadata")) {
                    is JSONObject -> value
                    is String -> JSONObject(value)
                    else -> null
                } ?: return@flatMap emptyList()
                val manifest = metadata.optString("hlsManifestUrl")
                val videos = metadata.optJSONArray("videos") ?: JSONArray()
                listOfNotNull(readStream(document, manifest, "application/x-mpegurl")) +
                    (0 until videos.length()).mapNotNull { i ->
                        val video = videos.optJSONObject(i) ?: return@mapNotNull null
                        readStream(document, video.optString("url"), "video/mp4")
                            ?.copy(resolution = quality[video.optString("name")])
                    }
            } catch (_: JSONException) {
                emptyList()
            }
        }.distinctBy(Media::url)

    private fun readStream(document: Document, value: String, mime: String): Media? {
        if (value.isBlank()) return null
        val uri = runCatching { URI(document.location()).resolve(value) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!host.endsWith(".okcdn.ru") && !host.endsWith(".vkuser.net")) return null
        return mediaAt(uri.toString(), mime)?.copy(referer = home)
    }
}
