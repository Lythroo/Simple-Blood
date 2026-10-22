//? if fabric {
package com.simpleblood.fabric;

import com.simpleblood.gui.BloodConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class SimpleBloodMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BloodConfigScreen::new;
    }
}
//?}
