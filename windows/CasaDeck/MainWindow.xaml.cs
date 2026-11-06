using System;
using System.IO;
using CasaDeck.Pages;
using CasaDeck.Services;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Media.Imaging;

namespace CasaDeck;

public sealed partial class MainWindow : Window
{
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern uint GetDpiForWindow(IntPtr hwnd);

    public IntPtr Hwnd => WinRT.Interop.WindowNative.GetWindowHandle(this);

    public MainWindow()
    {
        InitializeComponent();
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(TitleBar);
        TitleIcon.Source = new BitmapImage(new Uri(Path.Combine(AppContext.BaseDirectory, "logo.png")));
        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "icon.ico"));
        // AppWindow works in physical pixels: scale the size for the monitor DPI, then center it
        var scale = GetDpiForWindow(Hwnd) / 96.0;
        var work = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        int w = Math.Min((int)(1280 * scale), work.Width), h = Math.Min((int)(840 * scale), work.Height);
        AppWindow.MoveAndResize(new Windows.Graphics.RectInt32(work.X + (work.Width - w) / 2, work.Y + (work.Height - h) / 2, w, h));
        ApplyTheme();
        Root.ActualThemeChanged += (_, _) => TintCaptionButtons();

        CasaApi.LoggedOut += () => DispatcherQueue.TryEnqueue(ShowLogin);
        if (Settings.Current.Access.Length > 0 && Settings.Current.ServerUrl.Length > 0) ShowShell();
        else ShowLogin();
    }

    public void ShowLogin() => RootFrame.Navigate(typeof(LoginPage), null, new DrillInNavigationTransitionInfo());

    public void ShowShell() => RootFrame.Navigate(typeof(ShellPage), null, new DrillInNavigationTransitionInfo());

    public void ApplyTheme()
    {
        Root.RequestedTheme = Settings.Current.Theme switch { 1 => ElementTheme.Light, 2 => ElementTheme.Dark, _ => ElementTheme.Default };
        TintCaptionButtons();
    }

    /** The minimize/maximize/close glyphs don't follow RequestedTheme on their own. */
    void TintCaptionButtons()
    {
        var bar = AppWindow.TitleBar;
        var dark = Root.ActualTheme == ElementTheme.Dark;
        bar.ButtonBackgroundColor = Colors.Transparent;
        bar.ButtonInactiveBackgroundColor = Colors.Transparent;
        bar.ButtonForegroundColor = dark ? Colors.White : Colors.Black;
        bar.ButtonHoverForegroundColor = dark ? Colors.White : Colors.Black;
        bar.ButtonHoverBackgroundColor = dark ? Windows.UI.Color.FromArgb(0x20, 0xFF, 0xFF, 0xFF) : Windows.UI.Color.FromArgb(0x14, 0, 0, 0);
        bar.ButtonInactiveForegroundColor = Colors.Gray;
    }
}
