package com.limelight.preferences;

import static com.limelight.utils.ServerHelper.getActiveDisplay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.limelight.DebugInfoActivity;
import com.limelight.BuildConfig;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.FileUriUtils;
import com.limelight.utils.PerformanceDataTracker;
import com.limelight.utils.UiHelper;
import org.json.JSONObject;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.DevicesActivity;
import com.limelight.ui.si.Si;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class StreamSettings extends AppCompatActivity {
    private PreferenceConfiguration previousPrefs;
    private int previousDisplayPixelCount;

    private SettingsFragment prefsFragment;

    /** Opens Settings on one category, e.g. Devices. */
    public static final String EXTRA_CATEGORY = "si_category";

    // The sidebar, in DESIGN.md order: preference category key, name, icon
    static final String[] CATEGORY_KEYS = {
            "si_cat_display", "si_cat_audio", "si_cat_input", "si_cat_controllers",
            "si_cat_devices", "si_cat_appearance", "si_cat_advanced", "si_cat_about" };
    private static final int[] CATEGORY_TITLES = {
            R.string.si_cat_display, R.string.si_cat_audio, R.string.si_cat_input, R.string.si_cat_controllers,
            R.string.si_cat_devices, R.string.si_cat_appearance, R.string.si_cat_advanced, R.string.si_cat_about };
    private static final int[] CATEGORY_ICONS = {
            R.drawable.ic_si_display, R.drawable.ic_si_speaker, R.drawable.ic_si_keyboard, R.drawable.ic_si_gamepad,
            R.drawable.ic_si_usb, R.drawable.ic_si_palette, R.drawable.ic_si_sliders, R.drawable.ic_si_info };

    private String currentCategory = CATEGORY_KEYS[0];
    private boolean twoPane;
    // Narrow windows: showing one category's page (else the category list)
    private boolean showingPage;
    private OnBackPressedCallback pageBack;

    // HACK for Android 9
    static DisplayCutout displayCutoutP;

    void reloadSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();
            previousDisplayPixelCount = mode.getPhysicalWidth() * mode.getPhysicalHeight();
        }
        prefsFragment = new SettingsFragment(PreferenceConfiguration.readPreferences(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        ), currentCategory);
        getSupportFragmentManager().beginTransaction().replace(
                R.id.stream_settings, prefsFragment
        ).commitAllowingStateLoss();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        previousPrefs = PreferenceConfiguration.readPreferences(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_stream_settings);

        UiHelper.notifyNewRootView(this);

        String requested = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (savedInstanceState != null) {
            requested = savedInstanceState.getString(EXTRA_CATEGORY, requested);
        }
        if (requested != null) {
            currentCategory = requested;
        }

        twoPane = getResources().getConfiguration().screenWidthDp >= 760;
        buildSidebar();
        findViewById(R.id.settingsBack).setOnClickListener(v -> {
            if (!twoPane && showingPage) {
                showCategoryList();
            }
            else {
                exitSettings();
            }
        });
        pageBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showCategoryList();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, pageBack);

        if (twoPane) {
            findViewById(R.id.settingsContent).setVisibility(View.VISIBLE);
        }
        else if (requested != null) {
            showCategoryPage();
        }
        else {
            showCategoryList();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CATEGORY, currentCategory);
    }

    private void buildSidebar() {
        LinearLayout sidebar = findViewById(R.id.settingsSidebar);
        sidebar.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            final String key = CATEGORY_KEYS[i];
            View row = inflater.inflate(R.layout.si_settings_nav_row, sidebar, false);
            ((ImageView) row.findViewById(R.id.siNavIcon)).setImageResource(CATEGORY_ICONS[i]);
            ((TextView) row.findViewById(R.id.siNavText)).setText(CATEGORY_TITLES[i]);
            row.setTag(key);
            if (!twoPane) {
                // Narrow: the list is the page, one group of rows that each lead somewhere
                row.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                row.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                row.setMinimumHeight(Si.dp(this, 56));
                row.setPadding(Si.dp(this, 16), 0, Si.dp(this, 16), 0);
                int last = CATEGORY_KEYS.length - 1;
                row.setBackgroundResource(i == 0 ? R.drawable.si_row_top
                        : i == last ? R.drawable.si_row_bottom : R.drawable.si_row_middle);
                row.findViewById(R.id.siNavChevron).setVisibility(View.VISIBLE);
            }
            row.setOnClickListener(v -> selectCategory(key));
            if (twoPane) {
                // Moving onto a category shows it, so the D-pad can browse without pressing A
                row.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        selectCategory(key);
                    }
                });
            }
            sidebar.addView(row);
        }
        if (!twoPane) {
            sidebar.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            findViewById(R.id.settingsSidebarScroll).getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            sidebar.setPadding(sidebar.getPaddingLeft(), sidebar.getPaddingTop(),
                    sidebar.getPaddingLeft(), sidebar.getPaddingBottom());
        }
        updateSidebarSelection();
    }

    private void updateSidebarSelection() {
        LinearLayout sidebar = findViewById(R.id.settingsSidebar);
        for (int i = 0; i < sidebar.getChildCount(); i++) {
            View row = sidebar.getChildAt(i);
            boolean selected = currentCategory.equals(row.getTag());
            row.setActivated(twoPane && selected);
            // The D-pad starts on the open category
            row.setFocusedByDefault(selected);
        }
    }

    private void selectCategory(String key) {
        boolean changed = !key.equals(currentCategory);
        currentCategory = key;
        updateSidebarSelection();
        if (prefsFragment != null && changed) {
            prefsFragment.showCategory(key);
        }
        if (!twoPane) {
            showCategoryPage();
        }
    }

    private int categoryTitle(String key) {
        for (int i = 0; i < CATEGORY_KEYS.length; i++) {
            if (CATEGORY_KEYS[i].equals(key)) {
                return CATEGORY_TITLES[i];
            }
        }
        return R.string.si_settings;
    }

    private void showCategoryList() {
        showingPage = false;
        pageBack.setEnabled(false);
        findViewById(R.id.settingsSidebarScroll).setVisibility(View.VISIBLE);
        findViewById(R.id.settingsContent).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.settingsTitle)).setText(R.string.si_settings);
        View row = findViewById(R.id.settingsSidebar).findViewWithTag(currentCategory);
        if (row != null) {
            row.requestFocus();
        }
    }

    private void showCategoryPage() {
        showingPage = true;
        pageBack.setEnabled(true);
        findViewById(R.id.settingsSidebarScroll).setVisibility(View.GONE);
        findViewById(R.id.settingsContent).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.settingsTitle)).setText(categoryTitle(currentCategory));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // We have to use this hack on Android 9 because we don't have Display.getCutout()
        // which was added in Android 10.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Insets can be null when the activity is recreated on screen rotation
            // https://stackoverflow.com/questions/61241255/windowinsets-getdisplaycutout-is-null-everywhere-except-within-onattachedtowindo
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                displayCutoutP = insets.getDisplayCutout();
            }
        }

        reloadSettings();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode mode = getActiveDisplay(StreamSettings.this, previousPrefs).getMode();

            // If the display's physical pixel count has changed, we consider that it's a new display
            // and we should reload our settings (which include display-dependent values).
            //
            // NB: We aren't using displayId here because that stays the same (DEFAULT_DISPLAY) when
            // switching between screens on a foldable device.
            if (mode.getPhysicalWidth() * mode.getPhysicalHeight() != previousDisplayPixelCount) {
                reloadSettings();
            }
        }
    }

    @Override
    // NOTE: This will NOT be called on Android 13+ with android:enableOnBackInvokedCallback="true"
    public void onBackPressed() {
        if (!twoPane && showingPage) {
            showCategoryList();
            return;
        }
        exitSettings();
    }

    private void exitSettings() {
        finish();

        // Language changes are handled via configuration changes in Android 13+,
        // so manual activity relaunching is no longer required.
        PreferenceConfiguration newPrefs = PreferenceConfiguration.readPreferences(this);
        if (!newPrefs.language.equals(previousPrefs.language)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Restart the PC view to apply UI changes
                Intent intent = new Intent(this, PcView.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent, null);
            } else {
                if (newPrefs.language == PreferenceConfiguration.DEFAULT_LANGUAGE) {
                    Toast.makeText(this, "Language has been reset to default, please restart the app!", Toast.LENGTH_LONG).show();
                    System.exit(0);
                }
            }
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final String CUSTOM_VALUE = "custom";

        private PreferenceConfiguration prevPrefConfig;
        private String category;

        public SettingsFragment() {
            this(null, StreamSettings.CATEGORY_KEYS[0]);
        }

        /** Every category on one page (the profile editor). */
        public SettingsFragment(PreferenceConfiguration prefCfg) {
            this(prefCfg, null);
        }

        public SettingsFragment(PreferenceConfiguration prefCfg, String category) {
            prevPrefConfig = prefCfg;
            this.category = category;
        }

        // Moonlight removes options a device can't use; they may live in any group now
        private void removePref(String key) {
            removePref(findPreference(key));
        }

        private void removePref(Preference pref) {
            if (pref != null && pref.getParent() != null) {
                pref.getParent().removePreference(pref);
            }
        }

        /** Shows one sidebar category: its groups become the page. */
        public void showCategory(String key) {
            category = key;
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return;
            }
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference top = screen.getPreference(i);
                top.setVisible(key == null || (top.getKey() != null && top.getKey().equals(key)));
                if (top instanceof PreferenceGroup) {
                    // A group whose options were all removed on this device shows nothing
                    PreferenceGroup cat = (PreferenceGroup) top;
                    for (int j = 0; j < cat.getPreferenceCount(); j++) {
                        Preference group = cat.getPreference(j);
                        if (group instanceof PreferenceGroup) {
                            group.setVisible(((PreferenceGroup) group).getPreferenceCount() > 0);
                        }
                    }
                }
            }
            if (getListView() != null) {
                getListView().scrollToPosition(0);
            }
        }

        // Sliders show values that other settings can change (Quality follows resolution and frame rate)
        private final SharedPreferences.OnSharedPreferenceChangeListener sliderRefresher = (prefs, key) -> {
            Preference p = key != null ? findPreference(key) : null;
            if (p instanceof SeekBarPreference) {
                ((SeekBarPreference) p).refresh();
            }
        };

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(sliderRefresher);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(sliderRefresher);
            super.onPause();
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            RecyclerView list = getListView();
            list.setPadding(0, 0, 0, Si.dp(requireContext(), 24));
            list.setClipToPadding(false);
            list.setVerticalScrollBarEnabled(false);
            list.setItemAnimator(null);
            setDivider(null);
        }

        @NonNull
        @Override
        protected RecyclerView.Adapter onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
            return new SiPreferenceAdapter(preferenceScreen);
        }

        protected SharedPreferences getPrefs() {
            return getPreferenceManager().getSharedPreferences();
        }

        private void setValue(String preferenceKey, String value) {
            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            pref.setValue(value);
        }

        private void appendPreferenceEntry(ListPreference pref, String newEntryName, String newEntryValue) {
            CharSequence[] newEntries = Arrays.copyOf(pref.getEntries(), pref.getEntries().length + 1);
            CharSequence[] newValues = Arrays.copyOf(pref.getEntryValues(), pref.getEntryValues().length + 1);

            // Add the new option
            newEntries[newEntries.length - 1] = newEntryName;
            newValues[newValues.length - 1] = newEntryValue;

            pref.setEntries(newEntries);
            pref.setEntryValues(newValues);
        }

        // Automatic covers this screen's own size and rate, so the lists only gain custom entries
        // (the desktop lists other screens too; Android settings only see this one)
        private void addCustomResolutionEntry(int width, int height, boolean portrait) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);

            String newName = getResources().getString(R.string.resolution_prefix_custom);
            if (PreferenceConfiguration.isSquarishScreen(width, height)) {
                newName += " " + getResources().getString(portrait ?
                        R.string.resolution_prefix_native_portrait : R.string.resolution_prefix_native_landscape);
            }
            newName += " (" + width + " \u00d7 " + height + ")";

            String newValue = width + "x" + height;

            // Don't list a size twice
            for (CharSequence value : pref.getEntryValues()) {
                if (newValue.equals(value.toString())) {
                    return;
                }
            }
            appendPreferenceEntry(pref, newName, newValue);
        }

        private void addCustomResolutionEntries(int width, int height) {
            if (PreferenceConfiguration.isSquarishScreen(width, height)) {
                addCustomResolutionEntry(height, width, true);
            }
            addCustomResolutionEntry(width, height, false);
        }

        private void addCustomFrameRateEntry(float framerate) {
            ListPreference pref = (ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            String fpsValue = Float.toString(framerate);
            String fpsName = getResources().getString(R.string.resolution_prefix_custom) +
                    " (" + fpsValue.replaceAll("\\.0$", "") + " " + getResources().getString(R.string.fps_suffix_fps) + ")";

            // Don't list a rate twice
            for (CharSequence value : pref.getEntryValues()) {
                if (fpsValue.equals(value.toString()) ||
                        fpsValue.replaceAll("\\.0$", "").equals(value.toString())) {
                    return;
                }
            }
            appendPreferenceEntry(pref, fpsName, fpsValue);
        }

        private void removeValue(String preferenceKey, String value, Runnable onMatched) {
            int matchingCount = 0;

            ListPreference pref = (ListPreference) findPreference(preferenceKey);

            // Count the number of matching entries we'll be removing
            for (CharSequence seq : pref.getEntryValues()) {
                if (seq.toString().equalsIgnoreCase(value)) {
                    matchingCount++;
                }
            }

            // Create the new arrays
            CharSequence[] entries = new CharSequence[pref.getEntries().length-matchingCount];
            CharSequence[] entryValues = new CharSequence[pref.getEntryValues().length-matchingCount];
            int outIndex = 0;
            for (int i = 0; i < pref.getEntryValues().length; i++) {
                if (pref.getEntryValues()[i].toString().equalsIgnoreCase(value)) {
                    // Skip matching values
                    continue;
                }

                entries[outIndex] = pref.getEntries()[i];
                entryValues[outIndex] = pref.getEntryValues()[i];
                outIndex++;
            }

            if (pref.getValue().equalsIgnoreCase(value)) {
                onMatched.run();
            }

            // Update the preference with the new list
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        // Automatic quality follows the resolution and frame rate; Manual stays where the user set it
        private void resetBitrateToDefault(SharedPreferences prefs, String res, String fps) {
            if (!PreferenceConfiguration.isAutoBitrate(prefs)) {
                return;
            }
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }
            Context context = requireContext();

            prefs.edit()
                    .putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(
                                    PreferenceConfiguration.resolveResolution(context, res),
                                    PreferenceConfiguration.resolveFps(context, fps)))
                    .apply();
        }

        /**
         * Subtitles for Resolution, Frame rate and Quality follow their value (DESIGN.md
         * "It just works"), and the Bitrate slider shows only for Manual quality. Null means
         * "the stored value".
         */
        private void updateStreamRows(String res, String fps, String quality) {
            SharedPreferences prefs = getPrefs();
            Context context = requireContext();
            if (res == null) {
                res = prefs.getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            }
            if (fps == null) {
                fps = prefs.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            }
            if (quality == null) {
                quality = prefs.getString(PreferenceConfiguration.QUALITY_PREF_STRING, PreferenceConfiguration.DEFAULT_QUALITY);
            }

            Preference resPref = findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING);
            if (resPref != null) {
                if (PreferenceConfiguration.AUTO_VALUE.equals(res)) {
                    String[] size = PreferenceConfiguration.resolveResolution(context, res).split("x");
                    resPref.setSummary(getString(R.string.si_pref_resolution_auto_sub,
                            Integer.parseInt(size[0]), Integer.parseInt(size[1])));
                }
                else {
                    resPref.setSummary(R.string.si_pref_resolution_sub);
                }
            }

            Preference fpsPref = findPreference(PreferenceConfiguration.FPS_PREF_STRING);
            if (fpsPref != null) {
                if (PreferenceConfiguration.AUTO_VALUE.equals(fps)) {
                    fpsPref.setSummary(getString(R.string.si_pref_fps_auto_sub,
                            Math.round(Float.parseFloat(PreferenceConfiguration.resolveFps(context, fps)))));
                }
                else {
                    fpsPref.setSummary(R.string.si_pref_fps_sub);
                }
            }

            boolean manual = PreferenceConfiguration.MANUAL_VALUE.equals(quality);
            Preference qualityPref = findPreference(PreferenceConfiguration.QUALITY_PREF_STRING);
            if (qualityPref != null) {
                qualityPref.setSummary(manual ? R.string.si_pref_quality_manual_sub : R.string.si_pref_quality_auto_sub);
            }
            Preference bitratePref = findPreference(PreferenceConfiguration.BITRATE_PREF_STRING);
            if (bitratePref != null) {
                bitratePref.setVisible(manual);
            }
        }

        /** Opens a "Custom…" entry's input (the Advanced custom resolution / frame rate field). */
        private void openCustomInput(String key) {
            Preference pref = findPreference(key);
            if (pref instanceof EditTextPreference) {
                SiPreferenceDialogs.editText(this, (EditTextPreference) pref);
            }
        }

        @NonNull
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState, boolean unused) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            if (prevPrefConfig == null) {
                prevPrefConfig = PreferenceConfiguration.readPreferences(requireContext());
            }
            initializePreferences();

            Preference usb = findPreference("si_usb_devices");
            if (usb != null) {
                usb.setOnPreferenceClickListener(p -> {
                    startActivity(new Intent(getActivity(), DevicesActivity.class));
                    return true;
                });
            }
            showCategory(category);
        }

        public void initializePreferences() {
            addPreferencesFromResource(R.xml.preferences);
            PreferenceScreen screen = getPreferenceScreen();

            AppCompatActivity activity = (AppCompatActivity) requireActivity();
            PackageManager pm = activity.getPackageManager();

            // hide on-screen controls category on non touch screen devices
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    removePref(category);
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    removePref(category);
                }
            }

            // Hide remote desktop mouse mode on pre-Oreo (which doesn't have pointer capture)
            // and NVIDIA SHIELD devices (which support raw mouse input in pointer capture mode)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    getActivity().getPackageManager().hasSystemFeature("com.nvidia.feature.shield")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_input_settings");
                removePref("checkbox_absolute_mouse_mode");
            }

            // Hide gamepad motion sensor option when running on OSes before Android 12.
            // Support for motion, LED, battery, and other extensions were introduced in S.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                removePref("checkbox_gamepad_motion_sensors");
            }

            // Hide gamepad motion sensor fallback option if the device has no gyro or accelerometer
            if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER) &&
                    !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                removePref("checkbox_force_device_motion");
                removePref("checkbox_gamepad_motion_fallback");
            }

            // Hide USB driver options on devices without USB host support
            if (!pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_gamepad_settings");
                removePref("checkbox_usb_bind_all");
                removePref("checkbox_usb_driver");
            }

            // Remove PiP mode on devices pre-Oreo, where the feature is not available (some low RAM devices),
            // and on Fire OS where it violates the Amazon App Store guidelines for some reason.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !pm.hasSystemFeature("android.software.picture_in_picture") ||
                    pm.hasSystemFeature("com.amazon.software.fireos")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_ui_settings");
                removePref("checkbox_enable_pip");
            }

            // Fire TV apps are not allowed to use WebViews or browsers, so hide the Help category
            /*if (getActivity().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_help");
                removePref(category);
            }*/
            PreferenceCategory category_gamepad_settings =
                    (PreferenceCategory) findPreference("category_gamepad_settings");
            // Remove the vibration options if the device can't vibrate
            if (!((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator()) {
                removePref("checkbox_vibrate_fallback");
                removePref("seekbar_vibrate_fallback_strength");
                // The entire OSC category may have already been removed by the touchscreen check above
                PreferenceCategory category = findPreference("category_onscreen_controls");
                if (category != null) {
                    removePref("checkbox_vibrate_osc");
                }
                category = findPreference("category_special_key_layout");
                if (category != null) {
                    removePref("checkbox_vibrate_keyboard");
                }
                category = findPreference("category_gamepad_settings");
                if (category != null) {
                    removePref("checkbox_enable_device_rumble");
                }
            }
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !((Vibrator)getActivity().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl()) {
                // Remove the vibration strength selector of the device doesn't have amplitude control
                removePref("seekbar_vibrate_fallback_strength");
            }

            // Check custom resolution
            String customResStr = prevPrefConfig.customResolution;
            if(customResStr != null && !customResStr.isEmpty()){
                String[] resolutionSegments = customResStr.split("x");
                if(resolutionSegments.length == 2){
                    try {
                        addCustomResolutionEntries(Integer.parseInt(resolutionSegments[0]), Integer.parseInt(resolutionSegments[1]));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // Check custom refresh rate
            String customRefreshRateStr = prevPrefConfig.customRefreshRate;
            if (customRefreshRateStr != null && !customRefreshRateStr.isEmpty()) {
                try {
                    float customRefreshRateValue = Float.parseFloat(customRefreshRateStr);
                    if (customRefreshRateValue > 0) {
                        addCustomFrameRateEntry(customRefreshRateValue);
                    }
                } catch (NumberFormatException e) {
                    getPrefs().edit().remove(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING).apply();
                }
            }

            Display display = activity.getWindowManager().getDefaultDisplay();
            float maxSupportedFps = display.getRefreshRate();

            // Hide non-supported resolution/FPS combinations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int maxSupportedResW = 0;

                // Always allow resolutions that are smaller or equal to the active
                // display resolution because decoders can report total non-sense to us.
                // For example, a p201 device reports:
                // AVC Decoder: OMX.amlogic.avc.decoder.awesome
                // HEVC Decoder: OMX.amlogic.hevc.decoder.awesome
                // AVC supported width range: 64 - 384
                // HEVC supported width range: 64 - 544
                for (Display.Mode candidate : display.getSupportedModes()) {
                    // Some devices report their dimensions in the portrait orientation
                    // where height > width. Normalize these to the conventional width > height
                    // arrangement before we process them.

                    int width = Math.max(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());
                    int height = Math.min(candidate.getPhysicalWidth(), candidate.getPhysicalHeight());

                    if ((width >= 3840 || height >= 2160) && maxSupportedResW < 3840) {
                        maxSupportedResW = 3840;
                    }
                    else if ((width >= 2560 || height >= 1440) && maxSupportedResW < 2560) {
                        maxSupportedResW = 2560;
                    }
                    else if ((width >= 1920 || height >= 1080) && maxSupportedResW < 1920) {
                        maxSupportedResW = 1920;
                    }

                    if (candidate.getRefreshRate() > maxSupportedFps) {
                        maxSupportedFps = candidate.getRefreshRate();
                    }
                }

                // This must be called to do runtime initialization before calling functions that evaluate
                // decoder lists.
                MediaCodecHelper.initialize(getContext(), GlPreferences.readPreferences(requireContext()).glRenderer);

                MediaCodecInfo avcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/avc", -1);
                MediaCodecInfo hevcDecoder = MediaCodecHelper.findProbableSafeDecoder("video/hevc", -1);

                if (avcDecoder != null) {
                    Range<Integer> avcWidthRange = avcDecoder.getCapabilitiesForType("video/avc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("AVC supported width range: "+avcWidthRange.getLower()+" - "+avcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (avcWidthRange.contains(1280)) {
                        if (avcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (avcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                if (hevcDecoder != null) {
                    Range<Integer> hevcWidthRange = hevcDecoder.getCapabilitiesForType("video/hevc").getVideoCapabilities().getSupportedWidths();

                    LimeLog.info("HEVC supported width range: "+hevcWidthRange.getLower()+" - "+hevcWidthRange.getUpper());

                    // If 720p is not reported as supported, ignore all results from this API
                    if (hevcWidthRange.contains(1280)) {
                        if (hevcWidthRange.contains(3840) && maxSupportedResW < 3840) {
                            maxSupportedResW = 3840;
                        }
                        else if (hevcWidthRange.contains(1920) && maxSupportedResW < 1920) {
                            maxSupportedResW = 1920;
                        }
                        else if (maxSupportedResW < 1280) {
                            maxSupportedResW = 1280;
                        }
                    }
                }

                LimeLog.info("Maximum resolution slot: "+maxSupportedResW);

                if (maxSupportedResW != 0) {
                    if (maxSupportedResW < 3840) {
                        // 4K is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_4K, PreferenceConfiguration.RES_1440P);
                    }
                    if (maxSupportedResW < 2560) {
                        // 1440p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1440P, PreferenceConfiguration.RES_1080P);
                    }
                    if (maxSupportedResW < 1920) {
                        // 1080p is unsupported
                        removeEntryFromListAndSetValue(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.RES_1080P, PreferenceConfiguration.RES_720P);
                    }
                    // Never remove 720p
                }
            }

            if (!PreferenceConfiguration.readPreferences(this.getActivity()).unlockFps) {
                // We give some extra room in case the FPS is rounded down
                if (maxSupportedFps < 118) {
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "120", "90");
                }
                if (maxSupportedFps < 88) {
                    // 1080p is unsupported
                    removeEntryFromListAndSetValue(PreferenceConfiguration.FPS_PREF_STRING, "90", "60");
                }
                // Never remove 30 FPS or 60 FPS
            }

            // A fixed value that isn't in the lists (an old native entry, a profile) still shows,
            // as on the desktop
            String currentRes = getPrefs().getString(PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION);
            String[] currentSize = currentRes.split("x");
            if (currentSize.length == 2) {
                try {
                    addCustomResolutionEntry(Integer.parseInt(currentSize[0]), Integer.parseInt(currentSize[1]), false);
                } catch (NumberFormatException ignored) {
                }
            }
            String currentFps = getPrefs().getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
            if (!PreferenceConfiguration.AUTO_VALUE.equals(currentFps)) {
                try {
                    addCustomFrameRateEntry(Float.parseFloat(currentFps));
                } catch (NumberFormatException ignored) {
                }
            }

            // "Custom…" ends both lists, as on the desktop
            appendPreferenceEntry((ListPreference) findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING),
                    getString(R.string.si_custom_ellipsis), CUSTOM_VALUE);
            appendPreferenceEntry((ListPreference) findPreference(PreferenceConfiguration.FPS_PREF_STRING),
                    getString(R.string.si_custom_ellipsis), CUSTOM_VALUE);

            // Android L introduces the drop duplicate behavior of releaseOutputBuffer()
            // that the unlock FPS option relies on to not massively increase latency.
            findPreference(PreferenceConfiguration.UNLOCK_FPS_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // HACK: We need to let the preference change succeed before reinitializing to ensure
                    // it's reflected in the new layout.
                    reloadSettings();

                    // Allow the original preference change to take place
                    return true;
                }
            });

            // Remove HDR preference for devices below Nougat
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                LimeLog.info("Excluding HDR toggle based on OS");
                PreferenceCategory category =
                        (PreferenceCategory) findPreference("category_video_settings");
                removePref("checkbox_enable_hdr");
            }
            else {
                Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();
                Log.d("HDR CAP", display + "");
                // We must now ensure our display is compatible with HDR10
                boolean foundHdr10 = false;
                if (hdrCaps != null) {
                    // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                    for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                        if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                            foundHdr10 = true;
                            break;
                        }
                    }
                }

                if (!foundHdr10) {
                    LimeLog.info("Excluding HDR toggle based on display capabilities");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    removePref("checkbox_enable_hdr");
                }
                else if (PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr()) {
                    LimeLog.info("Disabling HDR toggle on old broken SHIELD TV firmware");
                    PreferenceCategory category =
                            (PreferenceCategory) findPreference("category_video_settings");
                    CheckBoxPreference hdrPref = (CheckBoxPreference) category.findPreference("checkbox_enable_hdr");
                    hdrPref.setEnabled(false);
                    hdrPref.setChecked(false);
                    hdrPref.setSummary("Update the firmware on your NVIDIA SHIELD Android TV to enable HDR");
                }
            }

            // Add a listener to the FPS and resolution preference
            // so the bitrate can be auto-adjusted
            findPreference(PreferenceConfiguration.RESOLUTION_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // "Custom…" asks for the size; saving it selects it
                    if (CUSTOM_VALUE.equals(valueStr)) {
                        openCustomInput(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING);
                        return false;
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, valueStr, null);
                    updateStreamRows(valueStr, null, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });
            findPreference(PreferenceConfiguration.FPS_PREF_STRING).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getPrefs();
                    String valueStr = (String) newValue;

                    // "Custom…" asks for the rate; saving it selects it
                    if (CUSTOM_VALUE.equals(valueStr)) {
                        openCustomInput(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
                        return false;
                    }

                    // Write the new bitrate value
                    resetBitrateToDefault(prefs, null, valueStr);
                    updateStreamRows(null, valueStr, null);

                    // Allow the original preference change to take place
                    return true;
                }
            });

            findPreference(PreferenceConfiguration.QUALITY_PREF_STRING).setOnPreferenceChangeListener((preference, newValue) -> {
                String valueStr = (String) newValue;
                if (!getPrefs().contains(PreferenceConfiguration.BITRATE_PREF_STRING)) {
                    // Manual starts from the default for what we'd stream now, and stays there
                    getPrefs().edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING,
                            PreferenceConfiguration.getDefaultBitrate(requireContext())).apply();
                }
                if (PreferenceConfiguration.AUTO_VALUE.equals(valueStr)) {
                    // Commit first so the default is written for Automatic
                    getPrefs().edit().putString(PreferenceConfiguration.QUALITY_PREF_STRING, valueStr).apply();
                    resetBitrateToDefault(getPrefs(), null, null);
                }
                updateStreamRows(null, null, valueStr);
                return true;
            });
            updateStreamRows(null, null, null);

            findPreference("checkbox_enable_perf_logging").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Boolean loggingEnabled = (Boolean) newValue;

                    if(!loggingEnabled) {
                        new PerformanceDataTracker().clearLogs(preference.getContext());
                    }

                    // Allow the original preference change to take place
                    return true;
                }
            });

            Preference _pref;
            _pref = findPreference("import_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("import_special_button_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        startActivityForResult(intent, READ_REQUEST_SPECIAL_CODE);
                        return false;
                    }
                });
            }

            _pref = findPreference("export_keyboard_file");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        File file = new File(requireActivity().getExternalCacheDir(),"export_settings");
                        if(!file.exists()){
                            file.mkdir();
                        }
                        File file1= getJsonContent(requireActivity(),file);
                        if(file1==null){
                            Toast.makeText(requireActivity(),getString(R.string.pref_error_occurred),Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        Uri uri;
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        String authority= BuildConfig.APPLICATION_ID+".fileprovider";
                        uri = FileProvider.getUriForFile(requireActivity(),authority,file1);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setType("application/json");
                        startActivity(Intent.createChooser(intent,getString(R.string.pref_save_keyboard_profile)));
                        return false;
                    }
                });
            }

            _pref = findPreference("pref_debug_info");
            if (_pref != null) {
                _pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        Intent intent=new Intent(requireActivity(), DebugInfoActivity.class);
                        requireActivity().startActivity(intent);
                        return false;
                    }
                });
            }

            EditTextPreference bitrateEditPref = findPreference(PreferenceConfiguration.CUSTOM_BITRATE_PREF_STRING);
            if (bitrateEditPref != null) {
                bitrateEditPref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
                });

                bitrateEditPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    float bitrateValue = Float.parseFloat(value) * 1000;
                    int bitrate = (int) bitrateValue;
                    SharedPreferences prefs = getPrefs();
                    prefs.edit().putInt(PreferenceConfiguration.BITRATE_PREF_STRING, bitrate).apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_set_success), Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            EditTextPreference resolutionEditPref = findPreference(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING);
            if (resolutionEditPref != null) {
                resolutionEditPref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(11)});
                });

                resolutionEditPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    // Verify format: [width]x[height]
                    String[] resolutionSegments = value.split("x");
                    if (resolutionSegments.length != 2) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    try {
                        int width = Integer.parseInt(resolutionSegments[0]);
                        int height = Integer.parseInt(resolutionSegments[1]);
                        
                        if (width <= 0 || height <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        // Select it (turning Automatic off), then save the value and reload settings
                        getPrefs().edit().putString(PreferenceConfiguration.RESOLUTION_PREF_STRING, value).apply();
                        resetBitrateToDefault(getPrefs(), value, null);
                        editAndReload(PreferenceConfiguration.CUSTOM_RESOLUTION_PREF_STRING, value);

                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }

            EditTextPreference customRefreshRatePref = findPreference(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING);
            if (customRefreshRatePref != null) {
                customRefreshRatePref.setOnBindEditTextListener((EditText editText) -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
                });

                customRefreshRatePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String value = (String) newValue;
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    
                    try {
                        float refreshRate = Float.parseFloat(value);
                        if (refreshRate <= 0) {
                            Toast.makeText(getActivity(), getString(R.string.pref_enter_value_0_9999), Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        
                        // Format to max 3 decimal places
                        String formattedValue = String.format("%.3f", refreshRate);
                        // Remove trailing zeros
                        formattedValue = formattedValue.replaceAll("0+$", "").replaceAll("\\.$", "");

                        // Select it (turning Automatic off); the list entry uses Float.toString
                        String fpsValue = Float.toString(Float.parseFloat(formattedValue));
                        getPrefs().edit().putString(PreferenceConfiguration.FPS_PREF_STRING, fpsValue).apply();
                        resetBitrateToDefault(getPrefs(), null, fpsValue);
                        editAndReload(PreferenceConfiguration.CUSTOM_REFRESH_RATE_PREF_STRING, formattedValue);

                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.pref_error_occurred), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }
        }

        private void removeEntryFromListAndSetValue(String resolutionPrefString, String entryToRemove, String nextDefault) {
            removeValue(resolutionPrefString, entryToRemove, new Runnable() {
                @Override
                public void run() {
                    SharedPreferences prefs = getPrefs();
                    setValue(resolutionPrefString, nextDefault);
                    resetBitrateToDefault(prefs, null, null);
                }
            });
        }

        private void editAndReload(String prefKey, String newVal) {
            SharedPreferences prefs = getPrefs();
            prefs.edit().putString(prefKey, newVal).apply();

            reloadSettings();
        }

        protected void reloadSettings() {
            // HACK: We need to let the preference change succeed before reinitializing to ensure
            // it's reflected in the new layout.
            final Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Ensure the activity is still open when this timeout expires
                    StreamSettings settingsActivity = (StreamSettings) SettingsFragment.this.getActivity();
                    if (settingsActivity != null) {
                        settingsActivity.reloadSettings();
                    }
                }
            }, 500);
        }

        int READ_REQUEST_CODE = 1001;
        int READ_REQUEST_SPECIAL_CODE = 1002;

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
                    SharedPreferences.Editor prefEditor = requireActivity().getSharedPreferences(name, Activity.MODE_PRIVATE).edit();
                    JSONObject object = new JSONObject(json);
                    Iterator it = object.keys();
                    prefEditor.clear();
                    while (it.hasNext()) {
                        String key = (String) it.next();// 获得key
                        String value = object.getString(key);// 获得value
                        prefEditor.putString(key, value);
                    }
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (requestCode == READ_REQUEST_SPECIAL_CODE && resultCode == Activity.RESULT_OK && data.getData() != null) {
                try {
                    Uri uri = data.getData();
                    String json = FileUriUtils.openUriForRead(getActivity(), uri);
                    if (TextUtils.isEmpty(json)) {
                        Toast.makeText(getActivity(), getString(R.string.pref_empty_file), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor prefEditor = getActivity().getSharedPreferences(GameMenu.PREF_NAME, Activity.MODE_PRIVATE).edit();
                    prefEditor.putString(GameMenu.KEY_NAME, json);
                    prefEditor.apply();
                    Toast.makeText(getActivity(), getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), getString(R.string.pref_error_occurred) + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            // Every picker and confirmation is a Stand-In popup or dialog (SiPreferenceDialogs)
            if (preference instanceof ConfirmDeleteOscPreference) {
                SiPreferenceDialogs.confirm(this, (DialogPreference) preference,
                        () -> ConfirmDeleteOscPreference.reset(requireContext()));
            } else if (preference instanceof ConfirmDeleteKeyboardPreference) {
                SiPreferenceDialogs.confirm(this, (DialogPreference) preference,
                        () -> ConfirmDeleteKeyboardPreference.reset(requireContext()));
            } else if (preference instanceof ListPreference) {
                SiPreferenceDialogs.pick(this, (ListPreference) preference);
            } else if (preference instanceof EditTextPreference) {
                SiPreferenceDialogs.editText(this, (EditTextPreference) preference);
            } else super.onDisplayPreferenceDialog(preference);
        }

        private File getJsonContent(Context context,File file){
            String name = getPrefs().getString(KeyBoardControllerConfigurationLoader.OSC_PREFERENCE, KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE);
            SharedPreferences pref = context.getSharedPreferences(name, Activity.MODE_PRIVATE);
            Map<String,?> map = pref.getAll();
            File file1= new File(file,name+".json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }

        //获取所有设置项配置文件
        private File getAllJsonData(File file){
            SharedPreferences pref = getPrefs();
            Map<String,?> map = pref.getAll();
            //获取适配电脑的数据库信息
//            List<ComputerDetails> map= new ComputerDatabaseManager(context).getAllComputers();
            File file1= new File(file,"allJSON.json");
            String jsonStr=new Gson().toJson(map);
            if(!FileUriUtils.writerFileString(file1,jsonStr)){
                return null;
            }
            return file1;
        }
    }
}

