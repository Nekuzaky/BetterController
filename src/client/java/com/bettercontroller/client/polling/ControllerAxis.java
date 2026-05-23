package com.bettercontroller.client.polling;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum ControllerAxis {
    LEFT_X(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X, false, "LEFT_X", "LX"),
    LEFT_Y(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y, false, "LEFT_Y", "LY"),
    RIGHT_X(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X, false, "RIGHT_X", "RX"),
    RIGHT_Y(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y, false, "RIGHT_Y", "RY"),
    LEFT_TRIGGER(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, true, "LEFT_TRIGGER", "LT", "L2"),
    RIGHT_TRIGGER(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, true, "RIGHT_TRIGGER", "RT", "R2");

    private static final Map<String, ControllerAxis> LOOKUP = new HashMap<>();

    static {
        for (ControllerAxis axis : values()) {
            for (String alias : axis.aliases) {
                LOOKUP.put(normalize(alias), axis);
            }
        }
    }

    private final int glfwAxisId;
    private final boolean trigger;
    private final String[] aliases;

    ControllerAxis(int glfwAxisId, boolean trigger, String... aliases) {
        this.glfwAxisId = glfwAxisId;
        this.trigger = trigger;
        this.aliases = aliases;
    }

    public int glfwAxisId() {
        return glfwAxisId;
    }

    public boolean isTrigger() {
        return trigger;
    }

    public static ControllerAxis fromTokenOrNull(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return LOOKUP.get(normalize(token));
    }

    public static Optional<ControllerAxis> fromToken(String token) {
        return Optional.ofNullable(fromTokenOrNull(token));
    }

    private static String normalize(String token) {
        return token.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
    }
}
