package dev.batista.aZ.client;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.utils.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** YACL settings screen. Only loaded when YACL is present. */
public final class AZConfigScreen {

    private AZConfigScreen() {}

    public static Screen create(Screen parent) {
        AZConfig defaults = new AZConfig();
        AZConfig cfg = AZConfig.get();
        boolean directionalAudioEnabled = Minecraft.getInstance().options.directionalAudio().get();
        List<Option<?>> allOptions = new ArrayList<>();

        // Audio
        Option<Boolean> hunger = toggle("Hunger sounds", "Play stomach growls while hungry.",
                defaults.hungerEnabled, () -> cfg.hungerEnabled, v -> cfg.hungerEnabled = v);
        Option<Boolean> digestion = toggle("Digestion sounds",
                "Play ambient digestive audio when your body burns saturation. (Why? Why not!)",
                defaults.digestionEnabled, () -> cfg.digestionEnabled, v -> cfg.digestionEnabled = v);
        Option<Boolean> digestionHungerCutoff = toggle("Stop digestion sounds at low hunger",
                "Stop digestion sounds when the hunger bar reaches the configured cutoff or lower.",
                defaults.digestionHungerCutoffEnabled, () -> cfg.digestionHungerCutoffEnabled,
                v -> cfg.digestionHungerCutoffEnabled = v);
        Option<Boolean> digestionWaitForRefill = toggle("Wait for refill after joining a world",
                "Keep digestion sounds silent after joining until you eat and replenish hunger or saturation.",
                defaults.digestionWaitForRefill, () -> cfg.digestionWaitForRefill,
                v -> cfg.digestionWaitForRefill = v);
        Option<Boolean> stomachPosition = toggle("Accurately place stomach sounds",
                "Play hunger and digestion sounds from the player's abdomen. Requires Minecraft's Directional Audio setting.",
                defaults.accuratelyPlaceStomachSounds, () -> cfg.accuratelyPlaceStomachSounds,
                v -> cfg.accuratelyPlaceStomachSounds = v);
        stomachPosition.setAvailable(directionalAudioEnabled);
        Option<Boolean> caveAmbience = toggle("Cave ambience",
                "Play quiet or loud ambient beds in enclosed, low-light Overworld caves.",
                defaults.caveAmbienceEnabled, () -> cfg.caveAmbienceEnabled, v -> cfg.caveAmbienceEnabled = v);
        Option<Boolean> caveEvents = toggle("Random cave sounds",
                "Occasionally play short ambience, distant tribal or horror sounds, and water drops while in caves.",
                defaults.caveEventsEnabled, () -> cfg.caveEventsEnabled, v -> cfg.caveEventsEnabled = v);
        Option<Boolean> lowHealth = toggle("Low-health sound", "Play an audio indication when your health is low.",
                defaults.lowHpEnabled, () -> cfg.lowHpEnabled, v -> cfg.lowHpEnabled = v);
        Option<Boolean> anxiety = toggle("Use anxiety music",
                "Use the anxiety song instead of the regular low-health sound, fading Minecraft music until your health recovers.",
                defaults.useAnxietySong, () -> cfg.useAnxietySong, v -> cfg.useAnxietySong = v);
        Option<Boolean> lowHealthMuffle = toggle("Muffle audio at low health",
                "Apply a low-pass filter to world audio while your health is critical.",
                defaults.lowHpMuffleAudio, () -> cfg.lowHpMuffleAudio, v -> cfg.lowHpMuffleAudio = v);
        Option<Boolean> underwaterMuffle = toggle("Muffle audio underwater",
                "Apply a low-pass filter to world audio while submerged.",
                defaults.underwaterMuffleAudio, () -> cfg.underwaterMuffleAudio, v -> cfg.underwaterMuffleAudio = v);
        Option<Boolean> muffleMusic = toggle("Muffle background music",
                "Include Minecraft's background music when audio muffling is active.",
                defaults.muffleMusic, () -> cfg.muffleMusic, v -> cfg.muffleMusic = v);
        Option<Boolean> keepUi = toggle("Keep UI sounds unmuffled",
                "Keep inventory clicks and other interface sounds crisp while audio muffling is active.",
                defaults.keepUIUnmuffled, () -> cfg.keepUIUnmuffled, v -> cfg.keepUIUnmuffled = v);

        List<Option<?>> audioOptions = List.of(hunger, digestion, digestionHungerCutoff,
                digestionWaitForRefill, stomachPosition, caveAmbience, caveEvents,
                lowHealth, anxiety, lowHealthMuffle, underwaterMuffle, muffleMusic, keepUi);
        allOptions.addAll(audioOptions);

        bindAvailability(() -> lowHealthMuffle.pendingValue() || underwaterMuffle.pendingValue(),
                List.of(muffleMusic, keepUi), lowHealthMuffle, underwaterMuffle);
        bindAvailability(digestion::pendingValue,
                List.of(digestionHungerCutoff, digestionWaitForRefill), digestion);
        bindAvailability(lowHealth::pendingValue, List.of(anxiety), lowHealth);

        ConfigCategory.Builder audioCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Audio"))
                .option(LabelOption.create(Component.literal("\u2014 HUNGER & DIGESTION \u2014")))
                .option(hunger)
                .option(digestion)
                .option(digestionHungerCutoff)
                .option(digestionWaitForRefill)
                .option(stomachPosition)
                .option(LabelOption.create(Component.literal("\u2014 CAVE ENVIRONMENT \u2014")))
                .option(caveAmbience)
                .option(caveEvents)
                .option(LabelOption.create(Component.literal("\u2014 LOW-HEALTH AUDIO \u2014")))
                .option(lowHealth)
                .option(anxiety)
                .option(LabelOption.create(Component.literal("\u2014 ENVIRONMENTAL FILTERING \u2014")))
                .option(lowHealthMuffle)
                .option(underwaterMuffle)
                .option(muffleMusic)
                .option(keepUi);
        addCategoryReset(audioCategory, audioOptions);

        // Volume & Mixing
        Option<Float> hungerVolume = floatSlider("Hunger volume", "Volume of hunger growls.",
                defaults.hungerVolume, 0f, 1f, 0.01f, true,
                () -> cfg.hungerVolume, v -> cfg.hungerVolume = v);
        Option<Float> digestionVolume = floatSlider("Digestion volume", "Volume of digestion audio.",
                defaults.digestionVolume, 0f, 1f, 0.01f, true,
                () -> cfg.digestionVolume, v -> cfg.digestionVolume = v);
        Option<Float> lowHealthVolume = floatSlider("Low-health volume", "Volume of the regular low-health sound.",
                defaults.lowHpVolume, 0f, 1f, 0.01f, true,
                () -> cfg.lowHpVolume, v -> cfg.lowHpVolume = v);
        Option<Float> anxietyVolume = floatSlider("Anxiety music volume", "Volume of the anxiety song.",
                defaults.anxietyVolume, 0f, 1f, 0.01f, true,
                () -> cfg.anxietyVolume, v -> cfg.anxietyVolume = v);
        Option<Float> caveAmbienceVolume = floatSlider("Cave ambience volume",
                "Volume of the continuous cave ambience beds.",
                defaults.caveAmbienceVolume, 0f, 1f, 0.01f, true,
                () -> cfg.caveAmbienceVolume, v -> cfg.caveAmbienceVolume = v);
        Option<Float> caveEventsVolume = floatSlider("Cave event volume",
                "Volume of occasional cave sounds.",
                defaults.caveEventsVolume, 0f, 1f, 0.01f, true,
                () -> cfg.caveEventsVolume, v -> cfg.caveEventsVolume = v);

        List<Option<?>> volumeOptions = List.of(hungerVolume, digestionVolume, lowHealthVolume,
                anxietyVolume, caveAmbienceVolume, caveEventsVolume);
        allOptions.addAll(volumeOptions);
        bindAvailability(hunger::pendingValue, List.of(hungerVolume), hunger);
        bindAvailability(digestion::pendingValue, List.of(digestionVolume), digestion);
        bindAvailability(() -> lowHealth.pendingValue() && !anxiety.pendingValue(), List.of(lowHealthVolume), lowHealth, anxiety);
        bindAvailability(() -> lowHealth.pendingValue() && anxiety.pendingValue(), List.of(anxietyVolume), lowHealth, anxiety);
        bindAvailability(caveAmbience::pendingValue, List.of(caveAmbienceVolume), caveAmbience);
        bindAvailability(caveEvents::pendingValue, List.of(caveEventsVolume), caveEvents);

        ConfigCategory.Builder volumeCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Volume & Mixing"))
                .option(LabelOption.create(Component.literal("\u2014 HUNGER & DIGESTION \u2014")))
                .option(hungerVolume)
                .option(digestionVolume)
                .option(LabelOption.create(Component.literal("\u2014 LOW-HEALTH AUDIO \u2014")))
                .option(lowHealthVolume)
                .option(anxietyVolume)
                .option(LabelOption.create(Component.literal("\u2014 CAVE ENVIRONMENT \u2014")))
                .option(caveAmbienceVolume)
                .option(caveEventsVolume);
        addCategoryReset(volumeCategory, volumeOptions);

        // Visual Effects
        Option<Boolean> lowHealthVignette = toggle("Low-health vignette",
                "Display a pulsing red vignette around the screen while health is low.",
                defaults.lowHpVignette, () -> cfg.lowHpVignette, v -> cfg.lowHpVignette = v);
        Option<Boolean> damageVignette = toggle("Damage vignette flash",
                "Flash a red vignette briefly whenever you take damage.",
                defaults.damageVignette, () -> cfg.damageVignette, v -> cfg.damageVignette = v);
        Option<Float> vignetteIntensity = floatSlider("Vignette intensity",
                "Maximum opacity of the low-health and damage vignette effects.",
                defaults.lowHpVignetteIntensity, 0.1f, 1f, 0.01f, true,
                () -> cfg.lowHpVignetteIntensity, v -> cfg.lowHpVignetteIntensity = v);

        List<Option<?>> visualOptions = List.of(lowHealthVignette, damageVignette, vignetteIntensity);
        allOptions.addAll(visualOptions);
        bindAvailability(() -> lowHealthVignette.pendingValue() || damageVignette.pendingValue(),
                List.of(vignetteIntensity), lowHealthVignette, damageVignette);

        ConfigCategory.Builder visualsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Visual Effects"))
                .option(LabelOption.create(Component.literal("\u2014 SCREEN EFFECTS \u2014")))
                .option(lowHealthVignette)
                .option(damageVignette)
                .option(LabelOption.create(Component.literal("\u2014 INTENSITY \u2014")))
                .option(vignetteIntensity);
        addCategoryReset(visualsCategory, visualOptions);

        // Triggers & Thresholds
        Option<Integer> hungerThreshold = intField("Hunger threshold (bars)",
                "Play hunger sounds at or below this many hunger bars.",
                defaults.hungerThresholdBars, 1, 10,
                () -> cfg.hungerThresholdBars, v -> cfg.hungerThresholdBars = v);
        Option<Integer> digestionHungerThreshold = intField("Digestion hunger cutoff (bars)",
                "Stop digestion sounds at or below this many hunger bars. The default is 8 bars.",
                defaults.digestionHungerThresholdBars, 1, 10,
                () -> cfg.digestionHungerThresholdBars, v -> cfg.digestionHungerThresholdBars = v);
        Option<Float> lowHealthThreshold = floatField("Low-health threshold (hearts)",
                "Trigger low-health audio at or below this many hearts. Half-heart precision is supported.",
                defaults.lowHpThresholdHearts, 0.5f, 10f,
                () -> cfg.lowHpThresholdHearts, v -> cfg.lowHpThresholdHearts = v);

        List<Option<?>> thresholdOptions = List.of(hungerThreshold, digestionHungerThreshold, lowHealthThreshold);
        allOptions.addAll(thresholdOptions);
        bindAvailability(hunger::pendingValue, List.of(hungerThreshold), hunger);
        bindAvailability(() -> digestion.pendingValue() && digestionHungerCutoff.pendingValue(),
                List.of(digestionHungerThreshold), digestion, digestionHungerCutoff);
        bindAvailability(lowHealth::pendingValue, List.of(lowHealthThreshold), lowHealth);

        ConfigCategory.Builder thresholdsCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Triggers & Thresholds"))
                .option(LabelOption.create(Component.literal("\u2014 HUNGER \u2014")))
                .option(hungerThreshold)
                .option(LabelOption.create(Component.literal("\u2014 DIGESTION \u2014")))
                .option(digestionHungerThreshold)
                .option(LabelOption.create(Component.literal("\u2014 HEALTH \u2014")))
                .option(lowHealthThreshold);
        addCategoryReset(thresholdsCategory, thresholdOptions);

        // Intervals & Timing
        Option<Integer> hungerMinGap = intField("Minimum hunger interval (seconds)",
                "Shortest permitted wait between hunger sounds.", defaults.hungerMinGapSeconds, 1, 120,
                () -> cfg.hungerMinGapSeconds, v -> cfg.hungerMinGapSeconds = v);
        Option<Integer> hungerMaxGap = intField("Maximum hunger interval (seconds)",
                "Longest permitted wait between hunger sounds.", defaults.hungerMaxGapSeconds, 1, 120,
                () -> cfg.hungerMaxGapSeconds, v -> cfg.hungerMaxGapSeconds = v);
        Option<Integer> digestionCooldown = intField("Digestion cooldown (seconds)",
                "Minimum time between digestion sounds.", defaults.digestionCooldownSeconds, 1, 300,
                () -> cfg.digestionCooldownSeconds, v -> cfg.digestionCooldownSeconds = v);

        List<Option<?>> timingOptions = List.of(hungerMinGap, hungerMaxGap, digestionCooldown);
        allOptions.addAll(timingOptions);
        bindAvailability(hunger::pendingValue, List.of(hungerMinGap, hungerMaxGap), hunger);
        bindAvailability(digestion::pendingValue, List.of(digestionCooldown), digestion);

        // Keep the interval pair valid while editing, not only when saving.
        hungerMinGap.addEventListener((option, event) -> {
            if (hungerMinGap.pendingValue() > hungerMaxGap.pendingValue()) {
                hungerMaxGap.requestSet(hungerMinGap.pendingValue());
            }
        });
        hungerMaxGap.addEventListener((option, event) -> {
            if (hungerMaxGap.pendingValue() < hungerMinGap.pendingValue()) {
                hungerMinGap.requestSet(hungerMaxGap.pendingValue());
            }
        });

        ConfigCategory.Builder timingCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Intervals & Timing"))
                .option(LabelOption.create(Component.literal("\u2014 HUNGER SOUNDS \u2014")))
                .option(hungerMinGap)
                .option(hungerMaxGap)
                .option(LabelOption.create(Component.literal("\u2014 DIGESTION SOUNDS \u2014")))
                .option(digestionCooldown);
        addCategoryReset(timingCategory, timingOptions);

        // Advanced Audio
        Option<Float> lowHealthCutoff = floatSlider("Low-health muffle cutoff",
                "High-frequency gain at critical health. Lower values produce stronger muffling.",
                defaults.lowHpMuffleCutoff, 0.05f, 0.50f, 0.01f, false,
                () -> cfg.lowHpMuffleCutoff, v -> cfg.lowHpMuffleCutoff = v);
        Option<Float> underwaterCutoff = floatSlider("Underwater muffle cutoff",
                "High-frequency gain underwater. Lower values produce stronger muffling.",
                defaults.underwaterMuffleCutoff, 0.05f, 0.50f, 0.01f, false,
                () -> cfg.underwaterMuffleCutoff, v -> cfg.underwaterMuffleCutoff = v);

        List<Option<?>> advancedOptions = List.of(lowHealthCutoff, underwaterCutoff);
        allOptions.addAll(advancedOptions);
        bindAvailability(lowHealthMuffle::pendingValue, List.of(lowHealthCutoff), lowHealthMuffle);
        bindAvailability(underwaterMuffle::pendingValue, List.of(underwaterCutoff), underwaterMuffle);

        ConfigCategory.Builder advancedCategory = ConfigCategory.createBuilder()
                .name(Component.literal("Advanced Audio"))
                .option(LabelOption.create(Component.literal("\u2014 LOW-HEALTH FILTER \u2014")))
                .option(lowHealthCutoff)
                .option(LabelOption.create(Component.literal("\u2014 UNDERWATER FILTER \u2014")))
                .option(underwaterCutoff);
        addCategoryReset(advancedCategory, advancedOptions);

        // The global reset is intentionally placed in Audio so it is easy to find.
        audioCategory.option(ButtonOption.createBuilder()
                .name(Component.literal("Reset all settings"))
                .text(Component.literal("Reset"))
                .description(OptionDescription.of(Component.literal(
                        "Restore every IndicationSounds setting to its default value.")))
                .action((screen, button) -> GuiUtils.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                allOptions.forEach(Option::requestSetDefault);
                            }
                            GuiUtils.setScreen(screen);
                        },
                        Component.literal("Reset all settings?"),
                        Component.literal("All IndicationSounds options will be restored to their defaults.")
                )))
                .build());
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("IndicationSounds"))
                .category(audioCategory.build())
                .category(volumeCategory.build())
                .category(visualsCategory.build())
                .category(thresholdsCategory.build())
                .category(timingCategory.build())
                .category(advancedCategory.build())
                .save(AZConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static void addCategoryReset(ConfigCategory.Builder category, List<Option<?>> options) {
        category.option(ButtonOption.createBuilder()
                .name(Component.literal("Reset this category"))
                .text(Component.literal("Reset"))
                .description(OptionDescription.of(Component.literal(
                        "Restore the settings in this category to their defaults.")))
                .action((screen, button) -> options.forEach(Option::requestSetDefault))
                .build());
    }

    @SafeVarargs
    private static void bindAvailability(BooleanSupplier condition, List<Option<?>> dependents,
                                          Option<Boolean>... parents) {
        Runnable update = () -> {
            boolean available = condition.getAsBoolean();
            dependents.forEach(option -> option.setAvailable(available));
        };
        update.run();
        for (Option<Boolean> parent : parents) {
            parent.addEventListener((option, event) -> update.run());
        }
    }

    private static Option<Boolean> toggle(String name, String desc, boolean def,
                                          Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> intField(String name, String desc, int def, int min, int max,
                                            Supplier<Integer> getter, Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(option -> IntegerFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    private static Option<Float> floatField(String name, String desc, float def, float min, float max,
                                            Supplier<Float> getter, Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(option -> FloatFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    private static Option<Float> floatSlider(String name, String desc, float def, float min, float max, float step,
                                             boolean percent, Supplier<Float> getter, Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(option -> FloatSliderControllerBuilder.create(option)
                        .range(min, max)
                        .step(step)
                        .formatValue(value -> Component.literal(percent
                                ? Math.round(value * 100) + "%"
                                : String.format(java.util.Locale.ROOT, "%.2f", value))))
                .build();
    }
}
