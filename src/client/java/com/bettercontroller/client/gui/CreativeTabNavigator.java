package com.bettercontroller.client.gui;

import com.bettercontroller.client.mixin.CreativeInventoryScreenAccessor;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;

import java.util.List;

/**
 * Cycles between visible creative inventory tabs without reflection - uses an accessor mixin.
 */
public final class CreativeTabNavigator {

    private CreativeTabNavigator() {
    }

    public static boolean cycle(HandledScreen<?> screen, int step) {
        if (!(screen instanceof CreativeInventoryScreen creativeScreen) || step == 0) {
            return false;
        }
        List<ItemGroup> groups = ItemGroups.getGroupsToDisplay();
        if (groups == null || groups.isEmpty()) {
            return false;
        }
        ItemGroup current = CreativeInventoryScreenAccessor.bettercontroller$getSelectedTab();
        int currentIndex = groups.indexOf(current);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = Math.floorMod(currentIndex + (step > 0 ? 1 : -1), groups.size());
        ItemGroup target = groups.get(nextIndex);
        if (target == null || target == current) {
            return false;
        }
        ((CreativeInventoryScreenAccessor) creativeScreen).bettercontroller$invokeSetSelectedTab(target);
        return true;
    }
}
