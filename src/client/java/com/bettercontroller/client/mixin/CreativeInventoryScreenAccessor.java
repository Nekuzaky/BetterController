package com.bettercontroller.client.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeInventoryScreen.class)
public interface CreativeInventoryScreenAccessor {
    @Accessor("selectedTab")
    static ItemGroup bettercontroller$getSelectedTab() {
        throw new AssertionError();
    }

    @Invoker("setSelectedTab")
    void bettercontroller$invokeSetSelectedTab(ItemGroup itemGroup);
}
