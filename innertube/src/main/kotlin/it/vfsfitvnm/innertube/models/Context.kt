package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val platform: String,
        val hl: String = "en",
        val visitorData: String = "CgtEUlRINDFjdm1YayjX1pSaBg%3D%3D",
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    companion object {
        val DefaultWeb = Context(
            client = Client(
                clientName = "WEB_REMIX",
                clientVersion = "1.20231101.01.00",
                platform = "DESKTOP",
            )
        )

        val DefaultAndroid = Context(
            client = Client(
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.27.52",
                platform = "MOBILE",
                androidSdkVersion = 30,
                userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
            )
        )

        val DefaultAgeRestrictionBypass = Context(
            client = Client(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                platform = "TV"
            )
        )

        // iOS client — useful as an alternative when Android gets blocked
        val DefaultIos = Context(
            client = Client(
                clientName = "IOS_MUSIC",
                clientVersion = "6.21",
                platform = "MOBILE",
                userAgent = "com.google.ios.youtubemusic/6.21 (iPhone14,3; U; CPU iPhone OS 16_7 like Mac OS X)"
            )
        )
    }
}
