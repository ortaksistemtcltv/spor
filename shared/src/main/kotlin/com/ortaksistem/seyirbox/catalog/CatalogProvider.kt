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

internal class CatalogProvider(
    displayName: String,
    private val contentType: TvType,
    private val sources: List<WebCatalog>,
) : MainAPI() {
    override var name = displayName
    override var mainUrl = sources.first().home
    override var lang = "tr"
    override val supportedTypes = setOf(contentType)
    override val hasMainPage = true
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
        return sources.flatMap { source ->
            source.search(term).map(::item)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val source = sources.firstOrNull { it.supports(url) } ?: return null
        val document = source.fetch(url) ?: return null
        val detail = source.detail(document) ?: return null
        if (contentType == TvType.Movie) {
            return newMovieLoadResponse(detail.title, url, contentType, url) {
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
        val source = sources.firstOrNull { it.supports(data) } ?: return false
        val document = source.fetch(data) ?: return false
        val streams = publicMedia(document)
        streams.forEach { stream ->
            val type = when (stream.format) {
                MediaFormat.HLS -> ExtractorLinkType.M3U8
                MediaFormat.DASH -> ExtractorLinkType.DASH
                MediaFormat.VIDEO -> ExtractorLinkType.VIDEO
            }
            callback(newExtractorLink(name, source.label, stream.url, type))
        }
        return streams.isNotEmpty()
    }

    private fun item(card: Card): SearchResponse =
        if (contentType == TvType.Movie) {
            newMovieSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        } else {
            newTvSeriesSearchResponse(card.title, card.url, contentType, fix = false) { posterUrl = card.poster }
        }
}
