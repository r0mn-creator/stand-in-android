package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;

import com.limelight.R;
import com.limelight.ui.si.SiDialog;

/**
 * Message / error dialog (DESIGN.md): the title says what happened, the body is the server's
 * own message when there is one plus what to try, then an optional "Details" and OK.
 */
public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final String details;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private SiDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message, String details, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.details = details;
        this.runOnDismiss = runOnDismiss;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            for (Dialog d : rundownDialogs) {
                if (d.alert != null) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(final Activity activity, String title, String message, final boolean endAfterDismiss)
    {
        displayDialog(activity, title, message, null, endAfterDismiss);
    }

    /** With technical text behind a "Details" button. */
    public static void displayDialog(final Activity activity, String title, String message, String details, final boolean endAfterDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, details, new Runnable() {
            @Override
            public void run() {
                if (endAfterDismiss) {
                    activity.finish();
                }
            }
        }));
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, null, runOnDismiss));
    }

    private void finishDialog() {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(Dialog.this);
            alert.dismiss();
        }
        runOnDismiss.run();
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = new SiDialog(activity)
                .title(title)
                .body(message)
                .cancelable(false);
        if (details != null && !details.isEmpty()) {
            alert.secondary(activity.getString(R.string.si_details), d -> new SiDialog(activity)
                    .title(title)
                    .body(details)
                    .primary(activity.getString(R.string.si_ok), null)
                    .show());
        }
        alert.primary(activity.getString(R.string.si_ok), d -> finishDialog());

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }

}
