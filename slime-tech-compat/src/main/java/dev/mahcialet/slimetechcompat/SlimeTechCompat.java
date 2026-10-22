package dev.mahcialet.slimetechcompat;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod("slime_tech_compat")
public final class SlimeTechCompat {
    public static final String TARGET_SHA256 = "f8ea80558f7f229314fa026311c194aa956f773bc235a76d7598b0a26d3030bb";
    public SlimeTechCompat() {
        verifyTarget();
        CompatConfig.get();
        System.getLogger("SlimeTechCompat").log(System.Logger.Level.INFO,
                "Slime Tech Compat 0.4.0-dev loaded; target SHA-256 verified. Back up before migration.");
    }
    private static void verifyTarget() {
        try {
            var path = ModList.get().getModFileById("slime_tech").getFile().getFilePath();
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(TARGET_SHA256)) throw new IllegalStateException(
                    "Unsupported Slime Tech JAR SHA-256: " + actual + "; expected " + TARGET_SHA256);
        } catch (Exception failure) {
            throw new IllegalStateException("Slime Tech Compat requires the audited slime_tech-0.1.44.jar; refusing world load", failure);
        }
    }
}
