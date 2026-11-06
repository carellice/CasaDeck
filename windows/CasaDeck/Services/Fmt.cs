using System;
using System.Globalization;

namespace CasaDeck.Services;

public static class Fmt
{
    static readonly CultureInfo It = new("it-IT");

    public static string Bytes(long b)
    {
        string[] units = ["B", "KB", "MB", "GB", "TB"];
        double v = b;
        var i = 0;
        while (v >= 1024 && i < units.Length - 1) { v /= 1024; i++; }
        return v.ToString(i == 0 || v >= 100 ? "0" : "0.#", It) + " " + units[i];
    }

    public static string Rate(long bytesPerSec) => Bytes(bytesPerSec) + "/s";

    public static string Percent(double fraction) => Math.Round(fraction * 100).ToString(It) + "%";

    /** CasaOS dates come as RFC 3339; anything else is shown as-is. */
    public static string Date(string s) =>
        DateTimeOffset.TryParse(s, CultureInfo.InvariantCulture, DateTimeStyles.None, out var d) ? d.LocalDateTime.ToString("d MMM yyyy, HH:mm", It) : s;
}
