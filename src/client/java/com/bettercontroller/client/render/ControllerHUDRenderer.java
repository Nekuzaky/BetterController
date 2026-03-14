package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ControllerHUDRenderer {
    private static final int CHIP_HEIGHT = 12;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_PADDING_X = 5;

    public void render(MinecraftClient client, DrawContext context, ControllerRuntime runtime) {
        if (client == null || context == null || runtime == null) {
            return;
        }

        ControllerConfig config = runtime.latestConfig();
        if (config == null || !config.hudHintsEnabled) {
            return;
        }
        if (client.currentScreen != null || client.player == null || client.world == null) {
            return;
        }
        if (runtime.latestSnapshot() == null || !runtime.latestSnapshot().isConnected()) {
            return;
        }

        ControllerConfig.ResolvedLayout layout = runtime.activeLayout();
        if (layout == null) {
            return;
        }

        List<Prompt> prompts = buildPrompts(client, layout);
        if (prompts.isEmpty()) {
            return;
        }

        int totalWidth = 0;
        int[] widths = new int[prompts.size()];
        for (int i = 0; i < prompts.size(); i++) {
            Prompt prompt = prompts.get(i);
            String buttonText = "[" + displayBindingToken(prompt.button()) + "]";
            int width = (CHIP_PADDING_X * 2)
                + client.textRenderer.getWidth(buttonText)
                + 4
                + client.textRenderer.getWidth(prompt.label());
            widths[i] = width;
            totalWidth += width;
            if (i > 0) {
                totalWidth += CHIP_GAP;
            }
        }

        int x = (context.getScaledWindowWidth() - totalWidth) / 2;
        int y = context.getScaledWindowHeight() - 48;
        int accent = 0xFFE2E8F0;

        for (int i = 0; i < prompts.size(); i++) {
            Prompt prompt = prompts.get(i);
            int width = widths[i];
            String buttonText = "[" + displayBindingToken(prompt.button()) + "]";

            context.fill(x, y, x + width, y + CHIP_HEIGHT, 0x8C0B0F14);
            context.fill(x, y + CHIP_HEIGHT - 1, x + width, y + CHIP_HEIGHT, 0x664E647A);

            int buttonX = x + CHIP_PADDING_X;
            int labelX = buttonX + client.textRenderer.getWidth(buttonText) + 4;
            context.drawText(client.textRenderer, buttonText, buttonX, y + 2, accent, false);
            context.drawText(client.textRenderer, prompt.label(), labelX, y + 2, 0xFFF7FAFF, false);
            x += width + CHIP_GAP;
        }
    }

    private static List<Prompt> buildPrompts(MinecraftClient client, ControllerConfig.ResolvedLayout layout) {
        List<Prompt> prompts = new ArrayList<>(4);
        prompts.add(new Prompt(firstBinding(layout, "jump"), "Jump"));
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
            prompts.add(new Prompt(firstBinding(layout, "attack"), "Mine/Attack"));
            prompts.add(new Prompt(firstBinding(layout, "use"), "Use"));
        } else {
            prompts.add(new Prompt(firstBinding(layout, "use"), "Use"));
        }
        prompts.add(new Prompt(firstBinding(layout, "inventory"), "Inventory"));
        return prompts;
    }

    private static String firstBinding(ControllerConfig.ResolvedLayout layout, String action) {
        if (layout == null || layout.actionBindings(action).isEmpty()) {
            return "?";
        }
        return layout.actionBindings(action).get(0);
    }

    private static String displayBindingToken(String token) {
        if (token == null || token.isBlank()) {
            return "?";
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        return switch (normalized) {
            case "DPAD_UP" -> "D-UP";
            case "DPAD_DOWN" -> "D-DOWN";
            case "DPAD_LEFT" -> "D-LEFT";
            case "DPAD_RIGHT" -> "D-RIGHT";
            case "LEFT_TRIGGER" -> "LT";
            case "RIGHT_TRIGGER" -> "RT";
            case "LEFT_BUMPER" -> "LB";
            case "RIGHT_BUMPER" -> "RB";
            case "LEFT_STICK" -> "L3";
            case "RIGHT_STICK" -> "R3";
            default -> normalized
                .replace("SWITCH_", "")
                .replace("NINTENDO_", "")
                .replace('_', ' ');
        };
    }

    private record Prompt(String button, String label) {
    }
}
