package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;

public final class ControllerGuiNavigationHooks implements GuiNavigationHooks {
    private final GuiNavigationController navigationController = new GuiNavigationController();
    private final GuiInputRouter inputRouter = new GuiInputRouter();
    private final InputTranslator inputTranslator;

    public ControllerGuiNavigationHooks(InputTranslator inputTranslator) {
        this.inputTranslator = inputTranslator;
    }

    @Override
    public void onScreenTick(
        MinecraftClient client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        GuiInputFrame frame = inputRouter.route(snapshot, config, layout, inputTranslator);
        if (!frame.anyNavigation()) {
            return;
        }
        navigationController.onScreenTick(client, frame);
    }

    public void reset() {
        inputRouter.reset();
        navigationController.reset();
    }
}
