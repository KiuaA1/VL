package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.game.renderer.RendererCache;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.RTSpinnerAdapter;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.game.renderer.GameRenderer;
import net.kdt.pojavlaunch.utils.CropperUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstanceEditorFragment extends Fragment implements CropperUtils.CropperReceiver {
    public static final String TAG = "InstanceEditorFragment";

    private Instance mInstance;
    private String mSelectedControlLayout;
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mVersionSelectButton;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultVersion, mDefaultControl;
    private ImageView mInstanceIcon;
    private CheckBox mSharedDataCheckbox;
    private int mRecommendedIconSize;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private List<String> mRenderNames;

    public InstanceEditorFragment(){
        super(R.layout.fragment_instance_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if(value != null){
            mSelectedControlLayout = value;
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);

        RendererCache list = RendererCache.getCompatibleRenderers(view.getContext());
        mRenderNames = list.rendererIds;
        List<String> renderList = new ArrayList<>(list.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(list.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(view.getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            InstanceIconProvider.dropIcon(mInstance);
            save();
            Tools.backToMainMenu(requireActivity());
        });

        mDeleteButton.setOnClickListener(v -> {
            DeleteConfirmDialogFragment dialogFragment = new DeleteConfirmDialogFragment();
            dialogFragment.show(getChildFragmentManager(), "delete_dialog_confirm");
        });

        View modsBtn = view.findViewById(R.id.vprof_editor_mods_button);
        if (modsBtn != null) {
            modsBtn.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(),
                            VLInstanceContentFragment.class,
                            VLInstanceContentFragment.TAG, null));
        }
        View favBtn = view.findViewById(R.id.vprof_editor_favorite_button);
        if (favBtn != null) {
            favBtn.setOnClickListener(v -> {
                if (mInstance == null || mInstance.name == null) return;
                String key = "vl_favorite_instances";
                java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
                String raw = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.getString(key, "");
                if (raw != null && !raw.isEmpty()) {
                    for (String s : raw.split("\u0001")) if (!s.isEmpty()) set.add(s);
                }
                boolean now;
                if (set.contains(mInstance.name)) { set.remove(mInstance.name); now = false; }
                else { set.add(mInstance.name); now = true; }
                StringBuilder sb = new StringBuilder();
                for (String s : set) {
                    if (sb.length() > 0) sb.append('\u0001');
                    sb.append(s);
                }
                net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(key, sb.toString()).apply();
                android.widget.Toast.makeText(requireContext(),
                        now ? "★ Favorited" : "Removed from favorites",
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        View dupBtn = view.findViewById(R.id.vprof_editor_duplicate_button);
        if (dupBtn != null) {
            dupBtn.setOnClickListener(v -> {
                if (mInstance == null) return;
                new Thread(() -> {
                    try {
                        java.io.File src = mInstance.getGameDirectory();
                        if (src == null) throw new IllegalStateException("No instance folder");
                        String base = (mInstance.name != null ? mInstance.name : "Instance") + " Copy";
                        net.kdt.pojavlaunch.instances.Instance copy =
                                net.kdt.pojavlaunch.instances.Instances.createInstance(inst -> {
                                    inst.name = base;
                                    inst.versionId = mInstance.versionId;
                                    inst.renderer = mInstance.renderer;
                                    inst.jvmArgs = mInstance.jvmArgs;
                                    inst.argsMode = mInstance.argsMode;
                                    inst.selectedRuntime = mInstance.selectedRuntime;
                                    inst.controlLayout = mInstance.controlLayout;
                                    inst.sharedData = mInstance.sharedData;
                                }, base);
                        // Copy game content (mods, saves, etc.) excluding metadata rewrite done by create
                        java.io.File dst = copy.getGameDirectory();
                        if (src.isDirectory() && dst != null) {
                            for (java.io.File child : src.listFiles()) {
                                if (child == null) continue;
                                String n = child.getName();
                                if ("instance.json".equals(n) || "instance.cfg".equals(n)) continue;
                                java.io.File target = new java.io.File(dst, n);
                                if (child.isDirectory()) {
                                    org.apache.commons.io.FileUtils.copyDirectory(child, target);
                                } else {
                                    org.apache.commons.io.FileUtils.copyFile(child, target);
                                }
                            }
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                android.widget.Toast.makeText(requireContext(),
                                        "Duplicated: " + base, android.widget.Toast.LENGTH_LONG).show();
                                Tools.backToMainMenu(requireActivity());
                            });
                        }
                    } catch (Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    android.widget.Toast.makeText(requireContext(),
                                            "Duplicate failed: " + e.getMessage(),
                                            android.widget.Toast.LENGTH_LONG).show());
                        }
                    }
                }).start();
                android.widget.Toast.makeText(requireContext(), "Duplicating…", android.widget.Toast.LENGTH_SHORT).show();
            });
        }
        View logsBtn = view.findViewById(R.id.vprof_editor_logs_button);
        if (logsBtn != null) {
            logsBtn.setOnClickListener(v -> {
                java.io.File crashDir = new java.io.File(Tools.DIR_HOME_CRASH);
                if (!crashDir.exists()) //noinspection ResultOfMethodCallIgnored
                    crashDir.mkdirs();
                Tools.openPath(requireActivity(), crashDir, false);
            });
        }

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        // Set up the icon change click listener
        mInstanceIcon.setOnClickListener(v -> {
            // Fill recommended size on click to ge the most up to date data
            mRecommendedIconSize = Math.max(v.getWidth(), v.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        });

        mSharedDataCheckbox.setOnCheckedChangeListener((v,checked) ->{
            mInstance.sharedData = checked;
            int text = R.string.instance_shared_data_off;
            if(checked) text = R.string.instance_shared_data_on;
            mSharedDataCheckbox.setText(text);
        });

        Instance selectedInstance = Instances.loadSelectedInstance();
        Context context = view.getContext();
        if(selectedInstance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            getParentFragmentManager().popBackStack();
        }else {
            loadValues(selectedInstance, context);
        }
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, (id, snapshot)-> mDefaultVersion.setText(id));
    }

    private static String nullToEmpty(String in) {
        if(in == null) return "";
        return in;
    }

    private void loadValues(@NonNull Instance instance, @NonNull Context context){
        mInstance = instance;
        mInstanceIcon.setImageDrawable(
                InstanceIconProvider.fetchIcon(getResources(), instance)
        );

        // Runtime spinner
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        int jvmIndex = -1;
        if(instance.selectedRuntime != null) {
            jvmIndex = runtimes.indexOf(new Runtime(instance.selectedRuntime));
        }
        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
        int rendererIndex = mRenderNames.indexOf(instance.getLaunchRenderer());
        if(rendererIndex == -1) {
            rendererIndex = mDefaultRenderer.getAdapter().getCount() - 1;
        }
        mDefaultRenderer.setSelection(rendererIndex);

        mDefaultVersion.setText(instance.versionId);
        mDefaultJvmArgument.setText(nullToEmpty(instance.jvmArgs));
        mDefaultName.setText(nullToEmpty(instance.name));
        mDefaultControl.setText(mSelectedControlLayout == null ? nullToEmpty(instance.controlLayout) : mSelectedControlLayout);
        mSharedDataCheckbox.setChecked(instance.sharedData);
    }

    private void bindViews(@NonNull View view){
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_instance_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);

        mDefaultName = view.findViewById(R.id.vprof_editor_instance_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mInstanceIcon = view.findViewById(R.id.vprof_editor_instance_icon);
        mSharedDataCheckbox = view.findViewById(R.id.vprof_editor_data_checkbox_container);
    }

    private void save(){
        //First, check for potential issues in the inputs
        mInstance.versionId = mDefaultVersion.getText().toString();
        mInstance.controlLayout = mDefaultControl.getText().toString();
        mInstance.jvmArgs = mDefaultJvmArgument.getText().toString();

        String newName = mDefaultName.getText().toString();
        if(mInstance.controlLayout.isEmpty()) mInstance.controlLayout = null;
        if(mInstance.jvmArgs.isEmpty()) mInstance.jvmArgs = null;

        Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
        mInstance.selectedRuntime = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                ? null : selectedRuntime.name;

        if(mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size()) mInstance.renderer = null;
        else mInstance.renderer = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());

        try {
            if(!newName.isEmpty() && !newName.equals(mInstance.name))
                Instances.renameInstanceDirectory(mInstance, newName);
            mInstance.name = newName;
            mInstance.write();
        }catch (Exception e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public float getAspectRatio() {
        return 1f;
    }

    @Override
    public int getTargetMaxSide() {
        return mRecommendedIconSize;
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        mInstanceIcon.setImageBitmap(contentBitmap);
        Log.i("bitmap", "w="+contentBitmap.getWidth() +" h="+contentBitmap.getHeight());
        try {
            mInstance.encodeNewIcon(contentBitmap);
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
