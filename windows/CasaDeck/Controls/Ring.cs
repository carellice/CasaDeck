using System;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;

namespace CasaDeck.Controls;

/** Thick rounded progress ring that glides to each new value. */
public sealed class Ring : Grid
{
    readonly Path track = new() { StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
    readonly Path arc = new() { StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
    double shown, target;
    bool animating;

    public double Thickness { get; set; } = 11;

    public Brush Fill { set => arc.Stroke = value; }

    public Ring()
    {
        track.Stroke = new SolidColorBrush(Windows.UI.Color.FromArgb(0x30, 0x80, 0x80, 0x90));
        arc.Stroke = new SolidColorBrush(Colors.SlateBlue);
        Children.Add(track);
        Children.Add(arc);
        SizeChanged += (_, _) => Draw();
        Unloaded += (_, _) => Stop();
    }

    /** 0..1 */
    public double Value
    {
        get => target;
        set
        {
            target = Math.Clamp(value, 0, 1);
            if (!animating) { animating = true; CompositionTarget.Rendering += Step; }
        }
    }

    void Step(object? sender, object e)
    {
        shown += (target - shown) * 0.12;
        if (Math.Abs(target - shown) < 0.001) { shown = target; Stop(); }
        Draw();
    }

    void Stop()
    {
        if (!animating) return;
        animating = false;
        CompositionTarget.Rendering -= Step;
    }

    void Draw()
    {
        var size = Math.Min(ActualWidth, ActualHeight);
        if (size <= 0) return;
        track.StrokeThickness = arc.StrokeThickness = Thickness;
        track.Data = Arc(size, 0.9999);
        arc.Data = shown > 0.002 ? Arc(size, shown) : null;
    }

    Geometry Arc(double size, double fraction)
    {
        var r = (size - Thickness) / 2;
        var c = new Point(ActualWidth / 2, ActualHeight / 2);
        var start = new Point(c.X, c.Y - r);
        var angle = fraction * 2 * Math.PI;
        var end = new Point(c.X + r * Math.Sin(angle), c.Y - r * Math.Cos(angle));
        var fig = new PathFigure { StartPoint = start, IsClosed = false };
        fig.Segments.Add(new ArcSegment { Point = end, Size = new Size(r, r), IsLargeArc = fraction > 0.5, SweepDirection = SweepDirection.Clockwise });
        var g = new PathGeometry();
        g.Figures.Add(fig);
        return g;
    }
}
