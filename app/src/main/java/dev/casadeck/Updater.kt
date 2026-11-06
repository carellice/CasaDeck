package dev.casadeck

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Self-update from the GitHub releases of this repo: the release must carry an .apk asset. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/carellice/CasaDeck/releases/latest"
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    data class Release(val version: String, val notes: String, val apkUrl: String)

    fun current(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** "1.10" > "1.9"; a leading "v" is ignored. */
    fun newer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** @return the latest release, or null when it has no apk. Throws on network errors. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        val o = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            JSONObject(r.body!!.string())
        }
        val assets = o.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        Release(o.optString("tag_name").trimStart('v', 'V'), o.optString("body"), apk.getString("browser_download_url"))
    }

    suspend fun download(ctx: Context, url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "CasaDeck.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
        }
        out
    }

    /** Hands the apk to the system installer, which also asks for the "unknown sources" permission if needed. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
