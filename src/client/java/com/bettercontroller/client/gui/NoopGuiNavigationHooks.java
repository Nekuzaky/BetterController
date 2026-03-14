package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import net.minecraft.client.MinecraftClient;

public final class NoopGuiNavigationHooks implements GuiNavigationHooks {
    @Override
    public void onScreenTick(
        MinecraftClient client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        // Placeholder hook for future GUI navigation logic.
    }
}
