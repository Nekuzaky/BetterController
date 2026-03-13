package com.bettercontroller.client.input;

import com.bettercontroller.BetterControllerMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerInputHandler {
    private static final float MOVE_DEADZONE = 0.25F;
    private static final float LOOK_DEADZONE = 0.15F;
    private static final float LOOK_SENSITIVITY = 12.0F;
    private static final float TRIGGER_THRESHOLD = 0.20F;

    private final GLFWGamepadState state = GLFWGamepadState.create();

    private int activeJoystickId = -1;
    private boolean wasInventoryPressed;
    private boolean wasSwapHandsPressed;
    private boolean wasDropPressed;
    private boolean wasNextSlotPressed;
    private boolean wasPrevSlotPressed;

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        int detectedJoystick = findJoystick();
        if (detectedJoystick == -1) {
            if (activeJoystickId != -1) {
                BetterControllerMod.LOGGER.info("Controller disconnected.");
                releaseAll(client);
                activeJoystickId = -1;
            }
            return;
        }

        if (activeJoystickId != detectedJoystick) {
            activeJoystickId = detectedJoystick;
            String joystickName = GLFW.glfwGetJoystickName(activeJoystickId);
            BetterControllerMod.LOGGER.info("Controller connected: {}", joystickName != null ? joystickName : "Unknown");
        }

        if (!GLFW.glfwGetGamepadState(activeJoystickId, state)) {
            releaseAll(client);
            return;
        }

        if (client.player == null || client.world == null || client.currentScreen != null) {
            releaseGameplayHolds(client);
            return;
        }

        applyMovement(client);
        applyLook(client);
        applyActions(client);
    }

    private int findJoystick() {
        if (activeJoystickId != -1 && GLFW.glfwJoystickPresent(activeJoystickId) && GLFW.glfwJoystickIsGamepad(activeJoystickId)) {
            return activeJoystickId;
        }

        for (int joystickId = GLFW.GLFW_JOYSTICK_1; joystickId <= GLFW.GLFW_JOYSTICK_LAST; joystickId++) {
            if (GLFW.glfwJoystickPresent(joystickId) && GLFW.glfwJoystickIsGamepad(joystickId)) {
                return joystickId;
            }
        }

        return -1;
    }

    private void applyMovement(MinecraftClient client) {
        float leftX = applyDeadzone(axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), MOVE_DEADZONE);
        float leftY = applyDeadzone(axis(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y), MOVE_DEADZONE);

        boolean moveLeft = leftX < -0.1F;
        boolean moveRight = leftX > 0.1F;
        boolean moveForward = leftY < -0.1F;
        boolean moveBackward = leftY > 0.1F;

        setHold(client.options.leftKey, moveLeft);
        setHold(client.options.rightKey, moveRight);
        setHold(client.options.forwardKey, moveForward);
        setHold(client.options.backKey, moveBackward);

        setHold(client.options.jumpKey, button(GLFW.GLFW_GAMEPAD_BUTTON_A));
        setHold(client.options.sprintKey, button(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB));
        setHold(client.options.sneakKey, button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB));
    }

    private void applyLook(MinecraftClient client) {
        float rightX = applyDeadzone(axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), LOOK_DEADZONE);
        float rightY = applyDeadzone(axis(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y), LOOK_DEADZONE);

        if (rightX == 0.0F && rightY == 0.0F) {
            return;
        }

        client.player.changeLookDirection(rightX * LOOK_SENSITIVITY, rightY * LOOK_SENSITIVITY);
    }

    private void applyActions(MinecraftClient client) {
        boolean attack = button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) || triggerPressed(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);
        boolean use = button(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) || triggerPressed(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER);

        setHold(client.options.attackKey, attack);
        setHold(client.options.useKey, use);

        boolean inventoryPressed = button(GLFW.GLFW_GAMEPAD_BUTTON_Y);
        if (inventoryPressed && !wasInventoryPressed) {
            tap(client.options.inventoryKey);
        }
        wasInventoryPressed = inventoryPressed;

        boolean swapHandsPressed = button(GLFW.GLFW_GAMEPAD_BUTTON_X);
        if (swapHandsPressed && !wasSwapHandsPressed) {
            tap(client.options.swapHandsKey);
        }
        wasSwapHandsPressed = swapHandsPressed;

        boolean dropPressed = button(GLFW.GLFW_GAMEPAD_BUTTON_B);
        if (dropPressed && !wasDropPressed) {
            tap(client.options.dropKey);
        }
        wasDropPressed = dropPressed;

        boolean nextSlotPressed = button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
        if (nextSlotPressed && !wasNextSlotPressed) {
            cycleSlot(client, 1);
        }
        wasNextSlotPressed = nextSlotPressed;

        boolean prevSlotPressed = button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT);
        if (prevSlotPressed && !wasPrevSlotPressed) {
            cycleSlot(client, -1);
        }
        wasPrevSlotPressed = prevSlotPressed;
    }

    private static void cycleSlot(MinecraftClient client, int offset) {
        if (client.player == null) {
            return;
        }

        int size = 9;
        int currentSlot = client.player.getInventory().selectedSlot;
        int nextSlot = Math.floorMod(currentSlot + offset, size);
        client.player.getInventory().selectedSlot = nextSlot;

        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
        }
    }

    private void releaseAll(MinecraftClient client) {
        releaseGameplayHolds(client);
        wasInventoryPressed = false;
        wasSwapHandsPressed = false;
        wasDropPressed = false;
        wasNextSlotPressed = false;
        wasPrevSlotPressed = false;
    }

    private static void releaseGameplayHolds(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        setHold(client.options.forwardKey, false);
        setHold(client.options.backKey, false);
        setHold(client.options.leftKey, false);
        setHold(client.options.rightKey, false);
        setHold(client.options.jumpKey, false);
        setHold(client.options.sprintKey, false);
        setHold(client.options.sneakKey, false);
        setHold(client.options.attackKey, false);
        setHold(client.options.useKey, false);
    }

    private static void tap(KeyBinding keyBinding) {
        KeyBinding.onKeyPressed(keyBinding.getBoundKey());
    }

    private static void setHold(KeyBinding keyBinding, boolean pressed) {
        KeyBinding.setKeyPressed(keyBinding.getBoundKey(), pressed);
    }

    private boolean button(int buttonId) {
        return state.buttons(buttonId) == GLFW.GLFW_PRESS;
    }

    private float axis(int axisId) {
        return state.axes(axisId);
    }

    private boolean triggerPressed(int axisId) {
        float value = axis(axisId);
        float normalized = (value + 1.0F) * 0.5F;
        return value > TRIGGER_THRESHOLD || normalized > 0.55F;
    }

    private static float applyDeadzone(float value, float deadzone) {
        float absolute = Math.abs(value);
        if (absolute < deadzone) {
            return 0.0F;
        }

        float scaled = (absolute - deadzone) / (1.0F - deadzone);
        return Math.copySign(scaled, value);
    }
}
