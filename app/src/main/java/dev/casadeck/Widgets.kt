package dev.casadeck

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.work.*
import coil.imageLoader
import coil.request.ImageRequest
import dev.casadeck.data.*
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Home-screen widgets. All of them are drawn by [WidgetWorker]; providers only ask for a refresh. */
open class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) = Widgets.refresh(ctx)
    override fun onDisabled(ctx: Context) = Widgets.refresh(ctx)
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == Widgets.ACTION_REFRESH) Widgets.refresh(ctx)
    }
}

class StatsWidget : BaseWidget()
class StatusWidget : BaseWidget()
class AppsWidget : BaseWidget()

object Widgets {
    const val ACTION_REFRESH = "dev.casadeck.WIDGET_REFRESH"
    private const val NOW = "widgets-now"
    private const val PERIODIC = "widgets"
    private val kinds = listOf(StatsWidget::class.java, StatusWidget::class.java, AppsWidget::class.java)

    fun ids(ctx: Context, cls: Class<*>): IntArray = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    /** Redraws now and keeps a 15-min refresh while at least one widget is on the home screen. */
    fun refresh(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (kinds.none { ids(ctx, it).isNotEmpty() }) return run { wm.cancelUniqueWork(PERIODIC) }
        val net = Constraints(requiredNetworkType = NetworkType.CONNECTED)
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetWorker>().build())
        wm.enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).setConstraints(net).build(),
        )
    }

    private fun openApp(ctx: Context, code: Int, app: String? = null) = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { app?.let { putExtra(MainActivity.EXTRA_APP, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun refreshIntent(ctx: Context, cls: Class<*>) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, cls).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pct(used: Long, total: Long) = if (total > 0) (used * 100 / total).toInt() else 0

    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val u = listOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "%.0f %s".format(v, u[i]) else "%.1f %s".format(v, u[i])
    }

    /** [error] set when the server could not be read; then numbers are hidden. */
    suspend fun render(ctx: Context, s: SysStats?, apps: List<CasaApp>?, error: String?, prefs: Prefs) {
        val m = AppWidgetManager.getInstance(ctx)
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val host = prefs.serverUrl.substringAfter("://").ifEmpty { "CasaOS" }
        val running = apps?.count { it.running }
        val status = error ?: "Online · $time"
        val dot = if (error == null) 0xFF2BD47D.toInt() else 0xFFFF5A5F.toInt()

        ids(ctx, StatsWidget::class.java).takeIf { it.isNotEmpty() }?.let { ids ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_stats)
            v.setTextViewText(R.id.host, host)
            v.setTextViewText(R.id.status, status)
            v.setTextColor(R.id.dot, dot)
            val cpu = s?.cpu?.toInt() ?: 0
            val ram = s?.let { pct(it.memUsed, it.memTotal) } ?: 0
            val disk = s?.let { pct(it.diskUsed, it.diskTotal) } ?: 0
            v.setProgressBar(R.id.cpu_bar, 100, cpu, false)
            v.setProgressBar(R.id.ram_bar, 100, ram, false)
            v.setProgressBar(R.id.disk_bar, 100, disk, false)
            v.setTextViewText(R.id.cpu_val, s?.let { "$cpu%" + (it.temp?.let { t -> " · $t°C" } ?: "") } ?: "—")
            v.setTextViewText(R.id.ram_val, s?.let { "${bytes(it.memUsed)} / ${bytes(it.memTotal)}" } ?: "—")
            v.setTextViewText(R.id.disk_val, s?.let { "${bytes(it.diskTotal - it.diskUsed)} liberi" } ?: "—")
            v.setOnClickPendingIntent(R.id.root, openApp(ctx, 1))
            v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx, StatsWidget::class.java))
            m.updateAppWidget(ids, v)
        }

        ids(ctx, StatusWidget::class.java).takeIf { it.isNotEmpty() }?.let { ids ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_status)
            v.setTextViewText(R.id.host, host)
            v.setTextColor(R.id.dot, dot)
            v.setTextViewText(R.id.line1, if (s == null) (error ?: "—") else "CPU ${s.cpu.toInt()}%" + (s.temp?.let { " · $it°C" } ?: ""))
            v.setTextViewText(
                R.id.line2,
                listOfNotNull(running?.let { "$it/${apps.size} app" }, s?.let { "${bytes(it.diskTotal - it.diskUsed)} liberi" })
                    .joinToString(" · ").ifEmpty { time },
            )
            v.setOnClickPendingIntent(R.id.root, openApp(ctx, 2))
            m.updateAppWidget(ids, v)
        }

        ids(ctx, AppsWidget::class.java).takeIf { it.isNotEmpty() }?.let { ids ->
            val v = RemoteViews(ctx.packageName, R.layout.widget_apps)
            v.setTextViewText(R.id.status, if (apps == null) (error ?: "—") else "$running/${apps.size} attive")
            v.setOnClickPendingIntent(R.id.title, openApp(ctx, 3))
            v.removeAllViews(R.id.grid)
            prefs.topApps(apps.orEmpty()).take(8).chunked(4).forEachIndexed { r, rowApps ->
                val row = RemoteViews(ctx.packageName, R.layout.widget_app_row)
                rowApps.forEachIndexed { c, a ->
                    val item = RemoteViews(ctx.packageName, R.layout.widget_app_item)
                    item.setTextViewText(R.id.label, a.title)
                    icon(ctx, a.icon)?.let { item.setImageViewBitmap(R.id.icon, it) }
                    item.setInt(R.id.icon, "setImageAlpha", if (a.running) 255 else 110)
                    item.setOnClickPendingIntent(R.id.item, openApp(ctx, 100 + r * 4 + c, a.id))
                    row.addView(R.id.row, item)
                }
                // keep columns aligned on a short last row
                repeat(4 - rowApps.size) { row.addView(R.id.row, RemoteViews(ctx.packageName, R.layout.widget_app_item).apply { setViewVisibility(R.id.item, android.view.View.INVISIBLE) }) }
                v.addView(R.id.grid, row)
            }
            m.updateAppWidget(ids, v)
        }
    }

    private suspend fun icon(ctx: Context, url: String) = if (url.isBlank()) null else runCatching {
        ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(128).allowHardware(false).build()).drawable?.toBitmap(128, 128)
    }.getOrNull()
}

class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.access.isEmpty()) {
            Widgets.render(applicationContext, null, null, "Accedi a CasaDeck", prefs)
            return Result.success()
        }
        val api = CasaApi(prefs)
        try {
            Widgets.render(applicationContext, api.stats(), api.apps(), null, prefs)
        } catch (e: CasaException) {
            Widgets.render(applicationContext, null, null, if (e is Unauthorized) "Accedi a CasaDeck" else "Offline", prefs)
        }
        return Result.success()
    }
}
