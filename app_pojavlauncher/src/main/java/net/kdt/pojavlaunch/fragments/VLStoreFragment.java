package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;

/**
 * VL Store hub – Modrinth / CurseForge (X-style)
 */
public class VLStoreFragment extends Fragment {
    public static final String TAG = "VLStoreFragment";
    public static final String ARG_IS_MODPACK = "is_modpack";

    public VLStoreFragment() {
        super(R.layout.fragment_vl_store);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.store_back).setOnClickListener(v ->
                Tools.backToMainMenu(requireActivity()));

        view.findViewById(R.id.store_modpacks).setOnClickListener(v -> openSearch(true));
        view.findViewById(R.id.store_mods).setOnClickListener(v -> openSearch(false));

        view.findViewById(R.id.store_resourcepacks).setOnClickListener(v ->
                Tools.openURL(requireActivity(), "https://modrinth.com/resourcepacks"));

        view.findViewById(R.id.store_shaders).setOnClickListener(v ->
                Tools.openURL(requireActivity(), "https://modrinth.com/shaders"));
    }

    private void openSearch(boolean modpack) {
        Bundle b = new Bundle(1);
        b.putBoolean(ARG_IS_MODPACK, modpack);
        Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, b);
    }
}
