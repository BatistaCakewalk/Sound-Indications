package dev.batista.aZ.mixin.client;

import dev.batista.aZ.client.AZFilterManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "play", at = @At("RETURN"))
    private void indicationSounds$onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        SoundEngine.PlayResult result = cir.getReturnValue();
        if (result == SoundEngine.PlayResult.STARTED || result == SoundEngine.PlayResult.STARTED_SILENTLY) {
            AZFilterManager.onSoundStarted((SoundEngine) (Object) this, sound);
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void indicationSounds$onDestroy(CallbackInfo ci) {
        AZFilterManager.reset();
    }
}
