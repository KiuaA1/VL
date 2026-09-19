package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.IOException;

/**
 * VL-styled Create Instance screen (X aesthetic)
 */
public class VLCreateInstanceFragment extends Fragment {
    public static final String TAG = "VLCreateInstanceFragment";

    public VLCreateInstanceFragment() {
        super(R.layout.fragment_vl_create_instance);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back
        view.findViewById(R.id.create_back_button).setOnClickListener(v ->
                Tools.backToMainMenu(requireActivity()));

        // Vanilla
        view.findViewById(R.id.option_vanilla).setOnClickListener(v -> {
            try {
                Instance instance = Instances.createDefaultInstance();
                Instances.setSelectedInstance(instance);
                Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                        InstanceEditorFragment.TAG, new Bundle(1));
            } catch (IOException e) {
                Tools.showError(view.getContext(), e);
            }
        });

        // OptiFine
        view.findViewById(R.id.option_optifine).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), OptiFineInstallFragment.class,
                        OptiFineInstallFragment.TAG, null));

        // Fabric
        view.findViewById(R.id.option_fabric).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), FabricInstallFragment.class,
                        FabricInstallFragment.TAG, null));

        // Quilt
        view.findViewById(R.id.option_quilt).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), QuiltInstallFragment.class,
                        QuiltInstallFragment.TAG, null));

        // Forge
        view.findViewById(R.id.option_forge).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ForgeInstallFragment.class,
                        ForgeInstallFragment.TAG, null));

        // NeoForge
        view.findViewById(R.id.option_neoforge).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), NeoforgeInstallFragment.class,
                        NeoforgeInstallFragment.TAG, null));

        // Legacy Fabric
        view.findViewById(R.id.option_legacy_fabric).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), LegacyFabricInstallFragment.class,
                        LegacyFabricInstallFragment.TAG, null));

        // Modpack
        view.findViewById(R.id.option_modpack).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class,
                        SearchModFragment.TAG, null));

        // BTA
        view.findViewById(R.id.option_bta).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), BTAInstallFragment.class,
                        BTAInstallFragment.TAG, null));
    }
}
