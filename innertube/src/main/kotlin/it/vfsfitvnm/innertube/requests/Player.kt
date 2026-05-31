package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

// Direct binding to your custom extractor package
import com.github.MetrolistGroup.MetrolistExtractor.NZiksExtractor 

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Primary request using standard InnerTube configurations (e.g., ANDROID_MUSIC)
    // Removed the restrictive .mask() to ensure metadata fields survive if playback is healthy
    val response = client.post(player) {
        setBody(body)
    }.body<PlayerResponse>()

    // Check if the native response contains streamable formats directly
    if (response.playabilityStatus?.status == "OK" && response.streamingData?.adaptiveFormats?.isNotEmpty() == true) {
        return@runCatchingNonCancellable response
    }

    // Secondary request utilizing embedded context to fetch complete fallback metadata
    val safePlayerResponse = client.post(player) {
        setBody(
            body.copy(
                context = it.vfsfitvnm.innertube.models.Context.DefaultAgeRestrictionBypass.copy(
                    thirdParty = it.vfsfitvnm.innertube.models.Context.ThirdParty(
                        embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                    )
                ),
            )
        )
    }.body<PlayerResponse>()

    // Use your custom local N-Ziks Extractor to salvage streams if YouTube returns unplayable signatures
    runCatching {
        // Fetch fresh stream endpoints directly from your custom engine
        val nziksStreamInfo = NZiksExtractor.getStreamInfo(body.videoId)
        
        // Base our object on safePlayerResponse to preserve complete UI metadata (thumbnails, details)
        safePlayerResponse.copy(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK"),
            streamingData = PlayerResponse.StreamingData(
                adaptiveFormats = nziksStreamInfo.audioStreams.map { stream ->
                    PlayerResponse.AdaptiveFormat(
                        url = stream.url,
                        bitrate = stream.bitrate,
                        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                        contentLength = stream.contentLength
                    )
                }
            )
        )
    }.getOrElse {
        // If the custom extractor fails or encounters structural anomalies, fallback to the safest available meta container
        if (safePlayerResponse.playabilityStatus?.status == "OK") safePlayerResponse else response
    }
}
