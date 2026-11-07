package dev.batista.aZ.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodData;

public class AZClient implements ClientModInitializer {

    private static final String MOD_ID = "a-z";
    private static final Identifier VIGNETTE_LOCATION = Identifier.withDefaultNamespace("textures/misc/vignette.png");

    /** Delay before the first growl after dropping into the hunger range (ticks). */
    private static final int HUNGER_FIRST_DELAY = 40;
    /** low_hp.ogg is ~600s long. Restart it after this if still low (ticks). */
    private static final int LOW_HP_LENGTH_TICKS = 12001;
    /** anxiety.ogg is ~930s long. Restart it after this if still low (ticks). */
    private static final int ANXIETY_LENGTH_TICKS = 18603;
    /** Vanilla drains saturation in steps of at most 1.0. Bigger drops are not natural burning. */
    private static final float MAX_NATURAL_SATURATION_DROP = 1.0001f;

    /** Minecraft background music fade speed: ~2s fade out (40 ticks), ~3s fade in (60 ticks). */
    private static final float MUSIC_FADE_OUT_STEP = 1f / 40f;
    private static final float MUSIC_FADE_IN_STEP = 1f / 60f;

    /** Anxiety song fade speeds: ~2s fade in (40 ticks), ~2.5s fade out (50 ticks). */
    private static final float ANXIETY_FADE_IN_STEP = 1f / 40f;
    private static final float SOUND_FADE_OUT_STEP = 1f / 50f;

    /** Damage vignette flash duration: 20 ticks = 1.0 second. */
    private static final int DAMAGE_FLASH_DURATION = 20;

    private static final SoundEvent HUNGER = event("hunger");
    private static final SoundEvent DIGESTION = event("digestion");
    private static final SoundEvent LOW_HP = event("low_hp");
    private static final SoundEvent ANXIETY = event("anxiety");

    private final RandomSource random = RandomSource.create();

    private long tick = 0;
    private int hungerTimer = -1;
    private long digestionReadyAt = 0;
    private float lastSaturation = -1f;
    private int lastFoodLevel = -1;
    private float lastHealth = -1f;

    private LowHpSoundInstance lowHpInstance = null;
    private boolean lowHpIsAnxiety = false;
    private long lowHpRestartAt = 0;
    private float musicGain = 1f;

    // Low HP pulsing vignette
    private float vignetteIntensity = 0f;
    private float prevVignetteIntensity = 0f;

    // Damage flash vignette (1-second fade-in/fade-out)
    private int damageFlashTicks = 0;
    private float damageFlashIntensity = 0f;
    private float damageFlashPeak = 1.0f;
    private float prevFlashIntensity = 0f;

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MOD_ID, name));
    }

    @Override
    public void onInitializeClient() {
        AZConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "low_hp_vignette"),
                this::renderVignette
        );
    }

    private void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        SoundManager sounds = client.getSoundManager();

        if (player == null) {
            resetAll(sounds);
            return;
        }
        if (client.isPaused()) {
            return;
        }
        tick++;

        AZConfig cfg = AZConfig.get();
        float currentHealth = player.getHealth();

        // 1-second damage flash when taking damage in game
        if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            if (lastHealth >= 0f && currentHealth < lastHealth && cfg.damageVignette) {
                triggerDamageFlash(lastHealth - currentHealth, cfg);
            }
            lastHealth = currentHealth;
        } else {
            lastHealth = -1f;
        }

        updateDamageFlash();

        boolean wantFadeOutMusic = false;
        boolean canHaveEffects = player.isAlive() && !player.isCreative() && !player.isSpectator();
        if (canHaveEffects) {
            FoodData food = player.getFoodData();
            handleHunger(sounds, cfg, food.getFoodLevel());
            handleDigestion(sounds, cfg, food.getFoodLevel(), food.getSaturationLevel());
            wantFadeOutMusic = handleLowHp(sounds, cfg, currentHealth);
            updateVignette(currentHealth, cfg);
        } else {
            stopImmediately(sounds);
            hungerTimer = -1;
            lastSaturation = -1f;
            lastFoodLevel = -1;
            vignetteIntensity = Math.max(0.0f, vignetteIntensity - 0.1f);
        }

        boolean isUnderwater = player.isUnderWater() && !player.isSpectator();
        AZFilterManager.tick(sounds, cfg, currentHealth, canHaveEffects, isUnderwater);
        fadeMusic(sounds, wantFadeOutMusic);
    }

    private void handleHunger(SoundManager sounds, AZConfig cfg, int foodLevel) {
        // 1 bar = 2 food points
        if (!cfg.hungerEnabled || foodLevel > cfg.hungerThresholdBars * 2) {
            hungerTimer = -1;
            return;
        }
        if (hungerTimer < 0) {
            hungerTimer = HUNGER_FIRST_DELAY;
        }
        if (--hungerTimer <= 0) {
            float pitch = 0.9f + random.nextFloat() * 0.2f;
            sounds.play(SimpleSoundInstance.forUI(HUNGER, pitch, cfg.hungerVolume));
            int minGap = Math.max(1, cfg.hungerMinGapSeconds) * 20;
            int maxGap = Math.max(minGap, cfg.hungerMaxGapSeconds * 20);
            hungerTimer = minGap + random.nextInt(maxGap - minGap + 1);
        }
    }

    private void handleDigestion(SoundManager sounds, AZConfig cfg, int foodLevel, float saturation) {
        boolean saturationBurned = lastSaturation >= 0f
                && saturation < lastSaturation
                && lastSaturation - saturation <= MAX_NATURAL_SATURATION_DROP;
        boolean hungerBarDropped = lastFoodLevel >= 0 && foodLevel < lastFoodLevel;

        if (cfg.digestionEnabled && saturationBurned && !hungerBarDropped && tick >= digestionReadyAt) {
            sounds.play(SimpleSoundInstance.forUI(DIGESTION, 1.0f, cfg.digestionVolume));
            digestionReadyAt = tick + Math.max(1, cfg.digestionCooldownSeconds) * 20L;
        }
        lastSaturation = saturation;
        lastFoodLevel = foodLevel;
    }

    /**
     * Handles low HP track playback.
     * @return true if Minecraft's background music should be faded out and kept silent.
     */
    private boolean handleLowHp(SoundManager sounds, AZConfig cfg, float health) {
        if (lowHpInstance != null && lowHpInstance.isStopped()) {
            lowHpInstance = null;
        }

        boolean shouldPlay = cfg.lowHpEnabled && health <= cfg.lowHpThresholdHearts * 2f;
        boolean wantAnxiety = cfg.useAnxietySong;

        if (shouldPlay) {
            if (lowHpInstance == null || tick >= lowHpRestartAt || lowHpIsAnxiety != wantAnxiety) {
                stopImmediately(sounds);

                SoundEvent event = wantAnxiety ? ANXIETY : LOW_HP;
                float volume = wantAnxiety ? cfg.anxietyVolume : cfg.lowHpVolume;
                // Anxiety music fades in smoothly over ~2s (startFaded = true) as Minecraft music fades out
                float fadeIn = wantAnxiety ? ANXIETY_FADE_IN_STEP : 1.0f;
                lowHpInstance = new LowHpSoundInstance(event, volume, SOUND_FADE_OUT_STEP, fadeIn, wantAnxiety);
                sounds.play(lowHpInstance);
                lowHpIsAnxiety = wantAnxiety;
                lowHpRestartAt = tick + (wantAnxiety ? ANXIETY_LENGTH_TICKS : LOW_HP_LENGTH_TICKS);
            } else {
                if (lowHpInstance.isFadingOut()) {
                    lowHpInstance.cancelFadeOut();
                }
                lowHpInstance.setBaseVolume(wantAnxiety ? cfg.anxietyVolume : cfg.lowHpVolume);
            }
        } else {
            // Smoothly fade away when health rises above threshold
            if (lowHpInstance != null && !lowHpInstance.isFadingOut()) {
                lowHpInstance.startFadeOut();
            }
        }

        // Minecraft background music stays faded out while low HP anxiety is active OR while anxiety track is fading away
        return (shouldPlay && wantAnxiety) || (lowHpInstance != null && lowHpIsAnxiety && !lowHpInstance.isStopped());
    }

    private void stopImmediately(SoundManager sounds) {
        if (lowHpInstance != null) {
            lowHpInstance.stopImmediately();
            sounds.stop(lowHpInstance);
            lowHpInstance = null;
        }
    }

    private void updateVignette(float health, AZConfig cfg) {
        prevVignetteIntensity = vignetteIntensity;
        if (!cfg.lowHpVignette) {
            vignetteIntensity = 0.0f;
            return;
        }
        float threshold = cfg.lowHpThresholdHearts * 2f;
        if (health <= threshold) {
            float missingFraction = Math.max(0.0f, threshold - health) / Math.max(1.0f, threshold);
            // Noticeable baseline strength (0.65) starting at threshold, rising to 1.0 at critical health
            float baseStrength = 0.65f + 0.35f * missingFraction;
            float target = baseStrength * cfg.lowHpVignetteIntensity;
            vignetteIntensity += (target - vignetteIntensity) * 0.15f;
        } else {
            // Smoothly fade away when above threshold
            vignetteIntensity = Math.max(0.0f, vignetteIntensity - 0.05f);
        }
    }

    private void triggerDamageFlash(float damage, AZConfig cfg) {
        damageFlashTicks = DAMAGE_FLASH_DURATION;
        // Noticeable deep red flash on hit
        float scale = Math.min(1.0f, 0.85f + (damage / 8.0f) * 0.15f);
        damageFlashPeak = scale * cfg.lowHpVignetteIntensity;
    }

    private void updateDamageFlash() {
        prevFlashIntensity = damageFlashIntensity;
        if (damageFlashTicks > 0) {
            damageFlashTicks--;
            int elapsed = DAMAGE_FLASH_DURATION - damageFlashTicks;
            float progress;
            if (elapsed <= 3) {
                // Quick 3-tick (~0.15s) punchy fade-in
                progress = elapsed / 3.0f;
            } else {
                // Smooth 17-tick (~0.85s) fade-out to 0
                progress = damageFlashTicks / 17.0f;
            }
            damageFlashIntensity = progress * damageFlashPeak;
        } else {
            damageFlashIntensity = 0.0f;
        }
    }

    private void renderVignette(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        AZConfig cfg = AZConfig.get();
        if (!cfg.lowHpVignette && !cfg.damageVignette) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isCreative() || client.player.isSpectator()) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float lowHp = cfg.lowHpVignette ? Mth.lerp(partialTick, prevVignetteIntensity, vignetteIntensity) : 0.0f;
        float flash = cfg.damageVignette ? Mth.lerp(partialTick, prevFlashIntensity, damageFlashIntensity) : 0.0f;
        float baseIntensity = Math.max(lowHp, flash);

        if (baseIntensity <= 0.001f) {
            return;
        }

        // Noticeable heartbeat pulse when low HP is active
        float pulse = 0.0f;
        if (lowHp > 0.05f) {
            float sin = (float) Math.sin((tick + partialTick) * 0.28f);
            float throb = Math.max(0.0f, sin);
            pulse = (float) Math.pow(throb, 2.5) * 0.40f * lowHp;
        }

        float finalIntensity = Mth.clamp(baseIntensity + pulse, 0.0f, 1.0f);

        // Primary pass: filters green and blue, turning screen edges deep red
        int color = ARGB.colorFromFloat(1.0f, 0.0f, finalIntensity, finalIntensity);
        graphics.blit(
                RenderPipelines.VIGNETTE,
                VIGNETTE_LOCATION,
                0, 0,
                0.0f, 0.0f,
                graphics.guiWidth(), graphics.guiHeight(),
                graphics.guiWidth(), graphics.guiHeight(),
                color
        );

        // Secondary pass for deep, dramatic blood-red edges when intensity is high
        if (finalIntensity > 0.15f) {
            float secondIntensity = (finalIntensity - 0.15f) / 0.85f;
            int secondColor = ARGB.colorFromFloat(1.0f, 0.0f, secondIntensity, secondIntensity);
            graphics.blit(
                    RenderPipelines.VIGNETTE,
                    VIGNETTE_LOCATION,
                    0, 0,
                    0.0f, 0.0f,
                    graphics.guiWidth(), graphics.guiHeight(),
                    graphics.guiWidth(), graphics.guiHeight(),
                    secondColor
            );
        }
    }

    /**
     * Smoothly fades Minecraft's background music out when anxiety starts (~2s),
     * and smoothly fades it back in once health recovers and anxiety song is finished (~3s).
     */
    private void fadeMusic(SoundManager sounds, boolean fadeOut) {
        float target = fadeOut ? 0.0f : 1.0f;
        if (musicGain == target) {
            return;
        }
        float step = fadeOut ? MUSIC_FADE_OUT_STEP : MUSIC_FADE_IN_STEP;
        musicGain = fadeOut
                ? Math.max(target, musicGain - step)
                : Math.min(target, musicGain + step);
        sounds.updateCategoryVolume(SoundSource.MUSIC, musicGain);
    }

    /** Called when there is no player (title screen, leaving a world). Restores everything instantly. */
    private void resetAll(SoundManager sounds) {
        stopImmediately(sounds);
        AZFilterManager.releaseAll(sounds);
        hungerTimer = -1;
        lastSaturation = -1f;
        lastFoodLevel = -1;
        lastHealth = -1f;
        vignetteIntensity = 0f;
        prevVignetteIntensity = 0f;
        damageFlashTicks = 0;
        damageFlashIntensity = 0f;
        prevFlashIntensity = 0f;
        if (musicGain != 1f) {
            musicGain = 1f;
            sounds.updateCategoryVolume(SoundSource.MUSIC, 1f);
        }
    }
}
