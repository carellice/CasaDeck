package dev.casadeck

import android.app.Application
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.casadeck.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class Stage { Onboarding, Main }

class CasaViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val api = CasaApi(prefs)

    val stage = MutableStateFlow(if (prefs.access.isNotEmpty()) Stage.Main else Stage.Onboarding)
    val ui = MutableStateFlow(prefs.loadUi())
    val stats = MutableStateFlow<SysStats?>(null)
    val cpuHistory = MutableStateFlow(List(30) { 0f })
    val netRate = MutableStateFlow(0L to 0L) // bytes/s down, up
    val apps = MutableStateFlow<List<CasaApp>?>(null)
    val message = MutableStateFlow<String?>(null)
    private var lastNet: Triple<Long, Long, Long>? = null

    /** App opened in the in-app browser: title to url. */
    val web = MutableStateFlow<Pair<String, String>?>(null)
    /** Upload in progress: file name to 0..1 progress. */
    val upload = MutableStateFlow<Pair<String, Float>?>(null)
    /** Quick-settings tile asked for the "most used apps" sheet. */
    val quickPick = MutableStateFlow(false)
    val lockOn = MutableStateFlow(prefs.lock)
    val notifyOn = MutableStateFlow(prefs.notify)
    val locked = MutableStateFlow(prefs.lock && prefs.access.isNotEmpty())
    /** SSH password is kept in memory only, never written to disk. */
    var sshPass = ""

    fun setLock(on: Boolean) { prefs.lock = on; lockOn.value = on }

    fun setNotify(on: Boolean) {
        prefs.notify = on
        notifyOn.value = on
        MonitorWorker.schedule(getApplication(), on)
    }

    fun openWeb(app: CasaApp) {
        val u = app.url ?: return run { message.value = "${app.title} non ha un'interfaccia web" }
        prefs.bumpUsage(app.id)
        web.value = app.title to u
    }

    /** Uploads run in [UploadWorker] so they survive closing the app or locking the screen. */
    fun uploadFiles(dir: String, uris: List<Uri>) = UploadWorker.enqueue(getApplication(), dir, uris = uris)
    fun uploadFolder(dir: String, tree: Uri) = UploadWorker.enqueue(getApplication(), dir, tree = tree)

    init {
        viewModelScope.launch {
            WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(UploadWorker.WORK).collect { infos ->
                upload.value = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.let {
                    (it.getString("name") ?: "Preparazione…") to it.getFloat("p", 0f)
                }
            }
        }
    }

    /** Opens an app by id (from the tile or a widget), loading the list first if needed. */
    fun openAppId(id: String) = viewModelScope.launch {
        if (apps.value == null) guard { apps.value = api.apps() }
        apps.value?.find { it.id == id }?.let(::openWeb)
    }

    /** state: restart | off */
    fun power(state: String) = viewModelScope.launch {
        guard {
            api.power(state)
            message.value = if (state == "off") "Spegnimento in corso…" else "Riavvio in corso…"
        }
    }

    fun updateUi(f: (UiSettings) -> UiSettings) {
        ui.value = f(ui.value)
        prefs.saveUi(ui.value)
    }

    suspend fun pollStats() = guard {
        val s = api.stats()
        val now = System.currentTimeMillis()
        lastNet?.let { (t, rx, tx) ->
            val dt = (now - t) / 1000f
            if (dt > 0) netRate.value = ((s.netRx - rx) / dt).toLong().coerceAtLeast(0) to ((s.netTx - tx) / dt).toLong().coerceAtLeast(0)
        }
        lastNet = Triple(now, s.netRx, s.netTx)
        stats.value = s
        cpuHistory.value = (cpuHistory.value + s.cpu).takeLast(30)
    }

    fun loadApps() = viewModelScope.launch {
        guard {
            apps.value = api.apps()
            // what the user has seen is the new baseline for "app stopped" alerts
            prefs.running = apps.value.orEmpty().filter { it.running }.map { it.id }.toSet()
        }
    }

    fun appAction(app: CasaApp, action: String) = viewModelScope.launch {
        guard {
            api.appAction(app.id, action)
            message.value = "${app.title}: comando \"$action\" inviato"
            delay(1500) // containers need a moment to change state
            apps.value = api.apps()
            prefs.running = apps.value.orEmpty().filter { it.running }.map { it.id }.toSet()
        }
    }

    suspend fun files(path: String) = guard { api.files(path) }

    /** Files download as-is, folders as a zip built by CasaOS. */
    fun download(f: FileEntry) {
        val name = if (f.isDir) "${f.name}.zip" else f.name
        val url = if (f.isDir) api.zipUrl(f.path) else api.fileUrl(f.path)
        enqueueDownload(url, name, prefs.access)
    }

    fun enqueueDownload(url: String, name: String, auth: String? = null) {
        val dm = getApplication<Application>().getSystemService(DownloadManager::class.java)
        runCatching {
            dm.enqueue(
                DownloadManager.Request(Uri.parse(url))
                    .apply { if (auth != null) addRequestHeader("Authorization", auth) }
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name),
            )
        }.onSuccess { message.value = "Download di $name avviato" }
            .onFailure { message.value = "Download non riuscito" }
    }

    fun onLoggedIn() {
        stage.value = Stage.Main
    }

    fun logout() {
        prefs.access = ""
        prefs.refresh = ""
        stats.value = null
        apps.value = null
        lastNet = null
        sshPass = ""
        web.value = null
        locked.value = false
        stage.value = Stage.Onboarding
    }

    private suspend fun <T> guard(block: suspend () -> T): T? = try {
        block()
    } catch (e: Unauthorized) {
        logout(); null
    } catch (e: CasaException) {
        message.value = e.message; null
    }
}
