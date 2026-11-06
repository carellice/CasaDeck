using System;
using System.IO;
using System.Text;
using CasaDeck.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.Web.WebView2.Core;

namespace CasaDeck.Pages;

public sealed partial class TerminalPage : Page
{
    /** SSH password is kept in memory only, never written to disk. */
    static string password = "";
    bool hooked;

    public TerminalPage()
    {
        InitializeComponent();
        SshUser.Text = Settings.Current.SshUser;
        SshPort.Value = int.TryParse(Settings.Current.SshPort, out var p) ? p : 22;
        Loaded += (_, _) => { if (password.Length > 0) Connect(); else SshPass.Focus(FocusState.Programmatic); };
    }

    void SshPass_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter) Connect_Click(sender, e);
    }

    void Connect_Click(object sender, RoutedEventArgs e)
    {
        if (SshUser.Text.Trim().Length == 0 || SshPass.Password.Length == 0) return;
        Settings.Current.SshUser = SshUser.Text.Trim();
        Settings.Current.SshPort = double.IsNaN(SshPort.Value) ? "22" : ((int)SshPort.Value).ToString();
        Settings.Current.Save();
        password = SshPass.Password;
        Connect();
    }

    void Reconnect_Click(object sender, RoutedEventArgs e) => Connect();

    void Disconnect_Click(object sender, RoutedEventArgs e)
    {
        password = "";
        SshPass.Password = "";
        Web.CoreWebView2?.Navigate("about:blank"); // closes the websocket
        TermCard.Visibility = Visibility.Collapsed;
        Actions.Visibility = Visibility.Collapsed;
        LoginCard.Visibility = Visibility.Visible;
    }

    async void Connect()
    {
        LoginCard.Visibility = Visibility.Collapsed;
        TermCard.Visibility = Visibility.Visible;
        Actions.Visibility = Visibility.Visible;
        try { await Web.EnsureCoreWebView2Async(); }
        catch
        {
            ShellPage.Current?.Notify("Serve Microsoft Edge WebView2 Runtime per il terminale", InfoBarSeverity.Error);
            return;
        }
        var core = Web.CoreWebView2;
        // served from the server's own origin, so the websocket Origin matches CasaOS
        var page = Settings.Current.ServerUrl + "/casadeck-terminal";
        if (!hooked)
        {
            hooked = true;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.AddWebResourceRequestedFilter(page + "*", CoreWebView2WebResourceContext.Document);
            core.WebResourceRequested += (_, e) =>
            {
                if (!e.Request.Uri.StartsWith(page)) return;
                var bytes = Encoding.UTF8.GetBytes(Html());
                e.Response = core.Environment.CreateWebResourceResponse(
                    new MemoryStream(bytes).AsRandomAccessStream(), 200, "OK", "Content-Type: text/html; charset=utf-8\r\nCache-Control: no-store");
            };
        }
        core.Navigate(page + "?t=" + DateTime.UtcNow.Ticks);
        Web.Focus(FocusState.Programmatic);
    }

    static string Html()
    {
        string Asset(string p) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Terminal", p));
        var s = Settings.Current;
        // placeholders first, libraries last, so library code is never rewritten
        return Asset("terminal.html")
            .Replace("\"ACCENT\"", "\"#5B6CFF\"")
            .Replace("WS_URL", CasaApi.SshUrl(s.SshUser, password, s.SshPort))
            .Replace("fontSize:13", "fontSize:14")
            // the extra key row is for phones; a real keyboard has them
            .Replace("</head>", "<style>#keys{display:none}</style></head>")
            .Replace("/*XTERM_CSS*/", Asset("xterm/xterm.css"))
            .Replace("/*XTERM_JS*/", Asset("xterm/xterm.js"))
            .Replace("/*FIT_JS*/", Asset("xterm/addon-fit.js"));
    }
}
