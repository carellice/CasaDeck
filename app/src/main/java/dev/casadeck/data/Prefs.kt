package dev.casadeck.data

import android.content.Context
import androidx.core.content.edit

data class UiSettings(
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val amoled: Boolean = false,
    val accent: Int = 0,
    val dynamic: Boolean = false,
    val gridCols: Int = 3,
    val refreshSec: Int = 3,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("casadeck", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("url", "")!!
        set(v) = sp.edit { putString("url", v) }
    var access: String
        get() = sp.getString("access", "")!!
        set(v) = sp.edit { putString("access", v) }
    var refresh: String
        get() = sp.getString("refresh", "")!!
        set(v) = sp.edit { putString("refresh", v) }
    var username: String
        get() = sp.getString("user", "")!!
        set(v) = sp.edit { putString("user", v) }

    var lock: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) = sp.edit { putBoolean("lock", v) }
    var notify: Boolean
        get() = sp.getBoolean("notify", false)
        set(v) = sp.edit { putBoolean("notify", v) }
    /** Ids of apps seen running last time, so the monitor only reports changes. */
    var running: Set<String>
        get() = sp.getStringSet("running", emptySet())!!
        set(v) = sp.edit { putStringSet("running", v) }
    /** Last alert keys raised (disk/temp/offline), to avoid repeating the same notification. */
    var alerts: Set<String>
        get() = sp.getStringSet("alerts", emptySet())!!
        set(v) = sp.edit { putStringSet("alerts", v) }
    var sshUser: String
        get() = sp.getString("sshUser", "root")!!
        set(v) = sp.edit { putString("sshUser", v) }
    var sshPort: String
        get() = sp.getString("sshPort", "22")!!
        set(v) = sp.edit { putString("sshPort", v) }

    /** How many times each CasaOS app was opened, for "most used" (tile, widget). */
    fun usage(id: String) = sp.getInt("use_$id", 0)
    fun bumpUsage(id: String) = sp.edit { putInt("use_$id", usage(id) + 1) }

    /** Most opened first, then running, then by name. */
    fun topApps(list: List<CasaApp>) =
        list.sortedWith(compareByDescending<CasaApp> { usage(it.id) }.thenByDescending { it.running }.thenBy { it.title.lowercase() })

    fun loadUi() = UiSettings(
        sp.getInt("theme", 0), sp.getBoolean("amoled", false), sp.getInt("accent", 0),
        sp.getBoolean("dynamic", false), sp.getInt("cols", 3), sp.getInt("refreshSec", 3),
    )

    fun saveUi(s: UiSettings) = sp.edit {
        putInt("theme", s.themeMode); putBoolean("amoled", s.amoled); putInt("accent", s.accent)
        putBoolean("dynamic", s.dynamic); putInt("cols", s.gridCols); putInt("refreshSec", s.refreshSec)
    }
}
