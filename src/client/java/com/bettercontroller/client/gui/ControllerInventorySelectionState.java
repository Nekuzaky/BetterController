package com.bettercontroller.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

import java.util.List;

/**
 * Shared state for controller-driven inventory slot selection across navigation and rendering.
 */
public final class ControllerInventorySelectionState {
    private Screen trackedScreen;
    private int selectedSlotId = -1;
    private long lastSelectionChangeMs;

    public void onScreenChanged(Screen screen) {
        if (screen == trackedScreen) {
            return;
        }
        trackedScreen = screen;
        selectedSlotId = -1;
        lastSelectionChangeMs = 0L;
    }

    public void setSelectedSlot(HandledScreen<?> screen, Slot slot) {
        if (screen == null || slot == null) {
            return;
        }
        if (trackedScreen != screen || selectedSlotId != slot.id) {
            lastSelectionChangeMs = System.currentTimeMillis();
        }
        trackedScreen = screen;
        selectedSlotId = slot.id;
    }

    public Slot resolveSelectedSlot(HandledScreen<?> screen) {
        if (screen == null || trackedScreen != screen || selectedSlotId < 0 || screen.getScreenHandler() == null) {
            return null;
        }
        List<Slot> slots = screen.getScreenHandler().slots;
        for (Slot slot : slots) {
            if (slot != null && slot.id == selectedSlotId) {
                return slot;
            }
        }
        return null;
    }

    public long lastSelectionChangeMs() {
        return lastSelectionChangeMs;
    }

    public void clear() {
        trackedScreen = null;
        selectedSlotId = -1;
        lastSelectionChangeMs = 0L;
    }
}
