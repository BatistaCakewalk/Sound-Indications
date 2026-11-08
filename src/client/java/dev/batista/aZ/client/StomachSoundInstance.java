package dev.batista.aZ.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** A one-shot stomach sound that follows the local player's abdomen. */
public final class StomachSoundInstance extends AbstractTickableSoundInstance {

    private final LocalPlayer player;

    public StomachSoundInstance(LocalPlayer player, SoundEvent event, float volume, float pitch) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.relative = false;
        this.attenuation = Attenuation.LINEAR;
        updatePosition();
    }

    @Override
    public void tick() {
        if (player == null || player.isRemoved() || !player.isAlive()) {
            stop();
            return;
        }
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    private void updatePosition() {
        // Place the source slightly in front of the torso, not merely beneath
        // the camera. This gives the sound engine a clearer body-level origin.
        float bodyYaw = player.getYRot() * Mth.DEG_TO_RAD;
        float forwardOffset = 0.28f;
        this.x = player.getX() - Mth.sin(bodyYaw) * forwardOffset;
        this.y = player.getY() + player.getBbHeight() * 0.48;
        this.z = player.getZ() + Mth.cos(bodyYaw) * forwardOffset;
    }
}
