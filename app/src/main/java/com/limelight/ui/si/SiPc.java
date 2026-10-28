package com.limelight.ui.si;

import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

/** PC state as the design names it (DESIGN.md "Status dot + text"), and local display names. */
public final class SiPc {
    private SiPc() {}

    public enum State { CHECKING, WAKING, AWAKE, IN_USE, NOT_PAIRED, ASLEEP, OFFLINE }

    private static final String NAMES_PREF = "si_pc_names";

    public static boolean canWake(ComputerDetails details) {
        return details.macAddress != null && !details.macAddress.equals("00:00:00:00:00:00");
    }

    public static State of(ComputerDetails d, boolean waking) {
        if (waking && d.state != ComputerDetails.State.ONLINE) {
            return State.WAKING;
        }
        switch (d.state) {
            case UNKNOWN:
                return State.CHECKING;
            case OFFLINE:
                return canWake(d) ? State.ASLEEP : State.OFFLINE;
            default:
                if (d.pairState != PairingManager.PairState.PAIRED) {
                    return State.NOT_PAIRED;
                }
                return d.runningGameId != 0 ? State.IN_USE : State.AWAKE;
        }
    }

    public static int dotColor(State s) {
        switch (s) {
            case AWAKE:
                return R.color.si_accent;
            case IN_USE:
            case NOT_PAIRED:
                return R.color.si_warning;
            case ASLEEP:
            case WAKING:
                return R.color.si_muted;
            default:
                return R.color.si_subtle;
        }
    }

    public static int shortText(State s) {
        switch (s) {
            case AWAKE: return R.string.si_short_awake;
            case IN_USE: return R.string.si_short_in_use;
            case NOT_PAIRED: return R.string.si_short_unpaired;
            case ASLEEP: return R.string.si_short_asleep;
            case WAKING: return R.string.si_status_waking;
            case OFFLINE: return R.string.si_short_offline;
            default: return R.string.si_short_checking;
        }
    }

    public static int heroText(State s) {
        switch (s) {
            case AWAKE: return R.string.si_status_ready;
            case IN_USE: return R.string.si_status_in_use;
            case NOT_PAIRED: return R.string.si_status_unpaired;
            case ASLEEP: return R.string.si_status_asleep;
            case WAKING: return R.string.si_status_waking;
            case OFFLINE: return R.string.si_status_offline;
            default: return R.string.si_status_checking;
        }
    }

    /** The hero button for a state, or 0 for none. */
    public static int heroButton(State s) {
        switch (s) {
            case AWAKE: return R.string.si_connect;
            case IN_USE: return R.string.si_take_over;
            case NOT_PAIRED: return R.string.si_pair;
            case ASLEEP: return R.string.si_wake_connect;
            default: return 0;
        }
    }

    /** The name to show: the user's own name for this PC if they renamed it, else the PC's. */
    public static String name(Context c, ComputerDetails d) {
        if (d == null) {
            return "";
        }
        String custom = c.getSharedPreferences(NAMES_PREF, Context.MODE_PRIVATE).getString(d.uuid, null);
        return custom != null && !custom.isEmpty() ? custom : (d.name != null ? d.name : "");
    }

    public static void rename(Context c, String uuid, String name) {
        SharedPreferences.Editor e = c.getSharedPreferences(NAMES_PREF, Context.MODE_PRIVATE).edit();
        if (name == null || name.trim().isEmpty()) {
            e.remove(uuid);
        }
        else {
            e.putString(uuid, name.trim());
        }
        e.apply();
    }

    public static void forget(Context c, String uuid) {
        rename(c, uuid, null);
    }
}
