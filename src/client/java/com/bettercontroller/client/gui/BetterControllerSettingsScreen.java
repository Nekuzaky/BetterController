package com.bettercontroller.client.gui;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.config.ControllerPreset;
import com.bettercontroller.client.input.ControllerRuntime;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

public final class BetterControllerSettingsScreen extends Screen {
    private static final int SLIDER_WIDTH = 286;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTENT_TOP = 72;
    private static final int CONTENT_BOTTOM_MARGIN = 62;
    private static final int SCROLL_STEP = 22;
    private static final int SECTION_HEADER_HEIGHT = 18;
    private static final int SECTION_GAP = 14;

    private final Screen parent;
    private final ControllerRuntime runtime;

    private float leftStickDeadzone;
    private float rightStickDeadzone;
    private float lookAntiDeadzone;
    private float lookSensitivityX;
    private float lookSensitivityY;
    private float lookSpeedMultiplier;
    private float triggerThreshold;
    private float menuAxisThreshold;
    private float cameraSmoothingStrength;
    private boolean invertLookY;
    private boolean hudHintsEnabled;
    private boolean debugOverlayEnabled;
    private boolean cameraSmoothing;
    private String lookResponseCurve;
    private String activePresetId;
    private boolean dirty;
    private boolean syncingWidgets;

    private ConfigSlider leftDeadzoneSlider;
    private ConfigSlider rightDeadzoneSlider;
    private ConfigSlider antiDeadzoneSlider;
    private ConfigSlider sensitivityXSlider;
    private ConfigSlider sensitivityYSlider;
    private ConfigSlider speedSlider;
    private ConfigSlider triggerThresholdSlider;
    private ConfigSlider menuThresholdSlider;
    private ConfigSlider smoothingStrengthSlider;
    private ButtonWidget invertYToggleButton;
    private ButtonWidget hudToggleButton;
    private ButtonWidget debugOverlayButton;
    private ButtonWidget smoothingToggleButton;
    private ButtonWidget responseCurveButton;
    private ButtonWidget doneButton;
    private final List<ScrollEntry> scrollEntries = new ArrayList<>();
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();
    private final IdentityHashMap<ClickableWidget, String> widgetHints = new IdentityHashMap<>();
    private final IdentityHashMap<ClickableWidget, String> widgetCategories = new IdentityHashMap<>();
    private int scrollOffset;
    private int maxScrollOffset;

    private String statusMessage = "";
    private int statusColor = 0xFFD1D5DB;

    public BetterControllerSettingsScreen(Screen parent, ControllerRuntime runtime) {
        super(Text.translatable("bettercontroller.screen.settings.title"));
        this.parent = parent;
        this.runtime = runtime;
        loadFromConfig();
    }

    @Override
    protected void init() {
        scrollEntries.clear();
        sectionHeaders.clear();
        widgetHints.clear();
        widgetCategories.clear();
        scrollOffset = 0;
        maxScrollOffset = 0;

        final String inputCategory = "Input";
        final String cameraCategory = "Camera";
        final String interfaceCategory = "Interface";

        int centerX = this.width / 2;
        int y = CONTENT_TOP;

        y = addSectionHeader(
            "Input",
            "Movement deadzones, trigger threshold, and menu stick behavior.",
            y
        );

        leftDeadzoneSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            leftStickDeadzone, 0.0F, 0.40F
        ) {
            @Override
            protected String label(float value) {
                return "Left Stick Deadzone";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                leftStickDeadzone = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            leftDeadzoneSlider,
            y,
            inputCategory,
            "Filters tiny accidental movement on the left stick."
        );
        y += 22;

        rightDeadzoneSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            rightStickDeadzone, 0.0F, 0.40F
        ) {
            @Override
            protected String label(float value) {
                return "Right Stick Deadzone";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                rightStickDeadzone = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            rightDeadzoneSlider,
            y,
            inputCategory,
            "Controls how much right-stick movement is ignored near center."
        );
        y += 22;

        triggerThresholdSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            triggerThreshold, 0.05F, 1.0F
        ) {
            @Override
            protected String label(float value) {
                return "Trigger Threshold";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                triggerThreshold = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            triggerThresholdSlider,
            y,
            inputCategory,
            "Sets how far triggers must be pressed before actions fire."
        );
        y += 22;

        menuThresholdSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            menuAxisThreshold, 0.2F, 0.8F
        ) {
            @Override
            protected String label(float value) {
                return "Menu Stick Threshold";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                menuAxisThreshold = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            menuThresholdSlider,
            y,
            inputCategory,
            "Higher values reduce accidental menu movement from analog drift."
        );
        y += SECTION_GAP;

        y = addSectionHeader(
            "Camera",
            "Look response, sensitivity, smoothing, and inversion.",
            y
        );

        antiDeadzoneSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookAntiDeadzone, 0.0F, 0.20F
        ) {
            @Override
            protected String label(float value) {
                return "Look Anti-Deadzone";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.3f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookAntiDeadzone = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            antiDeadzoneSlider,
            y,
            cameraCategory,
            "Adds a minimum look response to keep aiming responsive after deadzone."
        );
        y += 22;

        sensitivityXSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSensitivityX, 1.0F, 80.0F
        ) {
            @Override
            protected String label(float value) {
                return "X Look Sensitivity";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.1f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSensitivityX = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            sensitivityXSlider,
            y,
            cameraCategory,
            "Horizontal camera speed."
        );
        y += 22;

        sensitivityYSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSensitivityY, 1.0F, 80.0F
        ) {
            @Override
            protected String label(float value) {
                return "Y Look Sensitivity";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.1f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSensitivityY = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            sensitivityYSlider,
            y,
            cameraCategory,
            "Vertical camera speed."
        );
        y += 22;

        speedSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            lookSpeedMultiplier, 0.5F, 4.0F
        ) {
            @Override
            protected String label(float value) {
                return "Camera Turn Multiplier";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                lookSpeedMultiplier = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            speedSlider,
            y,
            cameraCategory,
            "Global multiplier for right-stick camera turning speed."
        );
        y += 22;

        smoothingStrengthSlider = new ConfigSlider(
            centerX - (SLIDER_WIDTH / 2), y, SLIDER_WIDTH, CONTROL_HEIGHT,
            cameraSmoothingStrength, 0.0F, 1.0F
        ) {
            @Override
            protected String label(float value) {
                return "Camera Smoothing Amount";
            }

            @Override
            protected String valueText(float value) {
                return String.format(Locale.ROOT, "%.2f", value);
            }

            @Override
            protected void onValueChanged(float value) {
                cameraSmoothingStrength = value;
                onMutableChange();
            }
        };
        addScrollableChild(
            smoothingStrengthSlider,
            y,
            cameraCategory,
            "Blends camera movement to reduce jitter near deadzone."
        );
        y += 26;

        invertYToggleButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(invertYLabel()),
            button -> {
                invertLookY = !invertLookY;
                button.setMessage(Text.literal(invertYLabel()));
                onMutableChange();
            }
        ).dimensions(centerX - 143, y, 140, CONTROL_HEIGHT).build(),
            y,
            cameraCategory,
            "Inverts vertical camera axis."
        );

        smoothingToggleButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(smoothingToggleLabel()),
            button -> {
                cameraSmoothing = !cameraSmoothing;
                button.setMessage(Text.literal(smoothingToggleLabel()));
                onMutableChange();
            }
        ).dimensions(centerX + 3, y, 140, CONTROL_HEIGHT).build(),
            y,
            cameraCategory,
            "Enables camera smoothing for stick look input."
        );
        y += 24;

        responseCurveButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(responseCurveLabel()),
            button -> {
                lookResponseCurve = nextCurve(lookResponseCurve);
                button.setMessage(Text.literal(responseCurveLabel()));
                onMutableChange();
            }
        ).dimensions(centerX - 143, y, 286, CONTROL_HEIGHT).build(),
            y,
            cameraCategory,
            "Changes right-stick curve: linear, light exponential, or strong exponential."
        );
        y += SECTION_GAP;

        y = addSectionHeader(
            "Interface",
            "HUD hints, diagnostics overlay, and quick presets.",
            y
        );

        hudToggleButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(hudToggleLabel()),
            button -> {
                hudHintsEnabled = !hudHintsEnabled;
                button.setMessage(Text.literal(hudToggleLabel()));
                onMutableChange();
            }
        ).dimensions(centerX - 143, y, 140, CONTROL_HEIGHT).build(),
            y,
            interfaceCategory,
            "Shows contextual controller hints in-game."
        );

        debugOverlayButton = addScrollableChild(ButtonWidget.builder(
            Text.literal(debugOverlayLabel()),
            button -> {
                debugOverlayEnabled = !debugOverlayEnabled;
                button.setMessage(Text.literal(debugOverlayLabel()));
                onMutableChange();
            }
        ).dimensions(centerX + 3, y, 140, CONTROL_HEIGHT).build(),
            y,
            interfaceCategory,
            "Enables the in-game diagnostics overlay."
        );
        y += 24;

        addPresetButtons(centerX, y, interfaceCategory);
        y += 24;

        addScrollableChild(ButtonWidget.builder(
            Text.literal("Reset Defaults"),
            button -> resetDefaultsNow()
        ).dimensions(centerX - 143, y, 140, CONTROL_HEIGHT).build(),
            y,
            interfaceCategory,
            "Restores all controller settings to safe defaults."
        );

        addScrollableChild(ButtonWidget.builder(
            Text.literal("Apply Changes"),
            button -> applyChanges("Controller settings applied.", 0xFF9FE870)
        ).dimensions(centerX + 3, y, 140, CONTROL_HEIGHT).build(),
            y,
            interfaceCategory,
            "Saves your current tuning to disk."
        );

        doneButton = addDrawableChild(ButtonWidget.builder(
            Text.literal("Done"),
            button -> close()
        ).dimensions(centerX - 143, this.height - 26, 286, CONTROL_HEIGHT).build());
        widgetCategories.put(doneButton, "Interface");
        widgetHints.put(doneButton, "Close settings and keep your latest controller tuning.");

        recalculateScrollBounds();
        updateScrollPositions();
        syncWidgetsFromState();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderSafeBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Console-grade analog tuning, glyphs, and diagnostics."),
            centerX,
            24,
            0xFFB8C1CC
        );
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Active Preset: " + ControllerPreset.fromId(activePresetId).displayName()),
            centerX,
            34,
            0xFFDDE7F2
        );

        renderLiveInputPreview(context, centerX, 46);
        renderSectionHeaders(context, centerX);
        renderFocusedHelp(context, centerX);

        if (maxScrollOffset > 0) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("^"), centerX, CONTENT_TOP - 10, 0xFFAAB4C0);
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
                this.height - 52,
                statusColor
            );
        }
    }

    private void renderSafeBackground(DrawContext context) {
        // Avoid Screen#renderBackground blur path here: this screen is rendered inside a pipeline
        // where another blur pass can already be active in the same frame.
        context.fill(0, 0, this.width, this.height, 0xCC0D1118);
        context.fill(0, 0, this.width, 48, 0x88171E2B);
        context.fill(0, this.height - 52, this.width, this.height, 0x88262D3A);
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

    private int addSectionHeader(String title, String subtitle, int baseY) {
        sectionHeaders.add(new SectionHeader(title, subtitle, baseY));
        return baseY + SECTION_HEADER_HEIGHT;
    }

    private void renderSectionHeaders(DrawContext context, int centerX) {
        int left = centerX - (SLIDER_WIDTH / 2);
        int right = centerX + (SLIDER_WIDTH / 2);
        int clipTop = CONTENT_TOP - 14;
        int clipBottom = this.height - CONTENT_BOTTOM_MARGIN + 8;

        for (SectionHeader header : sectionHeaders) {
            int y = header.baseY() - scrollOffset;
            if (y < clipTop || y > clipBottom) {
                continue;
            }
            context.fill(left, y - 1, right, y + 14, 0x3A23364B);
            context.fill(left, y + 13, right, y + 14, 0x665B7FA2);
            context.drawTextWithShadow(this.textRenderer, header.title(), left + 6, y + 3, 0xFFE9F2FC);
            if (header.subtitle() != null && !header.subtitle().isBlank()) {
                context.drawTextWithShadow(this.textRenderer, header.subtitle(), left + 96, y + 3, 0xFFB3C5D8);
            }
        }
    }

    private void renderFocusedHelp(DrawContext context, int centerX) {
        String category = "General";
        String hint = "Adjust values with left/right, confirm with A, back with B.";
        if (this.getFocused() instanceof ClickableWidget focusedWidget) {
            category = widgetCategories.getOrDefault(focusedWidget, category);
            hint = widgetHints.getOrDefault(focusedWidget, hint);
        }

        int left = centerX - 220;
        int right = centerX + 220;
        int top = this.height - 66;
        int bottom = this.height - 44;
        context.fill(left, top, right, bottom, 0x8A0D131D);
        context.fill(left, top, right, top + 1, 0x66456A90);
        context.drawTextWithShadow(this.textRenderer, "Category: " + category, left + 8, top + 4, 0xFFE2ECF8);
        context.drawTextWithShadow(this.textRenderer, fitToWidth(hint, 430), left + 8, top + 13, 0xFFBCCBDA);
    }

    private void addPresetButtons(int centerX, int y, String category) {
        int buttonWidth = 68;
        int gap = 4;
        int rowX = centerX - 143;
        ControllerPreset[] presets = ControllerPreset.values();
        for (int i = 0; i < presets.length; i++) {
            ControllerPreset preset = presets[i];
            int x = rowX + (i * (buttonWidth + gap));
            addScrollableChild(ButtonWidget.builder(
                Text.literal(preset.displayName()),
                button -> applyPresetNow(preset)
            ).dimensions(x, y, buttonWidth, CONTROL_HEIGHT).build(),
                y,
                category,
                "Applies the " + preset.displayName() + " tuning preset."
            );
        }
    }

    private void applyPresetNow(ControllerPreset preset) {
        runtime.applyPreset(preset);
        loadFromConfig();
        syncWidgetsFromState();
        dirty = false;
        statusMessage = preset.displayName() + " preset applied.";
        statusColor = 0xFF7DD3FC;
    }

    private void resetDefaultsNow() {
        runtime.resetToDefaults();
        loadFromConfig();
        syncWidgetsFromState();
        dirty = false;
        statusMessage = "Controller config reset to defaults.";
        statusColor = 0xFFEFC56F;
    }

    private void applyChanges(String message, int color) {
        runtime.updateConfig(this::applyStateToConfig);
        dirty = false;
        statusMessage = message;
        statusColor = color;
    }

    private void loadFromConfig() {
        ControllerConfig config = runtime.latestConfig();
        if (config == null) {
            config = ControllerConfig.createDefault();
        }

        leftStickDeadzone = config.movementDeadzone;
        rightStickDeadzone = config.lookDeadzone;
        lookAntiDeadzone = config.lookAntiDeadzone;
        lookSensitivityX = config.lookSensitivityX;
        lookSensitivityY = config.lookSensitivityY;
        lookSpeedMultiplier = config.lookSpeedMultiplier;
        invertLookY = config.invertLookY;
        triggerThreshold = config.triggerThreshold;
        menuAxisThreshold = config.menuAxisThreshold;
        cameraSmoothing = config.cameraSmoothing;
        cameraSmoothingStrength = config.cameraSmoothingStrength;
        lookResponseCurve = config.lookResponseCurve == null || config.lookResponseCurve.isBlank()
            ? "linear"
            : config.lookResponseCurve;
        hudHintsEnabled = config.hudHintsEnabled;
        debugOverlayEnabled = config.debugOverlayEnabled;
        activePresetId = ControllerPreset.fromId(config.activePreset).id();
    }

    private void syncWidgetsFromState() {
        syncingWidgets = true;
        if (leftDeadzoneSlider != null) leftDeadzoneSlider.setCurrentValue(leftStickDeadzone);
        if (rightDeadzoneSlider != null) rightDeadzoneSlider.setCurrentValue(rightStickDeadzone);
        if (antiDeadzoneSlider != null) antiDeadzoneSlider.setCurrentValue(lookAntiDeadzone);
        if (sensitivityXSlider != null) sensitivityXSlider.setCurrentValue(lookSensitivityX);
        if (sensitivityYSlider != null) sensitivityYSlider.setCurrentValue(lookSensitivityY);
        if (speedSlider != null) speedSlider.setCurrentValue(lookSpeedMultiplier);
        if (triggerThresholdSlider != null) triggerThresholdSlider.setCurrentValue(triggerThreshold);
        if (menuThresholdSlider != null) menuThresholdSlider.setCurrentValue(menuAxisThreshold);
        if (smoothingStrengthSlider != null) smoothingStrengthSlider.setCurrentValue(cameraSmoothingStrength);
        syncingWidgets = false;

        if (invertYToggleButton != null) invertYToggleButton.setMessage(Text.literal(invertYLabel()));
        if (hudToggleButton != null) hudToggleButton.setMessage(Text.literal(hudToggleLabel()));
        if (debugOverlayButton != null) debugOverlayButton.setMessage(Text.literal(debugOverlayLabel()));
        if (smoothingToggleButton != null) smoothingToggleButton.setMessage(Text.literal(smoothingToggleLabel()));
        if (responseCurveButton != null) responseCurveButton.setMessage(Text.literal(responseCurveLabel()));
    }

    private void renderLiveInputPreview(DrawContext context, int centerX, int y) {
        ControllerSnapshot snapshot = runtime.latestSnapshot();
        GameplayInputFrame frame = runtime.latestFrame();

        if (snapshot == null || !snapshot.isConnected() || frame == null) {
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Input Test: connect a controller to preview stick and trigger values."),
                centerX,
                y,
                0xFFA8B4C2
            );
            return;
        }

        String lineOne = "Input Test: "
            + runtime.activeControllerType()
            + " | Glyphs: "
            + runtime.glyphs().activeGlyphSetName()
            + " | Preset: "
            + ControllerPreset.fromId(activePresetId).displayName();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lineOne), centerX, y, 0xFFD7E3EF);

        String lineTwo = String.format(
            Locale.ROOT,
            "LS(%.2f, %.2f) RS(%.2f, %.2f) LT %.2f RT %.2f | %s",
            snapshot.axis(ControllerAxis.LEFT_X),
            snapshot.axis(ControllerAxis.LEFT_Y),
            snapshot.axis(ControllerAxis.RIGHT_X),
            snapshot.axis(ControllerAxis.RIGHT_Y),
            normalizeTrigger(snapshot.axis(ControllerAxis.LEFT_TRIGGER)),
            normalizeTrigger(snapshot.axis(ControllerAxis.RIGHT_TRIGGER)),
            pressedButtonsPreview(snapshot)
        );
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lineTwo), centerX, y + 10, 0xFFC4D3E0);
    }

    private String pressedButtonsPreview(ControllerSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (ControllerButton button : ControllerButton.values()) {
            if (!snapshot.isPressed(button)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(runtime.glyphs().glyphForButton(button));
            shown++;
            if (shown >= 5) {
                builder.append(", ...");
                break;
            }
        }
        return builder.length() == 0 ? "Buttons: none" : "Buttons: " + builder;
    }

    private String invertYLabel() {
        return invertLookY ? "Invert Y: ON" : "Invert Y: OFF";
    }

    private String hudToggleLabel() {
        return hudHintsEnabled ? "HUD Hints: ON" : "HUD Hints: OFF";
    }

    private String debugOverlayLabel() {
        return debugOverlayEnabled ? "Diagnostics: ON" : "Diagnostics: OFF";
    }

    private String smoothingToggleLabel() {
        return cameraSmoothing ? "Smoothing: ON" : "Smoothing: OFF";
    }

    private String responseCurveLabel() {
        return "Look Curve: " + lookResponseCurve;
    }

    private void onMutableChange() {
        if (syncingWidgets) {
            return;
        }
        markDirty();
        pushLivePreview();
    }

    private void markDirty() {
        dirty = true;
        statusMessage = "Unsaved changes.";
        statusColor = 0xFFEFC56F;
    }

    private void pushLivePreview() {
        runtime.previewConfig(this::applyStateToConfig);
    }

    private void applyStateToConfig(ControllerConfig config) {
        if (config == null) {
            return;
        }
        config.movementDeadzone = leftStickDeadzone;
        config.lookDeadzone = rightStickDeadzone;
        config.lookAntiDeadzone = lookAntiDeadzone;
        config.lookSensitivityX = lookSensitivityX;
        config.lookSensitivityY = lookSensitivityY;
        config.lookSpeedMultiplier = lookSpeedMultiplier;
        config.invertLookY = invertLookY;
        config.triggerThreshold = triggerThreshold;
        config.menuAxisThreshold = menuAxisThreshold;
        config.cameraSmoothing = cameraSmoothing;
        config.cameraSmoothingStrength = cameraSmoothingStrength;
        config.lookResponseCurve = lookResponseCurve;
        config.hudHintsEnabled = hudHintsEnabled;
        config.debugOverlayEnabled = debugOverlayEnabled;
        config.activePreset = activePresetId;
    }

    private <T extends ClickableWidget> T addScrollableChild(T widget, int baseY) {
        addDrawableChild(widget);
        scrollEntries.add(new ScrollEntry(widget, baseY));
        return widget;
    }

    private <T extends ClickableWidget> T addScrollableChild(
        T widget,
        int baseY,
        String category,
        String hint
    ) {
        addScrollableChild(widget, baseY);
        if (category != null && !category.isBlank()) {
            widgetCategories.put(widget, category);
        }
        if (hint != null && !hint.isBlank()) {
            widgetHints.put(widget, hint);
        }
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

    private String fitToWidth(String text, int maxWidth) {
        if (text == null || text.isBlank() || this.textRenderer == null) {
            return "";
        }
        if (this.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int length = text.length();
        while (length > 1) {
            String candidate = text.substring(0, length) + ellipsis;
            if (this.textRenderer.getWidth(candidate) <= maxWidth) {
                return candidate;
            }
            length--;
        }
        return ellipsis;
    }

    private static String nextCurve(String current) {
        String normalized = current == null ? "linear" : current.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "linear" -> "exponential_light";
            case "exponential_light" -> "exponential_strong";
            default -> "linear";
        };
    }

    private static float normalizeTrigger(float value) {
        float normalized = (value + 1.0F) * 0.5F;
        return Math.max(0.0F, Math.min(1.0F, normalized));
    }

    private abstract static class ConfigSlider extends SliderWidget {
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

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = getX();
            int top = getY();
            int right = left + getWidth();
            int bottom = top + getHeight();
            boolean focused = isFocused();

            int borderColor = focused ? 0xFF95C6F1 : 0x88516B85;
            int backgroundColor = focused ? 0x8F1A2C40 : 0x85101B2A;
            context.fill(left - 1, top - 1, right + 1, bottom + 1, borderColor);
            context.fill(left, top, right, bottom, backgroundColor);

            super.renderWidget(context, mouseX, mouseY, delta);

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return;
            }
            String valueLabel = valueText(currentValue());
            int labelWidth = client.textRenderer.getWidth(valueLabel);
            int badgeWidth = labelWidth + 8;
            int badgeLeft = right - badgeWidth - 4;
            int badgeTop = top + 3;
            context.fill(
                badgeLeft,
                badgeTop,
                badgeLeft + badgeWidth,
                badgeTop + 13,
                focused ? 0xAA30516F : 0x9920354A
            );
            context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(valueLabel),
                badgeLeft + (badgeWidth / 2),
                badgeTop + 2,
                0xFFEAF5FF
            );
        }

        protected void setCurrentValue(float newValue) {
            this.value = normalize(newValue, min, max);
            updateMessage();
            onValueChanged(currentValue());
        }

        protected float currentValue() {
            return (float) (min + (max - min) * value);
        }

        protected String valueText(float value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        private static double normalize(float v, float min, float max) {
            float clamped = Math.max(min, Math.min(max, v));
            return (clamped - min) / (max - min);
        }

        protected abstract String label(float value);

        protected abstract void onValueChanged(float value);
    }

    private record SectionHeader(String title, String subtitle, int baseY) {
    }

    private record ScrollEntry(ClickableWidget widget, int baseY) {
    }
}
