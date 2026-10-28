package com.limelight.preferences;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.ui.si.Si;
import com.limelight.ui.si.SiDialog;
import com.limelight.ui.si.SiMenu;

/** Stand-In pickers for settings rows (DESIGN.md "Choice picker", "Dialog", "Input"). */
final class SiPreferenceDialogs {
    private SiPreferenceDialogs() {}

    private static View findRow(PreferenceFragmentCompat fragment, String key) {
        RecyclerView list = fragment.getListView();
        if (list == null || key == null) {
            return null;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (key.equals(child.getTag(R.id.si_pref_key))) {
                return child;
            }
        }
        return null;
    }

    private static void choose(ListPreference pref, int index) {
        String value = pref.getEntryValues()[index].toString();
        if (pref.callChangeListener(value)) {
            pref.setValue(value);
        }
    }

    /** A popup anchored to the row, or a dialog with a list when there are more than 8 options. */
    static void pick(PreferenceFragmentCompat fragment, final ListPreference pref) {
        CharSequence[] entries = pref.getEntries();
        if (entries == null || pref.getEntryValues() == null) {
            return;
        }
        int selected = pref.findIndexOfValue(pref.getValue());
        View row = findRow(fragment, pref.getKey());

        if (entries.length <= 8 && row != null) {
            SiMenu menu = new SiMenu(fragment.requireContext()).minWidth(220);
            for (int i = 0; i < entries.length; i++) {
                menu.addChoice(i, Si.sentenceCase(entries[i]), i == selected);
            }
            menu.listener(id -> choose(pref, id));
            final View anchorRow = row;
            menu.onDismiss(anchorRow::requestFocus);
            menu.show(row, false);
            return;
        }

        final SiDialog dialog = new SiDialog(fragment.requireContext()).title(pref.getTitle());
        LinearLayout list = new LinearLayout(dialog.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        LayoutInflater inflater = LayoutInflater.from(dialog.getContext());
        View focus = null;
        for (int i = 0; i < entries.length; i++) {
            final int index = i;
            View item = inflater.inflate(R.layout.si_popup_row, list, false);
            ((TextView) item.findViewById(R.id.siRowText)).setText(Si.sentenceCase(entries[i]));
            item.findViewById(R.id.siRowIcon).setVisibility(View.GONE);
            ImageView check = item.findViewById(R.id.siRowCheck);
            check.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                choose(pref, index);
            });
            list.addView(item);
            if (i == selected || focus == null) {
                focus = item;
            }
        }
        ScrollView scroll = new ScrollView(dialog.getContext());
        scroll.addView(list);
        int max = Math.round(fragment.getResources().getDisplayMetrics().heightPixels * 0.5f);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        if (list.getMeasuredHeight() > max) {
            scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, max));
        }
        dialog.content(scroll).cancelButton(fragment.getString(R.string.si_cancel)).show();
        final View focusItem = focus;
        if (focusItem != null) {
            focusItem.post(() -> {
                focusItem.requestFocus();
                scroll.smoothScrollTo(0, Math.max(0, focusItem.getTop() - Si.dp(focusItem.getContext(), 44)));
            });
        }
    }

    static void editText(PreferenceFragmentCompat fragment, final EditTextPreference pref) {
        final SiDialog dialog = new SiDialog(fragment.requireContext())
                .title(pref.getDialogTitle() != null ? pref.getDialogTitle() : pref.getTitle());
        if (pref.getDialogMessage() != null) {
            dialog.body(pref.getDialogMessage());
        }
        View content = LayoutInflater.from(dialog.getContext()).inflate(R.layout.si_input_field, null, false);
        content.findViewById(R.id.siInputLabel).setVisibility(View.GONE);
        final EditText input = content.findViewById(R.id.siInput);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(pref.getText());
        androidx.preference.SiEditTextAccess.bind(pref, input);
        input.setSelection(input.getText().length());
        dialog.content(content)
                .cancelButton(fragment.getString(R.string.si_cancel))
                .primary(fragment.getString(R.string.si_save), d -> {
                    String value = input.getText().toString();
                    if (pref.callChangeListener(value)) {
                        pref.setText(value);
                    }
                    d.dismiss();
                });
        input.setOnEditorActionListener((v, actionId, event) -> {
            dialog.getPrimaryButton().performClick();
            return true;
        });
        dialog.show();
        input.requestFocus();
    }

    static void confirm(PreferenceFragmentCompat fragment, DialogPreference pref, Runnable onYes) {
        new SiDialog(fragment.requireContext())
                .title(pref.getDialogTitle() != null ? pref.getDialogTitle() : pref.getTitle())
                .body(pref.getDialogMessage() != null ? pref.getDialogMessage() : pref.getSummary())
                .cancelButton(fragment.getString(R.string.si_cancel))
                .destructive(pref.getPositiveButtonText() != null ? pref.getPositiveButtonText()
                        : pref.getTitle(), SiDialog.closeThen(onYes))
                .show();
    }
}
