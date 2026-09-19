package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Full mouse + cursor customization including custom image import
 */
public class VLMouseSettingsFragment extends Fragment {
    public static final String TAG = "VLMouseSettingsFragment";
    public static final String KEY_CURSOR_STYLE = "vl_cursor_style";
    public static final String KEY_CUSTOM_CURSOR = "vl_custom_cursor_path";
    public static final String CUSTOM_CURSOR_FILE = "vl_custom_cursor.png";

    private static final String[] STYLE_VALUES = {"default", "crosshair", "dot", "pointer"};
    private static final String[] STYLE_LABELS = {
            "Default pointer", "Crosshair", "Dot", "Large pointer"
    };

    private TextView mCursorStatus;

    private final ActivityResultLauncher<String> mPickCursor =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCursorPicked);

    public VLMouseSettingsFragment() {
        super(R.layout.fragment_vl_mouse);
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.mouse_back).setOnClickListener(v ->
                Tools.backToMainMenu(requireActivity()));

        Spinner style = view.findViewById(R.id.mouse_cursor_style);
        SeekBar speed = view.findViewById(R.id.mouse_speed_seek);
        TextView speedVal = view.findViewById(R.id.mouse_speed_value);
        SeekBar scale = view.findViewById(R.id.mouse_scale_seek);
        TextView scaleVal = view.findViewById(R.id.mouse_scale_value);
        SeekBar longPress = view.findViewById(R.id.mouse_longpress_seek);
        TextView longPressVal = view.findViewById(R.id.mouse_longpress_value);
        Switch virtualStart = view.findViewById(R.id.mouse_virtual_start);
        Switch disableSwap = view.findViewById(R.id.mouse_disable_swap);
        Switch disableGestures = view.findViewById(R.id.mouse_disable_gestures);
        Switch invertX = view.findViewById(R.id.mouse_invert_x);
        Switch invertY = view.findViewById(R.id.mouse_invert_y);
        mCursorStatus = view.findViewById(R.id.mouse_cursor_status);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, STYLE_LABELS);
        style.setAdapter(adapter);
        String curStyle = LauncherPreferences.DEFAULT_PREF.getString(KEY_CURSOR_STYLE, "default");
        int styleIdx = 0;
        for (int i = 0; i < STYLE_VALUES.length; i++) {
            if (STYLE_VALUES[i].equals(curStyle)) { styleIdx = i; break; }
        }
        style.setSelection(styleIdx);
        style.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(KEY_CURSOR_STYLE, STYLE_VALUES[position]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        int speedPct = Math.round(LauncherPreferences.PREF_MOUSESPEED * 100f);
        int scalePct = Math.round(LauncherPreferences.PREF_MOUSESCALE * 100f);
        int lp = LauncherPreferences.PREF_LONGPRESS_TRIGGER;
        speed.setProgress(speedPct);
        scale.setProgress(scalePct);
        longPress.setProgress(lp);
        speedVal.setText(speedPct + "%");
        scaleVal.setText(scalePct + "%");
        longPressVal.setText(lp + " ms");

        virtualStart.setChecked(LauncherPreferences.PREF_VIRTUAL_MOUSE_START);
        disableSwap.setChecked(LauncherPreferences.PREF_DISABLE_SWAP_HAND);
        disableGestures.setChecked(LauncherPreferences.PREF_DISABLE_GESTURES);
        invertX.setChecked(LauncherPreferences.PREF_GYRO_INVERT_X);
        invertY.setChecked(LauncherPreferences.PREF_GYRO_INVERT_Y);

        speed.setOnSeekBarChangeListener(simple((sb, p, u) -> {
            LauncherPreferences.PREF_MOUSESPEED = p / 100f;
            LauncherPreferences.DEFAULT_PREF.edit().putInt("mousespeed", p).apply();
            speedVal.setText(p + "%");
        }));
        scale.setOnSeekBarChangeListener(simple((sb, p, u) -> {
            LauncherPreferences.PREF_MOUSESCALE = p / 100f;
            LauncherPreferences.DEFAULT_PREF.edit().putInt("mousescale", p).apply();
            scaleVal.setText(p + "%");
        }));
        longPress.setOnSeekBarChangeListener(simple((sb, p, u) -> {
            LauncherPreferences.PREF_LONGPRESS_TRIGGER = p;
            LauncherPreferences.DEFAULT_PREF.edit().putInt("timeLongPressTrigger", p).apply();
            longPressVal.setText(p + " ms");
        }));

        virtualStart.setOnCheckedChangeListener((b, c) -> {
            LauncherPreferences.PREF_VIRTUAL_MOUSE_START = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("mouse_start", c).apply();
        });
        disableSwap.setOnCheckedChangeListener((b, c) -> {
            LauncherPreferences.PREF_DISABLE_SWAP_HAND = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("disableDoubleTap", c).apply();
        });
        disableGestures.setOnCheckedChangeListener((b, c) -> {
            LauncherPreferences.PREF_DISABLE_GESTURES = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("disableGestures", c).apply();
        });
        invertX.setOnCheckedChangeListener((b, c) -> {
            LauncherPreferences.PREF_GYRO_INVERT_X = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("gyroInvertX", c).apply();
        });
        invertY.setOnCheckedChangeListener((b, c) -> {
            LauncherPreferences.PREF_GYRO_INVERT_Y = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("gyroInvertY", c).apply();
        });

        View importBtn = view.findViewById(R.id.mouse_import_cursor);
        View clearBtn = view.findViewById(R.id.mouse_clear_cursor);
        if (importBtn != null) {
            importBtn.setOnClickListener(v -> mPickCursor.launch("image/*"));
        }
        if (clearBtn != null) {
            clearBtn.setOnClickListener(v -> {
                File f = getCustomCursorFile();
                if (f.exists()) //noinspection ResultOfMethodCallIgnored
                    f.delete();
                LauncherPreferences.DEFAULT_PREF.edit().remove(KEY_CUSTOM_CURSOR).apply();
                updateCursorStatus();
                Toast.makeText(requireContext(), "Custom cursor cleared", Toast.LENGTH_SHORT).show();
            });
        }
        updateCursorStatus();
    }

    private void onCursorPicked(Uri uri) {
        if (uri == null) return;
        try {
            File out = getCustomCursorFile();
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("Cannot open image");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
            }
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(KEY_CUSTOM_CURSOR, out.getAbsolutePath())
                    .apply();
            updateCursorStatus();
            Toast.makeText(requireContext(), "Cursor imported – size uses Cursor scale", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File getCustomCursorFile() {
        return new File(requireContext().getFilesDir(), CUSTOM_CURSOR_FILE);
    }

    private void updateCursorStatus() {
        if (mCursorStatus == null) return;
        File f = new File(requireContext().getFilesDir(), CUSTOM_CURSOR_FILE);
        if (f.exists()) {
            mCursorStatus.setText("Custom cursor: " + f.getName() + " (scale controls size)");
        } else {
            mCursorStatus.setText("Using built-in style");
        }
    }

    private interface SeekCb { void on(SeekBar sb, int p, boolean u); }

    private SeekBar.OnSeekBarChangeListener simple(SeekCb cb) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cb.on(seekBar, progress, fromUser);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }
}
