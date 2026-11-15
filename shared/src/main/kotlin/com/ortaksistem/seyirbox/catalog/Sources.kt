package com.ortaksistem.seyirbox.catalog

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

internal object FilmCehennemi : WebCatalog("HDFilmCehennemi", "https://www.hdfilmcehennemi.nl/") {
    override val firstPage = home + "category/film-izle-2/"
    override fun searchPage(query: String): String = home + "search?q=" + encode(query)

    override fun embeddedPlayers(document: Document): List<String> {
        val containers = document.select(".video-container, .video-player")
        return if (containers.isEmpty()) iframePlayers(document) else containers.flatMap(::iframePlayers).distinct()
    }

    fun installedProviderForUrl(url: String): MainAPI? = installedProvider(url)

    override fun handles(url: String): Boolean = providerHost(url) != null

    internal fun installedProvider(url: String, candidates: Iterable<MainAPI> = APIHolder.allProviders): MainAPI? {
        val host = providerHost(url) ?: return null
        val providers = candidates.filter { api -> api.name.equals(label, ignoreCase = true) && providerHost(api.mainUrl) != null }
        return providers.firstOrNull { providerHost(it.mainUrl) == host }
            ?: providers.firstOrNull().takeIf { url == firstPage }
    }

    private fun providerHost(url: String): String? = httpsUri(url)
        ?.takeIf { it.port in setOf(-1, 443) }
        ?.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
        ?.takeIf { it in setOf("hdfilmcehennemi.nl", "hdfilmcehennemi.com") }

    override fun alternativePlayerUrls(document: Document): List<String> =
        document.select(".alternative-links button.alternative-link[data-video]").asSequence()
            .filterNot { it.text().contains("fragman", ignoreCase = true) }
            .map { it.attr("data-video").trim() }
            .filter { videoId.matches(it) }
            .distinct().take(6).map { "${home}video/$it/" }.toList()

    override suspend fun fetchAlternativePlayer(url: String, referer: String): Document? {
        if (!supports(url) || !alternativePath.matches(URI(url).path.orEmpty())) return null
        val page = read(url, searchRequest = true, referer = referer) ?: return null
        return parseAlternativePlayer(page.body, page.url)
    }

    internal fun parseAlternativePlayer(response: String, responseUrl: String): Document? {
        if (response.trimStart().startsWith('<')) return Jsoup.parseBodyFragment(response, responseUrl)
        val values = when (val payload = jsonValue(response)) {
            is String -> listOf(payload)
            is JSONObject -> playerFragments(payload)
            is JSONArray -> playerFragments(payload)
            else -> listOf(response)
        }
        val html = values.filter { value ->
            val text = value.lowercase(Locale.ROOT)
            text.contains("<iframe") || text.contains("<video") || text.contains("<script")
        }.joinToString("\n")
        return html.takeIf(String::isNotBlank)?.let { Jsoup.parseBodyFragment(it, responseUrl) }
    }

    private fun playerFragments(payload: Any?, depth: Int = 0): List<String> {
        if (depth > 3) return emptyList()
        return when (payload) {
            is String -> listOf(payload)
            is JSONObject -> payload.keys().asSequence().take(30).flatMap { key ->
                playerFragments(payload.opt(key), depth + 1).asSequence()
            }.toList()
            is JSONArray -> (0 until minOf(payload.length(), 16)).flatMap {
                playerFragments(payload.opt(it), depth + 1)
            }
            else -> emptyList()
        }
    }

    private val videoId = Regex("[A-Za-z0-9_-]{1,96}")
    private val alternativePath = Regex("/video/[A-Za-z0-9_-]{1,96}/?")

    override suspend fun search(query: String): List<Card> {
        val page = read(searchPage(query), searchRequest = true) ?: return emptyList()
        if (!page.body.trimStart().startsWith('{')) return cards(Jsoup.parse(page.body, page.url))
        return try {
            val results = JSONObject(page.body).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { index ->
                val snippet = results.optString(index).takeIf(String::isNotBlank) ?: return@mapNotNull null
                filmCard(Jsoup.parseBodyFragment(snippet, page.url).body())
            }.distinctBy(Card::url)
        } catch (_: JSONException) {
            emptyList()
        }
    }

    override fun cards(document: Document): List<Card> = document
        .select(".section-content a.poster, a.poster:has(strong.poster-title), .search-result:has(h4.title)")
        .mapNotNull(::filmCard).distinctBy(Card::url)

    private fun filmCard(element: Element): Card? {
        val link = (if (element.tagName() == "a") element else element.selectFirst("a[href]"))
            ?: return null
        val url = link.absUrl("href")
        val badge = element.selectFirst(".poster-type, .poster-badge")?.text().orEmpty()
        if ("/dizi/" in url || badge.contains("dizi", ignoreCase = true)) return null
        val title = element.selectFirst("strong.poster-title, h4.title")?.text() ?: return null
        return Card(title, url, imageOf(element.selectFirst("img"))).takeIf { it.valid() }
    }

    override suspend fun detail(document: Document): Detail? {
        if (document.selectFirst(".seasons, .seasons-tab-content") != null) return null
        val title = document.selectFirst("h1.section-title, h1")?.text()
            ?.substringBefore(" izle")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val poster = imageOf(document.selectFirst("aside.post-info-poster img, .post-info-poster img"))
        val plot = document.selectFirst(".post-info-description, .post-info-content p")?.text()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
        return Detail(title, poster, plot)
    }
}

internal object DiziBox : WebCatalog("Dizibox", "https://www.dizibox.live/") {
    override val firstPage = home + "dizi-arsivi/"
    override fun searchPage(query: String): String = home + "?s=" + encode(query)
    private val seasonFromUrl = Regex("""(?:^|[/_-])(\d+)-sezon(?:[/_-]|$)""")
    private val episodeFromUrl = Regex("""(?:^|[/_-])(\d+)-bolum(?:[/_-]|$)""")
    private val seasonFromTitle = Regex("""(\d+)\s*\.?\s*sezon""", RegexOption.IGNORE_CASE)
    private val episodeFromTitle = Regex("""(\d+)\s*\.?\s*bölüm""", RegexOption.IGNORE_CASE)
    private val seasonRequests = Semaphore(4)

    override fun isDetailUrl(url: String): Boolean =
        handles(url) && URI(url).path.orEmpty().startsWith("/diziler/")

    override suspend fun search(query: String): List<Card> =
        super.search(query).ifEmpty { searchFirstPage(query) }

    override fun cards(document: Document): List<Card> = document
        .select("article.detailed-article h3 a[href]")
        .mapNotNull { link ->
            val article = link.closest("article") ?: return@mapNotNull null
            val url = link.absUrl("href")
            if (episodeFromUrl.containsMatchIn(url)) return@mapNotNull null
            Card(link.text(), url, imageOf(article.selectFirst("img"))).takeIf { it.valid() }
        }.distinctBy(Card::url)

    override suspend fun detail(document: Document): Detail? {
        val title = document.selectFirst(".tv-overview h1, h1")
            ?.text()?.trim()?.takeIf(String::isNotBlank) ?: return null
        val poster = imageOf(document.selectFirst(".tv-poster img, .tv-overview img"))
        val plot = document.selectFirst(".tv-story p")?.text()
        val localChapters = chaptersOn(document, null)
        val current = document.location().trimEnd('/')
        val seasons = document.select("#seasons-list a[href]")
            .mapNotNull { link ->
                val url = link.absUrl("href")
                if (!supports(url) || url.trimEnd('/') == current) return@mapNotNull null
                val number = seasonFromUrl.numberIn(url) ?: seasonFromTitle.numberIn(link.text())
                url to number
            }.distinctBy { it.first }
        val remoteChapters = coroutineScope {
            seasons.map { (url, number) ->
                async {
                    seasonRequests.withPermit {
                        fetch(url)?.let { chaptersOn(it, number) }.orEmpty()
                    }
                }
            }.awaitAll().flatten()
        }
        val chapters = (localChapters + remoteChapters).distinctBy { it.url.trimEnd('/') }
            .sortedWith(compareBy({ it.season ?: Int.MAX_VALUE }, { it.number ?: Int.MAX_VALUE }, { it.title }))
        return Detail(title, poster, plot, chapters)
    }

    internal fun chaptersOn(document: Document, season: Int?): List<Chapter> = document
        .select("article.grid-box .post-title a[href], a[href*=-bolum-], a[href$=-bolum/]")
        .mapNotNull { link: Element ->
            val url = link.absUrl("href").substringBefore('#')
            val label = link.text().ifBlank { link.attr("title") }.trim()
            val number = episodeFromUrl.numberIn(url) ?: episodeFromTitle.numberIn(label) ?: return@mapNotNull null
            if (label.isBlank() || !supports(url)) return@mapNotNull null
            val seasonNumber = seasonFromUrl.numberIn(url) ?: seasonFromTitle.numberIn(label) ?: season
            Chapter(label, url, seasonNumber, number)
        }.distinctBy(Chapter::url)

    private fun Regex.numberIn(text: String): Int? =
        find(text)?.groupValues?.get(1)?.toIntOrNull()
}
