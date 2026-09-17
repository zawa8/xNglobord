using System;
using System.Collections.Generic;

namespace XngloBord;

/// <summary>
/// Platform-independent port of XngloIME.kt's key state machine.
/// No Win32/WPF here at all -- MainWindow.xaml.cs supplies host
/// callbacks (commit text, delete, enter, schedule a timer, show a
/// popup, etc.) and drives this with <see cref="OnPress"/>/<see
/// cref="OnRelease"/>/<see cref="OnKey"/> for each key.
///
/// Ported from the same design as the earlier Python desktop ports'
/// xnglo_core.py (Linux/old Windows-Tkinter port), extended to match
/// what's since shipped on the Android app's main branch: the digit
/// row and QRTA row need no special handling (they fall through the
/// same generic "commit this literal character" path E/U/I/O/M/X and
/// L/Y/V/W/P/F already used, since they're not in
/// <see cref="HexLetterCodes"/>/<see cref="OperatorLetterCodes"/> or
/// the a-z range, the default branch handles them correctly on its
/// own); the punctuation/bracket row's ( [ { auto-pairing and
/// long-press popup are new here, ported from the matching
/// XngloIME.kt logic (commit 04c24ad / the row-add commit).
///
/// Not ported (same as the Python desktop ports, and for the same
/// reasons): voice input (mic key, codes=-3, accepted as a no-op).
/// </summary>
public sealed class KeyboardEngine
{
    // --- special key codes, same values as XngloIME.kt's companion object ---
    public const int KeycodeDelete = -5;
    public const int KeycodeEnter = -4;
    public const int ModeSwitchCode = -2;
    public const int ShiftCode = -1;
    public const int MicCode = -3; // accepted so layouts match; voice input isn't ported here

    public const int WordBoundarySpace = 32;
    public const int WordBoundaryComma = 44;
    public const int WordBoundaryPeriod = 46;

    public const int LeftParen = 40;   // (
    public const int LeftBracket = 91; // [
    public const int LeftBrace = 123;  // {
    public static readonly HashSet<int> BracketPairCodes = new() { LeftParen, LeftBracket, LeftBrace };

    // L Y V W P F -- hex digits 10-15, xi38's own letters
    public static readonly HashSet<int> HexLetterCodes = new() { 76, 89, 86, 87, 80, 70 };
    // E U I O M X -- plain letters an hscii font remaps to display as ==/!=/>=/<=/&&/||
    public static readonly HashSet<int> OperatorLetterCodes = new() { 69, 85, 73, 79, 77, 88 };

    private const int LowercaseA = 97;
    private const int LowercaseZ = 122;
    private const int CaseOffset = 32; // 'a' (97) - 'A' (65)

    public const int LongPressMs = 500;
    public const int CapsLockDoubleTapMs = 350;

    // --- host callbacks (wired up by MainWindow) ---
    public Action<string> CommitText = _ => { };
    public Action DeleteBackward = () => { };
    public Action SendEnter = () => { };
    /// <summary>Moves the caret one character left (used after a bracket
    /// pair is typed, since raw keystroke injection has no equivalent of
    /// InputConnection's newCursorPosition -- see NativeMethods.cs).</summary>
    public Action MoveCursorLeftOne = () => { };
    public Action<object> Cancel = _ => { };
    public Func<int, Action, object> Schedule = (_, fn) => { fn(); return new object(); };
    public Action<List<string>> OnCandidatesChanged = _ => { };
    public Action OnStateChanged = () => { };
    public Action OnFontPickerRequested = () => { };
    /// <summary>options, onPick(selectedOption)</summary>
    public Action<List<string>, Action<string>> OnPopupRequested = (_, __) => { };

    private readonly XngloDictionary dictionary;

    public string CurrentWord { get; private set; } = "";
    public bool IsShiftActive { get; private set; }
    public bool IsCapsLock { get; private set; }
    public bool IsNumericMode { get; private set; }
    private bool isNumericLocked;
    private long lastShiftTapMs;
    private long lastModeTapMs;

    private object? spaceTimer;
    private bool spaceLongPressFired;
    private object? commaTimer;
    private bool commaLongPressFired;
    private object? bracketTimer;
    private bool bracketLongPressFired;
    private object? letterTimer;
    private bool letterLongPressFired;
    private int letterLongPressCode = -1;

    public KeyboardEngine(XngloDictionary dictionary)
    {
        this.dictionary = dictionary;
    }

    public void OnPress(int code)
    {
        if (code == WordBoundarySpace)
        {
            spaceLongPressFired = false;
            spaceTimer = Schedule(LongPressMs, FireSpaceLongPress);
        }
        else if (code == WordBoundaryComma)
        {
            commaLongPressFired = false;
            commaTimer = Schedule(LongPressMs, FireCommaLongPress);
        }
        else if (BracketPairCodes.Contains(code))
        {
            bracketLongPressFired = false;
            bracketTimer = Schedule(LongPressMs, FireBracketLongPress);
        }
        else if (code is >= LowercaseA and <= LowercaseZ)
        {
            letterLongPressFired = false;
            letterLongPressCode = code;
            letterTimer = Schedule(LongPressMs, FireLetterLongPress);
        }
    }

    public void OnRelease(int code)
    {
        if (code == WordBoundarySpace && spaceTimer is not null) { Cancel(spaceTimer); spaceTimer = null; }
        else if (code == WordBoundaryComma && commaTimer is not null) { Cancel(commaTimer); commaTimer = null; }
        else if (BracketPairCodes.Contains(code) && bracketTimer is not null) { Cancel(bracketTimer); bracketTimer = null; }
        else if (code is >= LowercaseA and <= LowercaseZ && letterTimer is not null) { Cancel(letterTimer); letterTimer = null; }
    }

    public void OnKey(int code)
    {
        switch (code)
        {
            case KeycodeDelete:
                DeleteBackward();
                if (CurrentWord.Length > 0) CurrentWord = CurrentWord[..^1];
                Changed();
                break;

            case KeycodeEnter:
                SendEnter();
                CurrentWord = "";
                Changed();
                break;

            case WordBoundarySpace:
                if (spaceLongPressFired)
                {
                    spaceLongPressFired = false; // font picker already opened -- don't also insert a space
                }
                else
                {
                    CommitText(" ");
                    CurrentWord = "";
                    Changed();
                }
                break;

            case WordBoundaryComma:
                if (commaLongPressFired)
                {
                    commaLongPressFired = false; // , : ; popup already shown -- don't also insert a comma
                }
                else
                {
                    CommitText(",");
                    CurrentWord = "";
                    Changed();
                    MaybeAutoReturnFromNumeric();
                }
                break;

            case WordBoundaryPeriod:
                CommitText(".");
                CurrentWord = "";
                Changed();
                MaybeAutoReturnFromNumeric();
                break;

            case ModeSwitchCode:
                HandleModeSwitchTap();
                break;

            case ShiftCode:
                HandleShiftTap();
                break;

            case MicCode:
                break; // no-op: voice input not ported on desktop

            default:
                if (BracketPairCodes.Contains(code))
                {
                    if (bracketLongPressFired)
                    {
                        bracketLongPressFired = false; // ( ) [ ] { } popup already shown -- don't also insert the pair
                    }
                    else
                    {
                        char open = (char)code;
                        char close = code switch { LeftParen => ')', LeftBracket => ']', _ => '}' };
                        CommitText(open.ToString());
                        CommitText(close.ToString());
                        MoveCursorLeftOne(); // caret ends up between the pair
                        CurrentWord = "";
                        Changed();
                        MaybeAutoReturnFromNumeric();
                    }
                }
                else if (HexLetterCodes.Contains(code) || OperatorLetterCodes.Contains(code))
                {
                    CommitText(((char)code).ToString());
                    CurrentWord = "";
                    Changed();
                    MaybeAutoReturnFromNumeric();
                }
                else if (code is >= LowercaseA and <= LowercaseZ && letterLongPressFired && code == letterLongPressCode)
                {
                    letterLongPressFired = false; // long-press already committed the capital
                }
                else
                {
                    bool useShift = IsShiftActive && code is >= LowercaseA and <= LowercaseZ;
                    int codeToCommit = useShift ? code - CaseOffset : code;
                    if (useShift && !IsCapsLock)
                    {
                        IsShiftActive = false;
                    }
                    char ch = (char)codeToCommit;
                    CommitText(ch.ToString());
                    if (char.IsLetter(ch)) CurrentWord += ch; else CurrentWord = "";
                    Changed();
                    MaybeAutoReturnFromNumeric();
                }
                break;
        }
    }

    public List<string> RenderCandidates()
    {
        var suggestions = dictionary.SuggestionsFor(CurrentWord);
        OnCandidatesChanged(suggestions);
        return suggestions;
    }

    public void ApplyCandidate(string word)
    {
        for (int i = 0; i < CurrentWord.Length; i++) DeleteBackward();
        CommitText(word);
        CurrentWord = "";
        Changed();
    }

    public void ResetForNewField()
    {
        IsNumericMode = false;
        isNumericLocked = false;
        IsShiftActive = false;
        IsCapsLock = false;
        CurrentWord = "";
        Changed();
    }

    // --- internal helpers, mirroring XngloIME.kt's private methods ---

    private void FireSpaceLongPress()
    {
        spaceLongPressFired = true;
        OnFontPickerRequested();
    }

    private void FireCommaLongPress()
    {
        commaLongPressFired = true;
        OnPopupRequested(new List<string> { ",", ":", ";", ">", "<" }, symbol =>
        {
            CommitText(symbol);
            CurrentWord = "";
            Changed();
        });
    }

    private void FireBracketLongPress()
    {
        bracketLongPressFired = true;
        OnPopupRequested(new List<string> { "(", ")", "[", "]", "{", "}" }, symbol =>
        {
            CommitText(symbol);
            CurrentWord = "";
            Changed();
        });
    }

    private void FireLetterLongPress()
    {
        letterLongPressFired = true;
        char upper = char.ToUpperInvariant((char)letterLongPressCode);
        CommitText(upper.ToString());
        CurrentWord += upper;
        if (IsShiftActive && !IsCapsLock) IsShiftActive = false;
        Changed();
    }

    private void HandleShiftTap()
    {
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        if (IsCapsLock)
        {
            IsCapsLock = false;
            IsShiftActive = false;
        }
        else if (IsShiftActive && (now - lastShiftTapMs) < CapsLockDoubleTapMs)
        {
            IsCapsLock = true;
        }
        else
        {
            IsShiftActive = !IsShiftActive;
        }
        lastShiftTapMs = now;
        Changed();
    }

    private void HandleModeSwitchTap()
    {
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        bool isDoubleTap = (now - lastModeTapMs) < CapsLockDoubleTapMs;
        lastModeTapMs = now;

        if (!IsNumericMode)
        {
            IsNumericMode = true;
            isNumericLocked = false;
        }
        else if (isDoubleTap && !isNumericLocked)
        {
            isNumericLocked = true;
        }
        else
        {
            IsNumericMode = false;
            isNumericLocked = false;
        }

        if (IsShiftActive || IsCapsLock)
        {
            IsShiftActive = false;
            IsCapsLock = false;
        }
        Changed();
    }

    private void MaybeAutoReturnFromNumeric()
    {
        if (IsNumericMode && !isNumericLocked)
        {
            IsNumericMode = false;
            Changed();
        }
    }

    private void Changed()
    {
        OnStateChanged();
        RenderCandidates();
    }
}
