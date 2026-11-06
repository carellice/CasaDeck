using System;
using System.IO;
using CasaDeck.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media.Imaging;

namespace CasaDeck.Pages;

public sealed partial class LoginPage : Page
{
    public LoginPage()
    {
        InitializeComponent();
        Logo.Source = new BitmapImage(new Uri(Path.Combine(AppContext.BaseDirectory, "logo.png")));
        Server.Text = Settings.Current.ServerUrl;
        User.Text = Settings.Current.Username;
        Loaded += (_, _) =>
        {
            Drift.Begin();
            (Server.Text.Length == 0 ? (Control)Server : Pass).Focus(FocusState.Programmatic);
        };
        Unloaded += (_, _) => Drift.Stop();
    }

    void Pass_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter) Go_Click(sender, e);
    }

    async void Go_Click(object sender, RoutedEventArgs e)
    {
        if (!Go.IsEnabled) return;
        if (Server.Text.Trim().Length == 0 || User.Text.Trim().Length == 0 || Pass.Password.Length == 0)
        {
            Show("Compila indirizzo, utente e password");
            return;
        }
        Error.IsOpen = false;
        Go.IsEnabled = false;
        GoText.Opacity = 0;
        Busy.IsActive = true;
        try
        {
            await CasaApi.Login(CasaApi.Normalize(Server.Text), User.Text.Trim(), Pass.Password);
            App.Window.ShowShell();
        }
        catch (CasaException ex) { Show(ex.Message); }
        finally
        {
            Go.IsEnabled = true;
            GoText.Opacity = 1;
            Busy.IsActive = false;
        }
    }

    void Show(string message)
    {
        Error.Message = message;
        Error.IsOpen = true;
    }
}
