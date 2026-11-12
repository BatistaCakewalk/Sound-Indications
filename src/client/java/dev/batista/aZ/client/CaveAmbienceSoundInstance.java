package dev.batista.aZ.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Looping, listener-centred cave ambience with a short fade-in/fade-out. */
final class CaveAmbienceSoundInstance extends AbstractTickableSoundInstance {

    private float baseVolume;
    private float gain = 0.02f;
    private boolean fadingOut;

    CaveAmbienceSoundInstance(SoundEvent event, float volume) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.baseVolume = volume;
        this.volume = volume * gain;
        this.looping = true;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (fadingOut) {
            gain = Math.max(0.0f, gain - 1.0f / 40.0f);
            if (gain <= 0.0f) {
                this.volume = 0.0f;
                stop();
                return;
            }
        } else {
            gain = Math.min(1.0f, gain + 1.0f / 60.0f);
        }
        this.volume = baseVolume * gain;
    }

    void setBaseVolume(float volume) {
        baseVolume = volume;
        this.volume = baseVolume * gain;
    }

    void fadeOut() {
        fadingOut = true;
    }

    void cancelFadeOut() {
        fadingOut = false;
    }

    void stopImmediately() {
        gain = 0.0f;
        volume = 0.0f;
        stop();
    }

    boolean isFadingOut() {
        return fadingOut;
    }
}
