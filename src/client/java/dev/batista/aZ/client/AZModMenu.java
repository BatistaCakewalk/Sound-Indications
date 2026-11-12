package dev.batista.aZ.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

public class AZModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // The config screen needs YACL. Without it, Mod Menu just shows no config button.
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return parent -> null;
        }

        return parent -> {
            Minecraft client = Minecraft.getInstance();
            if (client.options.directionalAudio().get()) {
                return AZConfigScreen.create(parent);
            }

            return new ConfirmScreen(
                    confirmed -> client.setScreenAndShow(confirmed ? AZConfigScreen.create(parent) : parent),
                    Component.literal("Directional Audio is required"),
                    Component.literal(
                            "Directional Audio is disabled. IndicationSounds cannot play stomach sounds " +
                            "from the player's abdomen, so they will remain camera/listener-centred. " +
                            "Continue to the config? Enable Directional Audio in Minecraft's Sound Settings " +
                            "to unlock accurately placed stomach sounds."
                    )
            );
        };
    }
}