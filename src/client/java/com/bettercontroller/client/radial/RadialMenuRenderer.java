package com.bettercontroller.client.radial;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class RadialMenuRenderer {
    public void render(MinecraftClient client, DrawContext context, RadialMenu radialMenu) {
        if (client == null || context == null || radialMenu == null || !radialMenu.isActive() || radialMenu.slots().isEmpty()) {
            return;
        }

        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        int radius = 64;
        int slotWidth = 46;
        int slotHeight = 16;

        context.fill(centerX - 88, centerY - 88, centerX + 88, centerY + 88, 0x7F000000);

        int slotCount = radialMenu.slots().size();
        for (int i = 0; i < slotCount; i++) {
            double angle = ((Math.PI * 2.0D) / slotCount) * i;
            int x = centerX + (int) (Math.cos(angle) * radius) - (slotWidth / 2);
            int y = centerY - (int) (Math.sin(angle) * radius) - (slotHeight / 2);

            boolean selected = i == radialMenu.selectedIndex();
            int fillColor = selected ? 0xCC4CAF50 : 0xAA1E1E1E;
            int borderColor = selected ? 0xFFFFFFFF : 0xFF808080;

            context.fill(x, y, x + slotWidth, y + slotHeight, fillColor);
            context.drawBorder(x, y, slotWidth, slotHeight, borderColor);
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                radialMenu.slots().get(i).label(),
                x + (slotWidth / 2),
                y + 4,
                0xFFFFFFFF
            );
        }
    }
}
