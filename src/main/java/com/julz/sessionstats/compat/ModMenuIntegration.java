package com.julz.sessionstats.compat;

import com.julz.sessionstats.client.SessionStatsConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the config button in Mod Menu. Only loaded by Mod Menu itself. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SessionStatsConfigScreen::new;
    }
}
