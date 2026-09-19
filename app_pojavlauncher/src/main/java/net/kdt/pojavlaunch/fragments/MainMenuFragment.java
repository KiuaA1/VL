package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;

import android.content.Context;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.utils.FileUtils;

import com.kdt.mcgui.ProgressLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VL Home screen – X-style + Dynamic Island design
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private TextView mDynamicIslandText;
    private View mIslandActions;
    private View mIslandYes;
    private View mIslandNo;
    private java.util.List<String> mPendingMissingMods;
    private String mPendingModsDir;
    private net.kdt.pojavlaunch.utils.VLUpdateChecker.UpdateInfo mPendingUpdate;
    private String mPendingMcVersion;
    private ImageButton mAccountButton;
    private ImageButton mSettingsButton;
    private ImageButton mStoreButton;
    private ImageButton mMouseButton;
    private View mSkinPaper;
    private ImageButton mAddInstanceButton;
    private LinearLayout mInstancesContainer;
    private View mEmptyState;
    private String mInstanceQuery = "";
    private View mInstancesScroll;
    private String mIdleIslandText = "VL  •  Ready";
    private boolean mProgressActive = false;

    private final TaskCountListener mTaskCountListener = count -> {
        if (count == 0) {
            mProgressActive = false;
            updateDynamicIsland(mIdleIslandText);
        }
        return false;
    };

    private final ProgressListener mProgressListener = new ProgressListener() {
        @Override
        public void onProgressStarted() {
            mProgressActive = true;
            runOnUi(() -> updateDynamicIsland("Working…"));
        }

        @Override
        public void onProgressUpdated(int progress, int resid, Object... va) {
            mProgressActive = true;
            runOnUi(() -> {
                String msg = formatProgress(progress, resid, va);
                updateDynamicIsland(msg);
            });
        }

        @Override
        public void onProgressEnded() {
            // Task count listener will reset island when all tasks done
        }
    };

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_vl_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mDynamicIslandText = view.findViewById(R.id.dynamic_island_text);
        mIslandActions = view.findViewById(R.id.dynamic_island_actions);
        mIslandYes = view.findViewById(R.id.island_yes_button);
        mIslandNo = view.findViewById(R.id.island_no_button);
        if (mIslandYes != null) {
            mIslandYes.setOnClickListener(v -> onIslandYesInstallDeps());
        }
        if (mIslandNo != null) {
            mIslandNo.setOnClickListener(v -> hideIslandActions("OK  •  Skipped install"));
        }
        ExtraCore.addExtraListener(ExtraConstants.VL_DEP_PROMPT, (key, value) -> {
            if (value instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
                Object miss = map.get("missing");
                if (miss instanceof java.util.Collection) {
                    mPendingMissingMods = new java.util.ArrayList<>();
                    for (Object o : (java.util.Collection<?>) miss) {
                        if (o != null) mPendingMissingMods.add(o.toString());
                    }
                }
                Object dir = map.get("modsDir");
                mPendingModsDir = dir != null ? dir.toString() : null;
                Object ver = map.get("mcVersion");
                mPendingMcVersion = ver != null ? ver.toString() : null;
                int n = mPendingMissingMods != null ? mPendingMissingMods.size() : 0;
                showIslandActions("Install " + n + " missing mod(s)?");
            }
            return false;
        });
        View island = view.findViewById(R.id.dynamic_island);
        if (island != null) {
            island.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(),
                        VLAboutFragment.class,
                        VLAboutFragment.TAG, null);
                return true;
            });
        }
        mAccountButton = view.findViewById(R.id.account_button);
        mSettingsButton = view.findViewById(R.id.settings_button);
        mStoreButton = view.findViewById(R.id.store_button);
        mMouseButton = view.findViewById(R.id.mouse_button);
        mSkinPaper = view.findViewById(R.id.skin_paper_container);
        mAddInstanceButton = view.findViewById(R.id.add_instance_button);
        mInstancesContainer = view.findViewById(R.id.instances_container);
        mEmptyState = view.findViewById(R.id.empty_state);
        mInstancesScroll = view.findViewById(R.id.instances_scroll);

        updateDynamicIsland("VL  •  Ready");

        mAccountButton.setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));

        mSettingsButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment.class,
                        "SETTINGS_FRAGMENT", null));

        if (mStoreButton != null) {
            mStoreButton.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(),
                            VLStoreFragment.class,
                            VLStoreFragment.TAG, null));
        }
        if (mMouseButton != null) {
            mMouseButton.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(),
                            VLMouseSettingsFragment.class,
                            VLMouseSettingsFragment.TAG, null));
        }

        mSkinPaper.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(),
                        VLSkinFragment.class,
                        VLSkinFragment.TAG, null));

        mAddInstanceButton.setOnClickListener(v -> {
            if (ProgressKeeper.getTaskCount() > 0) {
                Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                return;
            }
            Tools.swapFragment(requireActivity(),
                    VLCreateInstanceFragment.class,
                    VLCreateInstanceFragment.TAG, null);
        });

        refreshInstanceCards();
        updateHomeSkinPreview(view);
        
        EditText search = view.findViewById(R.id.instance_search);
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    mInstanceQuery = s != null ? s.toString().trim().toLowerCase() : "";
                    refreshInstanceCards();
                }
            });
        }

        applyCustomTheme(view);
        registerProgressListeners();
    }

    private void registerProgressListeners() {
        ProgressKeeper.addTaskCountListener(mTaskCountListener);
        String[] keys = {
                ProgressLayout.DOWNLOAD_GAME,
                ProgressLayout.UNPACK_RUNTIME,
                ProgressLayout.DOWNLOAD_VERSION_LIST,
                ProgressLayout.AUTHENTICATE,
                ProgressLayout.INSTALL_MODPACK,
                ProgressLayout.INSTANCE_INSTALL,
                ProgressLayout.DATA_MIGRATION,
                ProgressLayout.EXTRACT_COMPONENTS,
                ProgressLayout.EXTRACT_SINGLE_FILES
        };
        for (String key : keys) {
            ProgressKeeper.addListener(key, mProgressListener);
        }
    }

    private void unregisterProgressListeners() {
        ProgressKeeper.removeTaskCountListener(mTaskCountListener);
        String[] keys = {
                ProgressLayout.DOWNLOAD_GAME,
                ProgressLayout.UNPACK_RUNTIME,
                ProgressLayout.DOWNLOAD_VERSION_LIST,
                ProgressLayout.AUTHENTICATE,
                ProgressLayout.INSTALL_MODPACK,
                ProgressLayout.INSTANCE_INSTALL,
                ProgressLayout.DATA_MIGRATION,
                ProgressLayout.EXTRACT_COMPONENTS,
                ProgressLayout.EXTRACT_SINGLE_FILES
        };
        for (String key : keys) {
            ProgressKeeper.removeListener(key, mProgressListener);
        }
    }

    private void runOnUi(Runnable r) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(r);
        }
    }

    private String formatProgress(int progress, int resid, Object... va) {
        String base = "Working";
        try {
            if (resid > 0 && getContext() != null) {
                if (va != null && va.length > 0) {
                    base = getString(resid, va);
                } else {
                    base = getString(resid);
                }
            }
        } catch (Exception ignored) {
            base = "Working";
        }
        // Keep Dynamic Island short
        if (base.length() > 28) {
            base = base.substring(0, 25) + "…";
        }
        if (progress >= 0 && progress <= 100) {
            return base + "  " + progress + "%";
        }
        return base;
    }

    private void applyCustomTheme(View root) {
        if (root == null) return;


        // Dynamic Island size (always applies)
        applyIslandSize(root);

        // Font always applies (system fonts only)
        android.graphics.Typeface tf =
                net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.getTypeface();
        applyTypefaceRecursive(root, tf);

        if (!net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.isCustomEnabled()) return;

        int bg = net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.getBgColor();
        int accent = net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.getAccentColor();
        int card = net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.getCardColor();
        int text = net.kdt.pojavlaunch.prefs.screens.VLCustomUIFragment.getTextColor();

        root.setBackgroundColor(bg);

        if (mDynamicIslandText != null) {
            mDynamicIslandText.setTextColor(text);
            View island = root.findViewById(R.id.dynamic_island);
            if (island != null && island.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd =
                        (android.graphics.drawable.GradientDrawable) island.getBackground().mutate();
                gd.setColor(card);
            }
        }

        if (mAddInstanceButton != null) {
            if (mAddInstanceButton.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                android.graphics.drawable.GradientDrawable gd =
                        (android.graphics.drawable.GradientDrawable) mAddInstanceButton.getBackground().mutate();
                gd.setColor(accent);
            }
        }

        if (mSkinPaper != null && mSkinPaper.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
            android.graphics.drawable.GradientDrawable gd =
                    (android.graphics.drawable.GradientDrawable) mSkinPaper.getBackground().mutate();
            gd.setColor(card);
        }
    }

    private void showIslandActions(String text) {
        updateDynamicIsland(text);
        if (mIslandActions != null) {
            mIslandActions.setVisibility(View.VISIBLE);
        }
    }

    private void hideIslandActions(String text) {
        if (mIslandActions != null) {
            mIslandActions.setVisibility(View.GONE);
        }
        mPendingMissingMods = null;
        if (text != null) updateDynamicIsland(text);
    }

    private void onIslandYesInstallDeps() {
        if (mPendingMissingMods == null || mPendingMissingMods.isEmpty()) {
            hideIslandActions("Nothing to install");
            return;
        }
        final java.util.List<String> ids = new java.util.ArrayList<>(mPendingMissingMods);
        final java.io.File modsDir = mPendingModsDir != null ? new java.io.File(mPendingModsDir) : null;
        final String mcVer = mPendingMcVersion;
        hideIslandActions("Downloading mods…");
        updateDynamicIsland("Downloading " + ids.size() + " mod(s)…");
        new Thread(() -> {
            net.kdt.pojavlaunch.utils.VLModAutoInstaller.Result res =
                    net.kdt.pojavlaunch.utils.VLModAutoInstaller.installMissing(modsDir, ids, mcVer);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    String msg = "Installed " + res.installed.size();
                    if (!res.failed.isEmpty()) {
                        msg += "  •  failed " + res.failed.size();
                    }
                    updateDynamicIsland(msg);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    // Re-scan remaining deps
                    if (modsDir != null) {
                        new Thread(() -> {
                            net.kdt.pojavlaunch.utils.VLModDependencyChecker.Report report =
                                    net.kdt.pojavlaunch.utils.VLModDependencyChecker.check(modsDir);
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                if (report.hasMissing()) {
                                    mPendingMissingMods = new java.util.ArrayList<>(report.missing.keySet());
                                    mPendingModsDir = modsDir.getAbsolutePath();
                                    showIslandActions("Still missing " + mPendingMissingMods.size() + " – install?");
                                } else {
                                    updateDynamicIsland("Deps OK  •  Ready");
                                }
                            });
                        }).start();
                    }
                });
            }
        }).start();
    }

    private void applyIslandSize(View root) {
        View island = root.findViewById(R.id.dynamic_island);
        if (island == null || LauncherPreferences.DEFAULT_PREF == null) return;
        String size = LauncherPreferences.DEFAULT_PREF.getString("vl_island_size", "medium");
        int hDp;
        float textSp;
        switch (size != null ? size : "medium") {
            case "small": hDp = 28; textSp = 11f; break;
            case "large": hDp = 44; textSp = 14f; break;
            case "xlarge": hDp = 52; textSp = 15f; break;
            default: hDp = 36; textSp = 13f; break;
        }
        ViewGroup.LayoutParams lp = island.getLayoutParams();
        lp.height = dp(hDp);
        island.setLayoutParams(lp);
        if (mDynamicIslandText != null) {
            mDynamicIslandText.setTextSize(textSp);
        }
    }

    private boolean isFavorite(String name) {
        if (name == null || LauncherPreferences.DEFAULT_PREF == null) return false;
        String raw = LauncherPreferences.DEFAULT_PREF.getString("vl_favorite_instances", "");
        if (raw == null || raw.isEmpty()) return false;
        for (String s : raw.split("\u0001")) {
            if (name.equals(s)) return true;
        }
        return false;
    }

    private void toggleFavorite(String name) {
        if (name == null || LauncherPreferences.DEFAULT_PREF == null) return;
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        String raw = LauncherPreferences.DEFAULT_PREF.getString("vl_favorite_instances", "");
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\u0001")) {
                if (!s.isEmpty()) set.add(s);
            }
        }
        if (set.contains(name)) set.remove(name);
        else set.add(name);
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append('\u0001');
            sb.append(s);
        }
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString("vl_favorite_instances", sb.toString())
                .apply();
        refreshInstanceCards();
    }

    private void applyTypefaceRecursive(View v, android.graphics.Typeface tf) {
        if (v instanceof TextView) {
            ((TextView) v).setTypeface(tf);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTypefaceRecursive(g.getChildAt(i), tf);
            }
        }
    }

    private void updateHomeSkinPreview(View root) {
        if (root == null) return;
        ImageView preview = root.findViewById(R.id.skin_preview);
        TextView label = root.findViewById(R.id.skin_label);

        Account account = Accounts.getCurrent();
        if (account != null) {
            Bitmap face = account.getSkinFace();
            // Full-body standing character (mc-heads body render)
            if (preview != null) {
                preview.setImageResource(R.drawable.ic_px_person);
                loadFullBodySkin(preview, account.username);
            }
            if (label != null) {
                label.setText(account.username != null ? account.username : "Skin");
            }
            // Account button keeps face/head
            if (mAccountButton != null) {
                if (face != null) {
                    mAccountButton.setImageBitmap(face);
                    mAccountButton.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    mAccountButton.setPadding(0, 0, 0, 0);
                } else {
                    mAccountButton.setImageResource(R.drawable.ic_px_person);
                    mAccountButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    mAccountButton.setPadding(dp(10), dp(10), dp(10), dp(10));
                }
            }
        } else {
            if (preview != null) preview.setImageResource(R.drawable.ic_px_person);
            if (label != null) label.setText("Add account");
            if (mAccountButton != null) {
                mAccountButton.setImageResource(R.drawable.ic_px_person);
                mAccountButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mAccountButton.setPadding(dp(10), dp(10), dp(10), dp(10));
            }
        }
    }

    /** Full-body character render (standing pose, like 3D skin previews) */
    private void loadFullBodySkin(ImageView target, String username) {
        if (target == null || username == null || username.isEmpty()) return;
        final String url = "https://mc-heads.net/body/" + username + "/right";
        target.setTag(username);
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "VL-Launcher");
                java.io.InputStream in = conn.getInputStream();
                final Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                conn.disconnect();
                if (bmp != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (username.equals(String.valueOf(target.getTag()))) {
                            target.setImageBitmap(bmp);
                            target.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        }
                    });
                }
            } catch (Exception e) {
                Log.w("VLHome", "Full body skin load failed", e);
            }
        }).start();
    }

    private int dp(int value) {
        if (getContext() == null) return value;
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateDynamicIsland(String text) {
        if (mDynamicIslandText != null) {
            mDynamicIslandText.setText(text);
        }
        // Remember idle text when not in progress
        if (!mProgressActive && text != null && !text.contains("%") && !text.startsWith("Working")
                && !text.startsWith("Launching") && !text.startsWith("Downloading")) {
            mIdleIslandText = text;
        }
    }

    private static final String PREF_INSTANCE_ORDER = "vl_instance_order";

    private void refreshInstanceCards() {
        if (mInstancesContainer == null || getContext() == null) return;

        mInstancesContainer.removeAllViews();

        try {
            Instances display = Instances.loadDisplay();
            List<DisplayInstance> list = new ArrayList<>(display.list);
            applySavedOrder(list);

            if (list.isEmpty()) {
                updateDynamicIsland("VL  •  No instances");
                if (mEmptyState != null) mEmptyState.setVisibility(View.VISIBLE);
                if (mInstancesScroll != null) mInstancesScroll.setVisibility(View.GONE);
                return;
            }

            if (mEmptyState != null) mEmptyState.setVisibility(View.GONE);
            if (mInstancesScroll != null) mInstancesScroll.setVisibility(View.VISIBLE);

            LayoutInflater inflater = LayoutInflater.from(requireContext());
            DisplayInstance selectedInstance = null;
            try {
                selectedInstance = Instances.loadSelectedInstance();
            } catch (Exception ignored) {}

            for (int i = 0; i < list.size(); i++) {
                DisplayInstance instance = list.get(i);
                if (mInstanceQuery != null && !mInstanceQuery.isEmpty()) {
                    String n = instance.name != null ? instance.name.toLowerCase() : "";
                    if (!n.contains(mInstanceQuery)) continue;
                }
                View card = inflater.inflate(R.layout.item_instance_card, mInstancesContainer, false);
                card.setTag(instance.name != null ? instance.name : ("instance_" + i));

                ImageButton moreBtn = card.findViewById(R.id.instance_more_button);
                ImageButton playBtn = card.findViewById(R.id.instance_play_button);
                ImageView iconView = card.findViewById(R.id.instance_image);
                TextView nameView = card.findViewById(R.id.instance_name);

                nameView.setText(instance.name != null ? instance.name : "Instance");
                // Favorite star
                boolean fav = isFavorite(instance.name);
                if (fav) {
                    nameView.setText("★ " + (instance.name != null ? instance.name : "Instance"));
                }


                try {
                    Drawable icon = InstanceIconProvider.fetchIcon(getResources(), instance);
                    iconView.setImageDrawable(icon);
                } catch (Exception e) {
                    iconView.setImageResource(R.drawable.ic_px_java_run);
                }

                boolean isSelected = selectedInstance != null
                        && selectedInstance.name != null
                        && selectedInstance.name.equals(instance.name);
                if (isSelected) {
                    card.setBackgroundResource(R.drawable.bg_instance_card_selected);
                    card.setAlpha(1f);
                } else {
                    card.setBackgroundResource(R.drawable.bg_instance_card);
                    card.setAlpha(0.9f);
                }

                final DisplayInstance finalInstance = instance;
                final int index = i;

                playBtn.setOnClickListener(v -> {
                    Instances.setSelectedInstance(finalInstance);
                    if (LauncherPreferences.DEFAULT_PREF != null && finalInstance.name != null) {
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putString("vl_last_played", finalInstance.name)
                                .putLong("vl_last_played_time", System.currentTimeMillis())
                                .apply();
                    }
                    updateDynamicIsland("Launching  •  " + (finalInstance.name != null ? finalInstance.name : "Instance"));
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                });

                moreBtn.setOnClickListener(v -> {
                    Instances.setSelectedInstance(finalInstance);
                    Tools.swapFragment(requireActivity(),
                            InstanceEditorFragment.class,
                            InstanceEditorFragment.TAG, null);
                });
                moreBtn.setOnLongClickListener(v -> {
                    toggleFavorite(finalInstance.name);
                    boolean nowFav = isFavorite(finalInstance.name);
                    updateDynamicIsland(nowFav
                            ? ("★  " + finalInstance.name)
                            : ("Unfavorited  •  " + finalInstance.name));
                    return true;
                });

                card.setOnClickListener(v -> {
                    Instances.setSelectedInstance(finalInstance);
                    updateDynamicIsland("Selected  •  " + (finalInstance.name != null ? finalInstance.name : "Instance"));
                    refreshInstanceCards();
                });

                // Long-press to drag & reorder
                card.setOnLongClickListener(v -> {
                    ClipData data = ClipData.newPlainText("instance", String.valueOf(index));
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        v.startDragAndDrop(data, shadow, v, 0);
                    } else {
                        v.startDrag(data, shadow, v, 0);
                    }
                    v.setAlpha(0.5f);
                    updateDynamicIsland("Drag to reorder…");
                    return true;
                });

                card.setOnDragListener((v, event) -> handleCardDrag(v, event));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                params.setMarginEnd(16);
                mInstancesContainer.addView(card, params);
            }

            // Container also accepts drops at ends
            mInstancesContainer.setOnDragListener((v, event) -> handleCardDrag(v, event));

            if (selectedInstance != null) {
                updateDynamicIsland("VL  •  " + (selectedInstance.name != null ? selectedInstance.name : "Ready"));
            }

        } catch (IOException e) {
            Log.e("VLHome", "Failed to load instances", e);
            updateDynamicIsland("VL  •  Error loading");
            if (mEmptyState != null) mEmptyState.setVisibility(View.VISIBLE);
            if (mInstancesScroll != null) mInstancesScroll.setVisibility(View.GONE);
            Toast.makeText(requireContext(), "Failed to load instances", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean handleCardDrag(View v, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (v != event.getLocalState()) v.setAlpha(0.7f);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                if (v != event.getLocalState()) v.setAlpha(0.9f);
                return true;
            case DragEvent.ACTION_DROP:
                View dragged = (View) event.getLocalState();
                if (dragged == null || mInstancesContainer == null) return false;
                int from = mInstancesContainer.indexOfChild(dragged);
                int to = (v == mInstancesContainer) ? mInstancesContainer.getChildCount() - 1
                        : mInstancesContainer.indexOfChild(v);
                if (from < 0 || to < 0 || from == to) return false;
                mInstancesContainer.removeView(dragged);
                mInstancesContainer.addView(dragged, Math.min(to, mInstancesContainer.getChildCount()));
                saveCurrentOrder();
                dragged.setAlpha(1f);
                updateDynamicIsland("Order saved");
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                View local = (View) event.getLocalState();
                if (local != null) local.setAlpha(1f);
                // Restore alphas
                if (mInstancesContainer != null) {
                    for (int i = 0; i < mInstancesContainer.getChildCount(); i++) {
                        mInstancesContainer.getChildAt(i).setAlpha(0.9f);
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private void applySavedOrder(List<DisplayInstance> list) {
        if (LauncherPreferences.DEFAULT_PREF == null || list == null || list.isEmpty()) return;
        String saved = LauncherPreferences.DEFAULT_PREF.getString(PREF_INSTANCE_ORDER, null);
        if (saved == null || saved.isEmpty()) return;

        List<String> order = Arrays.asList(saved.split("\u0001"));
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);

        Collections.sort(list, (a, b) -> {
            String na = a.name != null ? a.name : "";
            String nb = b.name != null ? b.name : "";
            // favorites-first
            boolean fa = isFavorite(na);
            boolean fb = isFavorite(nb);
            if (fa != fb) return fa ? -1 : 1;
            // last played next
            String last = LauncherPreferences.DEFAULT_PREF != null
                    ? LauncherPreferences.DEFAULT_PREF.getString("vl_last_played", "") : "";
            if (last != null && !last.isEmpty()) {
                if (last.equals(na) && !last.equals(nb)) return -1;
                if (last.equals(nb) && !last.equals(na)) return 1;
            }
            int ra = rank.containsKey(na) ? rank.get(na) : Integer.MAX_VALUE;
            int rb = rank.containsKey(nb) ? rank.get(nb) : Integer.MAX_VALUE;
            if (ra != rb) return Integer.compare(ra, rb);
            return na.compareToIgnoreCase(nb);
        });
    }

    private void saveCurrentOrder() {
        if (mInstancesContainer == null || LauncherPreferences.DEFAULT_PREF == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mInstancesContainer.getChildCount(); i++) {
            Object tag = mInstancesContainer.getChildAt(i).getTag();
            if (tag != null) {
                if (sb.length() > 0) sb.append('\u0001');
                sb.append(tag.toString());
            }
        }
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_INSTANCE_ORDER, sb.toString())
                .apply();
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        refreshInstanceCards();
        if (getView() != null) {
            updateHomeSkinPreview(getView());
            applyCustomTheme(getView());
        }
        if (!mProgressActive) {
            updateDynamicIsland(mIdleIslandText);
        }
    }

    @Override
    public void onDestroyView() {
        unregisterProgressListeners();
        super.onDestroyView();
    }
}
