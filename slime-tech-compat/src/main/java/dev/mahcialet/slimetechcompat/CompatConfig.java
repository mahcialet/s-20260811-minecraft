package dev.mahcialet.slimetechcompat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;

/** Server-side policy. No registry, recipes, or user files are modified except this config. */
public final class CompatConfig {
    public record Settings(boolean blockNonWaterWorldSources, boolean fixGrinderEnergy,
                           int maxTankNetworkMembers) { }
    private static volatile Settings settings;
    private static final String DEFAULT_FILE = """
            # Slime Tech Compat 0.4.0-dev -- restart after changing this file.
            # Keep false to preserve existing lava/mod-fluid source pumps.
            # true: refuse non-water source blocks; does NOT implement consuming pumps.
            blockNonWaterWorldSources=false
            # true: new grinders start empty; processing requires the full 120 FE.
            # Existing saved charge and the 120 FE/completion cost are preserved.
            fixGrinderEnergy=false
            # Oversized or partially unloaded connected tanks are blocked, not truncated.
            maxTankNetworkMembers=512
            """;
    private CompatConfig() { }
    public static Settings get() {
        Settings s = settings;
        if (s == null) {
            synchronized (CompatConfig.class) {
                if (settings == null) settings = load(FMLPaths.CONFIGDIR.get().resolve("slime-tech-compat.properties"));
                s = settings;
            }
        }
        return s;
    }
    public static Settings load(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            if (!Files.exists(path)) {
                Files.writeString(path, DEFAULT_FILE, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); }
            Set<String> valid = Set.of("blockNonWaterWorldSources", "fixGrinderEnergy", "maxTankNetworkMembers");
            for (String key : p.stringPropertyNames()) {
                if (!valid.contains(key)) throw new IllegalArgumentException("Unknown config key: " + key);
            }
            int limit = Integer.parseInt(p.getProperty("maxTankNetworkMembers", "512").trim());
            if (limit < 1 || limit > 4096) throw new IllegalArgumentException("maxTankNetworkMembers must be 1..4096");
            return new Settings(readBoolean(p, "blockNonWaterWorldSources"),
                    readBoolean(p, "fixGrinderEnergy"), limit);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid/unreadable Slime Tech Compat configuration: " + path, e);
        }
    }
    private static boolean readBoolean(Properties p, String name) {
        String v = p.getProperty(name, "false").trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(name + " must be true or false");
    }
}
