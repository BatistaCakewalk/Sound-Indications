package dev.batista.aZ.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Listener-centred one-shot cave sound; source recordings are stereo ambience. */
final class CaveEventSoundInstance extends AbstractTickableSoundInstance {

    CaveEventSoundInstance(SoundEvent event, float volume, float pitch) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        // One-shot playback is managed by the sound engine.
    }
}
