package dev.batista.aZ.client;

import com.mojang.blaze3d.audio.Channel;
import dev.batista.aZ.mixin.client.ChannelAccessor;
import dev.batista.aZ.mixin.client.SoundEngineAccessor;
import dev.batista.aZ.mixin.client.SoundManagerAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages an OpenAL EFX low-pass filter to muffle world audio and background music
 * when health is low or while submerged underwater.
 * Excludes internal vitals (Low HP heartbeat / anxiety song, stomach sounds, UI clicks)
 * to keep player feedback crisp and recognizable.
 */
public final class AZFilterManager {

    private static int filterId = 0;
    private static boolean filterSupported = true;
    private static float currentGainHF = 1.0f;
    private static float targetGainHF = 1.0f;
    private static boolean muffleActive = false;

    /** Tracks active Simple Voice Chat OpenAL sources when proximity voice chat is running. */
    private static final Set<Integer> voiceChatSources = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private AZFilterManager() {}

    public static boolean isMuffleActive() {
        return muffleActive;
    }

    public static int getFilterId() {
        return filterId;
    }

    public static float getCurrentGainHF() {
        return currentGainHF;
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
        // Stomach hunger growls & digestion sounds from IndicationSounds
        if (sound.getIdentifier() != null && "a-z".equals(sound.getIdentifier().getNamespace())) {
            return true;
        }
        // UI clicks (inventory, menu)
        if (cfg.keepUIUnmuffled && sound.getSource() == SoundSource.UI) {
            return true;
        }
        // If user disabled music muffling, keep background music clear
        if (!cfg.muffleMusic && (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS)) {
            return true;
        }
        return false;
    }

    /**
     * Called every client tick from {@link AZClient#onTick}.
     */
    public static void tick(SoundManager soundManager, AZConfig cfg, float health, boolean canHaveEffects, boolean isUnderwater) {
        boolean lowHpMuffle = canHaveEffects && cfg.lowHpMuffleAudio && (health <= cfg.lowHpThresholdHearts * 2f);
        boolean underWaterMuffle = isUnderwater && cfg.underwaterMuffleAudio;

        float desiredLowHpGain = 1.0f;
        if (lowHpMuffle) {
            float threshold = cfg.lowHpThresholdHearts * 2f;
            float missingFraction = Math.max(0.0f, threshold - health) / Math.max(1.0f, threshold);
            float cutoff = Math.max(0.01f, Math.min(1.0f, cfg.lowHpMuffleCutoff));
            // Start smoothly at 0.60f at threshold, dropping down to cutoff at 0 health
            desiredLowHpGain = 0.60f - (0.60f - cutoff) * missingFraction;
        }

        float desiredUnderwaterGain = 1.0f;
        if (underWaterMuffle) {
            desiredUnderwaterGain = Math.max(0.01f, Math.min(1.0f, cfg.underwaterMuffleCutoff));
        }

        boolean wantMuffle = lowHpMuffle || underWaterMuffle;
        if (wantMuffle) {
            // Use whichever muffling effect is stronger (lowest HF gain)
            targetGainHF = Math.min(desiredLowHpGain, desiredUnderwaterGain);
            muffleActive = true;
        } else {
            targetGainHF = 1.0f;
        }

        // Smooth frequency transition (~1.5s)
        currentGainHF += (targetGainHF - currentGainHF) * 0.15f;
        if (!wantMuffle && currentGainHF >= 0.98f) {
            currentGainHF = 1.0f;
            muffleActive = false;
        }

        if (!(soundManager instanceof SoundManagerAccessor managerAccessor)) {
            return;
        }
        SoundEngine engine = managerAccessor.indicationSounds$getSoundEngine();
        if (!(engine instanceof SoundEngineAccessor engineAccessor)) {
            return;
        }

        // Execute OpenAL updates on Minecraft's sound engine executor thread
        engineAccessor.indicationSounds$getExecutor().execute(() -> {
            updateChannels(engineAccessor, cfg);
            updateVoiceChatSources();
        });
    }

    /**
     * Called when a sound is started by {@link SoundEngine#play}.
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

        AZConfig cfg = AZConfig.get();
        boolean exempt = isExempt(sound, cfg);
        handle.execute(channel -> {
            if (channel instanceof ChannelExemption channelEx) {
                channelEx.indicationSounds$setExempt(exempt);
            }
            int source = ((ChannelAccessor) channel).indicationSounds$getSource();
            if (source != 0) {
                if (!exempt && muffleActive && currentGainHF < 0.99f && ensureFilter()) {
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
                } else {
                    AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                }
                AL10.alGetError();
            }
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
        if (muffleActive && currentGainHF < 0.99f && ensureFilter()) {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
        } else {
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        }
        AL10.alGetError();
    }

    private static void updateChannels(SoundEngineAccessor engineAccessor, AZConfig cfg) {
        boolean muffle = (muffleActive && currentGainHF < 0.99f);
        if (muffle && ensureFilter()) {
            EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAINHF, currentGainHF);
            AL10.alGetError();
        }

        // 1. Tag and update all channels tracked in instanceToChannel
        Map<SoundInstance, ChannelAccess.ChannelHandle> map = engineAccessor.indicationSounds$getInstanceToChannel();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : map.entrySet()) {
                SoundInstance sound = entry.getKey();
                ChannelAccess.ChannelHandle handle = entry.getValue();
                if (handle == null || handle.isStopped()) {
                    continue;
                }

                boolean exempt = isExempt(sound, cfg);
                handle.execute(channel -> {
                    if (channel == null) {
                        return;
                    }
                    if (channel instanceof ChannelExemption channelEx) {
                        channelEx.indicationSounds$setExempt(exempt);
                    }
                    int source = ((ChannelAccessor) channel).indicationSounds$getSource();
                    if (source == 0) {
                        return;
                    }
                    if (muffle && !exempt && ensureFilter()) {
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
                    } else {
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                    }
                    AL10.alGetError();
                });
            }
        }

        // 2. Also execute across ALL channels in ChannelAccess (guarantees streaming background music, discs, ambient)
        ChannelAccess channelAccess = engineAccessor.indicationSounds$getChannelAccess();
        if (channelAccess != null) {
            channelAccess.executeOnChannels(channelsStream -> {
                channelsStream.forEach(channel -> {
                    if (channel == null) {
                        return;
                    }
                    boolean exempt = (channel instanceof ChannelExemption chEx) && chEx.indicationSounds$isExempt();
                    int source = ((ChannelAccessor) channel).indicationSounds$getSource();
                    if (source == 0) {
                        return;
                    }
                    if (muffle && !exempt && ensureFilter()) {
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
                    } else {
                        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                    }
                    AL10.alGetError();
                });
            });
        }
    }

    private static void updateVoiceChatSources() {
        if (voiceChatSources.isEmpty()) {
            return;
        }
        boolean muffle = (muffleActive && currentGainHF < 0.99f);
        Iterator<Integer> it = voiceChatSources.iterator();
        while (it.hasNext()) {
            int source = it.next();
            if (!AL10.alIsSource(source)) {
                it.remove();
                continue;
            }
            if (muffle && ensureFilter()) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filterId);
            } else {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            }
            AL10.alGetError();
        }
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
                filterSupported = false;
                filterId = 0;
                return false;
            }
            EXTEfx.alFilteri(filterId, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(filterId, EXTEfx.AL_LOWPASS_GAINHF, currentGainHF);
            AL10.alGetError();
            return true;
        } catch (Throwable t) {
            filterSupported = false;
            filterId = 0;
            return false;
        }
    }

    /**
     * Resets OpenAL filter when the sound engine reloads or shuts down.
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
        currentGainHF = 1.0f;
        targetGainHF = 1.0f;
        muffleActive = false;
    }
}
