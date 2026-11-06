using System;
using System.IO;
using CasaDeck.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Imaging;

namespace CasaDeck.Pages;

public sealed partial class SettingsPage : Page
{
    readonly bool ready;

    public SettingsPage()
    {
        InitializeComponent();
        var s = Settings.Current;
        UserName.Text = s.Username;
        Avatar.DisplayName = s.Username;
        ServerUrl.Text = s.ServerUrl;
        Theme.SelectedIndex = Math.Clamp(s.Theme, 0, 2);
        Refresh.Value = s.RefreshSec;
        Logo.Source = new BitmapImage(new Uri(Path.Combine(AppContext.BaseDirectory, "logo.png")));
        Version.Text = $"CasaDeck {Updater.Current}";
        ready = true;
    }

    void Theme_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!ready) return;
        Settings.Current.Theme = Theme.SelectedIndex;
        Settings.Current.Save();
        App.Window.ApplyTheme();
    }

    void Refresh_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e)
    {
        if (!ready) return;
        Settings.Current.RefreshSec = (int)e.NewValue;
        Settings.Current.Save();
    }

    async void Check_Click(object sender, RoutedEventArgs e)
    {
        if (ShellPage.Current != null) await ShellPage.Current.CheckUpdates(manual: true);
    }

    async void Logout_Click(object sender, RoutedEventArgs e)
    {
        if (await Confirm("Uscire?", "Dovrai inserire di nuovo la password per accedere.", "Esci") != ContentDialogResult.Primary) return;
        CasaApi.Logout();
        App.Window.ShowLogin();
    }

    void Restart_Click(object sender, RoutedEventArgs e) =>
        Power("restart", "Riavviare il server?", "Tutte le app si fermeranno per un paio di minuti.", "Riavvia", "Riavvio in corso");

    void Off_Click(object sender, RoutedEventArgs e) =>
        Power("off", "Spegnere il server?", "Tutte le app si fermeranno. Per riaccenderlo dovrai staccare e riattaccare l'alimentazione.", "Spegni", "Spegnimento in corso");

    async void Power(string state, string title, string text, string action, string done)
    {
        if (await Confirm(title, text, action) != ContentDialogResult.Primary) return;
        try
        {
            await CasaApi.Power(state);
            ShellPage.Current?.Notify(done, InfoBarSeverity.Success);
        }
        catch (CasaException ex) { ShellPage.Current?.Notify(ex.Message, InfoBarSeverity.Error); }
    }

    Windows.Foundation.IAsyncOperation<ContentDialogResult> Confirm(string title, string text, string action) => new ContentDialog
    {
        Title = title,
        Content = text,
        PrimaryButtonText = action,
        CloseButtonText = "Annulla",
        DefaultButton = ContentDialogButton.Close,
        XamlRoot = XamlRoot,
        RequestedTheme = ActualTheme,
    }.ShowAsync();
}
