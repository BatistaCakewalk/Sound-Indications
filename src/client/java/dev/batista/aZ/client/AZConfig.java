package dev.batista.aZ.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config stored at config/a-z.json.
 * Works without Mod Menu or YACL; they only provide the in-game screen.
 */
public class AZConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("a-z.json");
    private static AZConfig instance = new AZConfig();

    // ---- Toggles ----
    public boolean hungerEnabled = true;
    public boolean digestionEnabled = false;
    public boolean lowHpEnabled = true;
    /** Replace the low health sound with the anxiety song (also fades out Minecraft's music). */
    public boolean useAnxietySong = false;
    /** Muffle world audio with a low-pass filter while health is low. */
    public boolean lowHpMuffleAudio = true;
    /** Muffle world audio with a low-pass filter while submerged underwater. */
    public boolean underwaterMuffleAudio = true;
    /** Muffle Minecraft's background music along with world audio. */
    public boolean muffleMusic = true;
    /** Keep UI sounds (inventory clicks, etc.) unmuffled while health is low or underwater. */
    public boolean keepUIUnmuffled = true;
    /** Lowest high-frequency gain cutoff for low-pass filter at critical health (0.05 - 0.50). */
    public float lowHpMuffleCutoff = 0.5f;
    /** Lowest high-frequency gain cutoff for low-pass filter when submerged underwater (0.05 - 0.50). */
    public float underwaterMuffleCutoff = 0.5f;
    /** Pulsing red vignette overlay on the screen edges when health is low. */
    public boolean lowHpVignette = true;
    /** Flashes a red vignette overlay for ~1 second when taking damage. */
    public boolean damageVignette = true;
    /** Maximum intensity of the red vignette overlays (0.1 - 1.0). */
    public float lowHpVignetteIntensity = 1.0f;

    // ---- Thresholds ----
    /** Hunger bars (1 bar = 2 food points). Sound plays at or below this. */
    public int hungerThresholdBars = 3;
    /** Hearts (1 heart = 2 health). Sound plays at or below this. */
    public float lowHpThresholdHearts = 3.0f;

    // ---- Volumes (0.0 - 1.0) ----
    public float hungerVolume = 0.15f;
    public float digestionVolume = 0.10f;
    public float lowHpVolume = 1.0f;
    public float anxietyVolume = 1.0f;

    // ---- Timing (seconds) ----
    public int hungerMinGapSeconds = 5;
    public int hungerMaxGapSeconds = 20;
    public int digestionCooldownSeconds = 30;

    public static AZConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                AZConfig loaded = GSON.fromJson(reader, AZConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception e) {
                System.err.println("[IndicationSounds] Could not read config, using defaults: " + e);
            }
        }
        save(); // writes defaults and any newly added fields
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            System.err.println("[IndicationSounds] Could not save config: " + e);
        }
    }
}
