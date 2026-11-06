using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Reflection;
using System.Text.Json.Nodes;
using System.Threading.Tasks;

namespace CasaDeck.Services;

/** Self-update from the GitHub releases of this repo: the release must carry a "*windows*.zip" asset. */
public static class Updater
{
    const string Latest = "https://api.github.com/repos/carellice/CasaDeck/releases/latest";
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(10) };

    static Updater() => Http.DefaultRequestHeaders.UserAgent.ParseAdd("CasaDeck-Windows");

    public record Release(string Version, string Notes, string ZipUrl);

    public static string Current
    {
        get
        {
            var v = Assembly.GetExecutingAssembly().GetName().Version ?? new Version(0, 0);
            return v.Build > 0 ? $"{v.Major}.{v.Minor}.{v.Build}" : $"{v.Major}.{v.Minor}";
        }
    }

    /** "1.10" > "1.9"; a leading "v" is ignored. */
    public static bool Newer(string candidate, string installed)
    {
        static int[] Parts(string v) => v.TrimStart('v', 'V').Split('.')
            .Select(p => int.TryParse(new string(p.TakeWhile(char.IsDigit).ToArray()), out var n) ? n : 0).ToArray();
        var a = Parts(candidate);
        var b = Parts(installed);
        for (var i = 0; i < Math.Max(a.Length, b.Length); i++)
        {
            var d = a.ElementAtOrDefault(i) - b.ElementAtOrDefault(i);
            if (d != 0) return d > 0;
        }
        return false;
    }

    /** @return the latest release, or null when it has no Windows zip. Throws on network errors. */
    public static async Task<Release?> LatestAsync()
    {
        var o = JsonNode.Parse(await Http.GetStringAsync(Latest))!;
        var zip = o["assets"]?.AsArray().FirstOrDefault(a =>
        {
            var n = a?["name"]?.ToString().ToLowerInvariant() ?? "";
            return n.EndsWith(".zip") && n.Contains("windows");
        });
        if (zip == null) return null;
        return new Release(o["tag_name"]!.ToString().TrimStart('v', 'V'), o["body"]?.ToString() ?? "", zip["browser_download_url"]!.ToString());
    }

    /** Downloads and unzips the new version, then a hidden script swaps the files once this process exits and restarts the app. */
    public static async Task InstallAsync(Release r, IProgress<double> progress)
    {
        var work = Path.Combine(Path.GetTempPath(), "CasaDeck-update");
        if (Directory.Exists(work)) Directory.Delete(work, true);
        Directory.CreateDirectory(work);
        var zipPath = Path.Combine(work, "update.zip");
        using (var resp = await Http.GetAsync(r.ZipUrl, HttpCompletionOption.ResponseHeadersRead))
        {
            resp.EnsureSuccessStatusCode();
            await using var input = await resp.Content.ReadAsStreamAsync();
            await using var output = File.Create(zipPath);
            await CasaApi.Copy(input, output, resp.Content.Headers.ContentLength ?? 0, progress);
        }
        var extracted = Path.Combine(work, "files");
        ZipFile.ExtractToDirectory(zipPath, extracted);
        // the zip may wrap everything in one folder
        var source = Directory.GetFiles(extracted, "CasaDeck.exe", SearchOption.AllDirectories).Select(Path.GetDirectoryName).FirstOrDefault()
            ?? throw new InvalidOperationException("Aggiornamento non valido");

        var target = AppContext.BaseDirectory.TrimEnd('\\');
        var script = Path.Combine(work, "update.cmd");
        File.WriteAllText(script, $"""
            @echo off
            :wait
            tasklist /FI "PID eq {Environment.ProcessId}" | find "{Environment.ProcessId}" >nul && (timeout /t 1 /nobreak >nul & goto wait)
            robocopy "{source}" "{target}" /E /R:5 /W:1 >nul
            start "" "{Path.Combine(target, "CasaDeck.exe")}"
            """);
        Process.Start(new ProcessStartInfo("cmd.exe", $"/c \"{script}\"") { CreateNoWindow = true, UseShellExecute = false });
    }
}
