package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.input.ControllerRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BetterControllerSettingsScreen extends Screen {
    private static final int SLIDER_WIDTH = 280;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTENT_TOP = 34;
    private static final int CONTENT_BOTTOM_MARGIN = 56;
    private static final int SCROLL_STEP = 22;

    private final Screen parent;
    private final ControllerRuntime runtime;

    private float movementDeadzone;
    private float lookDeadzone;
    private float lookSensitivityX;
    private float lookSensitivityY;
    private float lookSpeedMultiplier;
    private float triggerThreshold;
    private float menuAxisThreshold;
    private int menuInitialRepeatDelayMs;
    private int menuRepeatIntervalMs;
    private float cameraSmoothingStrength;
    private boolean hudHintsEnabled;
    private boolean cameraSmoothing;
    private String lookResponseCurve;
    private boolean dirty;

    private ConfigSlider movementDeadzoneSlider;
    private ConfigSlider lookDeadzoneSlider;
    private ConfigSlider sensitivityXSlider;
    private ConfigSlider sensitivityYSlider;
    private ConfigSlider lookSpeedSlider;
    private ConfigSlider triggerThresholdSlider;
    private ConfigSlider menuAxisThresholdSlider;
    private ConfigSlider menuInitialDelaySlider;
    private ConfigSlider menuRepeatIntervalSlider;
    private ConfigSlider smoothingStrengthSlider;
    private ButtonWidget hudToggleButton;
    private ButtonWidget smoothingToggleButton;
    private ButtonWidget responseCurveButton;
    private ButtonWidget doneButton;
    private final List<ScrollEntry> scrollEntries = new ArrayList<>();
    private int scrollOffset;
    private int maxScrollOffset;

    private String statusMessage = "";
    private int statusColor = 0xFFD1D5DB;

    public BetterControllerSettingsScreen(Screen parent, ControllerRuntime runtime) {
        super(Text.literal("BetterController Settings"));
        this.parent = parent;
        this.runtime = runtime;
        loadFromConfig();
    }

    @Override
    protected void init() {
        scrollEntries.clear();
        scrollOffset = 0;
        maxScrollOffset = 0;

        int centerX = this.width / 2;
        int y = 34;

        movementDeadzoneSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            movementDeadzone, 0.0F, 0.40F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Movement Deadzone: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                movementDeadzone = value;
                markDirty();
            }
        };
        addScrollableChild(movementDeadzoneSlider, y);
        y += 22;

        lookDeadzoneSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookDeadzone, 0.0F, 0.40F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Look Deadzone: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookDeadzone = value;
                markDirty();
            }
        };
        addScrollableChild(lookDeadzoneSlider, y);
        y += 22;

        sensitivityXSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSensitivityX, 1.0F, 80.0F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Look Sensitivity X: %.1f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSensitivityX = value;
                markDirty();
            }
        };
        addScrollableChild(sensitivityXSlider, y);
        y += 22;

        sensitivityYSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSensitivityY, 1.0F, 80.0F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Look Sensitivity Y: %.1f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSensitivityY = value;
                markDirty();
            }
        };
        addScrollableChild(sensitivityYSlider, y);
        y += 22;

        lookSpeedSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSpeedMultiplier, 0.5F, 4.0F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Look Speed Multiplier: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSpeedMultiplier = value;
                markDirty();
            }
        };
        addScrollableChild(lookSpeedSlider, y);
        y += 22;

        triggerThresholdSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            triggerThreshold, 0.05F, 1.0F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Trigger Threshold: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                triggerThreshold = value;
                markDirty();
            }
        };
        addScrollableChild(triggerThresholdSlider, y);
        y += 22;

        menuAxisThresholdSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            menuAxisThreshold, 0.2F, 0.8F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Menu Stick Threshold: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                menuAxisThreshold = value;
                markDirty();
            }
        };
        addScrollableChild(menuAxisThresholdSlider, y);
        y += 22;

        menuInitialDelaySlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            menuInitialRepeatDelayMs, 60.0F, 350.0F
        ) {
            @Override
            protected String label(float value) {
                return "Menu Initial Repeat Delay: " + Math.round(value) + " ms";
            }

            @Override
            protected void onValueChanged(float value) {
                menuInitialRepeatDelayMs = Math.round(value);
                markDirty();
            }
        };
        addScrollableChild(menuInitialDelaySlider, y);
        y += 22;

        menuRepeatIntervalSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            menuRepeatIntervalMs, 20.0F, 140.0F
        ) {
            @Override
            protected String label(float value) {
                return "Menu Repeat Interval: " + Math.round(value) + " ms";
            }

            @Override
            protected void onValueChanged(float value) {
                menuRepeatIntervalMs = Math.round(value);
                markDirty();
            }
        };
        addScrollableChild(menuRepeatIntervalSlider, y);
        y += 22;

        smoothingStrengthSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            cameraSmoothingStrength, 0.0F, 1.0F
        ) {
            @Override
            protected String label(float value) {
                return String.format(Locale.ROOT, "Camera Smoothing Strength: %.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                cameraSmoothingStrength = value;
                markDirty();
            }
        };
        addScrollableChild(smoothingStrengthSlider, y);
        y += 26;

        hudToggleButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(hudToggleLabel()),
            button -> {
                hudHintsEnabled = !hudHintsEnabled;
                button.setMessage(Text.literal(hudToggleLabel()));
                markDirty();
            }
        ).dimensions(centerX - 140, y, 136, CONTROL_HEIGHT).build(), y);

        smoothingToggleButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(smoothingToggleLabel()),
            button -> {
                cameraSmoothing = !cameraSmoothing;
                button.setMessage(Text.literal(smoothingToggleLabel()));
                markDirty();
            }
        ).dimensions(centerX + 4, y, 136, CONTROL_HEIGHT).build(), y);
        y += 24;

        responseCurveButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(responseCurveLabel()),
            button -> {
                lookResponseCurve = nextCurve(lookResponseCurve);
                button.setMessage(Text.literal(responseCurveLabel()));
                markDirty();
            }
        ).dimensions(centerX - 140, y, 280, CONTROL_HEIGHT).build(), y);
        y += 24;

        addScrollableChild(ButtonWidget.builder(
            Text.literal("Ultra Fluid Preset"),
            button -> applyUltraFluidPresetLocally()
        ).dimensions(centerX - 140, y, 136, CONTROL_HEIGHT).build(), y);

        addScrollableChild(ButtonWidget.builder(
            Text.literal("Apply"),
            button -> applyChanges("Controller settings applied.", 0xFF9FE870)
        ).dimensions(centerX + 4, y, 136, CONTROL_HEIGHT).build(), y);

        doneButton = addDrawableChild(ButtonWidget.builder(
            Text.literal("Done"),
            button -> close()
        ).dimensions(centerX - 140, this.height - 26, 280, CONTROL_HEIGHT).build());

        recalculateScrollBounds();
        updateScrollPositions();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Pro controller tuning (camera, menu navigation, HUD)"),
            centerX,
            22,
            0xFFB8C1CC
        );

        if (maxScrollOffset > 0) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("^"), centerX, CONTENT_TOP - 12, 0xFFAAB4C0);
            }
            if (scrollOffset < maxScrollOffset) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("v"), centerX, this.height - CONTENT_BOTTOM_MARGIN + 4, 0xFFAAB4C0);
            }
        }

        if (!statusMessage.isBlank()) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(statusMessage),
                centerX,
                this.height - 40,
                statusColor
            );
        }
    }

    @Override
    public void close() {
        if (dirty) {
            applyChanges("Controller settings saved.", 0xFF9FE870);
        }
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            return scrollBy(SCROLL_STEP * 3);
        }
        if (input.key() == GLFW.GLFW_KEY_PAGE_UP) {
            return scrollBy(-SCROLL_STEP * 3);
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0.0D) {
            return scrollBy(-SCROLL_STEP);
        }
        if (verticalAmount < 0.0D) {
            return scrollBy(SCROLL_STEP);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public void ensureWidgetVisible(ClickableWidget widget) {
        if (widget == null || scrollEntries.isEmpty()) {
            return;
        }
        ScrollEntry target = null;
        for (ScrollEntry entry : scrollEntries) {
            if (entry.widget == widget) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            return;
        }

        int visibleTop = CONTENT_TOP;
        int visibleBottom = this.height - CONTENT_BOTTOM_MARGIN - CONTROL_HEIGHT;
        int widgetTop = target.baseY - scrollOffset;
        int widgetBottom = widgetTop + CONTROL_HEIGHT;
        if (widgetTop < visibleTop) {
            scrollBy(widgetTop - visibleTop);
        } else if (widgetBottom > visibleBottom) {
            scrollBy(widgetBottom - visibleBottom);
        }
    }

    public void scrollByController(int delta) {
        scrollBy(delta);
    }

    private void applyUltraFluidPresetLocally() {
        movementDeadzone = 0.08F;
        lookDeadzone = 0.03F;
        lookSensitivityX = 18.0F;
        lookSensitivityY = 16.5F;
        lookSpeedMultiplier = 2.6F;
        triggerThreshold = 0.30F;
        menuAxisThreshold = 0.30F;
        menuInitialRepeatDelayMs = 100;
        menuRepeatIntervalMs = 38;
        lookResponseCurve = "linear";
        cameraSmoothing = false;
        cameraSmoothingStrength = 0.12F;
        hudHintsEnabled = true;

        syncWidgetsFromState();
        markDirty();
        statusMessage = "Ultra fluid preset loaded. Click Apply or Done.";
        statusColor = 0xFF7DD3FC;
    }

    private void applyChanges(String message, int color) {
        runtime.updateConfig(config -> {
            config.movementDeadzone = movementDeadzone;
            config.lookDeadzone = lookDeadzone;
            config.lookSensitivityX = lookSensitivityX;
            config.lookSensitivityY = lookSensitivityY;
            config.lookSpeedMultiplier = lookSpeedMultiplier;
            config.triggerThreshold = triggerThreshold;
            config.menuAxisThreshold = menuAxisThreshold;
            config.menuInitialRepeatDelayMs = menuInitialRepeatDelayMs;
            config.menuRepeatIntervalMs = menuRepeatIntervalMs;
            config.cameraSmoothing = cameraSmoothing;
            config.cameraSmoothingStrength = cameraSmoothingStrength;
            config.lookResponseCurve = lookResponseCurve;
            config.hudHintsEnabled = hudHintsEnabled;
        });
        dirty = false;
        statusMessage = message;
        statusColor = color;
    }

    private void loadFromConfig() {
        ControllerConfig config = runtime.latestConfig();
        if (config == null) {
            config = ControllerConfig.createDefault();
        }
        movementDeadzone = config.movementDeadzone;
        lookDeadzone = config.lookDeadzone;
        lookSensitivityX = config.lookSensitivityX;
        lookSensitivityY = config.lookSensitivityY;
        lookSpeedMultiplier = config.lookSpeedMultiplier;
        triggerThreshold = config.triggerThreshold;
        menuAxisThreshold = config.menuAxisThreshold;
        menuInitialRepeatDelayMs = config.menuInitialRepeatDelayMs;
        menuRepeatIntervalMs = config.menuRepeatIntervalMs;
        cameraSmoothing = config.cameraSmoothing;
        cameraSmoothingStrength = config.cameraSmoothingStrength;
        lookResponseCurve = config.lookResponseCurve == null || config.lookResponseCurve.isBlank()
            ? "linear"
            : config.lookResponseCurve;
        hudHintsEnabled = config.hudHintsEnabled;
    }

    private void syncWidgetsFromState() {
        if (movementDeadzoneSlider != null) movementDeadzoneSlider.setCurrentValue(movementDeadzone);
        if (lookDeadzoneSlider != null) lookDeadzoneSlider.setCurrentValue(lookDeadzone);
        if (sensitivityXSlider != null) sensitivityXSlider.setCurrentValue(lookSensitivityX);
        if (sensitivityYSlider != null) sensitivityYSlider.setCurrentValue(lookSensitivityY);
        if (lookSpeedSlider != null) lookSpeedSlider.setCurrentValue(lookSpeedMultiplier);
        if (triggerThresholdSlider != null) triggerThresholdSlider.setCurrentValue(triggerThreshold);
        if (menuAxisThresholdSlider != null) menuAxisThresholdSlider.setCurrentValue(menuAxisThreshold);
        if (menuInitialDelaySlider != null) menuInitialDelaySlider.setCurrentValue(menuInitialRepeatDelayMs);
        if (menuRepeatIntervalSlider != null) menuRepeatIntervalSlider.setCurrentValue(menuRepeatIntervalMs);
        if (smoothingStrengthSlider != null) smoothingStrengthSlider.setCurrentValue(cameraSmoothingStrength);
        if (hudToggleButton != null) hudToggleButton.setMessage(Text.literal(hudToggleLabel()));
        if (smoothingToggleButton != null) smoothingToggleButton.setMessage(Text.literal(smoothingToggleLabel()));
        if (responseCurveButton != null) responseCurveButton.setMessage(Text.literal(responseCurveLabel()));
    }

    private String hudToggleLabel() {
        return hudHintsEnabled ? "HUD: ON" : "HUD: OFF";
    }

    private String smoothingToggleLabel() {
        return cameraSmoothing ? "Smoothing: ON" : "Smoothing: OFF";
    }

    private String responseCurveLabel() {
        return "Look Curve: " + lookResponseCurve;
    }

    private void markDirty() {
        dirty = true;
        if (statusMessage.isBlank()) {
            statusMessage = "Unsaved changes.";
            statusColor = 0xFFEFC56F;
        }
    }

    private <T extends ClickableWidget> T addScrollableChild(T widget, int baseY) {
        addDrawableChild(widget);
        scrollEntries.add(new ScrollEntry(widget, baseY));
        return widget;
    }

    private void recalculateScrollBounds() {
        int contentBottom = 0;
        for (ScrollEntry entry : scrollEntries) {
            contentBottom = Math.max(contentBottom, entry.baseY + CONTROL_HEIGHT);
        }
        int visibleBottom = this.height - CONTENT_BOTTOM_MARGIN;
        maxScrollOffset = Math.max(0, contentBottom - visibleBottom);
        if (scrollOffset > maxScrollOffset) {
            scrollOffset = maxScrollOffset;
        }
    }

    private void updateScrollPositions() {
        for (ScrollEntry entry : scrollEntries) {
            entry.widget.setY(entry.baseY - scrollOffset);
        }
    }

    private boolean scrollBy(int delta) {
        if (maxScrollOffset <= 0) {
            return false;
        }
        int next = Math.max(0, Math.min(maxScrollOffset, scrollOffset + delta));
        if (next == scrollOffset) {
            return false;
        }
        scrollOffset = next;
        updateScrollPositions();
        return true;
    }

    private static String nextCurve(String current) {
        String normalized = current == null ? "linear" : current.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "linear" -> "exponential_light";
            case "exponential_light" -> "exponential_strong";
            default -> "linear";
        };
    }

    private static abstract class ConfigSlider extends SliderWidget {
        private final float min;
        private final float max;

        protected ConfigSlider(int x, int y, int width, int height, float value, float min, float max) {
            super(x, y, width, height, Text.empty(), normalize(value, min, max));
            this.min = min;
            this.max = max;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label(currentValue())));
        }

        @Override
        protected void applyValue() {
            onValueChanged(currentValue());
        }

        protected void setCurrentValue(float newValue) {
            this.value = normalize(newValue, min, max);
            updateMessage();
            onValueChanged(currentValue());
        }

        protected float currentValue() {
            return (float) (min + (max - min) * value);
        }

        private static double normalize(float v, float min, float max) {
            float clamped = Math.max(min, Math.min(max, v));
            return (clamped - min) / (max - min);
        }

        protected abstract String label(float value);

        protected abstract void onValueChanged(float value);
    }

    private static final class ScrollEntry {
        private final ClickableWidget widget;
        private final int baseY;

        private ScrollEntry(ClickableWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }
}
