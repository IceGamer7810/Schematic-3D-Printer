package com.deterministicprinter.integration;

import com.deterministicprinter.ui.DeterministicPrinterConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DeterministicPrinterConfigScreen::new;
    }
}
