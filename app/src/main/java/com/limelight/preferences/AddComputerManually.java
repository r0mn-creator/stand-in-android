package com.limelight.preferences;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.AppView;
import com.limelight.Game;
import com.limelight.PcView;
import com.limelight.ShortcutTrampoline;
import com.limelight.computers.ComputerManagerService;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddComputerManually extends AppCompatActivity {
    private TextView hostText;
    private boolean adding;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<>();
    private Thread addThread;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
            startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            managerBinder = null;
        }
    };

    private boolean isWrongSubnetSiteLocalAddress(String address) {
        try {
            InetAddress targetAddress = InetAddress.getByName(address);
            if (!(targetAddress instanceof Inet4Address) || !targetAddress.isSiteLocalAddress()) {
                return false;
            }

            // We have a site-local address. Look for a matching local interface.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address) || !addr.getAddress().isSiteLocalAddress()) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue;
                    }

                    byte[] targetAddrBytes = targetAddress.getAddress();
                    byte[] ifaceAddrBytes = addr.getAddress().getAddress();

                    // Compare prefix to ensure it's the same
                    boolean addressMatches = true;
                    for (int i = 0; i < addr.getNetworkPrefixLength(); i++) {
                        if ((ifaceAddrBytes[i / 8] & (1 << (i % 8))) != (targetAddrBytes[i / 8] & (1 << (i % 8)))) {
                            addressMatches = false;
                            break;
                        }
                    }

                    if (addressMatches) {
                        return false;
                    }
                }
            }

            // Couldn't find a matching interface
            return true;
        } catch (Exception e) {
            // Catch all exceptions because some broken Android devices
            // will throw an NPE from inside getNetworkInterfaces().
            e.printStackTrace();
            return false;
        }
    }

    private Uri parseRawUserInputToUri(String rawUserInput) {
        // Try adding a scheme and parsing the remaining input.
        // This handles input like 127.0.0.1:47989, [::1], [::1]:47989, and 127.0.0.1.
        Uri uri = Uri.parse("art://" + rawUserInput);
        if (uri.getHost() != null && !uri.getHost().isEmpty()) {
            return uri;
        }

        // Attempt to escape the input as an IPv6 literal.
        // This handles input like ::1.
        uri = Uri.parse("art://[" + rawUserInput + "]");
        if (uri.getHost() != null && !uri.getHost().isEmpty()) {
            return uri;
        }

        return null;
    }

    private void doAddPc(String rawUserInput) throws InterruptedException {
        boolean wrongSiteLocal = false;
        boolean invalidInput = false;
        boolean success;
        int portTestResult;

        runOnUiThread(() -> setBusy(true, null));

        Uri uri = parseRawUserInputToUri(rawUserInput);
        try {
            ComputerDetails details = new ComputerDetails();

            // Check if we parsed a host address successfully
            if (uri != null && uri.getHost() != null && !uri.getHost().isEmpty()) {
                String host = uri.getHost();
                int port = uri.getPort();

                // If a port was not specified, use the default
                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT;
                }

                details.manualAddress = new ComputerDetails.AddressTuple(host, port);
                success = managerBinder.addComputerBlocking(details);
                if (!success){
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
                }
            } else {
                // Invalid user input
                success = false;
                invalidInput = true;
            }
        } catch (InterruptedException e) {
            // Propagate the InterruptedException to the caller for proper handling
            runOnUiThread(() -> setBusy(false, null));
            throw e;
        } catch (IllegalArgumentException e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
            invalidInput = true;
        }

        // Keep the SpinnerDialog open while testing connectivity
        if (!success && !wrongSiteLocal && !invalidInput) {
            // Run the test before dismissing the spinner because it can take a few seconds.
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        } else {
            // Don't bother with the test if we succeeded or the IP address was bogus
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        }

        final String shownHost = uri != null && uri.getHost() != null ? uri.getHost() : rawUserInput;
        final String error;
        if (invalidInput) {
            error = getString(R.string.si_add_invalid, rawUserInput);
        }
        else if (wrongSiteLocal) {
            error = getString(R.string.si_add_wrong_subnet, shownHost);
        }
        else if (!success) {
            if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                error = getString(R.string.si_add_blocked, shownHost);
            }
            else {
                error = getString(R.string.si_add_unreachable, shownHost);
            }
        }
        else {
            error = null;
        }

        AddComputerManually.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setBusy(false, error);
                if (error != null) {
                    return;
                }

                if (!isFinishing()) {
                    // Close the dialog; the new PC shows up on the home screen
                    AddComputerManually.this.finish();
                }

                String pin = uri.getQueryParameter("pin");
                String passphrase = uri.getQueryParameter("passphrase");
                if (pin != null && passphrase != null) {
                    Intent intent = new Intent(AddComputerManually.this, PcView.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("hostname", uri.getHost());
                    intent.putExtra("port", uri.getPort());
                    intent.putExtra("pin", pin);
                    intent.putExtra("passphrase", passphrase);

                    startActivity(intent);
                }
            }
        });
    }

    // While adding, Add shows a spinner and the input locks; errors appear under the input
    private void setBusy(boolean busy, String error) {
        if (hostText == null) {
            return;
        }
        adding = busy;
        hostText.setEnabled(!busy);
        findViewById(R.id.addPcButtonText).setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.addPcSpinner).setVisibility(busy ? View.VISIBLE : View.GONE);
        TextView errorView = findViewById(R.id.addPcError);
        errorView.setText(error);
        errorView.setVisibility(error != null ? View.VISIBLE : View.GONE);
        if (!busy && error != null) {
            hostText.requestFocus();
        }
    }

    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        String computer = computersToAdd.take();
                        doAddPc(computer);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt();
            }

            addThread = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (managerBinder != null) {
            joinAddThread();
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();

        String server;
        String query;
        Uri data = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            int port = data.getPort();

            if (port == -1) {
                String urlAction = data.getHost();
                if (Objects.equals(urlAction, "launch")) {
                    String hostUUID = data.getQueryParameter("host_uuid");
                    String hostName = data.getQueryParameter("host_name");
                    String appUUID = data.getQueryParameter("app_uuid");
                    String appName = data.getQueryParameter("app_name");
                    String appID = data.getQueryParameter("app_id");

                    Intent intent = new Intent(AddComputerManually.this, ShortcutTrampoline.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(AppView.UUID_EXTRA, hostUUID);
                    intent.putExtra(AppView.NAME_EXTRA, hostName);
                    intent.putExtra(Game.EXTRA_APP_UUID, appUUID);
                    intent.putExtra(Game.EXTRA_APP_NAME, appName);
                    intent.putExtra(Game.EXTRA_APP_ID, appID);

                    finish();

                    startActivity(intent);

                    return;
                }
            }

            server = data.getAuthority();
            query = data.getQuery();
        } else {
            query = null;
            server = null;
        }

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_add_computer_manually);

        UiHelper.notifyNewRootView(this);

        this.hostText = findViewById(R.id.hostTextView);
        hostText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        hostText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        (keyEvent != null &&
                                keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                                keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    return handleDoneEvent();
                }
                else if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    // This is how the Fire TV dismisses the keyboard
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(hostText.getWindowToken(), 0);
                    return false;
                }

                return false;
            }
        });

        findViewById(R.id.addPcButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleDoneEvent();
            }
        });
        findViewById(R.id.addPcCancel).setOnClickListener(v -> finish());
        // Tapping the scrim outside the panel cancels, like any dialog
        findViewById(R.id.siScrim).setOnClickListener(v -> finish());
        ((com.limelight.ui.si.SiSpinner) findViewById(R.id.addPcSpinner)).setColors(
                getResources().getColor(R.color.si_on_accent, getTheme()), 0);

        // Bind to the ComputerManager service
        bindService(new Intent(AddComputerManually.this,
                    ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);


        // Check if we have been called from deep link
        if (data == null || server == null || query == null) {
            return;
        }

        hostText.setText(server);

        if (!query.isEmpty()) {
            String hostName = data.getQueryParameter("name");
            if (hostName != null && !hostName.isEmpty()) {
                hostName = hostName + " (" + server + ")";
            } else {
                hostName = server;
            }

            final String linkHost = hostName;
            new com.limelight.ui.si.SiDialog(this)
                    .title(getString(R.string.si_pair_link_title, linkHost))
                    .body(getString(R.string.si_pair_link_body, linkHost))
                    .cancelButton(getString(R.string.si_cancel))
                    .primary(getString(R.string.si_pair), d -> {
                        d.dismiss();
                        computersToAdd.add(server + '?' + query);
                    })
                    .show();
        }
    }

    // Returns true if the event should be eaten
    private boolean handleDoneEvent() {
        String hostAddress = hostText.getText().toString().trim();

        if (adding) {
            return true;
        }
        if (hostAddress.length() == 0) {
            setBusy(false, getString(R.string.si_add_empty));
            return true;
        }

        computersToAdd.add(hostAddress);
        return false;
    }
}
