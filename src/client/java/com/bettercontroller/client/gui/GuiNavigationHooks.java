package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import net.minecraft.client.MinecraftClient;

public interface GuiNavigationHooks {
    void onScreenTick(
        MinecraftClient client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    );
}
