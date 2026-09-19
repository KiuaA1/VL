package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.colorselector.ColorSelectionListener;
import net.kdt.pojavlaunch.colorselector.ColorSelector;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * VL Custom UI – color wheel and theme settings
 */
public class VLCustomUIFragment extends PreferenceFragmentCompat {

    public static final String KEY_ENABLED = "vl_custom_ui_enabled";
    public static final String KEY_BG = "vl_color_background";
    public static final String KEY_ACCENT = "vl_color_accent";
    public static final String KEY_CARD = "vl_color_card";
    public static final String KEY_TEXT = "vl_color_text";

    // Default X-style colors
    public static final int DEFAULT_BG = 0xFF000000;
    public static final int DEFAULT_ACCENT = 0xFF1D9BF0;
    public static final int DEFAULT_CARD = 0xFF16181C;
    public static final int DEFAULT_TEXT = 0xFFE7E9EA;

    private ColorSelector mColorSelector;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(getResources().getColor(R.color.background_app));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.pref_vl_custom_ui, rootKey);

        setupColorPref(KEY_BG, "Background", DEFAULT_BG);
        setupColorPref(KEY_ACCENT, "Accent", DEFAULT_ACCENT);
        setupColorPref(KEY_CARD, "Card", DEFAULT_CARD);
        setupColorPref(KEY_TEXT, "Text", DEFAULT_TEXT);

        Preference reset = findPreference("vl_reset_colors");
        if (reset != null) {
            reset.setOnPreferenceClickListener(pref -> {
                SharedPreferences.Editor ed = LauncherPreferences.DEFAULT_PREF.edit();
                ed.putInt(KEY_BG, DEFAULT_BG);
                ed.putInt(KEY_ACCENT, DEFAULT_ACCENT);
                ed.putInt(KEY_CARD, DEFAULT_CARD);
                ed.putInt(KEY_TEXT, DEFAULT_TEXT);
                ed.apply();
                updateSummaries();
                Toast.makeText(requireContext(), "Colors reset to default", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        Preference fontPref = findPreference(KEY_FONT);
        if (fontPref instanceof androidx.preference.ListPreference) {
            androidx.preference.ListPreference lp = (androidx.preference.ListPreference) fontPref;
            lp.setSummaryProvider(androidx.preference.ListPreference.SimpleSummaryProvider.getInstance());
        }


        Preference clearCache = findPreference("vl_clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(pref -> {
                try {
                    java.io.File cache = requireContext().getCacheDir();
                    deleteRecursive(cache);
                    Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Failed to clear cache", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        Preference islandSize = findPreference("vl_island_size");
        if (islandSize instanceof androidx.preference.ListPreference) {
            ((androidx.preference.ListPreference) islandSize)
                    .setSummaryProvider(androidx.preference.ListPreference.SimpleSummaryProvider.getInstance());
        }

        updateSummaries();
    }

    private void setupColorPref(String key, String label, int defaultColor) {
        Preference pref = findPreference(key);
        if (pref == null) return;

        pref.setOnPreferenceClickListener(p -> {
            int current = LauncherPreferences.DEFAULT_PREF.getInt(key, defaultColor);
            showColorPicker(key, label, current);
            return true;
        });
    }

    private void showColorPicker(String key, String label, int currentColor) {
        View root = getActivity() != null ? getActivity().findViewById(android.R.id.content) : null;
        if (!(root instanceof ViewGroup)) {
            Toast.makeText(requireContext(), "Cannot open color picker", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mColorSelector == null) {
            mColorSelector = new ColorSelector(requireContext(), (ViewGroup) root, null);
        }

        mColorSelector.setColorSelectionListener(newColor -> {
            // Strip alpha for solid UI colors (keep full opacity)
            int solid = (newColor & 0x00FFFFFF) | 0xFF000000;
            LauncherPreferences.DEFAULT_PREF.edit().putInt(key, solid).apply();
            updateSummaries();
        });

        mColorSelector.show(true, currentColor);
    }

    private void updateSummaries() {
        setColorSummary(KEY_BG, DEFAULT_BG);
        setColorSummary(KEY_ACCENT, DEFAULT_ACCENT);
        setColorSummary(KEY_CARD, DEFAULT_CARD);
        setColorSummary(KEY_TEXT, DEFAULT_TEXT);
    }

    private void setColorSummary(String key, int defaultColor) {
        Preference pref = findPreference(key);
        if (pref == null) return;
        int color = LauncherPreferences.DEFAULT_PREF.getInt(key, defaultColor);
        String hex = String.format("#%06X", (0xFFFFFF & color));
        pref.setSummary(hex);

        // Small color indicator as icon
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(color);
        swatch.setStroke(2, Color.GRAY);
        swatch.setSize(48, 48);
        pref.setIcon(swatch);
    }

    // ---- Helpers for other screens ----

    public static boolean isCustomEnabled() {
        return LauncherPreferences.DEFAULT_PREF != null
                && LauncherPreferences.DEFAULT_PREF.getBoolean(KEY_ENABLED, false);
    }

    public static int getBgColor() {
        return getColor(KEY_BG, DEFAULT_BG);
    }

    public static int getAccentColor() {
        return getColor(KEY_ACCENT, DEFAULT_ACCENT);
    }

    public static int getCardColor() {
        return getColor(KEY_CARD, DEFAULT_CARD);
    }

    public static int getTextColor() {
        return getColor(KEY_TEXT, DEFAULT_TEXT);
    }

    private static int getColor(String key, int def) {
        if (LauncherPreferences.DEFAULT_PREF == null) return def;
        return LauncherPreferences.DEFAULT_PREF.getInt(key, def);
    }

    public static final String KEY_FONT = "vl_font_family";

    /** System font family value (default, sans-serif, serif, …) */
    public static String getFontFamily() {
        if (LauncherPreferences.DEFAULT_PREF == null) return "default";
        return LauncherPreferences.DEFAULT_PREF.getString(KEY_FONT, "default");
    }

    /** Typeface for UI text – phone system fonts only */
    public static android.graphics.Typeface getTypeface() {
        String family = getFontFamily();
        if (family == null || family.equals("default")) {
            return android.graphics.Typeface.DEFAULT;
        }
        try {
            return android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL);
        } catch (Exception e) {
            return android.graphics.Typeface.DEFAULT;
        }
    }

    private static void deleteRecursive(java.io.File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        if (fileOrDirectory.isDirectory()) {
            java.io.File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDirectory.delete();
    }
}
