package com.bettercontroller.client.gui;

public record GuiInputFrame(
    boolean up,
    boolean down,
    boolean left,
    boolean right,
    boolean confirm,
    boolean back,
    boolean pageNext,
    boolean pagePrev,
    boolean tabNext,
    boolean tabPrev
) {
    public boolean anyNavigation() {
        return up || down || left || right || confirm || back || pageNext || pagePrev || tabNext || tabPrev;
    }
}
