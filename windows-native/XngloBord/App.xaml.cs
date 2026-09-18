using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace XngloBord;

public partial class App : Application
{
    // Since this is a WinExe with no console, an unhandled exception
    // would otherwise just silently kill the process -- exactly what's
    // being reported ("closes by itself"). This writes the crash to a
    // log file AND shows it in a MessageBox (so it's visible without
    // needing a debugger attached), and tries to keep the app running
    // (e.Handled = true) for UI-thread exceptions rather than exiting.
    public App()
    {
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnAppDomainUnhandledException;
    }

    private static void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        LogAndShow(e.Exception, recoverable: true);
        e.Handled = true; // try to keep the app running instead of crashing
    }

    private static void OnAppDomainUnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex) LogAndShow(ex, recoverable: false);
    }

    private static void LogAndShow(Exception ex, bool recoverable)
    {
        try
        {
            var logDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "xnglobord");
            Directory.CreateDirectory(logDir);
            var logPath = Path.Combine(logDir, "crash.log");
            File.AppendAllText(logPath, $"[{DateTime.Now:O}] {ex}\n\n");
        }
        catch (Exception)
        {
            // If even logging fails, at least still try to show the MessageBox below.
        }

        MessageBox.Show(
            ex.ToString(),
            recoverable ? "xNglobord ran into a problem (continuing)" : "xNglobord crashed",
            MessageBoxButton.OK,
            recoverable ? MessageBoxImage.Warning : MessageBoxImage.Error);
    }
}
