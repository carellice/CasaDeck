package dev.casadeck

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import dev.casadeck.data.CasaApi
import dev.casadeck.data.CasaException
import dev.casadeck.data.Prefs
import java.util.concurrent.TimeUnit

/** Periodic background check (WorkManager minimum: 15 min). Notifies only on changes. */
class MonitorWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.notify || prefs.access.isEmpty()) return Result.success()
        val api = CasaApi(prefs)
        val alerts = mutableSetOf<String>()
        try {
            val s = api.stats()
            if (s.diskTotal > 0 && s.diskUsed * 100 / s.diskTotal >= 90) alerts += "disk"
            if ((s.temp ?: 0) >= 75) alerts += "temp"
            val apps = api.apps()
            val nowRunning = apps.filter { it.running }.map { it.id }.toSet()
            apps.filter { it.id in prefs.running && it.id !in nowRunning }.forEach {
                notify(it.id.hashCode(), "${it.title} si è fermata", "L'app non è più in esecuzione su CasaOS")
            }
            prefs.running = nowRunning
            val new = alerts - prefs.alerts
            if ("disk" in new) notify(1, "Disco quasi pieno", "Spazio usato oltre il 90%")
            if ("temp" in new) notify(2, "Temperatura alta", "CPU a ${s.temp}°C")
        } catch (e: CasaException) {
            alerts += "offline"
            if ("offline" !in prefs.alerts) notify(3, "CasaOS non raggiungibile", e.message ?: "")
        }
        prefs.alerts = alerts
        return Result.success()
    }

    private fun notify(id: Int, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val tap = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(ctx).notify(
            id,
            NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_fg).setContentTitle(title)
                .setContentText(text).setContentIntent(tap).setAutoCancel(true).build(),
        )
    }

    companion object {
        const val CHANNEL = "alerts"
        private const val WORK = "monitor"

        fun schedule(ctx: Context, on: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!on) return run { wm.cancelUniqueWork(WORK) }
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build(),
            )
        }
    }
}
