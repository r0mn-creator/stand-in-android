package com.limelight.utils;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.ui.si.SiDialog;

/**
 * Work in progress. Normally a Stand-In dialog with an inline spinner; for connecting to a PC,
 * the full-window Connecting screen (DESIGN.md "Connecting"): spinner 48, "Connecting to PC…",
 * a status line and a Cancel button.
 */
public class SpinnerDialog implements Runnable {
    private final String title;
    private String message;
    private final Activity activity;
    private final boolean finish;
    private final boolean connecting;
    private SiDialog dialog;
    private android.app.Dialog screen;
    private TextView messageView;
    private boolean shown;

    private static final ArrayList<SpinnerDialog> rundownDialogs = new ArrayList<>();

    private SpinnerDialog(Activity activity, String title, String message, boolean finish, boolean connecting)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.finish = finish;
        this.connecting = connecting;
    }

    public static SpinnerDialog displayDialog(Activity activity, String title, String message, boolean finish)
    {
        SpinnerDialog spinner = new SpinnerDialog(activity, title, message, finish, false);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    /** The Connecting screen. Cancel (or Back) finishes the activity, like the old spinner did. */
    public static SpinnerDialog displayConnecting(Activity activity, String pcName)
    {
        String title = pcName != null && !pcName.isEmpty()
                ? activity.getString(R.string.si_connecting_to, pcName)
                : activity.getString(R.string.si_connecting);
        SpinnerDialog spinner = new SpinnerDialog(activity, title,
                activity.getString(R.string.si_connecting_status), true, true);
        activity.runOnUiThread(spinner);
        return spinner;
    }

    public static void closeDialogs(Activity activity)
    {
        synchronized (rundownDialogs) {
            Iterator<SpinnerDialog> i = rundownDialogs.iterator();
            while (i.hasNext()) {
                SpinnerDialog dialog = i.next();
                if (dialog.activity == activity) {
                    i.remove();
                    dialog.close();
                }
            }
        }
    }

    public void dismiss()
    {
        // Running again once shown will destroy it
        activity.runOnUiThread(this);
    }

    public void setMessage(final String message)
    {
        // The Connecting screen keeps its plain status line; stage names are jargon
        if (connecting) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SpinnerDialog.this.message = message;
                if (messageView != null) {
                    messageView.setText(message);
                }
            }
        });
    }

    private void close() {
        if (dialog != null) {
            dialog.dismiss();
        }
        if (screen != null && screen.isShowing()) {
            try {
                screen.dismiss();
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void cancel() {
        synchronized (rundownDialogs) {
            rundownDialogs.remove(this);
        }
        close();
        activity.finish();
    }

    private void showConnecting() {
        screen = new android.app.Dialog(activity, R.style.SiFullWindow);
        screen.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(screen.getContext()).inflate(R.layout.si_connecting, null, false);
        ((TextView) v.findViewById(R.id.siConnectingTitle)).setText(title);
        messageView = v.findViewById(R.id.siConnectingStatus);
        messageView.setText(message);
        v.findViewById(R.id.siConnectingCancel).setOnClickListener(x -> cancel());
        screen.setContentView(v);
        screen.setCancelable(true);
        screen.setOnCancelListener(d -> cancel());
        screen.show();
        Window w = screen.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        v.findViewById(R.id.siConnectingCancel).requestFocus();
    }

    private void showDialog() {
        View content = LayoutInflater.from(activity).inflate(R.layout.si_progress_row, null, false);
        messageView = content.findViewById(R.id.siProgressText);
        messageView.setText(message);
        dialog = new SiDialog(activity).title(title).content(content).cancelable(finish);
        if (finish) {
            dialog.onCancel(this::cancel).cancelButton(activity.getString(R.string.si_cancel));
        }
        dialog.show();
    }

    @Override
    public void run() {

        // If we're dying, don't bother doing anything
        if (activity.isFinishing()) {
            return;
        }

        if (!shown)
        {
            shown = true;
            synchronized (rundownDialogs) {
                rundownDialogs.add(this);
                if (connecting) {
                    showConnecting();
                }
                else {
                    showDialog();
                }
            }
        }
        else
        {
            synchronized (rundownDialogs) {
                if (rundownDialogs.remove(this)) {
                    close();
                }
            }
        }
    }
}
