package dev.batista.aZ.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A tickable sound instance that supports smooth fading in and fading out.
 */
public class LowHpSoundInstance extends AbstractTickableSoundInstance {

    private float baseVolume;
    private final float fadeOutStep;
    private final float fadeInStep;
    private float currentGain;
    private boolean fadingOut = false;

    public LowHpSoundInstance(SoundEvent event, float volume, float fadeOutStep, float fadeInStep, boolean startFaded) {
        super(event, SoundSource.UI, SoundInstance.createUnseededRandom());
        this.baseVolume = volume;
        this.fadeOutStep = fadeOutStep;
        this.fadeInStep = fadeInStep;
        this.currentGain = startFaded ? 0.0f : 1.0f;
        this.volume = this.baseVolume * this.currentGain;
        this.attenuation = Attenuation.NONE;
        this.relative = true;
        this.looping = false;
    }

    public void startFadeOut() {
        this.fadingOut = true;
    }

    public void cancelFadeOut() {
        this.fadingOut = false;
    }

    public boolean isFadingOut() {
        return this.fadingOut;
    }

    public float getCurrentGain() {
        return this.currentGain;
    }

    public void setBaseVolume(float volume) {
        this.baseVolume = volume;
        this.volume = this.baseVolume * this.currentGain;
    }

    public void stopImmediately() {
        this.currentGain = 0f;
        this.volume = 0f;
        stop();
    }

    @Override
    public void tick() {
        if (fadingOut) {
            currentGain -= fadeOutStep;
            if (currentGain <= 0.0f) {
                currentGain = 0.0f;
                this.volume = 0.0f;
                stop();
                return;
            }
        } else if (currentGain < 1.0f) {
            currentGain = Math.min(1.0f, currentGain + fadeInStep);
        }
        this.volume = baseVolume * currentGain;
    }
}
