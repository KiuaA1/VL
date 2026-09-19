package net.kdt.pojavlaunch.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Scans instance mods folder and reports missing required dependencies
 * from Fabric / Quilt (fabric.mod.json / quilt.mod.json) and Forge/NeoForge (mods.toml).
 */
public final class VLModDependencyChecker {

    public static final class ModInfo {
        public final String fileName;
        public final String modId;
        public final List<String> requiredIds = new ArrayList<>();

        public ModInfo(String fileName, String modId) {
            this.fileName = fileName;
            this.modId = modId;
        }
    }

    public static final class Report {
        public final List<ModInfo> mods = new ArrayList<>();
        public final Map<String, Set<String>> missing = new HashMap<>(); // missingId -> set of dependents
        public final List<String> errors = new ArrayList<>();
        public boolean hasMissing() {
            return !missing.isEmpty();
        }
    }

    private VLModDependencyChecker() {}

    public static Report check(File modsDir) {
        Report report = new Report();
        if (modsDir == null || !modsDir.isDirectory()) {
            report.errors.add("Mods folder not found");
            return report;
        }

        File[] files = modsDir.listFiles();
        if (files == null || files.length == 0) {
            report.errors.add("No mods installed");
            return report;
        }

        Map<String, String> idToFile = new HashMap<>();
        Set<String> installedIds = new HashSet<>();

        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name.endsWith(".disabled")) continue; // disabled mods don't satisfy deps
            if (!(name.endsWith(".jar") || name.endsWith(".zip"))) continue;

            try {
                ModInfo info = readModInfo(f);
                if (info == null) continue;
                report.mods.add(info);
                if (info.modId != null && !info.modId.isEmpty()) {
                    String id = info.modId.toLowerCase(Locale.ROOT);
                    installedIds.add(id);
                    idToFile.put(id, f.getName());
                }
            } catch (Exception e) {
                report.errors.add(f.getName() + ": " + e.getMessage());
            }
        }

        // Always present soft ids that games provide
        Set<String> provided = new HashSet<>(installedIds);
        provided.add("minecraft");
        provided.add("java");
        provided.add("fabricloader");
        provided.add("fabric-loader");
        provided.add("quilt_loader");
        provided.add("quilt-loader");
        provided.add("forge");
        provided.add("neoforge");
        provided.add("fabric");
        provided.add("fabric-api");

        for (ModInfo info : report.mods) {
            for (String dep : info.requiredIds) {
                String id = dep.toLowerCase(Locale.ROOT);
                if (id.isEmpty()) continue;
                if (provided.contains(id)) continue;
                // fabric nested ids sometimes use fabric-api modules – soft skip fabric-* if any fabric-api present
                if (id.startsWith("fabric-") && (provided.contains("fabric-api") || provided.contains("fabric"))) {
                    continue;
                }
                report.missing.computeIfAbsent(id, k -> new HashSet<>()).add(
                        info.modId != null ? info.modId : info.fileName);
            }
        }
        return report;
    }

    public static String formatReport(Report report) {
        StringBuilder sb = new StringBuilder();
        if (report.hasMissing()) {
            sb.append("Missing dependencies:\n\n");
            List<String> keys = new ArrayList<>(report.missing.keySet());
            Collections.sort(keys);
            for (String miss : keys) {
                Set<String> by = report.missing.get(miss);
                sb.append("• ").append(miss).append("\n");
                sb.append("  needed by: ").append(String.join(", ", by)).append("\n\n");
            }
            sb.append("Install the missing mods (Store or folder), then check again.");
        } else if (!report.errors.isEmpty() && report.mods.isEmpty()) {
            for (String e : report.errors) sb.append(e).append('\n');
        } else {
            sb.append("All required dependencies look present.\n");
            sb.append("Checked ").append(report.mods.size()).append(" mod(s).");
        }
        if (!report.errors.isEmpty() && report.hasMissing()) {
            sb.append("\n\nNotes:\n");
            for (String e : report.errors) sb.append("- ").append(e).append('\n');
        }
        return sb.toString().trim();
    }

    private static ModInfo readModInfo(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            // Fabric / Quilt
            ZipEntry fabric = jar.getEntry("fabric.mod.json");
            if (fabric == null) fabric = jar.getEntry("quilt.mod.json");
            if (fabric != null) {
                String json = readEntry(jar, fabric);
                return parseFabricLike(jarFile.getName(), json);
            }
            // Forge / NeoForge
            ZipEntry toml = jar.getEntry("META-INF/mods.toml");
            if (toml != null) {
                String text = readEntry(jar, toml);
                return parseModsToml(jarFile.getName(), text);
            }
        }
        return null;
    }

    private static String readEntry(JarFile jar, ZipEntry entry) throws Exception {
        try (InputStream in = jar.getInputStream(entry);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static ModInfo parseFabricLike(String fileName, String json) {
        JsonObject obj = Tools.GLOBAL_GSON.fromJson(json, JsonObject.class);
        if (obj == null) return null;
        String id = obj.has("id") ? obj.get("id").getAsString() : fileName;
        ModInfo info = new ModInfo(fileName, id);
        // depends + breaks not required; only depends are hard requirements
        if (obj.has("depends") && obj.get("depends").isJsonObject()) {
            JsonObject depends = obj.getAsJsonObject("depends");
            for (Map.Entry<String, JsonElement> e : depends.entrySet()) {
                info.requiredIds.add(e.getKey());
            }
        }
        return info;
    }

    private static ModInfo parseModsToml(String fileName, String text) {
        String modId = null;
        List<String> required = new ArrayList<>();
        String currentSection = "";
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.startsWith("[")) {
                currentSection = line;
                continue;
            }
            if (currentSection.contains("mods") && !currentSection.contains("dependencies")
                    && line.startsWith("modId")) {
                modId = unquote(valueOf(line));
            }
            if (currentSection.contains("dependencies") && line.startsWith("modId")) {
                String depId = unquote(valueOf(line));
                // mandatory defaults true in forge; we treat all listed as required unless mandatory=false later
                required.add(depId);
            }
            if (currentSection.contains("dependencies") && line.startsWith("mandatory")) {
                String v = unquote(valueOf(line));
                if ("false".equalsIgnoreCase(v) && !required.isEmpty()) {
                    required.remove(required.size() - 1);
                }
            }
        }
        if (modId == null) modId = fileName;
        ModInfo info = new ModInfo(fileName, modId);
        info.requiredIds.addAll(required);
        return info;
    }

    private static String valueOf(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) return "";
        return line.substring(eq + 1).trim();
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
