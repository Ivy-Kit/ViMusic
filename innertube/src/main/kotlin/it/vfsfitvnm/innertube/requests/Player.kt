package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    // Primary attempt: Android Music client (returns direct URLs, no cipher needed)
    val androidResponse = client.post(player) {
        setBody(body.copy(context = Context.DefaultAndroid))
    }.body<PlayerResponse>()

    if (androidResponse.playabilityStatus?.status == "OK" &&
        !androidResponse.streamingData?.adaptiveFormats.isNullOrEmpty()) {
        return@runCatchingNonCancellable androidResponse
    }

    // Fallback 1: Try iOS Music client
    val iosResponse = runCatching {
        client.post(player) {
            setBody(body.copy(context = Context.DefaultIos))
        }.body<PlayerResponse>()
    }.getOrNull()

    if (iosResponse?.playabilityStatus?.status == "OK" &&
        !iosResponse.streamingData?.adaptiveFormats.isNullOrEmpty()) {
        return@runCatchingNonCancellable iosResponse
    }

    // Fallback 2: Embedded/age-restriction bypass context
    val embeddedResponse = client.post(player) {
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

    // Return whichever fallback has a good status, preferring the embedded one
    if (embeddedResponse.playabilityStatus?.status == "OK" &&
        !embeddedResponse.streamingData?.adaptiveFormats.isNullOrEmpty()) {
        embeddedResponse
    } else if (iosResponse?.playabilityStatus?.status == "OK") {
        iosResponse
    } else {
        // Return the original android response so callers can inspect the error status
        androidResponse
    }
}
