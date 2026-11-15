package com.ortaksistem.seyirbox.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

internal data class Card(val title: String, val url: String, val poster: String?)
internal data class Chapter(val title: String, val url: String, val season: Int?, val number: Int?)
internal data class Detail(
    val title: String,
    val poster: String?,
    val plot: String?,
    val chapters: List<Chapter> = emptyList(),
)
internal enum class MediaFormat { HLS, DASH, VIDEO }
internal data class Media(
    val url: String,
    val format: MediaFormat,
    val referer: String? = null,
    val resolution: Int? = null,
)

internal abstract class WebCatalog(val label: String, val home: String) {
    private val hosts = setOf(URI(home).host, URI(home).host.removePrefix("www."))
    protected open val responseCharset = "UTF-8"
    abstract val firstPage: String
    abstract fun searchPage(query: String): String
    abstract fun cards(document: Document): List<Card>
    open suspend fun search(query: String): List<Card> =
        fetch(searchPage(query))?.let(::cards).orEmpty()

    protected suspend fun searchFirstPage(query: String): List<Card> {
        val term = searchText(query)
        if (term.isBlank()) return emptyList()
        return fetch(firstPage)?.let(::cards).orEmpty().filter { searchText(it.title).contains(term) }
    }

    abstract suspend fun detail(document: Document): Detail?

    open suspend fun embeddedMedia(document: Document): List<Media> = emptyList()

    open fun browserPlaybackPage(document: Document): String = document.location()

    open fun isDetailUrl(url: String): Boolean = handles(url) &&
        httpsUri(url)?.path?.trim('/')?.isNotBlank() == true

    fun supports(url: String): Boolean {
        val uri = httpsUri(url) ?: return false
        return uri.host in hosts && uri.port in setOf(-1, 443)
    }

    open fun handles(url: String): Boolean = supports(url)

    fun nextPage(document: Document): String? {
        val current = document.location().substringBefore('#')
        return document.select("link[rel=next], a[rel=next], a.next, a.nextpostslink").asSequence()
            .map { it.absUrl("href").substringBefore('#') }
            .firstOrNull { it != current && supports(it) }
    }

    suspend fun fetch(url: String): Document? =
        read(url)?.let { Jsoup.parse(it.body, it.url) }

    protected data class Page(val url: String, val body: String)

    protected suspend fun read(url: String, searchRequest: Boolean = false): Page? = withContext(Dispatchers.IO) {
        if (!supports(url)) return@withContext null
        try {
            fetchPage(url, searchRequest)
        } catch (_: IOException) {
            null
        }
    }

    private fun fetchPage(url: String, searchRequest: Boolean): Page? {
        var address = URI(url)
        repeat(4) {
            val connection = address.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                if (searchRequest) {
                    connection.setRequestProperty("Accept", "application/json,text/html")
                    connection.setRequestProperty("X-Requested-With", "fetch")
                } else {
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: return null
                    val redirected = runCatching { address.resolve(location) }.getOrNull() ?: return null
                    if (!supports(redirected.toString())) return null
                    address = redirected
                    return@repeat
                }
                if (status !in 200..299) return null
                val html = connection.inputStream.use { readPage(it) } ?: return null
                return Page(address.toString(), html)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun readPage(input: InputStream): String? {
        val buffer = ByteArray(8192)
        val page = ByteArrayOutputStream()
        while (page.size() <= MAX_PAGE_BYTES) {
            val read = input.read(buffer)
            if (read < 0) return page.toString(responseCharset)
            page.write(buffer, 0, read)
        }
        return null
    }

    protected fun encode(query: String): String = URLEncoder.encode(query, "UTF-8")

    protected fun Card.valid(): Boolean = title.isNotBlank() && supports(url)

    companion object {
        private const val MAX_PAGE_BYTES = 4 * 1024 * 1024
    }
}

private fun httpsUri(url: String): URI? =
    runCatching { URI(url) }.getOrNull()?.takeIf {
        it.scheme == "https" && it.host != null && it.userInfo == null
    }

internal fun isHttpsUrl(url: String): Boolean = httpsUri(url) != null

internal fun imageOf(element: Element?): String? {
    if (element == null) return null
    return sequenceOf("data-src", "data-lazy-src", "src")
        .map(element::absUrl).firstOrNull(::isHttpsUrl)
}

private fun searchText(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

internal fun publicMedia(document: Document): List<Media> =
    document.select("video[src], video[data-src], video source[src], video source[data-src]")
        .flatMap { element ->
            val mime = element.attr("type").substringBefore(';').trim().lowercase(Locale.ROOT)
            listOfNotNull(
                mediaAt(element.absUrl("src"), mime),
                mediaAt(element.absUrl("data-src"), mime),
            )
        }.distinctBy(Media::url)

internal fun mediaAt(url: String, mime: String): Media? {
    val uri = httpsUri(url) ?: return null
    val path = uri.path.orEmpty().lowercase(Locale.ROOT)
    val format = when {
        path.endsWith(".m3u8") || mime in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl") -> MediaFormat.HLS
        path.endsWith(".mpd") || mime == "application/dash+xml" -> MediaFormat.DASH
        path.endsWith(".mp4") || path.endsWith(".webm") || mime == "video/mp4" || mime == "video/webm" -> MediaFormat.VIDEO
        else -> return null
    }
    return Media(url, format)
}
