using System;
using System.Collections.Generic;
using System.Linq;
using CasaDeck.Services;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.System;

namespace CasaDeck.Pages;

public sealed class AppItem(CasaApp app)
{
    public CasaApp App { get; } = app;
    public ImageSource? Icon { get; } = Load(app.Icon);
    public Brush Status { get; } = new SolidColorBrush(app.Running ? Windows.UI.Color.FromArgb(255, 0x2E, 0xCC, 0x71) : Colors.Gray);
    public Visibility NoIcon => Icon == null ? Visibility.Visible : Visibility.Collapsed;
    public string Initial => App.Title.Length > 0 ? App.Title[..1].ToUpperInvariant() : "?";
    public string Hint => App.Running ? (App.Url != null ? "Clic per aprire, tasto destro per le azioni" : "In esecuzione") : "Ferma";

    static ImageSource? Load(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var u)) return null;
        return u.AbsolutePath.EndsWith(".svg", StringComparison.OrdinalIgnoreCase) ? new SvgImageSource(u) : new BitmapImage(u);
    }
}

public sealed partial class AppsPage : Page
{
    List<AppItem> all = [];

    public AppsPage()
    {
        InitializeComponent();
        Loaded += (_, _) => Load();
    }

    async void Load()
    {
        Spinner.IsActive = true;
        try
        {
            all = (await CasaApi.Apps()).Select(a => new AppItem(a)).ToList();
            Error.IsOpen = false;
            Filter();
        }
        catch (CasaException ex)
        {
            Error.Title = ex.Message;
            Error.IsOpen = true;
        }
        finally { Spinner.IsActive = false; }
    }

    void Filter()
    {
        var q = Search.Text.Trim();
        var list = q.Length == 0 ? all : all.Where(a => a.App.Title.Contains(q, StringComparison.OrdinalIgnoreCase)).ToList();
        AppsGrid.ItemsSource = list;
        var running = all.Count(a => a.App.Running);
        Count.Text = $"{all.Count} app · {running} in esecuzione";
    }

    void Icon_ImageFailed(object sender, ExceptionRoutedEventArgs e)
    {
        var img = (Image)sender;
        img.Visibility = Visibility.Collapsed;
        ((Grid)img.Parent).Children[0].Visibility = Visibility.Visible;
    }

    void Search_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args) => Filter();

    void Refresh_Click(object sender, RoutedEventArgs e) => Load();

    async void Grid_ItemClick(object sender, ItemClickEventArgs e)
    {
        var app = ((AppItem)e.ClickedItem).App;
        if (app.Url == null) { ShellPage.Current?.Notify($"{app.Title} non ha un'interfaccia web"); return; }
        await Launcher.LaunchUriAsync(new Uri(app.Url));
    }

    void Grid_RightTapped(object sender, RightTappedRoutedEventArgs e)
    {
        if ((e.OriginalSource as FrameworkElement)?.DataContext is not AppItem item) return;
        var menu = new MenuFlyout();
        void Add(string text, string glyph, Action click, bool enabled = true)
        {
            var i = new MenuFlyoutItem { Text = text, Icon = new FontIcon { Glyph = glyph }, IsEnabled = enabled };
            i.Click += (_, _) => click();
            menu.Items.Add(i);
        }
        Add("Apri", "", async () => { if (item.App.Url != null) await Launcher.LaunchUriAsync(new Uri(item.App.Url)); }, item.App.Url != null);
        menu.Items.Add(new MenuFlyoutSeparator());
        Add("Avvia", "", () => Act(item, "start", "avviata"), !item.App.Running);
        Add("Riavvia", "", () => Act(item, "restart", "riavviata"), item.App.Running);
        Add("Ferma", "", () => Act(item, "stop", "fermata"), item.App.Running);
        menu.ShowAt((FrameworkElement)e.OriginalSource, e.GetPosition((FrameworkElement)e.OriginalSource));
    }

    async void Act(AppItem item, string action, string done)
    {
        try
        {
            await CasaApi.AppAction(item.App.Id, action);
            ShellPage.Current?.Notify($"{item.App.Title} {done}", InfoBarSeverity.Success);
            Load();
        }
        catch (CasaException ex) { ShellPage.Current?.Notify(ex.Message, InfoBarSeverity.Error); }
    }
}
