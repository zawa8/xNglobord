using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace XngloBord;

/// <summary>
/// Port of XngloDictionary.kt (and the earlier Python ports' Dictionary
/// class): pools every *.txt file in a directory (one word per line),
/// keyed by first character, case-sensitive prefix match, sorted by
/// (length, alphabetically).
/// </summary>
public sealed class XngloDictionary
{
    private readonly Dictionary<char, List<string>> byFirstChar = new();
    private bool loaded;

    public void LoadAll(string dictionariesDir)
    {
        if (loaded) return;
        loaded = true;
        if (!Directory.Exists(dictionariesDir)) return;

        foreach (var path in Directory.EnumerateFiles(dictionariesDir, "*.txt"))
        {
            try
            {
                foreach (var line in File.ReadLines(path))
                {
                    var word = line.Trim();
                    if (word.Length > 0) AddWord(word);
                }
            }
            catch (Exception)
            {
                // a missing/unreadable file shouldn't crash the keyboard
            }
        }

        foreach (var words in byFirstChar.Values)
        {
            words.Sort((a, b) =>
            {
                int byLength = a.Length.CompareTo(b.Length);
                return byLength != 0 ? byLength : string.CompareOrdinal(a, b);
            });
        }
    }

    private void AddWord(string word)
    {
        if (!byFirstChar.TryGetValue(word[0], out var bucket))
        {
            bucket = new List<string>();
            byFirstChar[word[0]] = bucket;
        }
        if (!bucket.Contains(word)) bucket.Add(word);
    }

    public List<string> SuggestionsFor(string prefix, int limit = 5)
    {
        if (string.IsNullOrEmpty(prefix)) return new List<string>();
        if (!byFirstChar.TryGetValue(prefix[0], out var bucket)) return new List<string>();
        return bucket.Where(w => w != prefix && w.StartsWith(prefix, StringComparison.Ordinal)).Take(limit).ToList();
    }
}
