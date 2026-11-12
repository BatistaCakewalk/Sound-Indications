package dev.batista.aZ.client;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import dev.batista.aZ.mixin.client.ChannelAccessor;
import dev.batista.aZ.mixin.client.ChannelHandleAccessor;
import dev.batista.aZ.mixin.client.SoundEngineAccessor;
import dev.batista.aZ.mixin.client.SoundManagerAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages an OpenAL EFX low-pass filter to muffle world audio when health is low
 * or while submerged underwater.
 * <p>
 * "Strength" below is the config cutoff scale: 1.0 = clear, lower = more muffled.
 * <p>
 * Notes for future debugging:
 * <ul>
 *   <li>EFX's low-pass has a FIXED 5 kHz reference. GAINHF only sets a high-shelf above it, so a
 *       gain like 0.25-0.5 is barely audible. We map strength to a much steeper HF gain and also
 *       lower the overall gain a little so the effect actually sounds muffled.</li>
 *   <li>Vanilla Minecraft never uses EFX, so there is nothing to conflict with.</li>
 *   <li>{@code SoundEngine.instanceToChannel} is a plain HashMap mutated by the main thread.
 *       It is only ever iterated on the main thread; the sound thread gets a snapshot.</li>
 * </ul>
 */
public final class AZFilterManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float CLEAR = 0.99f;

    private static int filterId = 0;
    private static boolean filterSupported = true;

    // Written on the main thread, read on the sound thread.
    private static volatile float currentStrength = 1.0f;
    private static volatile boolean muffleActive = false;

    // Main thread only.
    private static float targetStrength = 1.0f;
    private static boolean appliedLastTick = false;
    private static boolean lastWantMuffle = false;

    private static int alErrorsLogged = 0;

    /** Tracks active Simple Voice Chat OpenAL sources when proximity voice chat is running. */
    private static final Set<Integer> voiceChatSources = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private record Tagged(ChannelAccess.ChannelHandle handle, boolean exempt) {}

    private AZFilterManager() {}

    public static boolean isMuffleActive() {
        return muffleActive;
    }

    public static int getFilterId() {
        return filterId;
    }

    public static float getCurrentGainHF() {
        return hfGain(currentStrength);
    }

    /** High-frequency gain for a strength value. Cubic, so 0.5 is about -18 dB and 0.25 about -36 dB. */
    private static float hfGain(float strength) {
        float s = Math.max(0.0f, Math.min(1.0f, strength));
        return s * s * s;
    }

    /** Overall gain for a strength value. Muffled audio is also a little quieter, like real muffling. */
    private static float overallGain(float strength) {
        float s = Math.max(0.0f, Math.min(1.0f, strength));
        return 0.45f + 0.55f * s;
    }

    /**
     * Checks if a sound instance is exempt from the low-pass filter.
     * Keeps our own indication sounds (heartbeat, anxiety track, stomach sounds) and UI clicks crisp.
     */
    public static boolean isExempt(SoundInstance sound, AZConfig cfg) {
        if (sound == null) {
            return false;
        }
        // Low HP sound / anxiety song is an internal vital cue and stays crisp
        if (sound instanceof LowHpSoundInstance) {
            return true;
        }
        // Cave ambience and events should respond to underwater and low-health filtering.
        if (sound instanceof CaveAmbienceSoundInstance || sound instanceof CaveEventSoundInstance) {
            return false;
        }
        // Stomach hunger growls and digestion sounds from IndicationSounds
        if (sound.getIdentifier() != null && "a-z".equals(sound.getIdentifier().getNamespace())) {
            return true;
        }
        // UI clicks (inventory, menu)
        if (cfg.keepUIUnmuffled && sound.getSource() == SoundSource.UI) {
            return true;
        }
        // Music and records stay clear unless the user turned music muffling on
        if (!cfg.muffleMusic && (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS)) {
            return true;
        }
        return false;
    }

    /**
     * Called every client tick from {@link AZClient#onTick}. Main thread.
     */
    public static void tick(SoundManager soundManager, AZConfig cfg, float health, boolean canHaveEffects, boolean isUnderwater) {
        boolean lowHpMuffle = canHaveEffects && cfg.lowHpMuffleAudio && (health <= cfg.lowHpThresholdHearts * 2f);
        boolean underWaterMuffle = isUnderwater && cfg.underwaterMuffleAudio;

        float desiredLowHp = 1.0f;
        if (lowHpMuffle) {
            float threshold = cfg.lowHpThresholdHearts * 2f;
            float missingFraction = Math.max(0.0f, threshold - health) / Math.max(1.0f, threshold);
            float cutoff = Math.max(0.01f, Math.min(1.0f, cfg.lowHpMuffleCutoff));
            // Start at 0.60 at the threshold, easing down to the cutoff at 0 health
            desiredLowHp = 0.60f - (0.60f - cutoff) * missingFraction;
        }

        float desiredUnderwater = 1.0f;
        if (underWaterMuffle) {
            desiredUnderwater = Math.max(0.01f, Math.min(1.0f, cfg.underwaterMuffleCutoff));
        }

        boolean wantMuffle = lowHpMuffle || underWaterMuffle;
        if (wantMuffle != lastWantMuffle) {
            lastWantMuffle = wantMuffle;
            LOGGER.info("[IndicationSounds] Muffle {} (underwater={}, lowHp={})",
                    wantMuffle ? "ON" : "OFF", underWaterMuffle, lowHpMuffle);
        }

        if (wantMuffle) {
            // Use whichever effect is stronger (lowest strength)
            targetStrength = Math.min(desiredLowHp, desiredUnderwater);
            muffleActive = true;
        } else {
            targetStrength = 1.0f;
        }

        // Smooth transition (~1.5s)
        float strength = currentStrength;
        strength += (targetStrength - strength) * 0.15f;
        if (!wantMuffle && strength >= 0.98f) {
            strength = 1.0f;
            muffleActive = false;
        }
        currentStrength = strength;

        boolean apply = muffleActive && strength < CLEAR;
        // Nothing attached and nothing to attach: skip. When muffle ends, one more pass detaches everything.
        if (!apply && !appliedLastTick) {
            return;
        }
        appliedLastTick = apply;
        pushToSoundThread(soundManager, cfg, apply, strength);
    }

    /**
     * Instantly clears the filter from every source. Called when there is no player (title screen),
     * so leaving a world while muffled doesn't leave the menu music muffled.
     */
    public static void releaseAll(SoundManager soundManager) {
        boolean needsClear = appliedLastTick || muffleActive;
        currentStrength = 1.0f;
        targetStrength = 1.0f;
        muffleActive = false;
        lastWantMuffle = false;
        if (needsClear) {
            appliedLastTick = false;
            pushToSoundThread(soundManager, AZConfig.get(), false, 1.0f);
        }
    }

    private static void pushToSoundThread(SoundManager soundManager, AZConfig cfg, boolean muffle, float strength) {
        if (!(soundManager instanceof SoundManagerAccessor managerAccessor)) {
            return;
        }
        SoundEngine engine = managerAccessor.indicationSounds$getSoundEngine();
        if (!(engine instanceof SoundEngineAccessor engineAccessor)) {
            return;
        }

        // Snapshot on the main thread. instanceToChannel is a plain HashMap that this thread mutates,
        // so the sound thread must never iterate it.
        List<Tagged> snapshot = new ArrayList<>();
        Map<SoundInstance, ChannelAccess.ChannelHandle> map = engineAccessor.indicationSounds$getInstanceToChannel();
        if (map != null) {
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : map.entrySet()) {
                ChannelAccess.ChannelHandle handle = entry.getValue();
                if (handle == null || handle.isStopped()) {
                    continue;
                }
                snapshot.add(new Tagged(handle, isExempt(entry.getKey(), cfg)));
            }
        }

        engineAccessor.indicationSounds$getExecutor().execute(() ->
                applyOnSoundThread(engineAccessor, snapshot, muffle, strength));
    }

    /** Runs on Minecraft's sound engine thread. */
    private static void applyOnSoundThread(SoundEngineAccessor engineAccessor, List<Tagged> snapshot,
                                           boolean muffle, float strength) {
        if (muffle && ensureFilter()) {
            setFilterParams(strength);
        }

        // Tag channels as exempt or not, using the main-thread snapshot
        for (Tagged tagged : snapshot) {
            Channel channel = ((ChannelHandleAccessor) tagged.handle()).indicationSounds$getChannel();
            if (channel instanceof ChannelExemption channelEx) {
                channelEx.indicationSounds$setExempt(tagged.exempt());
            }
        }

        // Attach or detach on EVERY channel, which also covers streaming music, records and ambience
        ChannelAccess channelAccess = engineAccessor.indicationSounds$getChannelAccess();
        if (channelAccess != null) {
            channelAccess.executeOnChannels(channels -> channels.forEach(channel -> attach(channel, muffle)));
        }

        updateVoiceChatSources(muffle);
    }

    /**
     * Called when a sound is started by {@link SoundEngine#play}. Main thread.
     */
    public static void onSoundStarted(SoundEngine soundEngine, SoundInstance sound) {
        if (!(soundEngine instanceof SoundEngineAccessor engineAccessor)) {
            return;
        }
        Map<SoundInstance, ChannelAccess.ChannelHandle> map = engineAccessor.indicationSounds$getInstanceToChannel();
        if (map == null) {
            return;
        }
        ChannelAccess.ChannelHandle handle = map.get(sound);
        if (handle == null) {
            return;
        }

        boolean exempt = isExempt(sound, AZConfig.get());
        handle.execute(channel -> {
            if (channel instanceof ChannelExemption channelEx) {
                channelEx.indicationSounds$setExempt(exempt);
            }
            boolean muffle = muffleActive && currentStrength < CLEAR;
            if (muffle) {
                ensureFilter();
            }
            attach(channel, muffle);
        });
    }

    /**
     * Called by {@link dev.batista.aZ.client.voicechat.AZVoicechatPlugin} when voice chat plays audio.
     */
    public static void onVoiceChatSource(int source) {
        if (source == 0) {
            return;
        }
        voiceChatSources.add(source);
        boolean muffle = muffleActive && currentStrength < CLEAR;
        if (muffle && ensureFilter()) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
        } else {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        }
        AL10.alGetError();
    }

    /** Attaches or detaches the filter on one channel. Sound thread only. */
    private static void attach(Channel channel, boolean muffle) {
        if (channel == null) {
            return;
        }
        int source = ((ChannelAccessor) channel).indicationSounds$getSource();
        if (source == 0) {
            return;
        }
        boolean exempt = (channel instanceof ChannelExemption channelEx) && channelEx.indicationSounds$isExempt();
        if (muffle && !exempt && filterId != 0) {
            // Filter params are copied on attach, so re-attaching is what applies a changed strength.
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
        } else {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        }
        checkAl("attach");
    }

    private static void updateVoiceChatSources(boolean muffle) {
        if (voiceChatSources.isEmpty()) {
            return;
        }
        Iterator<Integer> it = voiceChatSources.iterator();
        while (it.hasNext()) {
            int source = it.next();
            if (!AL10.alIsSource(source)) {
                it.remove();
                continue;
            }
            if (muffle && filterId != 0) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
            } else {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            }
            AL10.alGetError();
        }
    }

    private static void setFilterParams(float strength) {
        EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAIN, overallGain(strength));
        EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAINHF, hfGain(strength));
        checkAl("filter params");
    }

    private static boolean ensureFilter() {
        if (!filterSupported) {
            return false;
        }
        if (filterId != 0) {
            try {
                if (EXTEfx.alIsFilter(filterId)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        try {
            filterId = EXTEfx.alGenFilters();
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR || filterId == 0) {
                LOGGER.warn("[IndicationSounds] alGenFilters failed (AL error 0x{}), audio muffling disabled",
                        Integer.toHexString(err));
                filterSupported = false;
                filterId = 0;
                return false;
            }
            EXTEfx.alFilteri(filterId, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            setFilterParams(currentStrength);
            LOGGER.info("[IndicationSounds] OpenAL low-pass filter created (id={})", filterId);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[IndicationSounds] EFX low-pass filter unavailable, audio muffling disabled", t);
            filterSupported = false;
            filterId = 0;
            return false;
        }
    }

    /** Logs OpenAL errors instead of swallowing them, but only the first few. */
    private static void checkAl(String where) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR && alErrorsLogged < 8) {
            alErrorsLogged++;
            LOGGER.warn("[IndicationSounds] OpenAL error 0x{} during {}", Integer.toHexString(err), where);
        }
    }

    /**
     * Deletes the OpenAL filter when the sound engine reloads or shuts down.
     */
    public static void reset() {
        if (filterId != 0) {
            try {
                if (EXTEfx.alIsFilter(filterId)) {
                    EXTEfx.alDeleteFilters(filterId);
                }
            } catch (Throwable ignored) {}
            filterId = 0;
        }
        voiceChatSources.clear();
        currentStrength = 1.0f;
        targetStrength = 1.0f;
        muffleActive = false;
        appliedLastTick = false;
        lastWantMuffle = false;
    }
}
