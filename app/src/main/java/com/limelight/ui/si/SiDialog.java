package com.limelight.ui.si;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

/**
 * The Stand-In dialog (DESIGN.md "Dialog"): a 440-wide surface panel with a title, a muted
 * body, optional content and right-aligned buttons (secondary then primary). Back cancels.
 * Built from our own views so it never picks up the platform's or Material's dialog look.
 */
public class SiDialog {
    public interface Action {
        void run(SiDialog dialog);
    }

    private final Context context;
    private final android.app.Dialog dialog;
    private final View panel;
    private final TextView title, body;
    private final FrameLayout content;
    private final LinearLayout buttons;
    private TextView primaryButton;
    private Runnable onCancel;
    private boolean cancelable = true;

    public SiDialog(Context context) {
        this.context = context;
        dialog = new android.app.Dialog(context, R.style.SiDialogWindow);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        panel = LayoutInflater.from(new ContextThemeWrapper(context, R.style.SiDialogWindow))
                .inflate(R.layout.si_dialog, null, false);
        title = panel.findViewById(R.id.siDialogTitle);
        body = panel.findViewById(R.id.siDialogBody);
        content = panel.findViewById(R.id.siDialogContent);
        buttons = panel.findViewById(R.id.siDialogButtons);
        body.setVisibility(View.GONE);
        panel.findViewById(R.id.siDialogBodyScroll).setVisibility(View.GONE);
        dialog.setContentView(panel);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface d) {
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });
    }

    public Context getContext() {
        return panel.getContext();
    }

    public SiDialog title(CharSequence text) {
        title.setText(text);
        title.setVisibility(text != null && text.length() > 0 ? View.VISIBLE : View.GONE);
        return this;
    }

    public SiDialog body(CharSequence text) {
        boolean show = text != null && text.length() > 0;
        body.setText(text);
        body.setVisibility(show ? View.VISIBLE : View.GONE);
        panel.findViewById(R.id.siDialogBodyScroll).setVisibility(show ? View.VISIBLE : View.GONE);
        return this;
    }

    public SiDialog content(View view) {
        content.removeAllViews();
        content.addView(view);
        content.setVisibility(View.VISIBLE);
        return this;
    }

    /** Back/Escape and the secondary "Cancel" button run this. */
    public SiDialog onCancel(Runnable r) {
        onCancel = r;
        return this;
    }

    public SiDialog cancelable(boolean c) {
        cancelable = c;
        dialog.setCancelable(c);
        return this;
    }

    public SiDialog onDismiss(final Runnable r) {
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                r.run();
            }
        });
        return this;
    }

    /** A Cancel button that behaves exactly like Back. */
    public SiDialog cancelButton(CharSequence text) {
        addButton(text, R.style.SiButton_Secondary, new Action() {
            @Override
            public void run(SiDialog d) {
                d.dismiss();
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });
        return this;
    }

    public SiDialog secondary(CharSequence text, Action action) {
        addButton(text, R.style.SiButton_Secondary, action);
        return this;
    }

    /** Destructive confirmation button. Focus starts on Cancel, never on this. */
    public SiDialog destructive(CharSequence text, Action action) {
        addButton(text, R.style.SiButton_Destructive, action);
        return this;
    }

    public SiDialog primary(CharSequence text, Action action) {
        primaryButton = addButton(text, R.style.SiButton_Primary, action);
        return this;
    }

    /** Convenience: a button that just closes the dialog and runs r (if any). */
    public static Action closeThen(final Runnable r) {
        return new Action() {
            @Override
            public void run(SiDialog d) {
                d.dismiss();
                if (r != null) {
                    r.run();
                }
            }
        };
    }

    public TextView getPrimaryButton() {
        return primaryButton;
    }

    private TextView addButton(CharSequence text, int style, final Action action) {
        TextView b = new TextView(new ContextThemeWrapper(getContext(), style), null, 0);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Si.dp(getContext(), 44));
        if (buttons.getChildCount() > 0) {
            lp.setMarginStart(Si.dp(getContext(), 12));
        }
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action != null) {
                    action.run(SiDialog.this);
                }
                else {
                    dismiss();
                }
            }
        });
        buttons.addView(b, lp);
        return b;
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void show() {
        if (context instanceof Activity && ((Activity) context).isFinishing()) {
            return;
        }
        if (title.getVisibility() != View.VISIBLE) {
            ((ViewGroup.MarginLayoutParams) panel.findViewById(R.id.siDialogBodyScroll).getLayoutParams()).topMargin = 0;
        }
        if (buttons.getChildCount() == 0) {
            buttons.setVisibility(View.GONE);
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = Math.min(Si.dp(context, 440), Math.round(dm.widthPixels * 0.9f));
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        // Focus starts on the primary button; in a destructive confirmation, on Cancel
        View focus = primaryButton != null ? primaryButton
                : (buttons.getChildCount() > 0 ? buttons.getChildAt(0) : null);
        if (focus != null && content.getVisibility() != View.VISIBLE) {
            focus.setFocusedByDefault(true);
            final boolean keys = context instanceof Activity
                    && !((Activity) context).getWindow().getDecorView().isInTouchMode();
            focus.post(() -> {
                if (keys) {
                    focus.requestFocusFromTouch();
                }
                else {
                    focus.requestFocus();
                }
            });
        }
    }

    public void dismiss() {
        try {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (IllegalArgumentException e) {
            // Window already gone with its activity
        }
    }

    public android.app.Dialog getDialog() {
        return dialog;
    }

    public SiDialog onKey(DialogInterface.OnKeyListener l) {
        dialog.setOnKeyListener(l);
        return this;
    }
}
