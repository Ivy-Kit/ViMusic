package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import com.github.MetrolistGroup.MetrolistExtractor.NZiksExtractor

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Primary request using standard InnerTube configurations
    val response = client.post(player) {
        setBody(body)
    }.body<PlayerResponse>()

    // Check if the native response contains streamable formats directly
    if (response.playabilityStatus?.status == "OK" && !response.streamingData?.adaptiveFormats.isNullOrEmpty()) {
        return@runCatchingNonCancellable response
    }

    // Secondary request utilizing embedded context to fetch complete fallback metadata
    val safePlayerResponse = client.post(player) {
        setBody(
            body.copy(
                context = Context.DefaultAgeRestrictionBypass.copy(
                    thirdParty = Context.ThirdParty(
                        embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                    )
                ),
            )
        )
    }.body<PlayerResponse>()

    // Use custom N-Ziks Extractor to salvage streams if YouTube returns unplayable signatures
    runCatching {
        val nziksStreamInfo = NZiksExtractor.getStreamInfo(body.videoId)
        val audioStreams = nziksStreamInfo.audioStreams
        
        require(audioStreams.isNotEmpty()) { "NZiksExtractor returned no audio streams" }
        
        // Base our object on safePlayerResponse to preserve complete UI metadata
        safePlayerResponse.copy(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK"),
            streamingData = PlayerResponse.StreamingData(
                adaptiveFormats = audioStreams.map { stream ->
                    PlayerResponse.AdaptiveFormat(
                        url = stream.url,
                        bitrate = stream.bitrate ?: -1,
                        mimeType = stream.mimeType ?: "audio/mp4; codecs=\"mp4a.40.2\"",
                        contentLength = stream.contentLength
                    )
                }
            )
        )
    }.getOrElse { 
        // If the custom extractor fails, fallback to the safest available response
        if (safePlayerResponse.playabilityStatus?.status == "OK") safePlayerResponse else response
    }
}
