package com.bettercontroller.client.polling;

import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerSnapshot {
    private int joystickId = -1;
    private String joystickName = "Unknown";
    private String joystickGuid = "";
    private ControllerType controllerType = ControllerType.NONE;
    private final boolean[] buttons = new boolean[ControllerButton.values().length];
    private final float[] axes = new float[ControllerAxis.values().length];

    public void update(
        int joystickId,
        String joystickName,
        String joystickGuid,
        ControllerType controllerType,
        GLFWGamepadState state
    ) {
        this.joystickId = joystickId;
        this.joystickName = joystickName == null ? "Unknown" : joystickName;
        this.joystickGuid = joystickGuid == null ? "" : joystickGuid;
        this.controllerType = controllerType == null ? ControllerType.GENERIC : controllerType;

        for (ControllerButton button : ControllerButton.values()) {
            buttons[button.ordinal()] = state.buttons(button.glfwButtonId()) != 0;
        }
        for (ControllerAxis axis : ControllerAxis.values()) {
            axes[axis.ordinal()] = state.axes(axis.glfwAxisId());
        }
    }

    public void clearConnection() {
        joystickId = -1;
        joystickName = "Disconnected";
        joystickGuid = "";
        controllerType = ControllerType.NONE;
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = false;
        }
        for (int i = 0; i < axes.length; i++) {
            axes[i] = 0.0F;
        }
    }

    public int joystickId() {
        return joystickId;
    }

    public String joystickName() {
        return joystickName;
    }

    public String joystickGuid() {
        return joystickGuid;
    }

    public ControllerType controllerType() {
        return controllerType;
    }

    public boolean isConnected() {
        return joystickId >= 0;
    }

    public boolean isPressed(ControllerButton button) {
        return buttons[button.ordinal()];
    }

    public float axis(ControllerAxis axis) {
        return axes[axis.ordinal()];
    }
}
