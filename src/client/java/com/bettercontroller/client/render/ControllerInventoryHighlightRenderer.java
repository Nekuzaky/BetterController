package com.bettercontroller.client.render;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.gui.ControllerInventorySelectionState;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws an always-visible controller selection highlight and an in-screen help panel for container navigation.
 */
public final class ControllerInventoryHighlightRenderer {
    private static final Field HANDLED_X_FIELD = resolveIntField("x");
    private static final Field HANDLED_Y_FIELD = resolveIntField("y");
    private static final Field HANDLED_BACKGROUND_WIDTH_FIELD = resolveIntField("backgroundWidth");
    private static final Field HANDLED_BACKGROUND_HEIGHT_FIELD = resolveIntField("backgroundHeight");

    private static final int GUIDE_PANEL_DEFAULT_WIDTH = 166;
    private static final int GUIDE_PANEL_COMPACT_WIDTH = 136;
    private static final int GUIDE_PANEL_ULTRA_WIDTH = 108;
    private static final int GUIDE_PANEL_MIN_WIDTH = 120;
    private static final int GUIDE_PANEL_MIN_COMPACT_WIDTH = 104;
    private static final int GUIDE_PANEL_MIN_ULTRA_WIDTH = 84;
    private static final int GUIDE_PANEL_MARGIN = 8;
    private static final int GUIDE_PANEL_PADDING_X = 8;
    private static final int GUIDE_PANEL_PADDING_Y = 7;
    private static final int GUIDE_PANEL_LINE_HEIGHT = 11;

    public void render(MinecraftClient client, DrawContext context, ControllerRuntime runtime) {
        if (client == null || context == null || runtime == null) {
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return;
        }
        if (runtime.latestSnapshot() == null || !runtime.latestSnapshot().isConnected()) {
            return;
        }
        if (runtime.latestConfig() == null || !runtime.latestConfig().autoActivateOnController) {
            return;
        }

        ControllerInventorySelectionState selectionState = runtime.inventorySelectionState();
        Slot selectedSlot = selectionState.resolveSelectedSlot(handledScreen);
        if (selectedSlot != null && selectedSlot.isEnabled()) {
            renderSelectionHighlight(handledScreen, context, selectedSlot, selectionState);
        }
        renderInventoryGuide(client, context, runtime, handledScreen, selectedSlot);
    }

    private static void renderSelectionHighlight(
        HandledScreen<?> handledScreen,
        DrawContext context,
        Slot selectedSlot,
        ControllerInventorySelectionState selectionState
    ) {
        if (handledScreen == null || context == null || selectedSlot == null || selectionState == null) {
            return;
        }

        int screenX = readInt(HANDLED_X_FIELD, handledScreen, 0);
        int screenY = readInt(HANDLED_Y_FIELD, handledScreen, 0);

        int left = screenX + selectedSlot.x - 2;
        int top = screenY + selectedSlot.y - 2;
        int width = 20;
        int height = 20;
        int right = left + width;
        int bottom = top + height;

        long now = System.currentTimeMillis();
        double pulse = 0.5D + (0.5D * Math.sin(now * 0.009D));
        double glowBreath = 0.5D + (0.5D * Math.sin(now * 0.006D + 1.45D));
        long sinceChange = Math.max(0L, now - selectionState.lastSelectionChangeMs());
        double settle = Math.min(1.0D, sinceChange / 240.0D);
        int grow = (int) Math.round((1.0D - settle) * 1.5D);
        int glowPad = 2 + (int) Math.round(glowBreath * 2.0D);

        int outerAlpha = (int) (52 + (pulse * 28));
        int borderAlpha = (int) (176 + (pulse * 54));
        int innerAlpha = (int) (28 + (settle * 22));
        int cornerAlpha = (int) (154 + (pulse * 78));

        context.fill(
            left - glowPad,
            top - glowPad,
            right + glowPad,
            bottom + glowPad,
            (outerAlpha << 24) | 0x30B8FF
        );
        context.fill(left, top, right, bottom, (innerAlpha << 24) | 0x58C8FF);
        context.drawStrokedRectangle(
            left - grow,
            top - grow,
            width + (grow * 2),
            height + (grow * 2),
            (borderAlpha << 24) | 0xE7F7FF
        );
        context.drawStrokedRectangle(left + 1, top + 1, width - 2, height - 2, 0x66FFFFFF);
        drawCornerMarkers(context, left, top, right, bottom, cornerAlpha);
    }

    private static void renderInventoryGuide(
        MinecraftClient client,
        DrawContext context,
        ControllerRuntime runtime,
        HandledScreen<?> handledScreen,
        Slot selectedSlot
    ) {
        if (client == null || client.textRenderer == null || context == null || runtime == null || handledScreen == null) {
            return;
        }

        ControllerConfig config = runtime.latestConfig();
        if (config != null && !config.hudHintsEnabled) {
            return;
        }
        ControllerConfig.ResolvedLayout layout = runtime.activeLayout();
        if (layout == null) {
            return;
        }

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int screenX = readInt(HANDLED_X_FIELD, handledScreen, 0);
        int screenY = readInt(HANDLED_Y_FIELD, handledScreen, 0);
        int handledWidth = readInt(HANDLED_BACKGROUND_WIDTH_FIELD, handledScreen, 176);
        int handledHeight = readInt(HANDLED_BACKGROUND_HEIGHT_FIELD, handledScreen, 166);

        int rightSpace = Math.max(0, screenWidth - (screenX + handledWidth) - GUIDE_PANEL_MARGIN);
        int leftSpace = Math.max(0, screenX - GUIDE_PANEL_MARGIN);
        int bestSideSpace = Math.max(rightSpace, leftSpace);
        if (bestSideSpace < GUIDE_PANEL_MIN_ULTRA_WIDTH) {
            // Do not cover the inventory when there is no side space available.
            return;
        }

        boolean compact = screenWidth <= 980 || bestSideSpace < GUIDE_PANEL_COMPACT_WIDTH;
        boolean ultraCompact = screenWidth <= 760 || bestSideSpace < GUIDE_PANEL_MIN_COMPACT_WIDTH;

        String title = ultraCompact ? "Pad" : (compact ? "Controller" : "Controller Help");
        String subtitle = compact ? "" : runtime.glyphs().activeGlyphSetName() + " layout";
        List<String> lines = buildGuideLines(layout, runtime, selectedSlot, compact, ultraCompact);

        int preferredWidth = ultraCompact
            ? GUIDE_PANEL_ULTRA_WIDTH
            : (compact ? GUIDE_PANEL_COMPACT_WIDTH : GUIDE_PANEL_DEFAULT_WIDTH);
        int minWidth = ultraCompact
            ? GUIDE_PANEL_MIN_ULTRA_WIDTH
            : (compact ? GUIDE_PANEL_MIN_COMPACT_WIDTH : GUIDE_PANEL_MIN_WIDTH);
        int maxPanelWidth = Math.max(GUIDE_PANEL_MIN_ULTRA_WIDTH, screenWidth - (GUIDE_PANEL_MARGIN * 2));
        int textWidth = maxTextWidth(client, title, subtitle, lines);
        int panelWidth = clamp(textWidth + (GUIDE_PANEL_PADDING_X * 2), minWidth, preferredWidth);
        panelWidth = Math.min(panelWidth, maxPanelWidth);
        panelWidth = Math.min(panelWidth, bestSideSpace);
        if (panelWidth < GUIDE_PANEL_MIN_ULTRA_WIDTH) {
            return;
        }

        List<String> wrappedLines = wrapLines(client, lines, Math.max(72, panelWidth - (GUIDE_PANEL_PADDING_X * 2)));
        int headerLines = subtitle.isBlank() ? 1 : 2;
        int headerGap = subtitle.isBlank() ? 2 : 4;
        int panelHeight = (GUIDE_PANEL_PADDING_Y * 2)
            + (headerLines * GUIDE_PANEL_LINE_HEIGHT)
            + headerGap
            + (wrappedLines.size() * GUIDE_PANEL_LINE_HEIGHT);
        int maxPanelHeight = Math.max(80, screenHeight - (GUIDE_PANEL_MARGIN * 2));

        if (panelHeight > maxPanelHeight && !compact) {
            compact = true;
            title = "Controller";
            subtitle = "";
            lines = buildGuideLines(layout, runtime, selectedSlot, true, false);
            preferredWidth = GUIDE_PANEL_COMPACT_WIDTH;
            minWidth = GUIDE_PANEL_MIN_COMPACT_WIDTH;
            textWidth = maxTextWidth(client, title, subtitle, lines);
            panelWidth = clamp(textWidth + (GUIDE_PANEL_PADDING_X * 2), minWidth, preferredWidth);
            panelWidth = Math.min(panelWidth, maxPanelWidth);
            panelWidth = Math.min(panelWidth, bestSideSpace);
            wrappedLines = wrapLines(client, lines, Math.max(72, panelWidth - (GUIDE_PANEL_PADDING_X * 2)));
            headerLines = 1;
            headerGap = 2;
            panelHeight = (GUIDE_PANEL_PADDING_Y * 2)
                + (headerLines * GUIDE_PANEL_LINE_HEIGHT)
                + headerGap
                + (wrappedLines.size() * GUIDE_PANEL_LINE_HEIGHT);
        }

        if (panelHeight > maxPanelHeight && !ultraCompact) {
            ultraCompact = true;
            title = "Pad";
            subtitle = "";
            lines = buildGuideLines(layout, runtime, selectedSlot, true, true);
            preferredWidth = GUIDE_PANEL_ULTRA_WIDTH;
            minWidth = GUIDE_PANEL_MIN_ULTRA_WIDTH;
            textWidth = maxTextWidth(client, title, subtitle, lines);
            panelWidth = clamp(textWidth + (GUIDE_PANEL_PADDING_X * 2), minWidth, preferredWidth);
            panelWidth = Math.min(panelWidth, maxPanelWidth);
            panelWidth = Math.min(panelWidth, bestSideSpace);
            if (panelWidth < GUIDE_PANEL_MIN_ULTRA_WIDTH) {
                return;
            }
            wrappedLines = wrapLines(client, lines, Math.max(66, panelWidth - (GUIDE_PANEL_PADDING_X * 2)));
            headerLines = 1;
            headerGap = 2;
            panelHeight = (GUIDE_PANEL_PADDING_Y * 2)
                + GUIDE_PANEL_LINE_HEIGHT
                + headerGap
                + (wrappedLines.size() * GUIDE_PANEL_LINE_HEIGHT);
        }

        while (panelHeight > maxPanelHeight && wrappedLines.size() > 3) {
            wrappedLines.remove(wrappedLines.size() - 1);
            panelHeight = (GUIDE_PANEL_PADDING_Y * 2)
                + (headerLines * GUIDE_PANEL_LINE_HEIGHT)
                + headerGap
                + (wrappedLines.size() * GUIDE_PANEL_LINE_HEIGHT);
        }

        boolean placeRight = rightSpace >= leftSpace;
        int sideSpace = placeRight ? rightSpace : leftSpace;
        if (sideSpace < panelWidth) {
            placeRight = !placeRight;
            sideSpace = placeRight ? rightSpace : leftSpace;
            panelWidth = Math.min(panelWidth, sideSpace);
            if (panelWidth < GUIDE_PANEL_MIN_ULTRA_WIDTH) {
                return;
            }
        }

        int panelX = placeRight
            ? (screenX + handledWidth + GUIDE_PANEL_MARGIN)
            : (screenX - panelWidth - GUIDE_PANEL_MARGIN);
        panelX = clamp(panelX, GUIDE_PANEL_MARGIN, screenWidth - panelWidth - GUIDE_PANEL_MARGIN);
        int panelY = clamp(
            screenY + 4,
            GUIDE_PANEL_MARGIN,
            screenHeight - panelHeight - GUIDE_PANEL_MARGIN
        );

        int panelRight = panelX + panelWidth;
        int panelBottom = panelY + panelHeight;
        context.fill(panelX, panelY, panelRight, panelBottom, 0xC10B0F14);
        context.fill(panelX, panelY, panelRight, panelY + 1, 0xAA65C4FF);
        context.fill(panelX, panelBottom - 1, panelRight, panelBottom, 0x88435B72);
        context.drawStrokedRectangle(panelX, panelY, panelWidth, panelHeight, 0x77475A6E);

        int textX = panelX + GUIDE_PANEL_PADDING_X;
        int lineY = panelY + GUIDE_PANEL_PADDING_Y;
        context.drawTextWithShadow(client.textRenderer, title, textX, lineY, 0xFFEAF6FF);
        lineY += GUIDE_PANEL_LINE_HEIGHT;
        if (!subtitle.isBlank()) {
            context.drawText(client.textRenderer, subtitle, textX, lineY, 0xFFACBFD2, false);
            lineY += GUIDE_PANEL_LINE_HEIGHT + 4;
        } else {
            lineY += 2;
        }

        for (String line : wrappedLines) {
            context.drawText(client.textRenderer, line, textX, lineY, 0xFFE8EEF7, false);
            lineY += GUIDE_PANEL_LINE_HEIGHT;
        }
    }

    private static List<String> buildGuideLines(
        ControllerConfig.ResolvedLayout layout,
        ControllerRuntime runtime,
        Slot selectedSlot,
        boolean compact,
        boolean ultraCompact
    ) {
        List<String> lines = new ArrayList<>(6);
        lines.add(ultraCompact ? ("Move: " + movementHintShort(layout)) : ("Move: " + movementHint(layout)));
        lines.add("[" + glyphForAction(layout, runtime, "menu_confirm", "A") + "] Pick / Place");
        lines.add(ultraCompact
            ? "[" + glyphForAction(layout, runtime, "menu_back", "B") + "] Back"
            : "[" + glyphForAction(layout, runtime, "menu_back", "B") + "] Cancel / Close");

        String pagePrev = glyphForAction(layout, runtime, "menu_page_prev", "LB");
        String pageNext = glyphForAction(layout, runtime, "menu_page_next", "RB");
        lines.add((compact || ultraCompact)
            ? "[" + pagePrev + "/" + pageNext + "] Page"
            : "[" + pagePrev + "/" + pageNext + "] Transfer / Page");

        if (!compact && !ultraCompact) {
            String tabPrev = glyphForAction(layout, runtime, "menu_tab_prev", "LB");
            String tabNext = glyphForAction(layout, runtime, "menu_tab_next", "RB");
            if (!tabPrev.equals(pagePrev) || !tabNext.equals(pageNext)) {
                lines.add("[" + tabPrev + "/" + tabNext + "] Tabs");
            }
        }

        if (ultraCompact) {
            return lines;
        }

        String selectedInfo = selectedSlotLabel(selectedSlot);
        if (!selectedInfo.isBlank()) {
            lines.add(selectedInfo);
        }
        return lines;
    }

    private static String glyphForAction(
        ControllerConfig.ResolvedLayout layout,
        ControllerRuntime runtime,
        String actionKey,
        String fallbackToken
    ) {
        if (layout != null) {
            for (String binding : layout.actionBindings(actionKey)) {
                if (binding == null || binding.isBlank()) {
                    continue;
                }
                return runtime.glyphs().glyphForBinding(binding);
            }
        }
        return runtime.glyphs().glyphForBinding(fallbackToken);
    }

    private static String movementHint(ControllerConfig.ResolvedLayout layout) {
        if (layout == null) {
            return "D-Pad";
        }
        boolean hasDpad = usesAnyToken(layout, token -> token.startsWith("DPAD_"));
        boolean hasLeftStick = usesAnyToken(layout, token -> "LEFT_X".equals(token) || "LEFT_Y".equals(token));

        if (hasDpad && hasLeftStick) {
            return "D-Pad / Left Stick";
        }
        if (hasLeftStick) {
            return "Left Stick";
        }
        return "D-Pad";
    }

    private static String movementHintShort(ControllerConfig.ResolvedLayout layout) {
        if (layout == null) {
            return "D-Pad";
        }
        boolean hasDpad = usesAnyToken(layout, token -> token.startsWith("DPAD_"));
        boolean hasLeftStick = usesAnyToken(layout, token -> "LEFT_X".equals(token) || "LEFT_Y".equals(token));
        if (hasDpad && hasLeftStick) {
            return "D-Pad/Stick";
        }
        if (hasLeftStick) {
            return "Stick";
        }
        return "D-Pad";
    }

    private static boolean usesAnyToken(ControllerConfig.ResolvedLayout layout, java.util.function.Predicate<String> tokenMatcher) {
        if (layout == null || tokenMatcher == null) {
            return false;
        }
        String[] actions = {"menu_up", "menu_down", "menu_left", "menu_right"};
        for (String action : actions) {
            for (String binding : layout.actionBindings(action)) {
                String token = normalizeBindingToken(binding);
                if (!token.isBlank() && tokenMatcher.test(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeBindingToken(String binding) {
        if (binding == null || binding.isBlank()) {
            return "";
        }
        String token = binding.trim().toUpperCase(Locale.ROOT);
        if (token.startsWith("-")) {
            token = token.substring(1);
        }
        return token;
    }

    private static String selectedSlotLabel(Slot selectedSlot) {
        if (selectedSlot == null) {
            return "";
        }
        if (selectedSlot.getStack() == null || selectedSlot.getStack().isEmpty()) {
            return "Selected: Empty slot";
        }
        Text stackName = selectedSlot.getStack().getName();
        String name = stackName == null ? "" : stackName.getString();
        if (name.isBlank()) {
            return "Selected: Item";
        }
        if (selectedSlot.getStack().getCount() > 1) {
            return "Selected: " + name + " x" + selectedSlot.getStack().getCount();
        }
        return "Selected: " + name;
    }

    private static int maxTextWidth(MinecraftClient client, String title, String subtitle, List<String> lines) {
        if (client == null || client.textRenderer == null) {
            return 0;
        }
        int max = 0;
        if (title != null && !title.isBlank()) {
            max = Math.max(max, client.textRenderer.getWidth(title));
        }
        if (subtitle != null && !subtitle.isBlank()) {
            max = Math.max(max, client.textRenderer.getWidth(subtitle));
        }
        if (lines != null) {
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                max = Math.max(max, client.textRenderer.getWidth(line));
            }
        }
        return max;
    }

    private static List<String> wrapLines(MinecraftClient client, List<String> lines, int maxWidth) {
        List<String> wrapped = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return wrapped;
        }
        for (String line : lines) {
            wrapped.addAll(wrapLine(client, line, maxWidth));
        }
        return wrapped;
    }

    private static List<String> wrapLine(MinecraftClient client, String line, int maxWidth) {
        List<String> parts = new ArrayList<>();
        if (client == null || client.textRenderer == null || maxWidth <= 0) {
            parts.add("");
            return parts;
        }
        if (line == null || line.isBlank()) {
            parts.add("");
            return parts;
        }

        String remaining = line.trim();
        while (!remaining.isEmpty()) {
            if (client.textRenderer.getWidth(remaining) <= maxWidth) {
                parts.add(remaining);
                break;
            }

            int splitAt = remaining.length();
            while (splitAt > 1 && client.textRenderer.getWidth(remaining.substring(0, splitAt)) > maxWidth) {
                splitAt--;
            }
            if (splitAt <= 1) {
                break;
            }

            int wordBoundary = remaining.lastIndexOf(' ', splitAt - 1);
            if (wordBoundary <= 0) {
                parts.add(remaining.substring(0, splitAt));
                remaining = remaining.substring(splitAt).trim();
            } else {
                parts.add(remaining.substring(0, wordBoundary));
                remaining = remaining.substring(wordBoundary + 1).trim();
            }
        }

        if (parts.isEmpty()) {
            parts.add(line);
        }
        return parts;
    }

    private static Field resolveIntField(String name) {
        try {
            Field field = HandledScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int readInt(Field field, HandledScreen<?> screen, int fallback) {
        if (field == null || screen == null) {
            return fallback;
        }
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException ignored) {
            return fallback;
        }
    }

    private static void drawCornerMarkers(
        DrawContext context,
        int left,
        int top,
        int right,
        int bottom,
        int alpha
    ) {
        int color = (alpha << 24) | 0xCFF0FF;
        int length = 5;

        context.fill(left - 1, top - 1, left - 1 + length, top, color);
        context.fill(left - 1, top - 1, left, top - 1 + length, color);

        context.fill(right + 1 - length, top - 1, right + 1, top, color);
        context.fill(right, top - 1, right + 1, top - 1 + length, color);

        context.fill(left - 1, bottom, left - 1 + length, bottom + 1, color);
        context.fill(left - 1, bottom + 1 - length, left, bottom + 1, color);

        context.fill(right + 1 - length, bottom, right + 1, bottom + 1, color);
        context.fill(right, bottom + 1 - length, right + 1, bottom + 1, color);
    }
}
