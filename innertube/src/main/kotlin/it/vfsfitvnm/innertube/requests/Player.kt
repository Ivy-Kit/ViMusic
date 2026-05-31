package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.Serializable

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Primary request using standard context configurations (e.g., ANDROID_MUSIC)
    val response = client.post(player) {
        setBody(body)
        mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
    }.body<PlayerResponse>()

    if (response.playabilityStatus?.status == "OK") {
        return@runCatchingNonCancellable response
    }

    // Secondary request utilizing embed client structure to bypass age/region locks
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
        mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
    }.body<PlayerResponse>()

    // If the internal YouTube embed bypass fails, return original response to let upper layers handle errors
    if (safePlayerResponse.playabilityStatus?.status != "OK") {
        return@runCatchingNonCancellable response
    }

    // Fallback streaming mirror parsing block
    @Serializable
    data class AudioStream(
        val url: String,
        val bitrate: Long
    )

    @Serializable
    data class PipedResponse(
        val audioStreams: List<AudioStream>
    )

    runCatching {
        // NOTE: Keep this endpoint updated to an active, reliable fallback provider
        val fallbackInstance = "pipedapi.kavin.rocks" 
        
        val audioStreams = client.get("https://$fallbackInstance/streams/${body.videoId}") {
            contentType(ContentType.Application.Json)
        }.body<PipedResponse>().audioStreams

        safePlayerResponse.copy(
            streamingData = safePlayerResponse.streamingData?.copy(
                adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats?.map { adaptiveFormat ->
                    adaptiveFormat.copy(
                        url = audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.url ?: adaptiveFormat.url
                    )
                }
            )
        )
    }.getOrElse {
        // If external API structure fails or times out, fall back safely to the InnerTube response 
        safePlayerResponse
    }
}
