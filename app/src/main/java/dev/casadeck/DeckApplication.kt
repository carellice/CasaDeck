package dev.casadeck

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class DeckApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(MonitorWorker.CHANNEL, "Avvisi server", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(UploadWorker.CHANNEL, "Trasferimenti", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    // SVG support: some app icons (e.g. Jellyfin) are SVGs, which Coil skips by default.
    override fun newImageLoader() = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}
