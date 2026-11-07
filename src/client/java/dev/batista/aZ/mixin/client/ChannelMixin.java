package dev.batista.aZ.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import dev.batista.aZ.client.AZFilterManager;
import dev.batista.aZ.client.ChannelExemption;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class ChannelMixin implements ChannelExemption {

    @Shadow @Final private int source;

    @Unique
    private boolean indicationSounds$exempt = false;

    @Override
    public boolean indicationSounds$isExempt() {
        return this.indicationSounds$exempt;
    }

    @Override
    public void indicationSounds$setExempt(boolean exempt) {
        this.indicationSounds$exempt = exempt;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void indicationSounds$onPlay(CallbackInfo ci) {
        if (!this.indicationSounds$exempt && AZFilterManager.isMuffleActive()) {
            int filterId = AZFilterManager.getFilterId();
            if (filterId != 0) {
                AL10.alSourcei(this.source, EXTEfx.AL_DIRECT_FILTER, filterId);
                AL10.alGetError();
            }
        } else if (this.indicationSounds$exempt) {
            AL10.alSourcei(this.source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            AL10.alGetError();
        }
    }
}
