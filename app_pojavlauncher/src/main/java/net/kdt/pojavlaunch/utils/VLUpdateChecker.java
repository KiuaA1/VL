package net.kdt.pojavlaunch.utils;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import git.artdeell.mojo.BuildConfig;

/**
 * Checks GitHub Releases for a newer VL version.
 */
public final class VLUpdateChecker {
    private static final String TAG = "VLUpdateChecker";

    public static final class UpdateInfo {
        public final boolean updateAvailable;
        public final String latestTag;
        public final String releaseUrl;
        public final String apkUrl;
        public final String body;

        public UpdateInfo(boolean updateAvailable, String latestTag, String releaseUrl, String apkUrl, String body) {
            this.updateAvailable = updateAvailable;
            this.latestTag = latestTag;
            this.releaseUrl = releaseUrl;
            this.apkUrl = apkUrl;
            this.body = body;
        }

        public static UpdateInfo none() {
            return new UpdateInfo(false, null, null, null, null);
        }
    }

    private VLUpdateChecker() {}

    public static boolean isCheckEnabled() {
        return LauncherPreferences.DEFAULT_PREF == null
                || LauncherPreferences.DEFAULT_PREF.getBoolean("vl_check_updates", true);
    }

    public static String getOwner() {
        if (LauncherPreferences.DEFAULT_PREF == null) return "kiua";
        return LauncherPreferences.DEFAULT_PREF.getString("vl_github_owner", "kiua");
    }

    public static String getRepo() {
        if (LauncherPreferences.DEFAULT_PREF == null) return "VL";
        return LauncherPreferences.DEFAULT_PREF.getString("vl_github_repo", "VL");
    }

    public static UpdateInfo checkForUpdate() {
        if (!isCheckEnabled()) return UpdateInfo.none();
        try {
            String owner = getOwner();
            String repo = getRepo();
            String api = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
            HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setRequestProperty("User-Agent", "VL-Launcher");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GitHub releases HTTP " + code);
                return UpdateInfo.none();
            }
            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
            conn.disconnect();

            String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
            String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : null;
            String body = json.has("body") ? json.get("body").getAsString() : null;
            String apkUrl = null;
            if (json.has("assets") && json.get("assets").isJsonArray()) {
                for (JsonElement a : json.getAsJsonArray("assets")) {
                    JsonObject asset = a.getAsJsonObject();
                    String name = asset.has("name") ? asset.get("name").getAsString() : "";
                    String url = asset.has("browser_download_url")
                            ? asset.get("browser_download_url").getAsString() : null;
                    if (url != null && name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        apkUrl = url;
                        break;
                    }
                }
            }
            if (tag == null) return UpdateInfo.none();
            String current = safeVersion(BuildConfig.VERSION_NAME);
            String latest = safeVersion(tag);
            boolean newer = isNewer(latest, current);
            return new UpdateInfo(newer, tag, htmlUrl, apkUrl, body);
        } catch (Exception e) {
            Log.w(TAG, "Update check failed", e);
            return UpdateInfo.none();
        }
    }

    public static final class UpdateInfo {
        public final boolean updateAvailable;
        public final String latestTag;
        public final String releaseUrl;
        public final String apkUrl;
        public final String body;

        public UpdateInfo(boolean updateAvailable, String latestTag, String releaseUrl, String apkUrl, String body) {
            this.updateAvailable = updateAvailable;
            this.latestTag = latestTag;
            this.releaseUrl = releaseUrl;
            this.apkUrl = apkUrl;
            this.body = body;
        }

        public static UpdateInfo none() {
            return new UpdateInfo(false, null, null, null, null);
        }
    }

    private static final String TAG = "VLUpdateChecker";

    private static String safeVersion(String v) {
        if (v == null) return "0";
        return v.trim().replaceFirst("^[vV]", "");
    }

    /** Simple dotted numeric compare: 1.2.3 > 1.2.0 */
    private static boolean isNewer(String latest, String current) {
        try {
            String[] la = latest.split("[^0-9]+");
            String[] cu = current.split("[^0-9]+");
            int len = Math.max(la.length, cu.length);
            for (int i = 0; i < len; i++) {
                int li = i < la.length && !la[i].isEmpty() ? Integer.parseInt(la[i]) : 0;
                int ci = i < cu.length && !cu[i].isEmpty() ? Integer.parseInt(cu[i]) : 0;
                if (li > ci) return true;
                if (li < ci) return false;
            }
        } catch (Exception ignored) {
            return !latest.equals(current) && latest.compareTo(current) > 0;
        }
        return false;
    }
}
