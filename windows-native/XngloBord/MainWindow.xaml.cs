using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;

namespace XngloBord;

public partial class MainWindow : Window
{
    private static readonly string BaseDir = AppDomain.CurrentDomain.BaseDirectory;
    private static readonly string FontsDir = Path.Combine(BaseDir, "fonts");
    private static readonly string DictionariesDir = Path.Combine(BaseDir, "dictionaries");
    private static readonly string ConfigDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "xnglobord");

    private readonly XngloDictionary dictionary = new();
    private readonly FontManager fontManager;
    private readonly KeyboardEngine engine;
    private readonly Dictionary<int, List<Button>> keyButtons = new();
    private bool isNumericLayout;

    public MainWindow()
    {
        InitializeComponent();

        dictionary.LoadAll(DictionariesDir);
        fontManager = new FontManager(ConfigDir, FontsDir);

        engine = new KeyboardEngine(dictionary)
        {
            CommitText = NativeMethods.SendUnicodeText,
            DeleteBackward = () => NativeMethods.SendVirtualKey(NativeMethods.VK_BACK),
            SendEnter = () => NativeMethods.SendVirtualKey(NativeMethods.VK_RETURN),
            MoveCursorLeftOne = () => NativeMethods.SendVirtualKey(NativeMethods.VK_LEFT),
            Schedule = ScheduleOnce,
            Cancel = CancelScheduled,
            OnCandidatesChanged = RenderCandidates,
            OnStateChanged = UpdateKeyVisuals,
            OnFontPickerRequested = ShowFontPickerPopup,
            OnPopupRequested = ShowOptionsPopup,
        };

        BuildFontCombo();
        RenderLayout(Layouts.Letters);
        UpdateKeyVisuals();
    }

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        // Never let this window take Windows focus -- SendInput then always
        // reaches whatever app the user actually clicked into, exactly like
        // the earlier Python/Tkinter port's WS_EX_NOACTIVATE trick, but done
        // through the standard, well-tested C# GetWindowLongPtr/
        // SetWindowLongPtr P/Invoke pattern (see NativeMethods.cs).
        var hwnd = new WindowInteropHelper(this).Handle;
        var style = NativeMethods.GetWindowLongPtr(hwnd, NativeMethods.GWL_EXSTYLE).ToInt64();
        style |= NativeMethods.WS_EX_NOACTIVATE | NativeMethods.WS_EX_TOOLWINDOW;
        NativeMethods.SetWindowLongPtr(hwnd, NativeMethods.GWL_EXSTYLE, new IntPtr(style));
    }

    // --- timer plumbing for KeyboardEngine's long-press scheduling ---

    private object ScheduleOnce(int delayMs, Action fn)
    {
        var timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(delayMs) };
        timer.Tick += (_, _) => { timer.Stop(); fn(); };
        timer.Start();
        return timer;
    }

    private void CancelScheduled(object token)
    {
        if (token is DispatcherTimer t) t.Stop();
    }

    // --- key grid ---

    private void RenderLayout(List<List<KeyDef>> layout)
    {
        KeysPanel.Children.Clear();
        keyButtons.Clear();

        foreach (var row in layout)
        {
            // Star-sized columns proportional to each key's WidthPercent,
            // so e.g. the wider spacebar/comma/period actually render
            // wider instead of every key getting an equal share.
            var rowGrid = new Grid { Margin = new Thickness(0, 1, 0, 1) };
            foreach (var key in row)
                rowGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(key.WidthPercent, GridUnitType.Star) });

            for (int col = 0; col < row.Count; col++)
            {
                var key = row[col];
                var btn = new Button
                {
                    Content = key.Label,
                    Margin = new Thickness(1),
                    Height = 40,
                    Background = ColorFor(key.Label),
                    Tag = key.Code,
                };
                btn.PreviewMouseLeftButtonDown += (s, e) =>
                {
                    engine.OnPress((int)((Button)s!).Tag);
                    e.Handled = true;
                };
                btn.PreviewMouseLeftButtonUp += (s, e) =>
                {
                    int code = (int)((Button)s!).Tag;
                    engine.OnRelease(code);
                    engine.OnKey(code); // UpdateKeyVisuals (via engine's Changed()) re-renders the grid if the layout page changed
                    e.Handled = true;
                };
                Grid.SetColumn(btn, col);
                rowGrid.Children.Add(btn);

                if (!keyButtons.TryGetValue(key.Code, out var list))
                {
                    list = new List<Button>();
                    keyButtons[key.Code] = list;
                }
                list.Add(btn);
            }
            KeysPanel.Children.Add(rowGrid);
        }

        ApplyFontToKeys(fontManager.GetSelectedOption());
    }

    private static Brush ColorFor(string label)
    {
        if (Layouts.YellowHexLabels.Contains(label)) return new SolidColorBrush(Color.FromRgb(0xF5, 0xD7, 0x6E));
        if (Layouts.PinkOperatorLabels.Contains(label)) return new SolidColorBrush(Color.FromRgb(0xF2, 0xA2, 0xC2));
        return SystemColors.ControlBrush;
    }

    private void UpdateKeyVisuals()
    {
        // Keep the letter/numeric page in sync if the engine auto-returned
        // from a one-shot numeric page after a key committed elsewhere.
        if (isNumericLayout != engine.IsNumericMode)
        {
            isNumericLayout = engine.IsNumericMode;
            RenderLayout(isNumericLayout ? Layouts.Numeric : Layouts.Letters);
        }

        if (keyButtons.TryGetValue(KeyboardEngine.ShiftCode, out var shiftButtons))
        {
            foreach (var btn in shiftButtons)
            {
                if (engine.IsCapsLock) { btn.Content = "\u21ea"; btn.FontWeight = FontWeights.Bold; }
                else if (engine.IsShiftActive) { btn.Content = "\u21e7"; btn.FontWeight = FontWeights.Bold; }
                else { btn.Content = "\u21e7"; btn.FontWeight = FontWeights.Normal; }
            }
        }
    }

    // --- candidates strip ---

    private void RenderCandidates(List<string> words)
    {
        CandidatesPanel.Children.Clear();
        var family = fontManager.LoadFontFamily(fontManager.GetSelectedOption());
        foreach (var word in words)
        {
            var chip = new Button { Content = word, Margin = new Thickness(2), Padding = new Thickness(6, 2, 6, 2), FontFamily = family };
            chip.Click += (_, _) => engine.ApplyCandidate(word);
            CandidatesPanel.Children.Add(chip);
        }
    }

    // --- font picker ---

    private void BuildFontCombo()
    {
        foreach (var opt in LocalFonts.All) FontCombo.Items.Add(opt.DisplayName);
        var current = fontManager.GetSelectedOption();
        FontCombo.SelectedIndex = LocalFonts.All.FindIndex(o => o.FontId == current.FontId);
    }

    private void FontCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (FontCombo.SelectedIndex < 0) return;
        ApplyFont(LocalFonts.All[FontCombo.SelectedIndex].FontId);
    }

    private void ShowFontPickerPopup()
    {
        var menu = new ContextMenu();
        foreach (var opt in LocalFonts.All)
        {
            var item = new MenuItem { Header = opt.DisplayName };
            item.Click += (_, _) => ApplyFont(opt.FontId);
            menu.Items.Add(item);
        }
        menu.PlacementTarget = this;
        menu.IsOpen = true;
    }

    private void ApplyFont(string fontId)
    {
        fontManager.SetSelectedFontId(fontId);
        var idx = LocalFonts.All.FindIndex(o => o.FontId == fontId);
        if (idx >= 0) FontCombo.SelectedIndex = idx;
        ApplyFontToKeys(LocalFonts.ById(fontId)!);
        engine.RenderCandidates();
    }

    private void ApplyFontToKeys(LocalFontOption option)
    {
        var family = fontManager.LoadFontFamily(option);
        foreach (var buttons in keyButtons.Values)
            foreach (var btn in buttons)
                btn.FontFamily = family;
        FontDiagText.Text = fontManager.LastDiagnostic;
    }

    // --- comma / bracket long-press popups (SymbolAltPopup equivalent) ---

    private void ShowOptionsPopup(List<string> options, Action<string> onPick)
    {
        var popup = new Popup
        {
            PlacementTarget = KeysPanel,
            Placement = PlacementMode.Center,
            StaysOpen = false,
            AllowsTransparency = true,
        };
        var panel = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Background = SystemColors.WindowBrush,
        };
        panel.SetValue(TextElement.FontSizeProperty, 18.0);
        foreach (var opt in options)
        {
            var btn = new Button { Content = opt, Margin = new Thickness(2), Padding = new Thickness(10, 6, 10, 6) };
            btn.Click += (_, _) => { onPick(opt); popup.IsOpen = false; };
            panel.Children.Add(btn);
        }
        popup.Child = new Border
        {
            Child = panel,
            BorderBrush = SystemColors.ActiveBorderBrush,
            BorderThickness = new Thickness(1),
            Background = SystemColors.WindowBrush,
        };
        popup.IsOpen = true;
    }
}
