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
    }

    override val mainPage = mainPageOf(*sources.map { it.firstPage to it.menuLabel }.toTypedArray())

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val source = sources.firstOrNull { it.firstPage == request.data }
            ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
        if (page < 1) return newHomePageResponse(request.name, emptyList(), hasNext = false)

        val visited = mutableSetOf<String>()
        var next: String? = source.firstPage
        repeat(page - 1) {
            val url = next?.takeIf(visited::add)
                ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
            next = pageLoader(source, url)?.let(source::nextPage)
        }
        val document = next?.takeIf(visited::add)?.let { pageLoader(source, it) }
        val programs = document?.let(source::cards).orEmpty().ifEmpty {
            if (source === FilmCehennemi) installedFilmPage(source, page) else emptyList()
        }
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
                async { searchRequests.withPermit { source.search(term).map { item(it, source) } } }
            }.awaitAll().flatten()
        }
    }

    fun handles(url: String): Boolean = sources.any { it.handles(url.removePrefix(LEGACY_WEB_DATA)) }

    override suspend fun load(url: String): LoadResponse? {
        val source = sources.firstOrNull { it.handles(url) } ?: return null
        if (source is BroadcasterCatalog && source.isLivePage(url)) {
            val card = source.liveCard
            return newLiveStreamLoadResponse(card.title, card.url, card.url) { posterUrl = card.poster }
        }
        val document = pageLoader(source, url)
        val detail = document?.let { source.detail(it) }
        if (detail == null && source === FilmCehennemi) {
            val installed = findInstalledFilmProvider(url)
            val movie = installed?.load(url) as? MovieLoadResponse
            if (movie != null) {
                return newMovieLoadResponse(movie.name, url, contentType, url) {
                    posterUrl = movie.posterUrl
                    plot = movie.plot
                }
            }
        }
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
        val page = data.removePrefix(LEGACY_WEB_DATA)
        val source = sources.firstOrNull { it.handles(page) } ?: return false
        if (source === FilmCehennemi) {
            val installed = findInstalledFilmProvider(page)
            if (installed != null && emitInstalledLinks(installed, page, isCasting, subtitleCallback, callback)) return true
        }
        val document = pageLoader(source, page) ?: return false
        val streams = source.resolveMedia(document, pageLoader, alternativeLoader)
        return emitNativeLinks(name, source.label, document, streams.media, streams.players, subtitleCallback, callback)
    }

    private suspend fun installedFilmPage(source: FilmCehennemi, page: Int): List<Card> {
        if (page != 1) return emptyList()
        val installed = findInstalledFilmProvider(source.firstPage) ?: return emptyList()
        val row = installed.mainPage.firstOrNull { it.data.endsWith("/home/") }
            ?: installed.mainPage.firstOrNull() ?: return emptyList()
        return try {
            installed.getMainPage(page, MainPageRequest(row.name, row.data, row.horizontalImages))
                ?.items.orEmpty().flatMap { it.list }
                .filter { it.type == TvType.Movie && source.supports(it.url) }
                .map { Card(it.name, it.url, it.posterUrl) }
                .distinctBy(Card::url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
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
            provider.loadLinks(page, isCasting, subtitleCallback) { link ->
                if (isNativeLink(link) && urls.add(link.url)) callback(link)
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
