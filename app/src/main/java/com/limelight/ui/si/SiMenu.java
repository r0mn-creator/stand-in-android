package com.limelight.ui.si;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup menu (DESIGN.md): surface, 1px border, radius md, padding 6, shadow. Rows 44 high,
 * radius sm, padding 12, body text, optional leading 18 icon; destructive rows in danger;
 * the chosen value of a picker shows a check in accent.
 */
public class SiMenu {
    public interface Listener {
        void onItem(int id);
    }

    private static class Item {
        int id, icon;
        CharSequence text;
        boolean danger, checked;
    }

    private final Context context;
    private final List<Item> items = new ArrayList<>();
    private Listener listener;
    private Runnable onDismiss;
    private int minWidthDp = 200;

    public SiMenu(Context context) {
        this.context = context;
    }

    public SiMenu add(int id, int icon, CharSequence text) {
        Item i = new Item();
        i.id = id;
        i.icon = icon;
        i.text = text;
        items.add(i);
        return this;
    }

    public SiMenu addDanger(int id, int icon, CharSequence text) {
        add(id, icon, text);
        items.get(items.size() - 1).danger = true;
        return this;
    }

    public SiMenu addChoice(int id, CharSequence text, boolean checked) {
        add(id, 0, text);
        items.get(items.size() - 1).checked = checked;
        return this;
    }

    public SiMenu minWidth(int dp) {
        minWidthDp = dp;
        return this;
    }

    public SiMenu listener(Listener l) {
        listener = l;
        return this;
    }

    public SiMenu onDismiss(Runnable r) {
        onDismiss = r;
        return this;
    }

    /** Opens under the anchor, aligned to its end edge (or its start edge with alignStart). */
    public PopupWindow show(View anchor, boolean alignStart) {
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundResource(R.drawable.si_popup_background);
        scroll.setScrollbarFadingEnabled(true);
        scroll.addView(list);

        final PopupWindow popup = new PopupWindow(scroll, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setElevation(Si.dp(context, 12));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(R.style.SiDialogAnimation);

        boolean anyIcon = false, anyCheck = false;
        for (Item i : items) {
            anyIcon |= i.icon != 0;
            anyCheck |= i.checked;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View first = null;
        View checkedRow = null;
        for (final Item i : items) {
            View row = inflater.inflate(R.layout.si_popup_row, list, false);
            row.setMinimumWidth(Si.dp(context, minWidthDp));
            ImageView icon = row.findViewById(R.id.siRowIcon);
            TextView text = row.findViewById(R.id.siRowText);
            ImageView check = row.findViewById(R.id.siRowCheck);
            text.setText(i.text);
            if (i.icon != 0) {
                icon.setImageResource(i.icon);
            }
            icon.setVisibility(i.icon != 0 ? View.VISIBLE : (anyIcon ? View.INVISIBLE : View.GONE));
            check.setVisibility(i.checked ? View.VISIBLE : (anyCheck ? View.INVISIBLE : View.GONE));
            int color = Si.color(context, i.danger ? R.color.si_danger : R.color.si_text);
            text.setTextColor(color);
            icon.setColorFilter(color);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popup.dismiss();
                    if (listener != null) {
                        listener.onItem(i.id);
                    }
                }
            });
            list.addView(row);
            if (first == null) {
                first = row;
            }
            if (i.checked) {
                checkedRow = row;
            }
        }

        popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        });

        scroll.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = scroll.getMeasuredWidth();
        int xoff = alignStart ? 0 : anchor.getWidth() - w;
        popup.showAsDropDown(anchor, xoff, Si.dp(context, 4), Gravity.NO_GRAVITY);

        final View focus = checkedRow != null ? checkedRow : first;
        if (focus != null) {
            focus.post(new Runnable() {
                @Override
                public void run() {
                    focus.requestFocus();
                }
            });
        }
        return popup;
    }
}
