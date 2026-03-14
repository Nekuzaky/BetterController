package com.bettercontroller.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class GuiNavigationController {
    private Screen lastScreen;
    private int selectedIndex = -1;
    private boolean preferListFocus;

    public void onScreenTick(MinecraftClient client, GuiInputFrame inputFrame) {
        if (client == null || client.currentScreen == null || inputFrame == null) {
            return;
        }

        Screen screen = client.currentScreen;
        AlwaysSelectedEntryListWidget<?> listWidget = findListWidget(screen);
        if (screen != lastScreen) {
            preferListFocus = listWidget != null;
        } else if (listWidget == null) {
            preferListFocus = false;
        } else if (inputFrame.left() || inputFrame.right() || inputFrame.tabNext() || inputFrame.tabPrev()) {
            preferListFocus = false;
        } else if (inputFrame.up() || inputFrame.down() || inputFrame.confirm() || inputFrame.pageNext() || inputFrame.pagePrev()) {
            preferListFocus = true;
        }

        boolean listNavigationActive = handleListNavigation(screen, listWidget, inputFrame, preferListFocus);
        List<GuiFocusElement> focusElements = collectFocusElements(screen);
        if (focusElements.isEmpty()) {
            if (inputFrame.back()) {
                triggerBack(screen);
            }
            if (inputFrame.tabNext()) {
                pressKey(screen, GLFW.GLFW_KEY_TAB);
            }
            if (inputFrame.tabPrev()) {
                pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
            }
            return;
        }

        if (screen != lastScreen) {
            lastScreen = screen;
            selectedIndex = findInitialSelectionIndex(screen, focusElements);
        }
        if (selectedIndex < 0 || selectedIndex >= focusElements.size()) {
            selectedIndex = 0;
        }

        if (screen instanceof BetterControllerSettingsScreen) {
            selectedIndex = handleLinearSettingsNavigation(screen, focusElements, selectedIndex, inputFrame);
            return;
        }

        if (listNavigationActive) {
            if (inputFrame.back()) {
                triggerBack(screen);
            }
            return;
        }

        if (inputFrame.up()) {
            selectedIndex = findDirectionalSelection(focusElements, selectedIndex, 0, -1);
        } else if (inputFrame.down()) {
            selectedIndex = findDirectionalSelection(focusElements, selectedIndex, 0, 1);
        } else if (inputFrame.left()) {
            selectedIndex = findDirectionalSelection(focusElements, selectedIndex, -1, 0);
        } else if (inputFrame.right()) {
            selectedIndex = findDirectionalSelection(focusElements, selectedIndex, 1, 0);
        }

        applySelection(screen, focusElements, selectedIndex);

        if (inputFrame.confirm()) {
            triggerConfirm(focusElements.get(selectedIndex));
        }
        if (inputFrame.back()) {
            triggerBack(screen);
        }
        if (inputFrame.pageNext()) {
            pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
        }
        if (inputFrame.pagePrev()) {
            pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
        }
        if (inputFrame.tabNext()) {
            pressKey(screen, GLFW.GLFW_KEY_TAB);
        }
        if (inputFrame.tabPrev()) {
            pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        }

        if (focusElements.get(selectedIndex).widget() instanceof SliderWidget sliderWidget) {
            if (inputFrame.left()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_LEFT);
            }
            if (inputFrame.right()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_RIGHT);
            }
        }
    }

    public void reset() {
        lastScreen = null;
        selectedIndex = -1;
        preferListFocus = false;
    }

    private static boolean handleListNavigation(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        GuiInputFrame inputFrame,
        boolean preferListFocus
    ) {
        if (screen == null || listWidget == null || inputFrame == null || !preferListFocus) {
            return false;
        }

        boolean listInput = inputFrame.up()
            || inputFrame.down()
            || inputFrame.confirm()
            || inputFrame.pageNext()
            || inputFrame.pagePrev();
        if (!listInput) {
            return false;
        }

        boolean used = false;
        if (inputFrame.up()) {
            screen.setFocused(listWidget);
            used = pressKey(listWidget, GLFW.GLFW_KEY_UP) || pressKey(screen, GLFW.GLFW_KEY_UP) || used;
        }
        if (inputFrame.down()) {
            screen.setFocused(listWidget);
            used = pressKey(listWidget, GLFW.GLFW_KEY_DOWN) || pressKey(screen, GLFW.GLFW_KEY_DOWN) || used;
        }
        if (inputFrame.confirm()) {
            screen.setFocused(listWidget);
            used = pressKey(listWidget, GLFW.GLFW_KEY_ENTER)
                || pressKey(listWidget, GLFW.GLFW_KEY_KP_ENTER)
                || pressKey(screen, GLFW.GLFW_KEY_ENTER)
                || used;
        }
        if (inputFrame.pageNext()) {
            screen.setFocused(listWidget);
            used = pressKey(listWidget, GLFW.GLFW_KEY_PAGE_DOWN)
                || pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN)
                || used;
        }
        if (inputFrame.pagePrev()) {
            screen.setFocused(listWidget);
            used = pressKey(listWidget, GLFW.GLFW_KEY_PAGE_UP)
                || pressKey(screen, GLFW.GLFW_KEY_PAGE_UP)
                || used;
        }

        // Keep list navigation authoritative to avoid falling through to bottom buttons.
        return true;
    }

    private static int handleLinearSettingsNavigation(
        Screen screen,
        List<GuiFocusElement> focusElements,
        int selectedIndex,
        GuiInputFrame inputFrame
    ) {
        if (focusElements.isEmpty()) {
            return selectedIndex;
        }

        if (inputFrame.down()) {
            selectedIndex = Math.floorMod(selectedIndex + 1, focusElements.size());
        } else if (inputFrame.up()) {
            selectedIndex = Math.floorMod(selectedIndex - 1, focusElements.size());
        }

        applySelection(screen, focusElements, selectedIndex);
        GuiFocusElement selected = focusElements.get(selectedIndex);
        ClickableWidget widget = selected.widget();

        if (widget instanceof SliderWidget sliderWidget) {
            if (inputFrame.left()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_LEFT);
            }
            if (inputFrame.right()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_RIGHT);
            }
        } else {
            if (inputFrame.confirm()) {
                triggerConfirm(selected);
            }
        }

        if (screen instanceof BetterControllerSettingsScreen settingsScreen) {
            settingsScreen.ensureWidgetVisible(widget);
            if (inputFrame.pageNext()) {
                settingsScreen.scrollByController(66);
            }
            if (inputFrame.pagePrev()) {
                settingsScreen.scrollByController(-66);
            }
        }

        if (inputFrame.back()) {
            triggerBack(screen);
        }
        return selectedIndex;
    }

    private static List<GuiFocusElement> collectFocusElements(Screen screen) {
        List<GuiFocusElement> elements = new ArrayList<>();
        for (Element child : screen.children()) {
            if (child instanceof ClickableWidget clickableWidget && clickableWidget.active && clickableWidget.visible) {
                elements.add(GuiFocusElement.fromWidget(clickableWidget));
            }
        }
        return elements;
    }

    private static AlwaysSelectedEntryListWidget<?> findListWidget(Screen screen) {
        if (screen == null) {
            return null;
        }
        for (Element child : screen.children()) {
            if (child instanceof AlwaysSelectedEntryListWidget<?> listWidget) {
                return listWidget;
            }
        }
        return null;
    }

    private static int findInitialSelectionIndex(Screen screen, List<GuiFocusElement> elements) {
        Element focused = screen.getFocused();
        if (focused instanceof ClickableWidget focusedWidget) {
            for (int i = 0; i < elements.size(); i++) {
                if (elements.get(i).widget() == focusedWidget) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int findDirectionalSelection(List<GuiFocusElement> elements, int currentIndex, int directionX, int directionY) {
        GuiFocusElement current = elements.get(currentIndex);
        int bestIndex = currentIndex;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < elements.size(); i++) {
            if (i == currentIndex) {
                continue;
            }

            GuiFocusElement candidate = elements.get(i);
            int deltaX = candidate.centerX() - current.centerX();
            int deltaY = candidate.centerY() - current.centerY();

            if (directionX < 0 && deltaX >= 0) continue;
            if (directionX > 0 && deltaX <= 0) continue;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;

            double distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
            double axisPenalty = directionX != 0 ? Math.abs(deltaY) * 0.75D : Math.abs(deltaX) * 0.75D;
            double score = distance + axisPenalty;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestIndex == currentIndex && elements.size() > 1) {
            int step = (directionX > 0 || directionY > 0) ? 1 : -1;
            return Math.floorMod(currentIndex + step, elements.size());
        }
        return bestIndex;
    }

    private static void applySelection(Screen screen, List<GuiFocusElement> focusElements, int selectedIndex) {
        for (int i = 0; i < focusElements.size(); i++) {
            ClickableWidget widget = focusElements.get(i).widget();
            boolean selected = i == selectedIndex;
            widget.setFocused(selected);
            if (selected) {
                screen.setFocused(widget);
            }
        }
    }

    private static void triggerConfirm(GuiFocusElement selected) {
        ClickableWidget widget = selected.widget();
        double centerX = selected.centerX();
        double centerY = selected.centerY();
        Click click = new Click(centerX, centerY, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        widget.mouseClicked(click, false);
        widget.mouseReleased(click);
    }

    private static void triggerBack(Screen screen) {
        if (screen.shouldCloseOnEsc()) {
            screen.close();
        } else {
            pressKey(screen, GLFW.GLFW_KEY_ESCAPE);
        }
    }

    private static boolean pressKey(Element element, int keyCode) {
        return pressKey(element, keyCode, 0);
    }

    private static boolean pressKey(Element element, int keyCode, int modifiers) {
        if (element == null) {
            return false;
        }
        return element.keyPressed(new KeyInput(keyCode, 0, modifiers));
    }
}
