package com.bettercontroller.client;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.gui.BetterControllerSettingsScreen;
import com.bettercontroller.client.input.ControllerRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import com.bettercontroller.client.radial.RadialMenuRenderer;
import com.bettercontroller.client.render.ControllerDebugOverlayRenderer;
import com.bettercontroller.client.render.ControllerHUDRenderer;

public class BetterControllerClientMod implements ClientModInitializer {
    private final ControllerRuntime controllerRuntime = new ControllerRuntime();
    private final ControllerHUDRenderer controllerHUDRenderer = new ControllerHUDRenderer();
    private final RadialMenuRenderer radialMenuRenderer = new RadialMenuRenderer();
    private final ControllerDebugOverlayRenderer debugOverlayRenderer = new ControllerDebugOverlayRenderer();
    private boolean debugToggleLatch;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(controllerRuntime::tick);
        ClientTickEvents.END_CLIENT_TICK.register(this::handleDebugToggle);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof OptionsScreen || screen instanceof ControlsOptionsScreen || screen instanceof GameMenuScreen) {
                addSettingsButton(client, screen, scaledWidth);
            }
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            controllerRuntime.onRenderFrame(client);
            controllerHUDRenderer.render(client, drawContext, controllerRuntime);
            radialMenuRenderer.render(client, drawContext, controllerRuntime.radialMenu());
            debugOverlayRenderer.render(client, drawContext, controllerRuntime);
        });
        BetterControllerMod.LOGGER.info("Controller config path: {}", controllerRuntime.configPath());
        BetterControllerMod.LOGGER.info("BetterController client initialized.");
    }

    private void handleDebugToggle(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }

        boolean pressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
        if (pressed && !debugToggleLatch) {
            debugOverlayRenderer.toggleVisibility();
        }
        debugToggleLatch = pressed;
    }

    private void addSettingsButton(MinecraftClient client, Screen screen, int scaledWidth) {
        if (client == null || screen == null) {
            return;
        }

        String buttonLabel = "Controller Settings";
        boolean alreadyPresent = Screens.getButtons(screen).stream()
            .anyMatch(widget -> widget instanceof ButtonWidget button
                && buttonLabel.equals(button.getMessage().getString()));
        if (alreadyPresent) {
            return;
        }

        int x = scaledWidth - 152;
        int y = 6;
        int width = 146;
        if (screen instanceof ControlsOptionsScreen) {
            x = (scaledWidth / 2) - 155;
            y = screen.height - 52;
            width = 150;
        }

        ButtonWidget button = ButtonWidget.builder(
            Text.literal(buttonLabel),
            widget -> client.setScreen(new BetterControllerSettingsScreen(screen, controllerRuntime))
        ).dimensions(x, y, width, 20).build();
        Screens.getButtons(screen).add(button);
    }
}
