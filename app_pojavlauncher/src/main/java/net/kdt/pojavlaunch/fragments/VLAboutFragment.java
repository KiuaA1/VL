package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;

/**
 * About VL – logo, version, credits (By kiua)
 */
public class VLAboutFragment extends Fragment {
    public static final String TAG = "VLAboutFragment";

    public VLAboutFragment() {
        super(R.layout.fragment_vl_about);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.about_back).setOnClickListener(v ->
                Tools.backToMainMenu(requireActivity()));

        TextView version = view.findViewById(R.id.about_version);
        try {
            version.setText("v" + BuildConfig.VERSION_NAME + "  ·  " + BuildConfig.VERSION_CODE);
        } catch (Exception e) {
            version.setText("VL");
        }

        view.findViewById(R.id.about_github).setOnClickListener(v ->
                Tools.openURL(requireActivity(), "https://github.com/"));

        View logs = view.findViewById(R.id.about_logs);
        if (logs != null) {
            logs.setOnClickListener(v -> {
                java.io.File crashDir = new java.io.File(Tools.DIR_HOME_CRASH);
                if (!crashDir.exists()) //noinspection ResultOfMethodCallIgnored
                    crashDir.mkdirs();
                Tools.openPath(requireActivity(), crashDir, false);
            });
        }
    }
}
