package dev.batista.aZ.client;

/**
 * Duck interface implemented on {@link com.mojang.blaze3d.audio.Channel} to track
 * whether an audio channel should be exempt from low-pass muffling filters.
 */
public interface ChannelExemption {
    boolean indicationSounds$isExempt();
    void indicationSounds$setExempt(boolean exempt);
}
