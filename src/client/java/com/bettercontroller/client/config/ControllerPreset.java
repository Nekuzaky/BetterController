package com.bettercontroller.client.config;

import java.util.Locale;

// Presets group controller tuning values into safe, quick-switch profiles.
public enum ControllerPreset {
    CONSOLE("console", "Console"),
    SMOOTH("smooth", "Smooth"),
    PRECISION("precision", "Precision"),
    SOUTHPAW("southpaw", "Southpaw");

    private final String id;
    private final String displayName;

    ControllerPreset(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void applyTo(ControllerConfig config) {
        if (config == null) {
            return;
        }

        switch (this) {
            case CONSOLE -> {
                applyStandardAxes(config);
                config.movementDeadzone = 0.14F;
                config.lookDeadzone = 0.07F;
                config.lookAntiDeadzone = 0.02F;
                config.lookSensitivityX = 12.0F;
                config.lookSensitivityY = 11.0F;
                config.lookSpeedMultiplier = 2.25F;
                config.lookResponseCurve = "linear";
                config.cameraSmoothing = false;
                config.cameraSmoothingStrength = 0.25F;
                config.triggerThreshold = 0.45F;
                config.menuAxisThreshold = 0.35F;
                config.menuInitialRepeatDelayMs = 140;
                config.menuRepeatIntervalMs = 55;
            }
            case SMOOTH -> {
                applyStandardAxes(config);
                config.movementDeadzone = 0.12F;
                config.lookDeadzone = 0.06F;
                config.lookAntiDeadzone = 0.025F;
                config.lookSensitivityX = 13.0F;
                config.lookSensitivityY = 12.0F;
                config.lookSpeedMultiplier = 2.20F;
                config.lookResponseCurve = "exponential_light";
                config.cameraSmoothing = true;
                config.cameraSmoothingStrength = 0.38F;
                config.triggerThreshold = 0.35F;
                config.menuAxisThreshold = 0.30F;
                config.menuInitialRepeatDelayMs = 110;
                config.menuRepeatIntervalMs = 42;
            }
            case PRECISION -> {
                applyStandardAxes(config);
                config.movementDeadzone = 0.10F;
                config.lookDeadzone = 0.04F;
                config.lookAntiDeadzone = 0.01F;
                config.lookSensitivityX = 10.5F;
                config.lookSensitivityY = 9.5F;
                config.lookSpeedMultiplier = 1.85F;
                config.lookResponseCurve = "linear";
                config.cameraSmoothing = false;
                config.cameraSmoothingStrength = 0.18F;
                config.triggerThreshold = 0.38F;
                config.menuAxisThreshold = 0.33F;
                config.menuInitialRepeatDelayMs = 120;
                config.menuRepeatIntervalMs = 48;
            }
            case SOUTHPAW -> {
                CONSOLE.applyTo(config);
                applyAxesForAllLayouts(config, "RIGHT_X", "RIGHT_Y", "LEFT_X", "LEFT_Y");
            }
        }

        config.activePreset = id;
    }

    public static ControllerPreset fromId(String id) {
        if (id == null || id.isBlank()) {
            return CONSOLE;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ControllerPreset preset : values()) {
            if (preset.id.equals(normalized)) {
                return preset;
            }
        }
        return CONSOLE;
    }

    private static void applyStandardAxes(ControllerConfig config) {
        applyAxesForAllLayouts(config, "LEFT_X", "LEFT_Y", "RIGHT_X", "RIGHT_Y");
    }

    private static void applyAxesForAllLayouts(
        ControllerConfig config,
        String moveX,
        String moveY,
        String lookX,
        String lookY
    ) {
        if (config.axes == null) {
            config.axes = new ControllerConfig.AxisBindings();
        }

        config.axes.move_x = moveX;
        config.axes.move_y = moveY;
        config.axes.look_x = lookX;
        config.axes.look_y = config.invertLookY ? ("-" + lookY) : lookY;

        if (config.layouts == null || config.layouts.isEmpty()) {
            return;
        }

        for (ControllerConfig.ControllerLayout layout : config.layouts.values()) {
            if (layout == null) {
                continue;
            }
            if (layout.axes == null) {
                layout.axes = new ControllerConfig.AxisBindings();
            }
            layout.axes.move_x = moveX;
            layout.axes.move_y = moveY;
            layout.axes.look_x = lookX;
            layout.axes.look_y = config.invertLookY ? ("-" + lookY) : lookY;
        }
    }
}
