using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using Windows.Security.Credentials;

namespace CasaDeck.Services;

/** Plain settings in %LOCALAPPDATA%\CasaDeck\settings.json; tokens in the Windows credential vault. */
public class Settings
{
    public string ServerUrl { get; set; } = "";
    public string Username { get; set; } = "";
    public string SshUser { get; set; } = "root";
    public string SshPort { get; set; } = "22";
    /** 0 system, 1 light, 2 dark. */
    public int Theme { get; set; }
    public int RefreshSec { get; set; } = 3;

    static readonly string Dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CasaDeck");
    static readonly string FilePath = Path.Combine(Dir, "settings.json");
    const string Resource = "CasaDeck";

    public static Settings Current { get; } = Load();

    static Settings Load()
    {
        try { return JsonSerializer.Deserialize<Settings>(File.ReadAllText(FilePath)) ?? new(); }
        catch { return new(); }
    }

    public void Save()
    {
        Directory.CreateDirectory(Dir);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(this));
    }

    public string Access { get => Read("access"); set => Write("access", value); }
    public string Refresh { get => Read("refresh"); set => Write("refresh", value); }

    static string Read(string key)
    {
        try { return new PasswordVault().Retrieve(Resource, key).Let(c => { c.RetrievePassword(); return c.Password; }); }
        catch { return ""; } // not found throws
    }

    static void Write(string key, string value)
    {
        var vault = new PasswordVault();
        try { foreach (var c in vault.FindAllByResource(Resource).Where(c => c.UserName == key)) vault.Remove(c); } catch { }
        if (value.Length > 0) vault.Add(new PasswordCredential(Resource, key, value));
    }
}

static class Ext
{
    public static R Let<T, R>(this T value, Func<T, R> f) => f(value);
}
