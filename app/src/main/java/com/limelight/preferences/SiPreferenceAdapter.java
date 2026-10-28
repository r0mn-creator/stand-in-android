package com.limelight.preferences;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;

import com.limelight.R;
import com.limelight.ui.si.Si;

/**
 * Draws settings rows as DESIGN.md groups: the rows between two section labels form one rounded
 * card (each row paints its piece: first, middle, last or single), rows grow to 68 when they
 * have a subtitle, and value rows show their current value before the chevron.
 */
public class SiPreferenceAdapter extends PreferenceGroupAdapter {
    public SiPreferenceAdapter(@NonNull PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
    }

    private boolean isRow(int position) {
        if (position < 0 || position >= getItemCount()) {
            return false;
        }
        return !(getItem(position) instanceof PreferenceCategory);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference pref = getItem(position);
        View row = holder.itemView;
        if (pref == null || pref instanceof PreferenceCategory) {
            return;
        }
        row.setTag(R.id.si_pref_key, pref.getKey());

        // AndroidX greys the title of rows that can't be clicked; ours are sliders and info rows
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(row.getContext().getColorStateList(R.color.si_text_enabled));
            // Sentence case everywhere, also for the titles that came from Moonlight in Title Case
            title.setText(Si.sentenceCase(title.getText()));
        }
        row.setDefaultFocusHighlightEnabled(false);

        boolean first = !isRow(position - 1);
        boolean last = !isRow(position + 1);
        int bg;
        if (first && last) {
            bg = R.drawable.si_row_single;
        }
        else if (first) {
            bg = R.drawable.si_row_top;
        }
        else if (last) {
            bg = R.drawable.si_row_bottom;
        }
        else {
            bg = R.drawable.si_row_middle;
        }
        row.setBackgroundResource(bg);

        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        boolean hasSubtitle = summary != null && summary.getVisibility() == View.VISIBLE;
        if (!(pref instanceof SeekBarPreference)) {
            row.setMinimumHeight(Si.dp(row.getContext(), hasSubtitle ? 68 : 56));
        }

        TextView value = (TextView) holder.findViewById(R.id.si_value);
        if (value != null && !(pref instanceof SeekBarPreference)) {
            CharSequence text = null;
            if (pref instanceof ListPreference) {
                text = Si.sentenceCase(((ListPreference) pref).getEntry());
            }
            else if (pref instanceof EditTextPreference) {
                text = ((EditTextPreference) pref).getText();
            }
            else if ("si_about_version".equals(pref.getKey())) {
                text = row.getContext().getString(R.string.release_tag);
            }
            value.setText(text);
            value.setVisibility(text != null && text.length() > 0 ? View.VISIBLE : View.GONE);
            View chevron = holder.findViewById(R.id.si_chevron);
            if (chevron != null) {
                chevron.setVisibility(pref.isSelectable() ? View.VISIBLE : View.GONE);
            }
        }
    }
}
