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
    private val openInBrowser: suspend (String, String) -> Boolean = { title, url -> BrowserPlayback.open(title, url) },
) : MainAPI() {
    override var name = displayName
    override var mainUrl = sources.first().home
    override var lang = "tr"
    override val supportedTypes = setOf(contentType)
    override val hasMainPage = true
    private companion object {
        val searchRequests = Semaphore(6)
        const val BROWSER_DATA = "seyirbox-web:"
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
            next = source.fetch(url)?.let(source::nextPage)
        }
        val document = next?.takeIf(visited::add)?.let { source.fetch(it) }
            ?: return newHomePageResponse(request.name, emptyList(), hasNext = false)
        val items = source.cards(document).map(::item)
        val hasNext = items.isNotEmpty() && source.nextPage(document)?.let { it !in visited } == true
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return coroutineScope {
            sources.map { source ->
                async { searchRequests.withPermit { source.search(term).map(::item) } }
            }.awaitAll().flatten()
        }
    }

    fun handles(url: String): Boolean = sources.any { it.handles(url.removePrefix(BROWSER_DATA)) }

    override suspend fun load(url: String): LoadResponse? {
        val source = sources.firstOrNull { it.handles(url) } ?: return null
        val document = pageLoader(source, url)
        val detail = document?.let { source.detail(it) }
        if (detail == null) {
            if (!source.isDetailUrl(url)) return null
            return browserPage(source, url, null)
        }
        if (contentType == TvType.Movie) {
            val streams = mediaFor(source, document)
            if (streams.isEmpty()) return browserPage(source, url, detail, source.browserPlaybackPage(document))
            return newMovieLoadResponse(detail.title, url, contentType, url) {
                posterUrl = detail.poster
                plot = detail.plot
            }
        }
        if (detail.chapters.isEmpty()) return browserPage(source, url, detail)
        val episodes = detail.chapters.map { chapter ->
            newEpisode(browserData(chapter.url), {
                name = chapter.title
                season = chapter.season
                episode = chapter.number
            }, fix = false)
        }
        return newTvSeriesLoadResponse(detail.title, url, TvType.CustomMedia, episodes) {
            posterUrl = detail.poster
            plot = listOfNotNull(detail.plot, "Bölümler kaynak sitesinde tarayıcıyla açılır.").joinToString("\n\n")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val page = data.removePrefix(BROWSER_DATA)
        val source = sources.firstOrNull { it.handles(page) } ?: return false
        if (page != data) return !isCasting && openInBrowser(source.label, page)

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

    private suspend fun browserPage(
        source: WebCatalog,
        url: String,
        detail: Detail?,
        playbackPage: String = url,
    ): LoadResponse {
        val title = detail?.title ?: URI(url).path.trimEnd('/').substringAfterLast('/')
            .replace('-', ' ').replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
            .ifBlank { source.label }
        return newMovieLoadResponse(title, url, TvType.CustomMedia, browserData(playbackPage)) {
            posterUrl = detail?.poster
            val message = if (detail == null) {
                "Kaynak sayfası uygulama içinden okunamadı. Oynat, sayfayı tarayıcıda açar."
            } else {
                "Oynat, içeriği kaynak sitesinin oynatıcısında açar."
            }
            plot = listOfNotNull(detail?.plot, message).joinToString("\n\n")
        }
    }

    private fun browserData(url: String): String = BROWSER_DATA + url

    private fun item(card: Card): SearchResponse =
        if (contentType == TvType.Movie) {
            newMovieSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        } else {
            newTvSeriesSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        }
}
