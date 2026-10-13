//? if fabric {
package com.simpleblood.fabric;

import com.simpleblood.gui.BloodConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu integration for Simple Blood (Fabric only): opens the built-in settings screen. */
public class SimpleBloodMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BloodConfigScreen::new;
    }
}
//?}
