package net.kdt.pojavlaunch.utils;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Best-effort auto-download of mods from Modrinth by project slug / mod id.
 */
public final class VLModAutoInstaller {
    private static final String TAG = "VLModAutoInstaller";

    public static final class Result {
        public final List<String> installed = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    private VLModAutoInstaller() {}

    public static Result installMissing(File modsDir, Collection<String> modIds, String mcVersion) {
        Result result = new Result();
        if (modsDir == null) {
            result.failed.add("(no mods folder)");
            return result;
        }
        //noinspection ResultOfMethodCallIgnored
        modsDir.mkdirs();
        for (String id : modIds) {
            if (id == null || id.isEmpty()) continue;
            try {
                if (downloadOne(modsDir, id.trim(), mcVersion)) {
                    result.installed.add(id);
                } else {
                    result.failed.add(id);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to install " + id, e);
                result.failed.add(id);
            }
        }
        return result;
    }

    private static boolean downloadOne(File modsDir, String query, String mcVersion) throws Exception {
        String facets = "[[\"project_type:mod\"]]";
        String searchUrl = "https://api.modrinth.com/v2/search?limit=5&query="
                + URLEncoder.encode(query, "UTF-8")
                + "&facets=" + URLEncoder.encode(facets, "UTF-8");
        JsonObject search = httpGetJson(searchUrl).getAsJsonObject();
        JsonArray hits = search.getAsJsonArray("hits");
        if (hits == null || hits.size() == 0) return false;

        String projectId = null;
        for (JsonElement el : hits) {
            JsonObject hit = el.getAsJsonObject();
            String slug = hit.has("slug") ? hit.get("slug").getAsString() : "";
            String pid = hit.has("project_id") ? hit.get("project_id").getAsString() : "";
            if (query.equalsIgnoreCase(slug) || query.equalsIgnoreCase(pid)) {
                projectId = pid;
                break;
            }
        }
        if (projectId == null) {
            projectId = hits.get(0).getAsJsonObject().get("project_id").getAsString();
        }

        String verUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
        JsonArray versions = httpGetJson(verUrl).getAsJsonArray();
        if (versions == null || versions.size() == 0) return false;

        JsonObject chosen = null;
        for (JsonElement el : versions) {
            JsonObject v = el.getAsJsonObject();
            if (mcVersion != null && !mcVersion.isEmpty() && v.has("game_versions")) {
                boolean match = false;
                for (JsonElement gv : v.getAsJsonArray("game_versions")) {
                    if (mcVersion.equals(gv.getAsString())) {
                        match = true;
                        break;
                    }
                }
                if (!match) continue;
            }
            chosen = v;
            break;
        }
        if (chosen == null) chosen = versions.get(0).getAsJsonObject();

        JsonArray files = chosen.getAsJsonArray("files");
        if (files == null || files.size() == 0) return false;
        JsonObject primary = files.get(0).getAsJsonObject();
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) {
                primary = f;
                break;
            }
        }
        String fileUrl = primary.get("url").getAsString();
        String fileName = primary.get("filename").getAsString();
        File out = new File(modsDir, fileName);
        if (out.exists()) return true;

        downloadFile(fileUrl, out);
        return out.exists() && out.length() > 0;
    }

    private static JsonElement httpGetJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "VL-Launcher");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } finally {
            conn.disconnect();
        }
    }

    private static JsonElement getJson(String urlStr) throws Exception {
        return httpGetJson(urlStr);
    }

    private static void downloadFile(String urlStr, File out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "VL-Launcher");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
        } finally {
            conn.disconnect();
        }
    }
}
