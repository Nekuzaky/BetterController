package com.bettercontroller.client.input;

import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import java.lang.reflect.Field;

public final class MinecraftInputApplier {
    private static final Field BOUND_KEY_FIELD = resolveBoundKeyField();

    public void applyGameplayFrame(MinecraftClient client, GameplayInputFrame frame) {
        if (client == null || client.options == null || client.player == null) {
            return;
        }

        applyAnalogMovement(client, frame);
        setHold(client.options.forwardKey, frame.moveForward());
        setHold(client.options.backKey, frame.moveBackward());
        setHold(client.options.leftKey, frame.moveLeft());
        setHold(client.options.rightKey, frame.moveRight());
        setHold(client.options.jumpKey, frame.jumpHeld());
        setHold(client.options.sprintKey, frame.sprintHeld());
        setHold(client.options.sneakKey, frame.sneakHeld());
        setHold(client.options.attackKey, frame.attackHeld());
        setHold(client.options.useKey, frame.useHeld());
        setHold(client.options.playerListKey, frame.playerListHeld());

        if (frame.inventoryTap()) {
            tap(client.options.inventoryKey);
        }
        if (frame.swapHandsTap()) {
            tap(client.options.swapHandsKey);
        }
        if (frame.dropTap()) {
            tap(client.options.dropKey);
        }
        if (frame.chatTap()) {
            tap(client.options.chatKey);
        }
        if (frame.perspectiveTap()) {
            tap(client.options.togglePerspectiveKey);
        }
        if (frame.pickBlockTap()) {
            tap(client.options.pickItemKey);
        }
        if (frame.pauseTap()) {
            openPauseMenu(client);
        }
        if (frame.hotbarStep() != 0) {
            cycleSlot(client, frame.hotbarStep());
        }
        if (frame.hotbarSelect() >= 0) {
            selectSlot(client, frame.hotbarSelect());
        }
    }

    public void applyLookDelta(MinecraftClient client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }
        client.player.changeLookDirection(deltaX, deltaY);
    }

    public void releaseGameplayHolds(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        clearAnalogMovement(client);
        setHold(client.options.forwardKey, false);
        setHold(client.options.backKey, false);
        setHold(client.options.leftKey, false);
        setHold(client.options.rightKey, false);
        setHold(client.options.jumpKey, false);
        setHold(client.options.sprintKey, false);
        setHold(client.options.sneakKey, false);
        setHold(client.options.attackKey, false);
        setHold(client.options.useKey, false);
        setHold(client.options.playerListKey, false);
    }

    private static void applyAnalogMovement(MinecraftClient client, GameplayInputFrame frame) {
        if (client.player == null || client.player.input == null) {
            return;
        }

        client.player.input.movementSideways = frame.processedMoveX();
        client.player.input.movementForward = -frame.processedMoveY();
        client.player.input.pressingLeft = frame.moveLeft();
        client.player.input.pressingRight = frame.moveRight();
        client.player.input.pressingForward = frame.moveForward();
        client.player.input.pressingBack = frame.moveBackward();
        client.player.input.jumping = frame.jumpHeld();
        client.player.input.sneaking = frame.sneakHeld();
    }

    private static void clearAnalogMovement(MinecraftClient client) {
        if (client == null || client.player == null || client.player.input == null) {
            return;
        }

        client.player.input.movementSideways = 0.0F;
        client.player.input.movementForward = 0.0F;
        client.player.input.pressingLeft = false;
        client.player.input.pressingRight = false;
        client.player.input.pressingForward = false;
        client.player.input.pressingBack = false;
        client.player.input.jumping = false;
        client.player.input.sneaking = false;
    }

    private static void cycleSlot(MinecraftClient client, int offset) {
        if (client.player == null) {
            return;
        }

        int currentSlot = client.player.getInventory().selectedSlot;
        int nextSlot = Math.floorMod(currentSlot + offset, 9);
        selectSlot(client, nextSlot);
    }

    private static void selectSlot(MinecraftClient client, int slot) {
        if (client.player == null) {
            return;
        }
        int nextSlot = Math.floorMod(slot, 9);
        client.player.getInventory().selectedSlot = nextSlot;
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
        }
    }

    private static void openPauseMenu(MinecraftClient client) {
        if (client.currentScreen == null) {
            client.setScreen(new GameMenuScreen(true));
        }
    }

    private static void tap(KeyBinding keyBinding) {
        InputUtil.Key boundKey = readBoundKey(keyBinding);
        if (boundKey != null) {
            KeyBinding.onKeyPressed(boundKey);
            return;
        }

        keyBinding.setPressed(true);
        keyBinding.setPressed(false);
    }

    private static void setHold(KeyBinding keyBinding, boolean pressed) {
        keyBinding.setPressed(pressed);
    }

    private static InputUtil.Key readBoundKey(KeyBinding keyBinding) {
        if (BOUND_KEY_FIELD == null) {
            return null;
        }
        try {
            return (InputUtil.Key) BOUND_KEY_FIELD.get(keyBinding);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field resolveBoundKeyField() {
        try {
            Field field = KeyBinding.class.getDeclaredField("boundKey");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }
}
