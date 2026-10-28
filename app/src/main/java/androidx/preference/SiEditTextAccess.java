package androidx.preference;

import android.widget.EditText;

/**
 * Lets Stand-In's own text dialog apply the input setup (number keyboard, filters) that the
 * settings screen gives an EditTextPreference; AndroidX keeps that getter package-private.
 */
public final class SiEditTextAccess {
    private SiEditTextAccess() {}

    public static void bind(EditTextPreference pref, EditText editText) {
        EditTextPreference.OnBindEditTextListener l = pref.getOnBindEditTextListener();
        if (l != null) {
            l.onBindEditText(editText);
        }
    }
}
