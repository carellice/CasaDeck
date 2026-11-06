package dev.casadeck

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import dev.casadeck.data.CasaApi
import dev.casadeck.data.CasaException
import dev.casadeck.data.Prefs
import dev.casadeck.data.Unauthorized

/**
 * Uploads in a foreground worker: keeps going with the app closed or the screen locked.
 * Input: "dir" (CasaOS folder) plus either "uris" (files) or "tree" (a whole folder picked with SAF).
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private class Item(val uri: Uri, val name: String, val size: Long, val relative: String)

    private val nm = NotificationManagerCompat.from(ctx)

    override suspend fun doWork(): Result {
        val dir = inputData.getString("dir") ?: return Result.failure()
        val tree = inputData.getString("tree")?.let(Uri::parse)
        val uris = inputData.getStringArray("uris").orEmpty().map(Uri::parse)
        // may fail when started from the background (Android 12+); the upload still runs while the process lives
        runCatching { setForeground(foreground("Preparazione…", 0, true)) }

        val items = try {
            if (tree != null) walk(tree) else uris.mapNotNull(::describe)
        } catch (e: Exception) {
            done("Impossibile leggere i file selezionati"); release(tree, uris); return Result.failure()
        }
        val api = CasaApi(Prefs(applicationContext))
        val cr = applicationContext.contentResolver
        var ok = 0
        var lastPct = -1
        for ((i, item) in items.withIndex()) {
            if (isStopped) break
            val label = if (items.size > 1) "${item.name} (${i + 1}/${items.size})" else item.name
            lastPct = -1
            try {
                api.upload(dir, item.name, item.size, { cr.openInputStream(item.uri)!! }, item.relative) { sent ->
                    val p = if (item.size > 0) sent.toFloat() / item.size else 1f
                    val pct = (p * 100).toInt()
                    if (pct != lastPct) {
                        lastPct = pct
                        setProgressAsync(workDataOf("name" to label, "p" to p))
                        notifyProgress(label, pct)
                    }
                }
                ok++
            } catch (e: Unauthorized) {
                break
            } catch (e: CasaException) {
                // skip the file, keep uploading the rest
            }
        }
        release(tree, uris)
        val failed = items.size - ok
        done(
            when {
                isStopped -> "Caricamento annullato ($ok/${items.size})"
                failed == 0 -> "$ok file caricati"
                else -> "$ok file caricati, $failed non riusciti"
            },
        )
        return Result.success()
    }

    private fun describe(uri: Uri): Item? {
        var name = "file"
        var size = -1L
        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return if (size < 0) null else Item(uri, name, size, name)
    }

    /** Lists every file under [tree]; relative paths start with the picked folder's name. Empty subfolders are skipped. */
    private fun walk(tree: Uri): List<Item> {
        val cr = applicationContext.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val rootName = cr.query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), cols, null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(1) else null } ?: "Cartella"
        val out = mutableListOf<Item>()
        fun rec(docId: String, rel: String) {
            cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1)
                    if (c.getString(2) == Document.MIME_TYPE_DIR) rec(c.getString(0), "$rel/$name")
                    else out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), name, c.getLong(3), "$rel/$name")
                }
            }
        }
        rec(rootId, rootName)
        return out
    }

    private fun release(tree: Uri?, uris: List<Uri>) {
        val cr = applicationContext.contentResolver
        (listOfNotNull(tree) + uris).forEach { runCatching { cr.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    private fun notification(text: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Caricamento su CasaOS")
            .setContentText(text)
            .setProgress(100, pct, indeterminate)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addAction(0, "Annulla", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()

    private fun foreground(text: String, pct: Int, indeterminate: Boolean) =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF, notification(text, pct, indeterminate), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF, notification(text, pct, indeterminate))

    @Suppress("MissingPermission") // without the permission Android just hides it
    private fun notifyProgress(text: String, pct: Int) = runCatching { nm.notify(NOTIF, notification(text, pct, false)) }

    @Suppress("MissingPermission")
    private fun done(text: String) = runCatching {
        nm.notify(
            NOTIF + 1,
            NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Caricamento su CasaOS").setContentText(text).setAutoCancel(true).build(),
        )
    }

    companion object {
        const val WORK = "upload"
        const val CHANNEL = "transfers"
        private const val NOTIF = 1000

        /** Queued one after another; persistable read grants keep the files readable after the app closes. */
        fun enqueue(ctx: Context, dir: String, uris: List<Uri> = emptyList(), tree: Uri? = null) {
            val cr = ctx.contentResolver
            (listOfNotNull(tree) + uris).forEach { runCatching { cr.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            // WorkManager input is capped at 10 KB, so big selections go in batches
            val batches = if (tree != null) listOf(workDataOf("dir" to dir, "tree" to tree.toString()))
            else uris.chunked(40).map { b -> workDataOf("dir" to dir, "uris" to b.map(Uri::toString).toTypedArray()) }
            val wm = WorkManager.getInstance(ctx)
            batches.forEach { data ->
                wm.enqueueUniqueWork(
                    WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data)
                        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build(),
                )
            }
        }
    }
}
