package dev.batista.aZ.client.voicechat;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import dev.batista.aZ.client.AZFilterManager;

/**
 * Optional Simple Voice Chat integration.
 * Automatically registered via the "voicechat" entrypoint in fabric.mod.json.
 * Muffles other players' voices in proximity chat when the player is at low health.
 */
public class AZVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "a-z";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.class, event -> {
            AZFilterManager.onVoiceChatSource(event.getSource());
        });
    }
}
