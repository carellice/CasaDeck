using System;
using System.Linq;
using System.Threading.Tasks;
using CasaDeck.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Animation;

namespace CasaDeck.Pages;

public sealed partial class ShellPage : Page
{
    public static ShellPage? Current { get; private set; }
    static readonly Type[] Pages = [typeof(DashboardPage), typeof(AppsPage), typeof(FilesPage), typeof(TerminalPage), typeof(SettingsPage)];
    int index = -1;
    Updater.Release? release;

    public ShellPage()
    {
        InitializeComponent();
        Current = this;
        Nav.SelectedItem = Nav.MenuItems[0];
        Loaded += async (_, _) => await CheckUpdates(manual: false);
    }

    void Nav_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        var next = args.IsSettingsSelected ? 4 : int.Parse((string)((NavigationViewItem)args.SelectedItem).Tag);
        if (next == index) return;
        // slide in the direction of the menu, like the Android tab bar
        var effect = index < 0 ? SlideNavigationTransitionEffect.FromBottom
            : next > index ? SlideNavigationTransitionEffect.FromRight : SlideNavigationTransitionEffect.FromLeft;
        index = next;
        Body.Navigate(Pages[next], null, new SlideNavigationTransitionInfo { Effect = effect });
    }

    public async Task CheckUpdates(bool manual)
    {
        Updater.Release? r = null;
        try { r = await Updater.LatestAsync(); }
        catch { if (manual) Notify("Impossibile controllare gli aggiornamenti", InfoBarSeverity.Warning); return; }
        if (r != null && Updater.Newer(r.Version, Updater.Current))
        {
            release = r;
            UpdateBar.Title = $"CasaDeck {r.Version} disponibile";
            UpdateBar.Message = string.Join(" ", r.Notes.Split('\n').Select(l => l.Trim().TrimStart('-', '*', ' ')).Where(l => l.Length > 0).Take(3));
            UpdateBar.Severity = InfoBarSeverity.Informational;
            UpdateButton.Visibility = Visibility.Visible;
            UpdateBar.IsOpen = true;
        }
        else if (manual) Notify("Hai già l'ultima versione", InfoBarSeverity.Success);
    }

    /** Short message in the bar at the top of every page. */
    public void Notify(string message, InfoBarSeverity severity = InfoBarSeverity.Informational)
    {
        release = null;
        UpdateBar.Title = message;
        UpdateBar.Message = "";
        UpdateBar.Severity = severity;
        UpdateButton.Visibility = Visibility.Collapsed;
        UpdateProgress.Visibility = Visibility.Collapsed;
        UpdateBar.IsOpen = true;
    }

    async void Update_Click(object sender, RoutedEventArgs e)
    {
        if (release == null) return;
        UpdateButton.IsEnabled = false;
        UpdateProgress.Visibility = Visibility.Visible;
        try
        {
            await Updater.InstallAsync(release, new Progress<double>(p => UpdateProgress.Value = p));
            Application.Current.Exit();
        }
        catch
        {
            Notify("Download dell'aggiornamento non riuscito", InfoBarSeverity.Error);
        }
        finally { UpdateButton.IsEnabled = true; }
    }
}
