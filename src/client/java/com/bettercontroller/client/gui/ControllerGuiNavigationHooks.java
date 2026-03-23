package com.bettercontroller.client.gui;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.InputTranslator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Consumer;

public final class ControllerGuiNavigationHooks implements GuiNavigationHooks {
    private final ControllerInventorySelectionState inventorySelectionState;
    private final GuiNavigationController navigationController;
    private final GuiInputRouter inputRouter = new GuiInputRouter();
    private final InputTranslator inputTranslator;
    private final Consumer<ControllerConfig> virtualKeyboardRequest;
    private long debugTickCount;
    private long debugNoNavigationTickCount;
    private long debugLastSummaryLogMs;
    private Screen debugLastScreen;

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
        if (client == null || client.currentScreen == null || snapshot == null || config == null || layout == null) {
            return;
        }

        boolean allowConfirmRepeat = client != null && client.currentScreen instanceof ControllerVirtualKeyboardScreen;
        GuiInputFrame frame = inputRouter.route(snapshot, config, layout, inputTranslator, allowConfirmRepeat);
        debugTickCount++;
        if (!frame.anyNavigation()) {
            debugNoNavigationTickCount++;
        }
        long now = System.currentTimeMillis();
        if (client.currentScreen != debugLastScreen) {
            debugLastScreen = client.currentScreen;
            BetterControllerMod.LOGGER.info(
                "[GUI-HOTFIX] hooks screen={} anyNav={} connected={}",
                client.currentScreen.getClass().getSimpleName(),
                frame.anyNavigation(),
                snapshot.isConnected()
            );
        } else if ((now - debugLastSummaryLogMs) >= 2000L) {
            debugLastSummaryLogMs = now;
            BetterControllerMod.LOGGER.info(
                "[GUI-HOTFIX] hooks ticks={} noNavTicks={} screen={}",
                debugTickCount,
                debugNoNavigationTickCount,
                client.currentScreen.getClass().getSimpleName()
            );
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
