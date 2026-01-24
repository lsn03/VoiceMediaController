package ru.lsn03.voicemediacontroller.media

import android.media.MediaMetadata

class NowPlayingGatewayImpl (
    private val mediaControllerProvider: MediaControllerProvider
): NowPlayingGateway {
    override fun nowPlayingPhrase(): String {
        val controller = mediaControllerProvider.getTopMediaController()
        if (controller == null) {
            return "Не вижу активный плеер";
        }

        val md = controller.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

        val t = title?.takeIf { it.isNotBlank() }
        val a = artist?.takeIf { it.isNotBlank() }

      return when {
            a != null && t != null -> "Сейчас играет: $a — $t"
            t != null -> "Сейчас играет: $t"
            else -> "Не удалось получить название трека"
        }
    }
}