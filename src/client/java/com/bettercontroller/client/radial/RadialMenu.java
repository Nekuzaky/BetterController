package com.bettercontroller.client.radial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RadialMenu {
    private final List<RadialMenuSlot> slots = new ArrayList<>();
    private boolean active;
    private int selectedIndex = -1;

    public void open(int requestedSlots) {
        open(requestedSlots, List.of());
    }

    public void open(int requestedSlots, List<String> labels) {
        int slotCount = Math.max(4, Math.min(9, requestedSlots));
        configureSlots(slotCount, labels == null ? List.of() : labels);
        active = true;
        if (selectedIndex < 0 || selectedIndex >= slots.size()) {
            selectedIndex = 0;
        }
    }

    public void close() {
        active = false;
        selectedIndex = -1;
    }

    public boolean isActive() {
        return active;
    }

    public List<RadialMenuSlot> slots() {
        return Collections.unmodifiableList(slots);
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < slots.size()) {
            this.selectedIndex = selectedIndex;
        }
    }

    public int selectedHotbarSlot() {
        if (selectedIndex < 0 || selectedIndex >= slots.size()) {
            return -1;
        }
        return slots.get(selectedIndex).hotbarSlot();
    }

    private void configureSlots(int slotCount, List<String> labels) {
        slots.clear();
        for (int i = 0; i < slotCount; i++) {
            String fallback = "Slot " + (i + 1);
            String label = i < labels.size() && labels.get(i) != null && !labels.get(i).isBlank()
                ? labels.get(i)
                : fallback;
            slots.add(new RadialMenuSlot(i, label));
        }
    }
}
