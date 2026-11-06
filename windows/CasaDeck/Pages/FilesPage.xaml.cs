using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using CasaDeck.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage;
using Windows.Storage.Pickers;

namespace CasaDeck.Pages;

public sealed class FileItem(FileEntry e)
{
    static readonly Brush Folder = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 0xFF, 0xB0, 0x20));
    static readonly Brush Doc = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 0x8D, 0x99, 0xFF));
    public FileEntry Entry { get; } = e;
    public string Glyph => Entry.IsDir ? "" : "";
    public Brush Tint => Entry.IsDir ? Folder : Doc;
    public string SizeText => Entry.IsDir ? "" : Fmt.Bytes(Entry.Size);
    public string DateText => Fmt.Date(Entry.Date);
}

public sealed partial class FilesPage : Page
{
    /** Kept across tab switches, like the Android app. */
    static string path = "/DATA";
    bool transferring;

    public FilesPage()
    {
        InitializeComponent();
        Loaded += (_, _) => Open(path);
    }

    async void Open(string p)
    {
        Spinner.IsActive = true;
        Empty.Visibility = Visibility.Collapsed;
        try
        {
            var items = await CasaApi.Files(p);
            path = p;
            Error.IsOpen = false;
            List.ItemsSource = items.Select(i => new FileItem(i)).ToList();
            Empty.Visibility = items.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            Crumbs.ItemsSource = new[] { "/" }.Concat(p.Split('/', StringSplitOptions.RemoveEmptyEntries)).ToList();
            UpButton.IsEnabled = p != "/";
        }
        catch (CasaException ex)
        {
            Error.Title = ex.Message;
            Error.IsOpen = true;
        }
        finally { Spinner.IsActive = false; }
    }

    static string ParentOf(string p) => p.TrimEnd('/').LastIndexOf('/') is var i and > 0 ? p[..i] : "/";

    void Up_Click(object sender, RoutedEventArgs e) => Open(ParentOf(path));

    void Refresh_Click(object sender, RoutedEventArgs e) => Open(path);

    void Crumbs_ItemClicked(BreadcrumbBar sender, BreadcrumbBarItemClickedEventArgs args)
    {
        var parts = path.Split('/', StringSplitOptions.RemoveEmptyEntries);
        Open("/" + string.Join('/', parts.Take(args.Index)));
    }

    void List_DoubleTapped(object sender, DoubleTappedRoutedEventArgs e)
    {
        if (List.SelectedItem is FileItem { Entry.IsDir: true } f) Open(f.Entry.Path);
    }

    void List_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter && List.SelectedItem is FileItem { Entry.IsDir: true } f) Open(f.Entry.Path);
        else if (e.Key == Windows.System.VirtualKey.Back) Open(ParentOf(path));
    }

    void List_SelectionChanged(object sender, SelectionChangedEventArgs e) => DownloadButton.IsEnabled = List.SelectedItem != null;

    async void Download_Click(object sender, RoutedEventArgs e)
    {
        if (List.SelectedItem is not FileItem item || transferring) return;
        var f = item.Entry;
        var name = f.IsDir ? f.Name + ".zip" : f.Name;
        var picker = new FileSavePicker { SuggestedFileName = Path.GetFileNameWithoutExtension(name) };
        var ext = Path.GetExtension(name);
        picker.FileTypeChoices.Add(ext.Length > 0 ? ext.ToUpperInvariant().TrimStart('.') : "File", [ext.Length > 0 ? ext : ".bin"]);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, App.Window.Hwnd);
        var target = await picker.PickSaveFileAsync();
        if (target == null) return;
        await Run($"Scarico {name}", async progress =>
        {
            await using var stream = await target.OpenStreamForWriteAsync();
            stream.SetLength(0);
            await CasaApi.Download(f, stream, progress);
        }, $"{name} scaricato");
    }

    async void Upload_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add("*");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, App.Window.Hwnd);
        var files = await picker.PickMultipleFilesAsync();
        await Upload(files.Select(f => f.Path).ToList());
    }

    void Page_DragOver(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems)) return;
        e.AcceptedOperation = DataPackageOperation.Copy;
        e.DragUIOverride.Caption = $"Carica in {path}";
    }

    async void Page_Drop(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems)) return;
        var items = await e.DataView.GetStorageItemsAsync();
        // shortcut: dropped folders are skipped, only files are uploaded; walk them recursively if needed
        await Upload(items.OfType<StorageFile>().Select(f => f.Path).ToList());
    }

    async Task Upload(List<string> paths)
    {
        if (paths.Count == 0 || transferring) return;
        var dir = path;
        for (var i = 0; i < paths.Count; i++)
        {
            var p = paths[i];
            var label = paths.Count > 1 ? $"Carico {Path.GetFileName(p)} ({i + 1} di {paths.Count})" : $"Carico {Path.GetFileName(p)}";
            if (!await Run(label, progress => CasaApi.Upload(dir, p, progress), null)) return;
        }
        ShellPage.Current?.Notify(paths.Count == 1 ? $"{Path.GetFileName(paths[0])} caricato" : $"{paths.Count} file caricati", InfoBarSeverity.Success);
        if (path == dir) Open(dir);
    }

    async Task<bool> Run(string label, Func<IProgress<double>, Task> work, string? done)
    {
        transferring = true;
        TransferText.Text = label;
        TransferBar.Value = 0;
        Transfer.Visibility = Visibility.Visible;
        try
        {
            await work(new Progress<double>(v => TransferBar.Value = v));
            if (done != null) ShellPage.Current?.Notify(done, InfoBarSeverity.Success);
            return true;
        }
        catch (CasaException ex) { ShellPage.Current?.Notify(ex.Message, InfoBarSeverity.Error); return false; }
        catch (IOException ex) { ShellPage.Current?.Notify(ex.Message, InfoBarSeverity.Error); return false; }
        finally
        {
            transferring = false;
            Transfer.Visibility = Visibility.Collapsed;
        }
    }
}
