package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public final class ControllerGuiNavigationHooks implements GuiNavigationHooks {
    private final ControllerInventorySelectionState inventorySelectionState;
    private final GuiNavigationController navigationController;
    private final GuiInputRouter inputRouter = new GuiInputRouter();
    private final InputTranslator inputTranslator;
    private final Consumer<ControllerConfig> virtualKeyboardRequest;

    public ControllerGuiNavigationHooks(
        InputTranslator inputTranslator,
        Consumer<ControllerConfig> virtualKeyboardRequest,
        ControllerInventorySelectionState inventorySelectionState
    ) {
        this.inputTranslator = inputTranslator;
        this.virtualKeyboardRequest = virtualKeyboardRequest;
        this.inventorySelectionState = inventorySelectionState == null
            ? new ControllerInventorySelectionState()
            : inventorySelectionState;
        this.navigationController = new GuiNavigationController(this.inventorySelectionState);
    }

    @Override
    public void onScreenTick(
        MinecraftClient client,
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout
    ) {
        boolean allowConfirmRepeat = client != null && client.currentScreen instanceof ControllerVirtualKeyboardScreen;
        GuiInputFrame frame = inputRouter.route(snapshot, config, layout, inputTranslator, allowConfirmRepeat);
        if (!frame.anyNavigation()) {
            return;
        }
        navigationController.onScreenTick(client, frame, request -> {
            if (request && virtualKeyboardRequest != null) {
                virtualKeyboardRequest.accept(config);
            }
        });
    }

    public ControllerInventorySelectionState inventorySelectionState() {
        return inventorySelectionState;
    }

    public void reset() {
        inputRouter.reset();
        navigationController.reset();
    }
}
