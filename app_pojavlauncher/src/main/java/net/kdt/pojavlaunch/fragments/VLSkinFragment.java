package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

/**
 * VL Skin screen – preview + Laby.net / Mineskin integration
 */
public class VLSkinFragment extends Fragment {
    public static final String TAG = "VLSkinFragment";

    private static final String LABY_URL = "https://laby.net/";
    private static final String MINESKIN_URL_TEMPLATE = "https://mineskin.eu/skin/%s";

    private ImageView mSkinPreview;
    private TextView mUsernameText;
    private TextView mAccountTypeText;

    public VLSkinFragment() {
        super(R.layout.fragment_vl_skin);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSkinPreview = view.findViewById(R.id.skin_preview_image);
        mUsernameText = view.findViewById(R.id.skin_username);
        mAccountTypeText = view.findViewById(R.id.skin_account_type);

        view.findViewById(R.id.skin_back_button).setOnClickListener(v ->
                Tools.backToMainMenu(requireActivity()));

        view.findViewById(R.id.skin_refresh_button).setOnClickListener(v -> refreshSkin());

        view.findViewById(R.id.skin_laby_button).setOnClickListener(v ->
                Tools.openURL(requireActivity(), LABY_URL));

        view.findViewById(R.id.skin_mineskin_button).setOnClickListener(v -> {
            Account account = Accounts.getCurrent();
            if (account == null || account.username == null) {
                Toast.makeText(requireContext(), "No account selected", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = String.format(MINESKIN_URL_TEMPLATE, account.username);
            Tools.openURL(requireActivity(), url);
        });

        view.findViewById(R.id.skin_change_account).setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));

        loadCurrentSkin();
    }

    private void loadCurrentSkin() {
        Account account = Accounts.getCurrent();
        if (account == null) {
            mUsernameText.setText("No account");
            mAccountTypeText.setText("Add an account first");
            mSkinPreview.setImageResource(R.drawable.ic_px_person);
            return;
        }

        mUsernameText.setText(account.username != null ? account.username : "Unknown");

        String typeLabel;
        if (account.isMicrosoft) {
            typeLabel = "Microsoft account";
        } else if (account.isLocal()) {
            typeLabel = "Local / Offline account";
        } else {
            typeLabel = "Online account";
        }
        mAccountTypeText.setText(typeLabel);

        Bitmap face = account.getSkinFace();
        if (face != null) {
            mSkinPreview.setImageBitmap(face);
        } else {
            mSkinPreview.setImageResource(R.drawable.ic_px_person);
        }
        // Prefer full-body standing render
        loadFullBody(account.username);
    }

    private void loadFullBody(String username) {
        if (username == null || username.isEmpty() || mSkinPreview == null) return;
        final String url = "https://mc-heads.net/body/" + username + "/right";
        mSkinPreview.setTag(username);
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "VL-Launcher");
                java.io.InputStream in = c.getInputStream();
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                c.disconnect();
                if (bmp != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (username.equals(String.valueOf(mSkinPreview.getTag()))) {
                            mSkinPreview.setImageBitmap(bmp);
                            mSkinPreview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void refreshSkin() {
        Account account = Accounts.getCurrent();
        if (account == null) {
            Toast.makeText(requireContext(), "No account selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (account.isLocal()) {
            Toast.makeText(requireContext(), "Local accounts have no online skin", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Refreshing skin…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            account.updateSkinFace();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    loadCurrentSkin();
                    Toast.makeText(requireContext(), "Skin updated", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCurrentSkin();
    }
}
