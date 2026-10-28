package com.limelight;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.ui.si.Si;
import com.limelight.ui.si.SiMenu;
import com.limelight.ui.si.SiPc;
import com.limelight.utils.UiHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Devices (DESIGN.md "Devices"): which PC devices are sent to, and the USB devices plugged into
 * this machine. Android has no USB sending backend yet, so the switches are disabled and a
 * banner says so; the page still lists what's plugged in.
 */
public class DevicesActivity extends AppCompatActivity {
    private static final String TARGET_PREF = "si_devices_target";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final Map<String, ComputerDetails> pcs = new LinkedHashMap<>();
    private boolean polling;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                managerBinder = localBinder;
                runOnUiThread(DevicesActivity.this::startPolling);
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            managerBinder = null;
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshDevices();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        setContentView(R.layout.activity_devices);
        UiHelper.notifyNewRootView(this);

        findViewById(R.id.devicesBack).setOnClickListener(v -> finish());
        findViewById(R.id.devicesSendTo).setOnClickListener(this::pickTarget);

        bindService(new Intent(this, ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
        refreshTarget();
        refreshDevices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        else {
            registerReceiver(usbReceiver, filter);
        }
        refreshDevices();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(usbReceiver);
        if (managerBinder != null && polling) {
            managerBinder.stopPolling();
            polling = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    private void startPolling() {
        if (managerBinder == null || polling || isFinishing()) {
            return;
        }
        polling = true;
        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                runOnUiThread(() -> {
                    pcs.put(details.uuid, details);
                    refreshTarget();
                });
            }
        });
    }

    // --- Send to ---

    private SharedPreferences prefs() {
        return getSharedPreferences(TARGET_PREF, MODE_PRIVATE);
    }

    private ComputerDetails target() {
        String uuid = prefs().getString("uuid", null);
        if (uuid == null) {
            // Default to the PC last connected to from the home screen
            uuid = getSharedPreferences("si_last_used_pc", MODE_PRIVATE).getString("uuid", null);
        }
        if (uuid != null && pcs.containsKey(uuid)) {
            return pcs.get(uuid);
        }
        for (ComputerDetails d : pcs.values()) {
            if (d.pairState == PairingManager.PairState.PAIRED) {
                return d;
            }
        }
        return pcs.isEmpty() ? null : pcs.values().iterator().next();
    }

    private void refreshTarget() {
        ComputerDetails pc = target();
        TextView name = findViewById(R.id.devicesTarget);
        TextView status = findViewById(R.id.devicesTargetStatus);
        ImageView dot = findViewById(R.id.devicesTargetDot);
        if (pc == null) {
            name.setText(R.string.si_no_pc);
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
            return;
        }
        boolean online = pc.state == ComputerDetails.State.ONLINE;
        name.setText(SiPc.name(this, pc));
        status.setVisibility(View.VISIBLE);
        dot.setVisibility(View.VISIBLE);
        status.setText(online ? R.string.si_online : R.string.si_offline);
        dot.setImageTintList(ColorStateList.valueOf(Si.color(this, online ? R.color.si_accent : R.color.si_subtle)));
    }

    private void pickTarget(View anchor) {
        if (pcs.isEmpty()) {
            return;
        }
        // Devices can only go to a PC this device is paired with
        final List<ComputerDetails> list = new ArrayList<>();
        for (ComputerDetails d : pcs.values()) {
            if (d.pairState == PairingManager.PairState.PAIRED) {
                list.add(d);
            }
        }
        if (list.isEmpty()) {
            list.addAll(pcs.values());
        }
        ComputerDetails current = target();
        SiMenu menu = new SiMenu(this).minWidth(240);
        for (int i = 0; i < list.size(); i++) {
            menu.addChoice(i, SiPc.name(this, list.get(i)), current != null && list.get(i).uuid.equals(current.uuid));
        }
        menu.listener(id -> {
            prefs().edit().putString("uuid", list.get(id).uuid).apply();
            refreshTarget();
        });
        menu.onDismiss(anchor::requestFocus);
        menu.show(anchor, false);
    }

    // --- On this machine ---

    private static class Device {
        String name, detail;
        int icon;
    }

    private static int kindIcon(UsbDevice device) {
        List<Integer> classes = new ArrayList<>();
        classes.add(device.getDeviceClass());
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            classes.add(iface.getInterfaceClass());
            // Xbox controllers are vendor-specific (class 0xFF, subclass 0x47/0x5D)
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC
                    && (iface.getInterfaceSubclass() == 0x5D || iface.getInterfaceSubclass() == 0x47)) {
                return R.drawable.ic_si_gamepad;
            }
        }
        if (classes.contains(UsbConstants.USB_CLASS_HID)) {
            return R.drawable.ic_si_gamepad;
        }
        if (classes.contains(UsbConstants.USB_CLASS_MASS_STORAGE)) {
            return R.drawable.ic_si_drive;
        }
        if (classes.contains(UsbConstants.USB_CLASS_AUDIO)) {
            return R.drawable.ic_si_speaker;
        }
        if (classes.contains(UsbConstants.USB_CLASS_VIDEO)) {
            return R.drawable.ic_si_camera;
        }
        if (classes.contains(UsbConstants.USB_CLASS_WIRELESS_CONTROLLER)) {
            return R.drawable.ic_si_bluetooth;
        }
        if (classes.contains(UsbConstants.USB_CLASS_COMM) || classes.contains(UsbConstants.USB_CLASS_CDC_DATA)) {
            return R.drawable.ic_si_globe;
        }
        return R.drawable.ic_si_usb;
    }

    private List<Device> usbDevices() {
        List<Device> out = new ArrayList<>();
        UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usb == null) {
            return out;
        }
        List<UsbDevice> devices = new ArrayList<>(usb.getDeviceList().values());
        Collections.sort(devices, (a, b) -> a.getDeviceName().compareTo(b.getDeviceName()));
        for (UsbDevice d : devices) {
            if (d.getDeviceClass() == UsbConstants.USB_CLASS_HUB) {
                continue;
            }
            Device dev = new Device();
            String product = d.getProductName();
            String maker = d.getManufacturerName();
            String ids = String.format(Locale.ROOT, "%04x:%04x", d.getVendorId(), d.getProductId());
            dev.name = product != null && !product.trim().isEmpty() ? product.trim() : getString(R.string.si_usb_devices);
            dev.detail = (maker != null && !maker.trim().isEmpty() ? maker.trim() : getString(R.string.si_unknown_maker)) + " · " + ids;
            dev.icon = kindIcon(d);
            out.add(dev);
        }
        return out;
    }

    private void refreshDevices() {
        List<Device> devices = usbDevices();
        Device test = new Device();
        test.name = getString(R.string.si_test_controller);
        test.detail = getString(R.string.si_test_controller_sub);
        test.icon = R.drawable.ic_si_flask;
        devices.add(test);

        LinearLayout list = findViewById(R.id.devicesList);
        list.removeAllViews();
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            View row = getLayoutInflater().inflate(R.layout.si_device_row, list, false);
            ((ImageView) row.findViewById(R.id.deviceIcon)).setImageResource(d.icon);
            ((TextView) row.findViewById(R.id.deviceName)).setText(d.name);
            ((TextView) row.findViewById(R.id.deviceDetail)).setText(d.detail);
            ((TextView) row.findViewById(R.id.deviceStatus)).setText(R.string.si_device_not_sent);
            boolean first = i == 0, last = i == devices.size() - 1;
            row.setBackgroundResource(first && last ? R.drawable.si_row_single
                    : first ? R.drawable.si_row_top : last ? R.drawable.si_row_bottom : R.drawable.si_row_middle);
            list.addView(row);
        }
    }
}
