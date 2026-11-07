package dev.batista.aZ.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** YACL settings screen. Only loaded when YACL is present. */
public final class AZConfigScreen {

    private AZConfigScreen() {}

    public static Screen create(Screen parent) {
        AZConfig defaults = new AZConfig();
        AZConfig cfg = AZConfig.get();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Indication Sounds"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Sounds"))
                        .option(toggle("Hunger sound", "Stomach growls while hungry.",
                                defaults.hungerEnabled, () -> cfg.hungerEnabled, v -> cfg.hungerEnabled = v))
                        .option(toggle("Digestion sound", "Plays when your body burns saturation.",
                                defaults.digestionEnabled, () -> cfg.digestionEnabled, v -> cfg.digestionEnabled = v))
                        .option(toggle("Low health sound", "Plays while your health is low.",
                                defaults.lowHpEnabled, () -> cfg.lowHpEnabled, v -> cfg.lowHpEnabled = v))
                        .option(toggle("Anxiety song instead of low health sound",
                                "Plays the anxiety song instead of the low health sound, and fades out Minecraft's music until your health recovers.",
                                defaults.useAnxietySong, () -> cfg.useAnxietySong, v -> cfg.useAnxietySong = v))
                        .option(toggle("Muffled audio at low health",
                                "Muffles world sounds with an OpenAL low-pass filter when health is low.",
                                defaults.lowHpMuffleAudio, () -> cfg.lowHpMuffleAudio, v -> cfg.lowHpMuffleAudio = v))
                        .option(toggle("Keep UI unmuffled",
                                "Keeps UI and inventory clicks crisp while low health muffled audio is active.",
                                defaults.keepUIUnmuffled, () -> cfg.keepUIUnmuffled, v -> cfg.keepUIUnmuffled = v))
                        .option(floatSlider("Low health muffle cutoff",
                                "How muffled high frequencies become at critical health (lower = more muffled).",
                                defaults.lowHpMuffleCutoff, 0.05f, 0.50f, 0.05f, true,
                                () -> cfg.lowHpMuffleCutoff, v -> cfg.lowHpMuffleCutoff = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Visuals"))
                        .option(toggle("Low health vignette", "Displays a pulsating red vignette overlay on the screen edges while health is low.",
                                defaults.lowHpVignette, () -> cfg.lowHpVignette, v -> cfg.lowHpVignette = v))
                        .option(toggle("Damage vignette flash", "Flashes a red vignette for a second whenever you take damage in game.",
                                defaults.damageVignette, () -> cfg.damageVignette, v -> cfg.damageVignette = v))
                        .option(floatSlider("Vignette max intensity", "Maximum opacity of the red vignette overlays.",
                                defaults.lowHpVignetteIntensity, 0.1f, 1.0f, 0.05f, true,
                                () -> cfg.lowHpVignetteIntensity, v -> cfg.lowHpVignetteIntensity = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Thresholds"))
                        .option(intSlider("Hunger threshold (bars)", "Hunger sound plays at or below this many bars.",
                                defaults.hungerThresholdBars, 1, 10, 1,
                                () -> cfg.hungerThresholdBars, v -> cfg.hungerThresholdBars = v))
                        .option(floatSlider("Low health threshold (hearts)", "Low health sound plays at or below this many hearts.",
                                defaults.lowHpThresholdHearts, 0.5f, 10f, 0.5f, false,
                                () -> cfg.lowHpThresholdHearts, v -> cfg.lowHpThresholdHearts = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Volume"))
                        .option(floatSlider("Hunger volume", "Volume of the hunger growls.",
                                defaults.hungerVolume, 0f, 1f, 0.05f, true,
                                () -> cfg.hungerVolume, v -> cfg.hungerVolume = v))
                        .option(floatSlider("Digestion volume", "Volume of the digestion sound.",
                                defaults.digestionVolume, 0f, 1f, 0.05f, true,
                                () -> cfg.digestionVolume, v -> cfg.digestionVolume = v))
                        .option(floatSlider("Low health volume", "Volume of the low health sound.",
                                defaults.lowHpVolume, 0f, 1f, 0.05f, true,
                                () -> cfg.lowHpVolume, v -> cfg.lowHpVolume = v))
                        .option(floatSlider("Anxiety song volume", "Volume of the anxiety song.",
                                defaults.anxietyVolume, 0f, 1f, 0.05f, true,
                                () -> cfg.anxietyVolume, v -> cfg.anxietyVolume = v))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Timing"))
                        .option(intSlider("Hunger gap, minimum (s)", "Shortest wait between growls.",
                                defaults.hungerMinGapSeconds, 1, 120, 1,
                                () -> cfg.hungerMinGapSeconds, v -> cfg.hungerMinGapSeconds = v))
                        .option(intSlider("Hunger gap, maximum (s)", "Longest wait between growls.",
                                defaults.hungerMaxGapSeconds, 1, 120, 1,
                                () -> cfg.hungerMaxGapSeconds, v -> cfg.hungerMaxGapSeconds = v))
                        .option(intSlider("Digestion cooldown (s)", "Minimum time between digestion sounds.",
                                defaults.digestionCooldownSeconds, 1, 300, 1,
                                () -> cfg.digestionCooldownSeconds, v -> cfg.digestionCooldownSeconds = v))
                        .build())
                .save(AZConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(String name, String desc, boolean def,
                                          java.util.function.Supplier<Boolean> getter,
                                          java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> intSlider(String name, String desc, int def, int min, int max, int step,
                                             java.util.function.Supplier<Integer> getter,
                                             java.util.function.Consumer<Integer> setter) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }

    private static Option<Float> floatSlider(String name, String desc, float def, float min, float max, float step,
                                             boolean percent,
                                             java.util.function.Supplier<Float> getter,
                                             java.util.function.Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, getter, setter)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(min, max)
                        .step(step)
                        .formatValue(v -> Component.literal(percent
                                ? Math.round(v * 100) + "%"
                                : String.valueOf(v))))
                .build();
    }
}
