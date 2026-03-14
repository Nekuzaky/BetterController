package com.bettercontroller.client.radial;

import com.bettercontroller.client.config.ControllerConfig;
import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerSnapshot;
import com.bettercontroller.client.translation.GameplayAction;
import com.bettercontroller.client.translation.InputTranslator;

public final class RadialMenuController {
    private final RadialMenu radialMenu = new RadialMenu();
    private boolean radialHoldPressed;

    public int tick(
        ControllerSnapshot snapshot,
        ControllerConfig config,
        ControllerConfig.ResolvedLayout layout,
        InputTranslator inputTranslator
    ) {
        if (!config.radialMenuEnabled) {
            reset();
            return -1;
        }

        boolean holdPressed = inputTranslator.isActionPressed(snapshot, config, layout, GameplayAction.RADIAL_MENU);
        if (holdPressed) {
            if (!radialHoldPressed) {
                radialMenu.open(config.radialMenuSlots);
            }
            radialHoldPressed = true;
            updateSelection(snapshot, layout);
            return -1;
        }

        if (radialHoldPressed) {
            radialHoldPressed = false;
            int selected = config.radialConfirmOnRelease ? radialMenu.selectedHotbarSlot() : -1;
            radialMenu.close();
            return selected;
        }

        radialMenu.close();
        return -1;
    }

    public RadialMenu menu() {
        return radialMenu;
    }

    public void reset() {
        radialHoldPressed = false;
        radialMenu.close();
    }

    private void updateSelection(ControllerSnapshot snapshot, ControllerConfig.ResolvedLayout layout) {
        String lookXToken = layout.axisToken("look_x");
        String lookYToken = layout.axisToken("look_y");
        float lookX = readAxis(snapshot, lookXToken);
        float lookY = readAxis(snapshot, lookYToken);

        double magnitude = Math.sqrt((lookX * lookX) + (lookY * lookY));
        if (magnitude < 0.35D) {
            return;
        }

        double angle = Math.atan2(-lookY, lookX);
        if (angle < 0) {
            angle += Math.PI * 2.0D;
        }

        int slotCount = radialMenu.slots().size();
        if (slotCount == 0) {
            return;
        }

        int selected = (int) Math.floor((angle / (Math.PI * 2.0D)) * slotCount);
        if (selected >= slotCount) {
            selected = slotCount - 1;
        }
        radialMenu.setSelectedIndex(selected);
    }

    private static float readAxis(ControllerSnapshot snapshot, String axisToken) {
        if (axisToken == null || axisToken.isBlank()) {
            return 0.0F;
        }

        boolean invert = axisToken.startsWith("-");
        String normalizedToken = invert ? axisToken.substring(1) : axisToken;
        ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalizedToken);
        if (axis == null) {
            return 0.0F;
        }

        float value = snapshot.axis(axis);
        return invert ? -value : value;
    }
}
