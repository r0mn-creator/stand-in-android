package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import com.limelight.utils.CacheHelper;
import java.io.StringReader;
import java.net.UnknownHostException;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.profiles.ProfilesManager;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;
import com.limelight.ui.si.Si;
import com.limelight.ui.si.SiDialog;
import com.limelight.ui.si.SiMenu;
import com.limelight.ui.si.SiPc;
import com.limelight.nvstream.http.HostHttpResponseException;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends AppCompatActivity {
    private PcGridAdapter pcGridAdapter;

    // The big card: the PC you picked, or the one you most likely want
    private String pickedUuid;
    private ComputerDetails heroComputer;
    // Set by "Wake and connect"; connects as soon as that PC comes up
    private String connectWhenAwakeUuid;
    private TextView heroName, heroStatus;
    private TextView heroButton;
    private ImageView heroIcon;
    private View heroSpinner, heroContent, heroEmpty, heroMore;
    private LinearLayout pcRow;
    private SiDialog pairDialog;
    private static final String LAST_USED_PREF = "si_last_used_pc";
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private ComputerDetails.AddressTuple pendingPairingAddress;
    private String pendingPairingPin, pendingPairingPassphrase;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }

        refreshProfileButton();
    }

    private final static int PAIR_ID = 2;
    private final static int UNPAIR_ID = 3;
    private final static int WOL_ID = 4;
    private final static int DELETE_ID = 5;
    private final static int RESUME_ID = 6;
    private final static int QUIT_ID = 7;
    private final static int VIEW_DETAILS_ID = 8;
    private final static int FULL_APP_LIST_ID = 9;
    private final static int TEST_NETWORK_ID = 10;
    private final static int GAMESTREAM_EOL_ID = 11;
    private final static int OPEN_MANAGEMENT_PAGE_ID = 20;
    private final static int PAIR_ID_OTP = 21;

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view);

        UiHelper.notifyNewRootView(this);

        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        pcGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(PcView.this, StreamSettings.class)));
        findViewById(R.id.devicesButton).setOnClickListener(v ->
                startActivity(new Intent(PcView.this, DevicesActivity.class)));
        findViewById(R.id.manuallyAddPc).setOnClickListener(v -> showAddPc());
        findViewById(R.id.heroAddManually).setOnClickListener(v -> showAddPc());

        heroName = findViewById(R.id.heroName);
        heroStatus = findViewById(R.id.heroStatus);
        heroButton = findViewById(R.id.heroButton);
        heroIcon = findViewById(R.id.heroIcon);
        heroSpinner = findViewById(R.id.heroSpinner);
        heroContent = findViewById(R.id.heroContent);
        heroEmpty = findViewById(R.id.heroEmpty);
        heroMore = findViewById(R.id.heroMore);
        pcRow = findViewById(R.id.pcRow);
        findViewById(R.id.homeScroll).setVerticalScrollBarEnabled(false);
        heroButton.setOnClickListener(v -> onHeroAction());
        heroMore.setOnClickListener(v -> {
            if (heroComputer != null) {
                showPcMenu(heroComputer, heroMore);
            }
        });

        refreshHero();
        heroButton.post(() -> {
            if (heroButton.getVisibility() == View.VISIBLE) {
                heroButton.requestFocus();
            }
        });
    }

    private void showAddPc() {
        startActivity(new Intent(PcView.this, AddComputerManually.class));
    }

    public static boolean canWake(ComputerDetails details) {
        return SiPc.canWake(details);
    }

    // The hero: the PC you picked, else the last-used awake PC, else the first paired PC, else the first PC
    private ComputerDetails pickHero() {
        String lastUsed = getSharedPreferences(LAST_USED_PREF, MODE_PRIVATE).getString("uuid", null);
        ComputerDetails best = null;
        int bestScore = -1;
        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerDetails details = ((ComputerObject) pcGridAdapter.getItem(i)).details;
            if (details.uuid.equals(pickedUuid)) {
                return details;
            }

            boolean online = details.state == ComputerDetails.State.ONLINE;
            boolean paired = details.pairState == PairState.PAIRED;
            int score;
            if (online && details.uuid.equals(lastUsed)) {
                score = 5;
            }
            else if (online && paired) {
                score = 4;
            }
            else if (paired || details.uuid.equals(lastUsed)) {
                score = 3;
            }
            else if (online) {
                score = 2;
            }
            else {
                score = canWake(details) ? 1 : 0;
            }
            if (score > bestScore) {
                best = details;
                bestScore = score;
            }
        }
        return best;
    }

    private void rememberLastUsed(ComputerDetails computer) {
        getSharedPreferences(LAST_USED_PREF, MODE_PRIVATE).edit().putString("uuid", computer.uuid).apply();
    }

    // One row of tiles; each tile is its own focus stop so the D-pad walks along the row
    private void rebuildTiles() {
        if (pcRow == null) {
            return;
        }
        int count = pcGridAdapter.getCount();
        int gap = Si.dp(this, 12);
        for (int i = 0; i < count; i++) {
            View existing = i < pcRow.getChildCount() ? pcRow.getChildAt(i) : null;
            View tile = pcGridAdapter.getView(i, existing, pcRow);
            if (existing == null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        Si.dp(this, 112), Si.dp(this, 96));
                lp.setMarginStart(i == 0 ? 0 : gap);
                pcRow.addView(tile, lp);
            }
            final ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);
            tile.setOnClickListener(v -> {
                // A tile brings that PC into the hero card
                pickedUuid = computer.details.uuid;
                refreshHero();
            });
            tile.setOnLongClickListener(v -> {
                showPcMenu(computer.details, v);
                return true;
            });
        }
        while (pcRow.getChildCount() > count) {
            pcRow.removeViewAt(pcRow.getChildCount() - 1);
        }
    }

    private void refreshHero() {
        if (heroName == null) {
            return;
        }
        heroComputer = pickHero();
        pcGridAdapter.setHeroUuid(heroComputer != null ? heroComputer.uuid : null);
        pcGridAdapter.setWakingUuid(connectWhenAwakeUuid);
        rebuildTiles();

        if (heroComputer == null) {
            // No PCs at all: the hero card becomes the empty state
            heroContent.setVisibility(View.GONE);
            heroMore.setVisibility(View.GONE);
            heroEmpty.setVisibility(View.VISIBLE);
            return;
        }
        heroContent.setVisibility(View.VISIBLE);
        heroMore.setVisibility(View.VISIBLE);
        heroEmpty.setVisibility(View.GONE);

        SiPc.State state = SiPc.of(heroComputer, heroComputer.uuid.equals(connectWhenAwakeUuid));
        boolean busy = state == SiPc.State.CHECKING || state == SiPc.State.WAKING;
        heroIcon.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        heroSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        heroIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Si.color(this,
                state == SiPc.State.OFFLINE || state == SiPc.State.ASLEEP ? R.color.si_muted : R.color.si_text)));
        heroName.setText(SiPc.name(this, heroComputer));
        heroStatus.setText(SiPc.heroText(state));

        int button = SiPc.heroButton(state);
        boolean hadFocus = heroButton.hasFocus();
        if (button != 0) {
            heroButton.setText(button);
            heroButton.setVisibility(View.VISIBLE);
        }
        else {
            heroButton.setVisibility(View.INVISIBLE);
            if (hadFocus) {
                heroMore.requestFocus();
            }
        }
    }

    private void onHeroAction() {
        ComputerDetails computer = heroComputer;
        if (computer == null) {
            return;
        }

        switch (SiPc.of(computer, computer.uuid.equals(connectWhenAwakeUuid))) {
            case ASLEEP:
                connectWhenAwakeUuid = computer.uuid;
                doWakeOnLan(computer);
                refreshHero();
                break;
            case NOT_PAIRED:
                doPair(computer, null, null);
                break;
            case AWAKE:
            case IN_USE:
                doConnect(computer);
                break;
            default:
                break;
        }
    }

    private static final int MENU_ALL_APPS = 1, MENU_WAKE = 2, MENU_RENAME = 3, MENU_TEST_NETWORK = 4, MENU_REMOVE = 5;

    // The PC menu (DESIGN.md "Home"): All apps, Wake (asleep only), Rename, Test network, Remove
    private void showPcMenu(final ComputerDetails computer, View anchor) {
        SiMenu menu = new SiMenu(this);
        if (computer.state == ComputerDetails.State.ONLINE && computer.pairState == PairState.PAIRED) {
            menu.add(MENU_ALL_APPS, R.drawable.ic_si_apps, getString(R.string.si_menu_all_apps));
        }
        if (computer.state != ComputerDetails.State.ONLINE && canWake(computer)) {
            menu.add(MENU_WAKE, R.drawable.ic_si_power, getString(R.string.si_menu_wake));
        }
        menu.add(MENU_RENAME, R.drawable.ic_si_sliders, getString(R.string.si_menu_rename));
        menu.add(MENU_TEST_NETWORK, R.drawable.ic_si_globe, getString(R.string.si_menu_test_network));
        menu.addDanger(MENU_REMOVE, R.drawable.ic_si_close, getString(R.string.si_menu_remove));
        menu.listener(id -> {
            switch (id) {
                case MENU_ALL_APPS:
                    doAppList(computer, false, true);
                    break;
                case MENU_WAKE:
                    doWakeOnLan(computer);
                    break;
                case MENU_RENAME:
                    showRename(computer);
                    break;
                case MENU_TEST_NETWORK:
                    ServerHelper.doNetworkTest(PcView.this);
                    break;
                case MENU_REMOVE:
                    confirmRemove(computer);
                    break;
            }
        });
        menu.show(anchor, anchor != heroMore);
    }

    private void showRename(final ComputerDetails computer) {
        View content = getLayoutInflater().inflate(R.layout.si_input_field, null, false);
        TextView label = content.findViewById(R.id.siInputLabel);
        final EditText input = content.findViewById(R.id.siInput);
        content.findViewById(R.id.siInputError).setVisibility(View.GONE);
        label.setText(R.string.si_rename_label);
        input.setText(SiPc.name(this, computer));
        input.setSelectAllOnFocus(true);
        input.setHint(computer.name);
        final SiDialog d = new SiDialog(this)
                .title(getString(R.string.si_rename_title, SiPc.name(this, computer)))
                .body(getString(R.string.si_rename_body))
                .content(content)
                .cancelButton(getString(R.string.si_cancel));
        d.primary(getString(R.string.si_save), dialog -> {
            String name = input.getText().toString().trim();
            SiPc.rename(PcView.this, computer.uuid, name.equals(computer.name) ? null : name);
            dialog.dismiss();
            pcGridAdapter.sortList();
            refreshHero();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            d.getPrimaryButton().performClick();
            return true;
        });
        d.show();
        input.requestFocus();
    }

    private void confirmRemove(final ComputerDetails computer) {
        if (ActivityManager.isUserAMonkey()) {
            LimeLog.info("Ignoring delete PC request from monkey");
            return;
        }
        new SiDialog(this)
                .title(getString(R.string.si_remove_title, SiPc.name(this, computer)))
                .body(getString(R.string.si_remove_body))
                .cancelButton(getString(R.string.si_cancel))
                .destructive(getString(R.string.si_menu_remove), SiDialog.closeThen(() -> {
                    if (managerBinder == null) {
                        Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
                        return;
                    }
                    removeComputer(computer);
                }))
                .show();
    }

    /** Message / error dialog (DESIGN.md): what happened, the server's own words, what to try. */
    private void showMessage(String title, String body, String details) {
        SiDialog d = new SiDialog(this).title(title).body(body);
        if (details != null && !details.isEmpty()) {
            d.secondary(getString(R.string.si_details), dialog -> {
                dialog.dismiss();
                new SiDialog(PcView.this).title(title).body(details)
                        .primary(getString(R.string.si_ok), null).show();
            });
        }
        d.primary(getString(R.string.si_ok), null).show();
    }
    // Connect goes straight to the desktop: resume the running session, or start Desktop
    private void doConnect(ComputerDetails computer) {
        rememberLastUsed(computer);
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        if (computer.runningGameId != 0) {
            ServerHelper.doStart(this, new NvApp("app", null, computer.runningGameId, false), computer, managerBinder, false);
            return;
        }

        String rawAppList = computer.rawAppList;
        if (rawAppList == null) {
            try {
                rawAppList = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", computer.uuid));
            } catch (IOException e) {
                // Nothing cached yet, e.g. right after pairing
            }
        }

        if (rawAppList != null) {
            startDesktop(computer, findDesktop(computer, rawAppList));
            return;
        }

        // Ask the PC for its app list before giving up on going straight to the desktop
        new Thread(() -> {
            String fetched = null;
            try {
                NvHTTP httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                        computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                        PlatformBinding.getCryptoProvider(PcView.this));
                fetched = httpConn.getAppListRaw();
            } catch (IOException e) {
                LimeLog.warning("Couldn't fetch app list for " + computer.name + ": " + e.getMessage());
            }
            final NvApp desktop = fetched != null ? findDesktop(computer, fetched) : null;
            runOnUiThread(() -> startDesktop(computer, desktop));
        }).start();
    }

    private static NvApp findDesktop(ComputerDetails computer, String rawAppList) {
        try {
            for (NvApp app : NvHTTP.getAppListByReader(new StringReader(rawAppList))) {
                if (app.getAppName().equalsIgnoreCase("Desktop")) {
                    return app;
                }
            }
        } catch (IOException | XmlPullParserException e) {
            LimeLog.warning("Bad app list for " + computer.name + ": " + e.getMessage());
        }
        return null;
    }

    private void startDesktop(ComputerDetails computer, NvApp desktop) {
        if (desktop != null) {
            ServerHelper.doStart(this, desktop, computer, managerBinder,
                    PreferenceConfiguration.readPreferences(this).useVirtualDisplay);
        }
        else {
            // No Desktop entry on this server; let the user pick from its app list
            doAppList(computer, false, false);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }

        Intent intent = getIntent();

        String hostname = intent.getStringExtra("hostname");
        int port = intent.getIntExtra("port", NvHTTP.DEFAULT_HTTP_PORT);
        pendingPairingPin = intent.getStringExtra("pin");
        pendingPairingPassphrase = intent.getStringExtra("passphrase");

        if (hostname != null && pendingPairingPin != null && pendingPairingPassphrase != null) {
            pendingPairingAddress = new ComputerDetails.AddressTuple(hostname, port);
        } else {
            pendingPairingPin = null;
            pendingPairingPassphrase = null;
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
//                        } else
                        }
                            if (pendingPairingAddress != null) {
                            if (
                                details.state == ComputerDetails.State.ONLINE &&
                                details.activeAddress.equals(pendingPairingAddress)
                            ) {
                                PcView.this.runOnUiThread(() -> {
                                    doPair(details, pendingPairingPin, pendingPairingPassphrase);
                                    pendingPairingAddress = null;
                                    pendingPairingPin = null;
                                    pendingPairingPassphrase = null;
                                });
                            }
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    private void refreshProfileButton() {
        ExtendedFloatingActionButton profilesButton = findViewById(R.id.profilesButton);
        // User report Samsung and Xiaomi devices have this problem
        // Why just these two brands have the most problems?
        if (profilesButton == null) {
            return;
        }
        String activeProfileName = ProfilesManager.getInstance().getActiveName();
        if (activeProfileName.isEmpty()) {
            profilesButton.shrink();
        } else {
            profilesButton.setText(activeProfileName);
            profilesButton.extend();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        refreshProfileButton();

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
    }

    private void doPair(final ComputerDetails computer, String otp, String passphrase) {
        final String pcName = SiPc.name(this, computer);
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            showMessage(getString(R.string.si_cant_reach_title, pcName), getString(R.string.si_pc_offline, pcName), null);
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        final String pinStr = otp != null ? otp : PairingManager.generatePinString();
        final boolean[] cancelled = { false };

        // Pair (DESIGN.md): the code in four boxes, a waiting line, and Cancel
        View content = getLayoutInflater().inflate(R.layout.si_pair_code, null, false);
        int[] boxes = { R.id.siPinDigit1, R.id.siPinDigit2, R.id.siPinDigit3, R.id.siPinDigit4 };
        for (int i = 0; i < boxes.length; i++) {
            ((TextView) content.findViewById(boxes[i])).setText(i < pinStr.length() ? String.valueOf(pinStr.charAt(i)) : "");
        }
        ((TextView) content.findViewById(R.id.siPairWaiting)).setText(getString(R.string.si_pair_waiting, pcName));
        if (pairDialog != null) {
            pairDialog.dismiss();
        }
        pairDialog = new SiDialog(this)
                .title(getString(R.string.si_pair_title, pcName))
                .body(getString(passphrase == null ? R.string.si_pair_body : R.string.si_pair_body_otp, pcName))
                .content(content)
                .onCancel(() -> {
                    cancelled[0] = true;
                    startComputerUpdates();
                })
                .cancelButton(getString(R.string.si_cancel));
        pairDialog.show();
        final SiDialog shownDialog = pairDialog;

        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                String details = null;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        message = null;
                        success = true;
                    }
                    else {
                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr, passphrase);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getString(R.string.si_pair_wrong_code);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getString(R.string.si_pair_in_use, pcName);
                            }
                            else {
                                message = getString(R.string.si_pair_failed_body, pcName);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getString(R.string.si_pair_busy, pcName);
                        }
                        else if (pairState == PairState.PAIRED) {
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getString(R.string.si_cant_reach_body);
                    details = e.toString();
                } catch (HostHttpResponseException e) {
                    // The server's own words, then what to try
                    message = e.getErrorMessage() + "\n\n" + getString(R.string.si_pair_failed_body, pcName);
                    details = e.getMessage();
                } catch (FileNotFoundException e) {
                    message = getString(R.string.si_pair_failed_body, pcName);
                    details = getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = getString(R.string.si_cant_reach_body);
                    details = e.getMessage();
                }

                final String errorMessage = message;
                final String errorDetails = details;
                final boolean pairSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        shownDialog.dismiss();
                        if (cancelled[0]) {
                            return;
                        }

                        if (errorMessage != null) {
                            showMessage(getString(R.string.si_pair_failed_title, pcName), errorMessage, errorDetails);
                        }

                        if (pairSuccess) {
                            // Pairing only earns trust; the hero button becomes Connect
                            pickedUuid = computer.uuid;
                            Toast.makeText(PcView.this, getString(R.string.si_paired, pcName), Toast.LENGTH_SHORT).show();
                        }

                        // Start polling again if we're still in the foreground
                        startComputerUpdates();
                    }
                });
            }
        }).start();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                SiPc.forget(this, details.uuid);
                if (details.uuid.equals(pickedUuid)) {
                    pickedUuid = null;
                }
                refreshHero();
                break;
            }
        }
    }

    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));
        }

        if (details.uuid.equals(connectWhenAwakeUuid) && details.state == ComputerDetails.State.ONLINE) {
            connectWhenAwakeUuid = null;
            if (details.pairState == PairState.PAIRED) {
                doConnect(details);
            }
        }

        // Notify the view that the data has changed
        refreshHero();
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
        public String guessManagementUrl() {
            if (details.activeAddress == null) return null;
            return "https://" + details.activeAddress.address + ":" + (details.guessExternalPort() + 1);
        }
    }
}
