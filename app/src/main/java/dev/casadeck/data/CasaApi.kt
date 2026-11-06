package dev.casadeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

open class CasaException(message: String) : Exception(message)
class Unauthorized : CasaException("Sessione scaduta, accedi di nuovo")

data class SysStats(
    val cpu: Float, val cores: Int, val temp: Int?,
    val memUsed: Long, val memTotal: Long,
    val diskUsed: Long, val diskTotal: Long,
    val netRx: Long, val netTx: Long,
)

data class CasaApp(val id: String, val title: String, val icon: String, val url: String?, val running: Boolean)
data class FileEntry(val name: String, val path: String, val isDir: Boolean, val size: Long, val date: String)

/** Thin client for the CasaOS REST API (v0.4.x). Auth header is the raw JWT, no "Bearer". */
class CasaApi(private val prefs: Prefs) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    /** @return true when CasaOS already has a user (setup done in the web UI). */
    suspend fun probe(base: String): Boolean {
        val o = exec(Request.Builder().url("$base/v1/users/status").build())
        val data = o.optJSONObject("data") ?: throw CasaException("Non sembra un server CasaOS")
        return data.optBoolean("initialized", true)
    }

    suspend fun login(base: String, user: String, pass: String) {
        val body = JSONObject().put("username", user).put("password", pass).toString().toRequestBody(json)
        val o = try {
            exec(Request.Builder().url("$base/v1/users/login").post(body).build())
        } catch (e: Unauthorized) {
            throw CasaException("Utente o password errati")
        }
        val tok = o.optJSONObject("data")?.optJSONObject("token")
            ?: throw CasaException(o.optString("message").ifEmpty { "Login fallito" })
        prefs.serverUrl = base
        prefs.username = user
        prefs.access = tok.getString("access_token")
        prefs.refresh = tok.getString("refresh_token")
    }

    suspend fun stats(): SysStats {
        val d = authed("/v1/sys/utilization").optJSONObject("data") ?: JSONObject()
        val cpu = d.optJSONObject("cpu") ?: JSONObject()
        val mem = d.optJSONObject("mem") ?: JSONObject()
        val disk = d.optJSONObject("sys_disk") ?: JSONObject()
        val net = d.optJSONArray("net") ?: JSONArray()
        var rx = 0L
        var tx = 0L
        for (i in 0 until net.length()) {
            val n = net.getJSONObject(i)
            val name = n.optString("name")
            if (name == "lo" || name.startsWith("docker") || name.startsWith("veth") || name.startsWith("br-")) continue
            rx += n.optLong("bytesRecv")
            tx += n.optLong("bytesSent")
        }
        return SysStats(
            cpu.optDouble("percent", 0.0).toFloat(), cpu.optInt("num"), cpu.optInt("temperature").takeIf { it > 0 },
            mem.optLong("used"), mem.optLong("total"), disk.optLong("used"), disk.optLong("size"), rx, tx,
        )
    }

    suspend fun apps(): List<CasaApp> {
        val arr = authed("/v2/app_management/web/appgrid").optJSONArray("data") ?: JSONArray()
        val host = prefs.serverUrl.toHttpUrl().host
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val t = o.opt("title")
            val title = when (t) {
                is JSONObject -> t.optString("en_us").ifEmpty { t.keys().asSequence().firstOrNull()?.let(t::optString).orEmpty() }
                else -> o.optString("title")
            }.ifEmpty { o.optString("name") }
            val port = o.optString("port")
            val h = o.optString("hostname").ifEmpty { host }
            val scheme = o.optString("scheme").ifEmpty { "http" }
            val index = o.optString("index").let { if (it.startsWith("/")) it else "/$it" }
            // icons can be relative to the CasaOS host (custom/imported apps)
            val icon = o.optString("icon").let { if (it.startsWith("/")) prefs.serverUrl + it else it }
            CasaApp(
                o.optString("name"), title, icon,
                if (port.isNotEmpty()) "$scheme://$h:$port$index" else null,
                o.optString("status") == "running",
            )
        }.sortedBy { it.title.lowercase() }
    }

    /** action: start | stop | restart */
    suspend fun appAction(id: String, action: String) {
        authed("/v2/app_management/compose/${enc(id)}/status", "PUT", JSONObject.quote(action))
    }

    /** state: restart | off (reboots or powers off the whole server) */
    suspend fun power(state: String) {
        authed("/v1/sys/state/$state", "PUT", "{}")
    }

    suspend fun files(path: String): List<FileEntry> {
        val arr = authed("/v1/folder?path=${enc(path)}").optJSONObject("data")?.optJSONArray("content") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            FileEntry(it.optString("name"), it.optString("path"), it.optBoolean("is_dir"), it.optLong("size"), it.optString("date"))
        }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    fun fileUrl(path: String) = "${prefs.serverUrl}/v1/file?path=${enc(path)}"

    /** Folders are zipped server-side by /v1/batch (same as the CasaOS web UI). */
    fun zipUrl(path: String) = "${prefs.serverUrl}/v1/batch?format=zip&files=${enc(path)}"

    /** Web terminal websocket. Token goes in the query: websockets cannot send headers from JS. */
    fun sshUrl(user: String, pass: String, port: String, cols: Int, rows: Int) =
        prefs.serverUrl.replaceFirst("http", "ws") +
            "/v1/sys/wsssh?username=${enc(user)}&password=${enc(pass)}&port=${enc(port)}&cols=$cols&rows=$rows&token=${enc(prefs.access)}"

    /** Single-chunk upload in the simple-uploader format CasaOS expects; [relative] may hold subfolders, CasaOS creates them. */
    suspend fun upload(dir: String, name: String, size: Long, open: () -> java.io.InputStream, relative: String = name, onProgress: (Long) -> Unit) {
        val file = object : okhttp3.RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: okio.BufferedSink) {
                open().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent)
                    }
                }
            }
        }
        val s = size.toString()
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("path", dir).addFormDataPart("chunkNumber", "1").addFormDataPart("chunkSize", s)
            .addFormDataPart("currentChunkSize", s).addFormDataPart("totalSize", s)
            .addFormDataPart("identifier", "$size-${relative.filter(Char::isLetterOrDigit)}")
            .addFormDataPart("filename", name).addFormDataPart("relativePath", relative).addFormDataPart("totalChunks", "1")
            .addFormDataPart("file", name, file).build()
        authedBody("/v1/file/upload?path=${enc(dir)}", body)
    }

    private suspend fun authedBody(path: String, body: okhttp3.RequestBody, retry: Boolean = true): JSONObject {
        val req = Request.Builder().url(prefs.serverUrl + path).header("Authorization", prefs.access).post(body).build()
        return try {
            exec(req, longCall = true)
        } catch (e: Unauthorized) {
            if (!retry || prefs.refresh.isEmpty()) throw e
            refreshToken()
            authedBody(path, body, false)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun refreshToken() {
        val body = JSONObject().put("refresh_token", prefs.refresh).toString().toRequestBody(json)
        val d = exec(Request.Builder().url("${prefs.serverUrl}/v1/users/refresh").post(body).build()).optJSONObject("data")
        val access = d?.optString("access_token").orEmpty()
        if (access.isEmpty()) throw Unauthorized()
        prefs.access = access
        d?.optString("refresh_token")?.takeIf { it.isNotEmpty() }?.let { prefs.refresh = it }
    }

    private suspend fun authed(path: String, method: String = "GET", body: String? = null, retry: Boolean = true): JSONObject {
        val req = Request.Builder().url(prefs.serverUrl + path).header("Authorization", prefs.access)
            .method(method, body?.toRequestBody(json)).build()
        return try {
            exec(req)
        } catch (e: Unauthorized) {
            if (!retry || prefs.refresh.isEmpty()) throw e
            refreshToken()
            authed(path, method, body, false)
        }
    }

    private suspend fun exec(req: Request, longCall: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        try {
            val client = if (longCall) http.newBuilder().writeTimeout(0, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build() else http
            client.newCall(req).execute().use { r ->
                if (r.code == 401) throw Unauthorized()
                val txt = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(txt) }.getOrNull()
                    ?: throw CasaException("Risposta non valida (HTTP ${r.code})")
                if (!r.isSuccessful) throw CasaException(o.optString("message").ifEmpty { "Errore HTTP ${r.code}" })
                o
            }
        } catch (e: IOException) {
            throw CasaException("Server non raggiungibile")
        } catch (e: IllegalArgumentException) {
            throw CasaException("Indirizzo non valido")
        }
    }
}
