package com.bettercontroller;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterControllerMod implements ModInitializer {
    public static final String MOD_ID = "bettercontroller";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("BetterController initialized.");
    }
}
