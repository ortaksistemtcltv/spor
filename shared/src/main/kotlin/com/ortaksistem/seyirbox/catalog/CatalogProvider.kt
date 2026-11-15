package com.ortaksistem.seyirbox.catalog

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

internal class CatalogProvider(
    displayName: String,
    private val contentType: TvType,
    private val sources: List<WebCatalog>,
    private val pageLoader: suspend (WebCatalog, String) -> Document? = { source, url -> source.fetch(url) },
    private val alternativeLoader: suspend (WebCatalog, String, String) -> Document? = { source, url, referer ->
        source.fetchAlternativePlayer(url, referer)
    },
    private val findInstalledFilmProvider: (String) -> MainAPI? = FilmCehennemi::installedProviderForUrl,
) : MainAPI() {
    override var name = displayName
    override var mainUrl = sources.first().home
    override var lang = "tr"
    override val supportedTypes = setOf(contentType)
    override val hasMainPage = true
    private companion object {
        val searchRequests = Semaphore(6)
        const val LEGACY_WEB_DATA = "seyirbox-web:"
        const val INSTALLED_FILM_DATA = "seyirbox-hdfilm:"
    }

    override val mainPage = mainPageOf(*sources.map { it.firstPage to it.menuLabel }.toTypedArray())

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val source = sources.firstOrNull { it.firstPage == request.data }
            ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
        if (page < 1) return newHomePageResponse(request.name, emptyList(), hasNext = false)

        if (source === FilmCehennemi) {
            installedFilmPage(source, page, request)?.let { return it }
        }

        val visited = mutableSetOf<String>()
        var next: String? = source.firstPage
        repeat(page - 1) {
            val url = next?.takeIf(visited::add)
                ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
            next = pageLoader(source, url)?.let(source::nextPage)
        }
        val document = next?.takeIf(visited::add)?.let { pageLoader(source, it) }
        val programs = document?.let(source::cards).orEmpty()
        val broadcaster = source as? BroadcasterCatalog
        val items = buildList {
            if (page == 1 && broadcaster != null) add(item(broadcaster.liveCard, source))
            addAll(programs.filter { broadcaster?.isLivePage(it.url) != true }.map { item(it, source) })
        }
        val hasNext = programs.isNotEmpty() && document?.let(source::nextPage)?.let { it !in visited } == true
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return coroutineScope {
            sources.map { source ->
                async { searchRequests.withPermit { searchSource(source, term).map { item(it, source) } } }
            }.awaitAll().flatten()
        }
    }

    fun handles(url: String): Boolean {
        val page = if (url.startsWith(INSTALLED_FILM_DATA)) installedFilmData(url)?.first ?: return false else url
        return sources.any { it.handles(page.removePrefix(LEGACY_WEB_DATA)) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val source = sources.firstOrNull { it.handles(url) } ?: return null
        if (source is BroadcasterCatalog && source.isLivePage(url)) {
            val card = source.liveCard
            return newLiveStreamLoadResponse(card.title, card.url, card.url) { posterUrl = card.poster }
        }
        if (source === FilmCehennemi) {
            installedMovie(url)?.let { movie ->
                val playback = JSONObject().put("page", url).put("data", movie.dataUrl).toString()
                return newMovieLoadResponse(movie.name, url, contentType, INSTALLED_FILM_DATA + playback) {
                    posterUrl = movie.posterUrl
                    plot = movie.plot
                    year = movie.year
                }
            }
        }
        val document = pageLoader(source, url)
        val detail = document?.let { source.detail(it) }
        if (detail == null) {
            if (!source.isDetailUrl(url)) return null
            throw ErrorLoadingException("${source.label} içerik bilgilerini şu anda sunamıyor.")
        }
        if (contentType == TvType.Movie) {
            return newMovieLoadResponse(detail.title, url, contentType, source.playbackPage(document)) {
                posterUrl = detail.poster
                plot = detail.plot
            }
        }
        if (detail.chapters.isEmpty()) {
            return newMovieLoadResponse(detail.title, url, TvType.Movie, source.playbackPage(document)) {
                posterUrl = detail.poster
                plot = detail.plot
            }
        }
        val episodes = detail.chapters.map { chapter ->
            newEpisode(chapter.url, {
                name = chapter.title
                season = chapter.season
                episode = chapter.number
            }, fix = false)
        }
        return newTvSeriesLoadResponse(detail.title, url, contentType, episodes) {
            posterUrl = detail.poster
            plot = detail.plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val installedData = installedFilmData(data)
        val page = installedData?.first ?: data.removePrefix(LEGACY_WEB_DATA)
        val source = sources.firstOrNull { it.handles(page) } ?: return false
        if (source === FilmCehennemi) {
            val installed = findInstalledFilmProvider(page)
            val playData = installedData?.second ?: page
            if (installed != null && emitInstalledLinks(installed, playData, isCasting, subtitleCallback, callback)) return true
        }
        val document = pageLoader(source, page) ?: return false
        val streams = source.resolveMedia(document, pageLoader, alternativeLoader)
        return emitNativeLinks(name, source.label, document, streams.media, streams.players, subtitleCallback, callback)
    }

    private fun installedFilmData(data: String): Pair<String, String>? {
        if (!data.startsWith(INSTALLED_FILM_DATA) || data.length > 16_384) return null
        val json = jsonValue(data.removePrefix(INSTALLED_FILM_DATA)) as? JSONObject ?: return null
        val page = json.optString("page").takeIf(FilmCehennemi::handles) ?: return null
        val playback = json.optString("data").takeIf(String::isNotBlank) ?: return null
        return page to playback
    }

    private suspend fun installedMovie(page: String): MovieLoadResponse? =
        try {
            withTimeoutOrNull(12_000) { findInstalledFilmProvider(page)?.load(page) as? MovieLoadResponse }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun filmCards(results: List<SearchResponse>): List<Card> = results
        .filter { it.type == TvType.Movie && FilmCehennemi.handles(it.url) && findInstalledFilmProvider(it.url) != null }
        .map { Card(it.name, it.url, it.posterUrl) }
        .distinctBy(Card::url)

    private suspend fun searchSource(source: WebCatalog, term: String): List<Card> {
        if (source === FilmCehennemi) {
            val installed = findInstalledFilmProvider(source.firstPage)
            if (installed != null) {
                try {
                    val cards = filmCards(withTimeoutOrNull(12_000) { installed.search(term) }.orEmpty())
                    if (cards.isNotEmpty()) return cards
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The local catalog is still available as a fallback.
                }
            }
        }
        return source.search(term)
    }

    private suspend fun installedFilmPage(source: FilmCehennemi, page: Int, request: MainPageRequest): HomePageResponse? {
        val installed = findInstalledFilmProvider(source.firstPage) ?: return null
        val row = installed.mainPage.firstOrNull { it.data.endsWith("/home/") }
            ?: installed.mainPage.firstOrNull() ?: return null
        return try {
            val response = withTimeoutOrNull(12_000) {
                installed.getMainPage(page, MainPageRequest(row.name, row.data, row.horizontalImages))
            } ?: return null
            val cards = filmCards(response.items.flatMap { it.list })
            if (cards.isEmpty()) return null
            newHomePageResponse(request.name, cards.map { item(it, source) }, response.hasNext)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun emitInstalledLinks(
        provider: MainAPI,
        page: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val urls = ConcurrentHashMap.newKeySet<String>()
        try {
            withTimeoutOrNull(25_000) {
                provider.loadLinks(page, isCasting, subtitleCallback) { link ->
                    if (isNativeLink(link) && urls.add(link.url)) callback(link)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Use SEYİRBOX's own resolver when the installed provider returns no media.
        }
        return urls.isNotEmpty()
    }

    private fun item(card: Card, source: WebCatalog): SearchResponse = when {
        card == (source as? BroadcasterCatalog)?.liveCard ->
            newLiveSearchResponse(card.title, card.url, TvType.Live, fix = false) { posterUrl = card.poster }
        contentType == TvType.Movie ->
            newMovieSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        else ->
            newTvSeriesSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
    }
}
