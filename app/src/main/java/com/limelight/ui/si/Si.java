package com.limelight.ui.si;

import android.content.Context;
import android.util.TypedValue;

/** Small helpers shared by the Stand-In components. */
public final class Si {
    private Si() {}

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return c.getResources().getColor(res, c.getTheme());
    }

    // Words that stay capitalized when a Title Case title is turned into sentence case
    private static final java.util.Set<String> PROPER = new java.util.HashSet<>(java.util.Arrays.asList(
            "Android", "Xbox", "Nintendo", "PlayStation", "DualShock", "DualSense", "Joy-Con", "Joy-Cons",
            "Switch", "Pro", "Moonlight", "Artemis", "Apollo", "Sunshine", "Stand-In", "Windows", "Tailscale",
            "NVIDIA", "SHIELD", "GameStream", "Google", "Samsung", "DeX", "Fire", "TV", "Meta", "Alt", "Ctrl",
            "Shift", "Esc", "Tab", "Guide", "Back", "Start", "Select", "L3", "R3", "I", "X", "Y"));

    /** Turns a Title Case label from upstream into sentence case, keeping acronyms and names. */
    public static CharSequence sentenceCase(CharSequence text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        String[] words = text.toString().split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) {
                out.append(' ');
                // Only plain capitalized words (Word), never acronyms (HDR) or names
                if (w.length() > 1 && Character.isUpperCase(w.charAt(0))
                        && w.substring(1).equals(w.substring(1).toLowerCase())
                        && !PROPER.contains(w.replaceAll("[^A-Za-z0-9-]", ""))) {
                    w = Character.toLowerCase(w.charAt(0)) + w.substring(1);
                }
            }
            out.append(w);
        }
        return out.toString();
    }

}
