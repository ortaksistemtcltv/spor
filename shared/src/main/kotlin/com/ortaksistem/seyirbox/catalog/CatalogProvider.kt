package com.ortaksistem.seyirbox.catalog

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document
import java.net.URI
import java.util.Locale

internal class CatalogProvider(
    displayName: String,
    private val contentType: TvType,
    private val sources: List<WebCatalog>,
    private val pageLoader: suspend (WebCatalog, String) -> Document? = { source, url -> source.fetch(url) },
    private val openWebPlayer: suspend (String, String) -> Boolean = { title, url -> WebPlayback.open(title, url) },
) : MainAPI() {
    override var name = displayName
    override var mainUrl = sources.first().home
    override var lang = "tr"
    override val supportedTypes = setOf(contentType)
    override val hasMainPage = true
    private companion object {
        val searchRequests = Semaphore(6)
        const val WEB_DATA = "seyirbox-web:"
    }

    override val mainPage = mainPageOf(*sources.map { it.firstPage to it.label }.toTypedArray())

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
        val programs = document?.let(source::cards).orEmpty()
        val broadcaster = source as? BroadcasterCatalog
        val items = buildList {
            if (page == 1 && broadcaster != null) {
                add(item(broadcaster.liveCard, source))
                if (programs.isEmpty()) add(item(broadcaster.archiveCard, source))
            }
            addAll(programs.filter { broadcaster?.playbackCard(it.url) == null }.map { item(it, source) })
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

    fun handles(url: String): Boolean = sources.any { it.handles(url.removePrefix(WEB_DATA)) }

    override suspend fun load(url: String): LoadResponse? {
        val source = sources.firstOrNull { it.handles(url) } ?: return null
        (source as? BroadcasterCatalog)?.playbackCard(url)?.let { card ->
            return newMovieLoadResponse(card.title, card.url, TvType.CustomMedia, webPlaybackData(card.url)) {
                posterUrl = card.poster
                plot = "Oynat, yayıncının sayfasını uygulama içindeki web oynatıcıda açar."
            }
        }
        val document = pageLoader(source, url)
        val detail = document?.let { source.detail(it) }
        if (detail == null) {
            if (!source.isDetailUrl(url)) return null
            return webPlayerResponse(source, url, null)
        }
        if (contentType == TvType.Movie) {
            val streams = mediaFor(source, document)
            if (streams.isEmpty()) return webPlayerResponse(source, url, detail, source.browserPlaybackPage(document))
            return newMovieLoadResponse(detail.title, url, contentType, url) {
                posterUrl = detail.poster
                plot = detail.plot
            }
        }
        if (detail.chapters.isEmpty()) return webPlayerResponse(source, url, detail)
        val episodes = detail.chapters.map { chapter ->
            newEpisode(webPlaybackData(chapter.url), {
                name = chapter.title
                season = chapter.season
                episode = chapter.number
            }, fix = false)
        }
        return newTvSeriesLoadResponse(detail.title, url, TvType.CustomMedia, episodes) {
            posterUrl = detail.poster
            plot = listOfNotNull(detail.plot, "Bölümler uygulama içindeki web oynatıcıda açılır.").joinToString("\n\n")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val page = data.removePrefix(WEB_DATA)
        val source = sources.firstOrNull { it.handles(page) } ?: return false
        if (page != data) return !isCasting && openWebPlayer(source.label, page)

        val document = pageLoader(source, page) ?: return false
        val streams = mediaFor(source, document)
        streams.forEach { stream ->
            val type = when (stream.format) {
                MediaFormat.HLS -> ExtractorLinkType.M3U8
                MediaFormat.DASH -> ExtractorLinkType.DASH
                MediaFormat.VIDEO -> ExtractorLinkType.VIDEO
            }
            callback(newExtractorLink(name, source.label, stream.url, type) {
                referer = stream.referer ?: document.location()
                stream.resolution?.let { quality = it }
            })
        }
        return streams.isNotEmpty()
    }

    private suspend fun mediaFor(source: WebCatalog, document: Document): List<Media> =
        (publicMedia(document) + source.embeddedMedia(document)).distinctBy(Media::url)

    private suspend fun webPlayerResponse(
        source: WebCatalog,
        url: String,
        detail: Detail?,
        playbackPage: String = url,
    ): LoadResponse {
        val title = detail?.title ?: URI(url).path.trimEnd('/').substringAfterLast('/')
            .replace('-', ' ').replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
            .ifBlank { source.label }
        return newMovieLoadResponse(title, url, TvType.CustomMedia, webPlaybackData(playbackPage)) {
            posterUrl = detail?.poster
            val message = if (detail == null) {
                "Kaynak ayrıntıları alınamadı. Oynat ile uygulama içindeki web oynatıcıyı açabilirsiniz."
            } else {
                "Oynat, kaynak oynatıcısını uygulama içinde açar."
            }
            plot = listOfNotNull(detail?.plot, message).joinToString("\n\n")
        }
    }

    private fun webPlaybackData(url: String): String = WEB_DATA + url

    private fun item(card: Card, source: WebCatalog): SearchResponse = when {
        card == (source as? BroadcasterCatalog)?.liveCard ->
            newLiveSearchResponse(card.title, card.url, TvType.Live, fix = false) { posterUrl = card.poster }
        contentType == TvType.Movie ->
            newMovieSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        else ->
            newTvSeriesSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
    }
}
