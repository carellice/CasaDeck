using System;
using System.Collections.Generic;
using System.Linq;
using CasaDeck.Controls;
using CasaDeck.Services;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.Foundation;
using Windows.UI;

namespace CasaDeck.Pages;

public sealed partial class DashboardPage : Page
{
    record Tile(Border Card, Ring Ring, TextBlock Value, TextBlock Sub);

    readonly Tile cpu, mem, disk, temp;
    readonly List<double> history = Enumerable.Repeat(0.0, 30).ToList();
    DispatcherQueueTimer? timer;
    (long rx, long tx, DateTime at)? lastNet;
    bool busy;

    public DashboardPage()
    {
        InitializeComponent();
        var s = Settings.Current;
        Hello.Text = $"Ciao, {s.Username}";
        Server.Text = s.ServerUrl;
        cpu = AddTile("CPU", Color.FromArgb(255, 0x5B, 0x6C, 0xFF), "");
        mem = AddTile("Memoria", Color.FromArgb(255, 0x00, 0xC2, 0xA8), "");
        disk = AddTile("Disco", Color.FromArgb(255, 0xFF, 0xA6, 0x3D), "");
        temp = AddTile("Temperatura", Color.FromArgb(255, 0xFF, 0x5F, 0xA2), "");
        SizeChanged += (_, e) => Layout(e.NewSize.Width);
        Loaded += (_, _) => Start();
        Unloaded += (_, _) => timer?.Stop();
    }

    Tile AddTile(string title, Color color, string glyph)
    {
        var ring = new Ring { Width = 92, Height = 92, Fill = new SolidColorBrush(color) };
        var value = new TextBlock { Text = "—", Style = (Style)Application.Current.Resources["SubtitleTextBlockStyle"], HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        ring.Children.Add(value);
        var sub = new TextBlock { Style = (Style)Application.Current.Resources["Secondary"], TextTrimming = TextTrimming.CharacterEllipsis };
        var header = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        header.Children.Add(new FontIcon { Glyph = glyph, FontSize = 16, Foreground = new SolidColorBrush(color) });
        header.Children.Add(new TextBlock { Text = title, Style = (Style)Application.Current.Resources["BodyStrongTextBlockStyle"] });
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Spacing = 6 };
        text.Children.Add(header);
        text.Children.Add(sub);
        var row = new Grid { ColumnSpacing = 16 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition());
        row.Children.Add(ring);
        Grid.SetColumn(text, 1);
        row.Children.Add(text);
        var card = new Border { Style = (Style)Application.Current.Resources["Card"], Child = row };
        Tiles.Children.Add(card);
        return new Tile(card, ring, value, sub);
    }

    /** 4 tiles in a row on wide windows, 2x2 otherwise; network card drops below the chart when narrow. */
    void Layout(double width)
    {
        var cols = width >= 1100 ? 4 : 2;
        Tiles.ColumnDefinitions.Clear();
        Tiles.RowDefinitions.Clear();
        for (var i = 0; i < cols; i++) Tiles.ColumnDefinitions.Add(new ColumnDefinition());
        for (var i = 0; i < 4 / cols; i++) Tiles.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        for (var i = 0; i < Tiles.Children.Count; i++)
        {
            Grid.SetColumn((FrameworkElement)Tiles.Children[i], i % cols);
            Grid.SetRow((FrameworkElement)Tiles.Children[i], i / cols);
        }
        var narrow = width < 900;
        Lower.ColumnDefinitions[1].Width = narrow ? new GridLength(0) : new GridLength(1, GridUnitType.Star);
        Lower.ColumnSpacing = narrow ? 0 : 16;
        if (Lower.RowDefinitions.Count == 0) { Lower.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); Lower.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); }
        Grid.SetColumn(NetCard, narrow ? 0 : 1);
        Grid.SetRow(NetCard, narrow ? 1 : 0);
    }

    void Start()
    {
        timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromSeconds(Math.Clamp(Settings.Current.RefreshSec, 2, 10));
        timer.Tick += (_, _) => Poll();
        timer.Start();
        Poll();
    }

    async void Poll()
    {
        if (busy) return;
        busy = true;
        try
        {
            var s = await CasaApi.Stats();
            Error.IsOpen = false;
            Show(s);
        }
        catch (CasaException ex)
        {
            Error.Title = ex.Message;
            Error.IsOpen = true;
        }
        finally { busy = false; }
    }

    void Show(SysStats s)
    {
        var c = s.Cpu / 100;
        cpu.Ring.Value = c;
        cpu.Value.Text = Fmt.Percent(c);
        cpu.Sub.Text = $"{s.Cores} core";
        var m = s.MemTotal > 0 ? (double)s.MemUsed / s.MemTotal : 0;
        mem.Ring.Value = m;
        mem.Value.Text = Fmt.Percent(m);
        mem.Sub.Text = $"{Fmt.Bytes(s.MemUsed)} di {Fmt.Bytes(s.MemTotal)}";
        var d = s.DiskTotal > 0 ? (double)s.DiskUsed / s.DiskTotal : 0;
        disk.Ring.Value = d;
        disk.Value.Text = Fmt.Percent(d);
        disk.Sub.Text = $"{Fmt.Bytes(s.DiskUsed)} di {Fmt.Bytes(s.DiskTotal)}";
        temp.Ring.Value = (s.Temp ?? 0) / 100.0;
        temp.Value.Text = s.Temp is { } t ? $"{t}°" : "—";
        temp.Sub.Text = s.Temp is { } t2 ? (t2 >= 75 ? "Calda" : t2 >= 60 ? "Tiepida" : "Fresca") : "Non disponibile";

        var now = DateTime.UtcNow;
        if (lastNet is { } l && now > l.at)
        {
            var sec = (now - l.at).TotalSeconds;
            Down.Text = Fmt.Rate((long)(Math.Max(0, s.NetRx - l.rx) / sec));
            Up.Text = Fmt.Rate((long)(Math.Max(0, s.NetTx - l.tx) / sec));
        }
        lastNet = (s.NetRx, s.NetTx, now);

        history.RemoveAt(0);
        history.Add(c);
        DrawChart();
    }

    void Chart_SizeChanged(object sender, SizeChangedEventArgs e) => DrawChart();

    void DrawChart()
    {
        double w = Chart.ActualWidth, h = Chart.ActualHeight;
        if (w <= 0) return;
        var line = new PointCollection();
        var fill = new PointCollection { new Point(0, h) };
        for (var i = 0; i < history.Count; i++)
        {
            var p = new Point(i * w / (history.Count - 1), h - 4 - history[i] * (h - 8));
            line.Add(p);
            fill.Add(p);
        }
        fill.Add(new Point(w, h));
        ChartLine.Points = line;
        ChartFill.Points = fill;
    }
}
