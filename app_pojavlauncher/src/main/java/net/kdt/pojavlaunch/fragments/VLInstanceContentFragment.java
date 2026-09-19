package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.utils.VLModDependencyChecker;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

/**
 * Per-instance mods & resource packs: list, enable/disable, delete, open folder, store
 */
public class VLInstanceContentFragment extends Fragment {
    public static final String TAG = "VLInstanceContentFragment";

    private boolean mShowMods = true;
    private ListView mList;
    private TextView mTitle;
    private final List<File> mFiles = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();

    public VLInstanceContentFragment() {
        super(R.layout.fragment_vl_instance_content);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mList = view.findViewById(R.id.content_list);
        mTitle = view.findViewById(R.id.content_title);
        Button tabMods = view.findViewById(R.id.content_tab_mods);
        Button tabRp = view.findViewById(R.id.content_tab_rp);

        view.findViewById(R.id.content_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        tabMods.setOnClickListener(v -> {
            mShowMods = true;
            tabMods.setBackgroundResource(R.drawable.bg_vl_button_primary);
            tabRp.setBackgroundResource(R.drawable.bg_circle_dark);
            refresh();
        });
        tabRp.setOnClickListener(v -> {
            mShowMods = false;
            tabRp.setBackgroundResource(R.drawable.bg_vl_button_primary);
            tabMods.setBackgroundResource(R.drawable.bg_circle_dark);
            refresh();
        });

        view.findViewById(R.id.content_open_folder).setOnClickListener(v -> {
            File dir = contentDir();
            if (dir != null) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                Tools.openPath(requireActivity(), dir, false);
            }
        });

        view.findViewById(R.id.content_store).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), VLStoreFragment.class, VLStoreFragment.TAG, null));

        View checkDeps = view.findViewById(R.id.content_check_deps);
        if (checkDeps != null) {
            checkDeps.setOnClickListener(v -> runDependencyCheck());
        }
        View enAll = view.findViewById(R.id.content_enable_all);
        View disAll = view.findViewById(R.id.content_disable_all);
        if (enAll != null) enAll.setOnClickListener(v -> setAllEnabled(true));
        if (disAll != null) disAll.setOnClickListener(v -> setAllEnabled(false));

        mList.setOnItemClickListener((parent, v, position, id) -> toggleFile(position));
        mList.setOnItemLongClickListener((parent, v, position, id) -> {
            confirmDelete(position);
            return true;
        });

        refresh();
    }

    private File contentDir() {
        Instance inst = Instances.loadSelectedInstance();
        if (inst == null) return null;
        File root = inst.getGameDirectory();
        if (root == null) return null;
        return new File(root, mShowMods ? "mods" : "resourcepacks");
    }

    private void refresh() {
        mFiles.clear();
        mLabels.clear();
        File dir = contentDir();
        mTitle.setText(mShowMods ? "Mods" : "Resource packs");
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.isFile()) continue;
                    String name = f.getName();
                    boolean disabled = name.endsWith(".disabled");
                    String label = disabled
                            ? ("○  " + name.replace(".disabled", "") + "  (off)")
                            : ("●  " + name + "  (on)");
                    mFiles.add(f);
                    mLabels.add(label);
                }
            }
        }
        if (mLabels.isEmpty()) {
            mLabels.add(mShowMods ? "No mods yet – open Store or folder" : "No resource packs yet");
        }
        mList.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, mLabels));
    }

    private void toggleFile(int position) {
        if (position < 0 || position >= mFiles.size()) return;
        File f = mFiles.get(position);
        String name = f.getName();
        File target;
        if (name.endsWith(".disabled")) {
            target = new File(f.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        } else {
            target = new File(f.getParentFile(), name + ".disabled");
        }
        if (f.renameTo(target)) {
            Toast.makeText(requireContext(),
                    target.getName().endsWith(".disabled") ? "Disabled" : "Enabled",
                    Toast.LENGTH_SHORT).show();
            refresh();
        } else {
            Toast.makeText(requireContext(), "Could not toggle", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(int position) {
        if (position < 0 || position >= mFiles.size()) return;
        File f = mFiles.get(position);
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete?")
                .setMessage(f.getName())
                .setPositiveButton("Delete", (d, w) -> {
                    if (f.delete()) refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runDependencyCheck() {
        if (!mShowMods) {
            Toast.makeText(requireContext(), "Switch to Mods tab to check dependencies", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = contentDir();
        new Thread(() -> {
            VLModDependencyChecker.Report report = VLModDependencyChecker.check(dir);
            String msg = VLModDependencyChecker.formatReport(report);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (report.hasMissing()) {
                    // Drive Dynamic Island Yes/No on home
                    java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
                    payload.put("missing", new java.util.ArrayList<>(report.missing.keySet()));
                    payload.put("message", msg);
                    File modsDir = contentDir();
                    if (modsDir != null) payload.put("modsDir", modsDir.getAbsolutePath());
                    try {
                        net.kdt.pojavlaunch.instances.Instance inst =
                                net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
                        if (inst != null && inst.versionId != null) {
                            payload.put("mcVersion", inst.versionId);
                        }
                    } catch (Exception ignored) {}
                    ExtraCore.setValue(ExtraConstants.VL_DEP_PROMPT, payload);
                    Toast.makeText(requireContext(),
                            "Missing deps – check Dynamic Island", Toast.LENGTH_LONG).show();
                    Tools.backToMainMenu(requireActivity());
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Dependency check")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
        Toast.makeText(requireContext(), "Checking mods…", Toast.LENGTH_SHORT).show();
    }


    private void setAllEnabled(boolean enable) {
        File dir = contentDir();
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        int n = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (enable) {
                if (name.endsWith(".disabled")) {
                    File target = new File(dir, name.substring(0, name.length() - ".disabled".length()));
                    if (f.renameTo(target)) n++;
                }
            } else {
                if (!name.endsWith(".disabled")) {
                    File target = new File(dir, name + ".disabled");
                    if (f.renameTo(target)) n++;
                }
            }
        }
        Toast.makeText(requireContext(),
                enable ? ("Enabled " + n) : ("Disabled " + n),
                Toast.LENGTH_SHORT).show();
        refresh();
    }

}
