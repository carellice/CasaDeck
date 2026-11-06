using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace CasaDeck.Services;

public class CasaException(string message) : Exception(message);
public class Unauthorized() : CasaException("Sessione scaduta, accedi di nuovo");

public record SysStats(double Cpu, int Cores, int? Temp, long MemUsed, long MemTotal, long DiskUsed, long DiskTotal, long NetRx, long NetTx);
public record CasaApp(string Id, string Title, string Icon, string? Url, bool Running);
public record FileEntry(string Name, string Path, bool IsDir, long Size, string Date);

/** Thin client for the CasaOS REST API (v0.4.x), same calls as the Android app. Auth header is the raw JWT, no "Bearer". */
public static class CasaApi
{
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(20) };
    static readonly HttpClient Long = new() { Timeout = TimeSpan.FromMinutes(30) };
    static Settings S => Settings.Current;

    /** Raised when the session cannot be refreshed: the UI goes back to login. */
    public static event Action? LoggedOut;

    public static string Normalize(string url)
    {
        url = url.Trim().TrimEnd('/');
        if (!url.StartsWith("http://") && !url.StartsWith("https://")) url = "http://" + url;
        return url;
    }

    public static async Task Login(string baseUrl, string user, string pass)
    {
        var body = new JsonObject { ["username"] = user, ["password"] = pass };
        JsonNode o;
        try { o = await Exec(new HttpRequestMessage(HttpMethod.Post, $"{baseUrl}/v1/users/login") { Content = Json(body) }); }
        catch (Unauthorized) { throw new CasaException("Utente o password errati"); }
        var tok = o["data"]?["token"] ?? throw new CasaException(o["message"]?.ToString() is { Length: > 0 } m ? m : "Login fallito");
        S.ServerUrl = baseUrl;
        S.Username = user;
        S.Save();
        S.Access = tok["access_token"]!.ToString();
        S.Refresh = tok["refresh_token"]!.ToString();
    }

    public static void Logout()
    {
        S.Access = "";
        S.Refresh = "";
    }

    public static async Task<SysStats> Stats()
    {
        var d = (await Authed("/v1/sys/utilization"))["data"];
        long rx = 0, tx = 0;
        foreach (var n in d?["net"]?.AsArray() ?? [])
        {
            var name = n?["name"]?.ToString() ?? "";
            if (name == "lo" || name.StartsWith("docker") || name.StartsWith("veth") || name.StartsWith("br-")) continue;
            rx += L(n?["bytesRecv"]);
            tx += L(n?["bytesSent"]);
        }
        var cpu = d?["cpu"];
        var temp = (int)L(cpu?["temperature"]);
        return new SysStats(
            D(cpu?["percent"]), (int)L(cpu?["num"]), temp > 0 ? temp : null,
            L(d?["mem"]?["used"]), L(d?["mem"]?["total"]), L(d?["sys_disk"]?["used"]), L(d?["sys_disk"]?["size"]), rx, tx);
    }

    public static async Task<List<CasaApp>> Apps()
    {
        var arr = (await Authed("/v2/app_management/web/appgrid"))["data"]?.AsArray() ?? [];
        var host = new Uri(S.ServerUrl).Host;
        return arr.Where(o => o != null).Select(o =>
        {
            var t = o!["title"];
            var title = t is JsonObject to
                ? (to["en_us"]?.ToString() is { Length: > 0 } en ? en : to.FirstOrDefault().Value?.ToString() ?? "")
                : t?.ToString() ?? "";
            if (title.Length == 0) title = o["name"]?.ToString() ?? "";
            var port = o["port"]?.ToString() ?? "";
            var h = o["hostname"]?.ToString() is { Length: > 0 } hn ? hn : host;
            var scheme = o["scheme"]?.ToString() is { Length: > 0 } sc ? sc : "http";
            var index = o["index"]?.ToString() ?? "";
            if (!index.StartsWith('/')) index = "/" + index;
            // icons can be relative to the CasaOS host (custom/imported apps)
            var icon = o["icon"]?.ToString() ?? "";
            if (icon.StartsWith('/')) icon = S.ServerUrl + icon;
            return new CasaApp(o["name"]?.ToString() ?? "", title, icon, port.Length > 0 ? $"{scheme}://{h}:{port}{index}" : null,
                o["status"]?.ToString() == "running");
        }).OrderBy(a => a.Title.ToLowerInvariant()).ToList();
    }

    /** action: start | stop | restart */
    public static Task AppAction(string id, string action) =>
        Authed($"/v2/app_management/compose/{Enc(id)}/status", HttpMethod.Put, JsonValue.Create(action));

    /** state: restart | off (reboots or powers off the whole server) */
    public static Task Power(string state) => Authed($"/v1/sys/state/{state}", HttpMethod.Put, new JsonObject());

    public static async Task<List<FileEntry>> Files(string path)
    {
        var arr = (await Authed($"/v1/folder?path={Enc(path)}"))["data"]?["content"]?.AsArray() ?? [];
        return arr.Where(o => o != null).Select(o => new FileEntry(
                o!["name"]?.ToString() ?? "", o["path"]?.ToString() ?? "", o["is_dir"]?.GetValue<bool>() ?? false, L(o["size"]), o["date"]?.ToString() ?? ""))
            .OrderBy(f => !f.IsDir).ThenBy(f => f.Name.ToLowerInvariant()).ToList();
    }

    /** Files as-is, folders zipped server-side by /v1/batch (same as the CasaOS web UI). */
    public static async Task Download(FileEntry f, Stream target, IProgress<double> progress)
    {
        var url = f.IsDir ? $"{S.ServerUrl}/v1/batch?format=zip&files={Enc(f.Path)}" : $"{S.ServerUrl}/v1/file?path={Enc(f.Path)}";
        await WithRefresh(async () =>
        {
            var req = new HttpRequestMessage(HttpMethod.Get, url);
            req.Headers.TryAddWithoutValidation("Authorization", S.Access);
            using var r = await Send(Long, req, HttpCompletionOption.ResponseHeadersRead);
            if (r.StatusCode == HttpStatusCode.Unauthorized) throw new Unauthorized();
            if (!r.IsSuccessStatusCode) throw new CasaException($"Download non riuscito (HTTP {(int)r.StatusCode})");
            var total = r.Content.Headers.ContentLength ?? 0;
            await using var input = await r.Content.ReadAsStreamAsync();
            await Copy(input, target, total, progress);
            return true;
        });
    }

    /** Single-chunk upload in the simple-uploader format CasaOS expects. */
    public static Task Upload(string dir, string localPath, IProgress<double> progress) => WithRefresh(async () =>
    {
        var name = Path.GetFileName(localPath);
        var size = new FileInfo(localPath).Length.ToString();
        using var form = new MultipartFormDataContent();
        void Add(string k, string v) => form.Add(new StringContent(v), k);
        Add("path", dir); Add("chunkNumber", "1"); Add("chunkSize", size); Add("currentChunkSize", size); Add("totalSize", size);
        Add("identifier", size + "-" + new string(name.Where(char.IsLetterOrDigit).ToArray()));
        Add("filename", name); Add("relativePath", name); Add("totalChunks", "1");
        var file = new ProgressContent(File.OpenRead(localPath), progress);
        file.Headers.ContentType = new("application/octet-stream");
        form.Add(file, "file", name);
        var req = new HttpRequestMessage(HttpMethod.Post, $"{S.ServerUrl}/v1/file/upload?path={Enc(dir)}") { Content = form };
        req.Headers.TryAddWithoutValidation("Authorization", S.Access);
        await Exec(req, Long);
        return true;
    });

    /** Web terminal websocket. Token goes in the query: websockets cannot send headers from JS. */
    public static string SshUrl(string user, string pass, string port) =>
        "ws" + S.ServerUrl[4..] + // http -> ws, https -> wss
        $"/v1/sys/wsssh?username={Enc(user)}&password={Enc(pass)}&port={Enc(port)}&cols=COLS&rows=ROWS&token={Enc(S.Access)}";

    // --- plumbing ---------------------------------------------------------------------------

    static Task<JsonNode> Authed(string path, HttpMethod? method = null, JsonNode? body = null) => WithRefresh(() =>
    {
        var req = new HttpRequestMessage(method ?? HttpMethod.Get, S.ServerUrl + path);
        req.Headers.TryAddWithoutValidation("Authorization", S.Access);
        if (body != null) req.Content = Json(body);
        return Exec(req);
    });

    static async Task<T> WithRefresh<T>(Func<Task<T>> call)
    {
        try { return await call(); }
        catch (Unauthorized)
        {
            try { await RefreshToken(); }
            catch (Unauthorized) { Logout(); LoggedOut?.Invoke(); throw; }
            return await call();
        }
    }

    static async Task RefreshToken()
    {
        if (S.Refresh.Length == 0) throw new Unauthorized();
        var o = await Exec(new HttpRequestMessage(HttpMethod.Post, $"{S.ServerUrl}/v1/users/refresh") { Content = Json(new JsonObject { ["refresh_token"] = S.Refresh }) });
        var access = o["data"]?["access_token"]?.ToString() ?? "";
        if (access.Length == 0) throw new Unauthorized();
        S.Access = access;
        if (o["data"]?["refresh_token"]?.ToString() is { Length: > 0 } r) S.Refresh = r;
    }

    static async Task<JsonNode> Exec(HttpRequestMessage req, HttpClient? client = null)
    {
        using var r = await Send(client ?? Http, req, HttpCompletionOption.ResponseContentRead);
        if (r.StatusCode == HttpStatusCode.Unauthorized) throw new Unauthorized();
        var txt = await r.Content.ReadAsStringAsync();
        JsonNode? o = null;
        try { o = JsonNode.Parse(txt); } catch { }
        if (o == null) throw new CasaException($"Risposta non valida (HTTP {(int)r.StatusCode})");
        if (!r.IsSuccessStatusCode) throw new CasaException(o["message"]?.ToString() is { Length: > 0 } m ? m : $"Errore HTTP {(int)r.StatusCode}");
        return o;
    }

    static async Task<HttpResponseMessage> Send(HttpClient client, HttpRequestMessage req, HttpCompletionOption opt)
    {
        try { return await client.SendAsync(req, opt); }
        catch (HttpRequestException) { throw new CasaException("Server non raggiungibile"); }
        catch (TaskCanceledException) { throw new CasaException("Server non raggiungibile"); }
        catch (UriFormatException) { throw new CasaException("Indirizzo non valido"); }
    }

    public static async Task Copy(Stream input, Stream output, long total, IProgress<double> progress)
    {
        var buf = new byte[81920];
        long done = 0;
        int n;
        while ((n = await input.ReadAsync(buf)) > 0)
        {
            await output.WriteAsync(buf.AsMemory(0, n));
            done += n;
            if (total > 0) progress.Report((double)done / total);
        }
    }

    static StringContent Json(JsonNode body) => new(body.ToJsonString(), Encoding.UTF8, "application/json");
    static string Enc(string s) => Uri.EscapeDataString(s);
    static long L(JsonNode? n) { try { return n?.GetValue<long>() ?? 0; } catch { return (long)D(n); } }
    static double D(JsonNode? n) { try { return n?.GetValue<double>() ?? 0; } catch { return double.TryParse(n?.ToString(), System.Globalization.CultureInfo.InvariantCulture, out var v) ? v : 0; } }

    /** Upload body that reports how much was sent. */
    class ProgressContent(Stream source, IProgress<double> progress) : HttpContent
    {
        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            await using (source) await Copy(source, stream, source.Length, progress);
        }
        protected override bool TryComputeLength(out long length) { length = source.Length; return true; }
    }
}
