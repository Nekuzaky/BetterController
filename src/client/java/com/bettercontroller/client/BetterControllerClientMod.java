package com.bettercontroller.client;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.input.ControllerInputHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class BetterControllerClientMod implements ClientModInitializer {
    private final ControllerInputHandler controllerInputHandler = new ControllerInputHandler();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(controllerInputHandler::tick);
        BetterControllerMod.LOGGER.info("BetterController client initialized.");
    }
}
