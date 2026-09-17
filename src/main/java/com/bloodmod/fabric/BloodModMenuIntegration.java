//? if fabric {
package com.bloodmod.fabric;

import com.bloodmod.gui.BloodConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class BloodModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BloodConfigScreen::new;
    }
}
//?}
