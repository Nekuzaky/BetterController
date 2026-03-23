package com.bettercontroller.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GuiNavigationController {
    private static final int SLOT_ROW_TOLERANCE = 10;
    private static final int SLOT_TARGET_ROW_TOLERANCE = 6;
    private static final long CONTROLLER_CURSOR_CAPTURE_TIMEOUT_MS = 2200L;
    private final ControllerInventorySelectionState inventorySelectionState;
    private Screen lastScreen;
    private int selectedIndex = -1;
    private int selectedHandledSlotId = -1;
    private boolean preferListFocus;
    private boolean controllerCursorCaptured;
    private long lastControllerUiInputMs;

    public GuiNavigationController(ControllerInventorySelectionState inventorySelectionState) {
        this.inventorySelectionState = inventorySelectionState == null
            ? new ControllerInventorySelectionState()
            : inventorySelectionState;
    }

    public void onScreenTick(
        MinecraftClient client,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (client == null || client.currentScreen == null || inputFrame == null) {
            return;
        }

        Screen screen = client.currentScreen;
        boolean screenChanged = screen != lastScreen;
        if (screenChanged) {
            selectedIndex = -1;
            selectedHandledSlotId = -1;
            inventorySelectionState.onScreenChanged(screen);
        }
        lastScreen = screen;
        if (inputFrame.anyNavigation()) {
            lastControllerUiInputMs = System.currentTimeMillis();
        }

        AlwaysSelectedEntryListWidget<?> listWidget = findListWidget(screen);
        if (screenChanged) {
            preferListFocus = listWidget != null;
        } else if (listWidget == null) {
            preferListFocus = false;
        } else if (inputFrame.left() || inputFrame.right() || inputFrame.tabNext() || inputFrame.tabPrev()) {
            preferListFocus = false;
        } else if (inputFrame.up() || inputFrame.down() || inputFrame.pageNext() || inputFrame.pagePrev()) {
            preferListFocus = true;
        } else if (inputFrame.confirm() && screen.getFocused() == listWidget) {
            preferListFocus = true;
        }

        boolean focusedTextInput = screen.getFocused() instanceof TextFieldWidget;
        if (focusedTextInput
            && screen instanceof HandledScreen<?>
            && (inputFrame.up() || inputFrame.down() || inputFrame.left() || inputFrame.right())) {
            // Let D-pad/stick immediately reclaim inventory navigation on handled screens
            // (notably Creative inventory search field focus).
            screen.setFocused(null);
            focusedTextInput = false;
        }
        if (screen instanceof HandledScreen<?> handledScreen && !focusedTextInput) {
            boolean captureCursor = (System.currentTimeMillis() - lastControllerUiInputMs) <= CONTROLLER_CURSOR_CAPTURE_TIMEOUT_MS;
            boolean handledInventoryNavigation = handleHandledScreenNavigation(
                client,
                handledScreen,
                inputFrame,
                screenChanged,
                captureCursor
            );
            if (handledInventoryNavigation) {
                if (inputFrame.back()) {
                    triggerBack(screen);
                }
                return;
            }
        }
        releaseControllerCursorCapture(client);

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

        if (screenChanged) {
            selectedIndex = findInitialSelectionIndex(screen, focusElements);
            if (screen instanceof BetterControllerSettingsScreen) {
                selectedIndex = preferNonSliderSelection(focusElements, selectedIndex);
            }
        }
        if (selectedIndex < 0 || selectedIndex >= focusElements.size()) {
            selectedIndex = 0;
        }
        selectedIndex = ensureValidSelection(screen, focusElements, selectedIndex);
        applySelection(screen, focusElements, selectedIndex);

        if (screen instanceof BetterControllerSettingsScreen) {
            selectedIndex = handleLinearSettingsNavigation(
                screen,
                focusElements,
                selectedIndex,
                inputFrame,
                virtualKeyboardRequest
            );
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
            triggerConfirm(focusElements.get(selectedIndex), virtualKeyboardRequest);
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
        selectedHandledSlotId = -1;
        preferListFocus = false;
        controllerCursorCaptured = false;
        lastControllerUiInputMs = 0L;
        inventorySelectionState.clear();
    }

    private boolean handleHandledScreenNavigation(
        MinecraftClient client,
        HandledScreen<?> handledScreen,
        GuiInputFrame inputFrame,
        boolean screenChanged,
        boolean captureCursor
    ) {
        boolean hasInventoryNavigationInput = inputFrame.up()
            || inputFrame.down()
            || inputFrame.left()
            || inputFrame.right()
            || inputFrame.confirm();

        List<Slot> slots = collectNavigableSlots(handledScreen);
        if (slots.isEmpty()) {
            releaseControllerCursorCapture(client);
            return false;
        }

        updateControllerCursorCapture(client, captureCursor, inputFrame.anyNavigation());

        if (screenChanged || findSlotById(slots, selectedHandledSlotId) == null) {
            Slot initial = resolveInitialSlot(client, slots);
            selectedHandledSlotId = initial.id;
        }

        Slot selectedSlot = findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.id;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);

        if (!hasInventoryNavigationInput) {
            // Keep controller selection authoritative every tick on handled screens,
            // otherwise vanilla mouse hover can steal focus after a pickup/place action.
            return true;
        }

        if (inputFrame.up()) {
            selectedHandledSlotId = findDirectionalSlotId(slots, selectedHandledSlotId, 0, -1);
        } else if (inputFrame.down()) {
            selectedHandledSlotId = findDirectionalSlotId(slots, selectedHandledSlotId, 0, 1);
        } else if (inputFrame.left()) {
            selectedHandledSlotId = findDirectionalSlotId(slots, selectedHandledSlotId, -1, 0);
        } else if (inputFrame.right()) {
            selectedHandledSlotId = findDirectionalSlotId(slots, selectedHandledSlotId, 1, 0);
        }

        selectedSlot = findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.id;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);
        if (inputFrame.confirm()) {
            clickHandledSlot(client, handledScreen, selectedSlot);
        }
        if (inputFrame.tabNext()) {
            pressKey(handledScreen, GLFW.GLFW_KEY_TAB);
        }
        if (inputFrame.tabPrev()) {
            pressKey(handledScreen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        }
        if (inputFrame.pageNext()) {
            pressKey(handledScreen, GLFW.GLFW_KEY_PAGE_DOWN);
        }
        if (inputFrame.pagePrev()) {
            pressKey(handledScreen, GLFW.GLFW_KEY_PAGE_UP);
        }
        return true;
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

        boolean directionalOrPage = inputFrame.up()
            || inputFrame.down()
            || inputFrame.pageNext()
            || inputFrame.pagePrev();
        boolean listFocused = screen.getFocused() == listWidget;
        boolean routeToList = (preferListFocus && directionalOrPage)
            || (listFocused && (directionalOrPage || inputFrame.confirm()));
        if (!routeToList) {
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

        // Only consume when we actually routed a list action, so focused buttons still receive confirm.
        return used || routeToList;
    }

    private static int handleLinearSettingsNavigation(
        Screen screen,
        List<GuiFocusElement> focusElements,
        int selectedIndex,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
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
                triggerConfirm(selected, virtualKeyboardRequest);
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

    private static int ensureValidSelection(Screen screen, List<GuiFocusElement> focusElements, int selectedIndex) {
        if (focusElements.isEmpty()) {
            return -1;
        }

        if (selectedIndex >= 0 && selectedIndex < focusElements.size()) {
            ClickableWidget selectedWidget = focusElements.get(selectedIndex).widget();
            if (selectedWidget.active && selectedWidget.visible) {
                return selectedIndex;
            }
        }

        int focusedIndex = findInitialSelectionIndex(screen, focusElements);
        if (focusedIndex >= 0 && focusedIndex < focusElements.size()) {
            if (screen instanceof BetterControllerSettingsScreen) {
                return preferNonSliderSelection(focusElements, focusedIndex);
            }
            return focusedIndex;
        }
        if (screen instanceof BetterControllerSettingsScreen) {
            return preferNonSliderSelection(focusElements, 0);
        }
        return 0;
    }

    private static int preferNonSliderSelection(List<GuiFocusElement> focusElements, int fallbackIndex) {
        if (focusElements == null || focusElements.isEmpty()) {
            return -1;
        }
        if (fallbackIndex >= 0
            && fallbackIndex < focusElements.size()
            && !(focusElements.get(fallbackIndex).widget() instanceof SliderWidget)) {
            return fallbackIndex;
        }
        for (int i = 0; i < focusElements.size(); i++) {
            if (!(focusElements.get(i).widget() instanceof SliderWidget)) {
                return i;
            }
        }
        return Math.max(0, Math.min(fallbackIndex, focusElements.size() - 1));
    }

    private static int findDirectionalSlotId(List<Slot> slots, int currentId, int directionX, int directionY) {
        Slot current = findSlotById(slots, currentId);
        if (current == null) {
            return slots.get(0).id;
        }

        Slot rowColumnCandidate = findRowColumnNeighborSlot(slots, current, directionX, directionY);
        if (rowColumnCandidate != null) {
            return rowColumnCandidate.id;
        }

        Slot laneCandidate = findLaneAlignedSlot(slots, current, directionX, directionY);
        if (laneCandidate != null) {
            return laneCandidate.id;
        }

        int currentCenterX = current.x + 8;
        int currentCenterY = current.y + 8;
        Slot best = current;
        double bestScore = Double.MAX_VALUE;

        for (Slot candidate : slots) {
            if (candidate == null || candidate.id == current.id) {
                continue;
            }

            int deltaX = (candidate.x + 8) - currentCenterX;
            int deltaY = (candidate.y + 8) - currentCenterY;

            if (directionX < 0 && deltaX >= 0) continue;
            if (directionX > 0 && deltaX <= 0) continue;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;

            double distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
            double axisPenalty = directionX != 0 ? Math.abs(deltaY) * 0.55D : Math.abs(deltaX) * 0.55D;
            double score = distance + axisPenalty;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best.id;
    }

    private static Slot findRowColumnNeighborSlot(List<Slot> slots, Slot current, int directionX, int directionY) {
        if (slots == null || current == null || (directionX == 0 && directionY == 0)) {
            return null;
        }

        int currentCenterX = current.x + 8;
        int currentCenterY = current.y + 8;

        if (directionX != 0) {
            Slot best = null;
            int bestPrimary = Integer.MAX_VALUE;
            int bestSecondary = Integer.MAX_VALUE;
            for (Slot candidate : slots) {
                if (candidate == null || candidate.id == current.id) {
                    continue;
                }
                int deltaX = (candidate.x + 8) - currentCenterX;
                int deltaY = (candidate.y + 8) - currentCenterY;
                if (directionX < 0 && deltaX >= 0) continue;
                if (directionX > 0 && deltaX <= 0) continue;

                int rowDistance = Math.abs(deltaY);
                if (rowDistance > SLOT_ROW_TOLERANCE) {
                    continue;
                }

                int columnDistance = Math.abs(deltaX);
                if (columnDistance < bestPrimary || (columnDistance == bestPrimary && rowDistance < bestSecondary)) {
                    bestPrimary = columnDistance;
                    bestSecondary = rowDistance;
                    best = candidate;
                }
            }
            return best;
        }

        int nearestTargetRowDistance = Integer.MAX_VALUE;
        Integer targetRowCenterY = null;
        for (Slot candidate : slots) {
            if (candidate == null || candidate.id == current.id) {
                continue;
            }
            int deltaY = (candidate.y + 8) - currentCenterY;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;
            int rowDistance = Math.abs(deltaY);
            if (rowDistance < nearestTargetRowDistance) {
                nearestTargetRowDistance = rowDistance;
                targetRowCenterY = candidate.y + 8;
            }
        }
        if (targetRowCenterY == null) {
            return null;
        }

        Slot best = null;
        int bestPrimary = Integer.MAX_VALUE;
        int bestSecondary = Integer.MAX_VALUE;
        for (Slot candidate : slots) {
            if (candidate == null || candidate.id == current.id) {
                continue;
            }
            int candidateCenterX = candidate.x + 8;
            int candidateCenterY = candidate.y + 8;
            int deltaY = candidateCenterY - currentCenterY;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;

            int targetRowDistance = Math.abs(candidateCenterY - targetRowCenterY);
            if (targetRowDistance > SLOT_TARGET_ROW_TOLERANCE) {
                continue;
            }

            int columnDistance = Math.abs(candidateCenterX - currentCenterX);
            int rowDistance = Math.abs(deltaY);
            if (columnDistance < bestPrimary || (columnDistance == bestPrimary && rowDistance < bestSecondary)) {
                bestPrimary = columnDistance;
                bestSecondary = rowDistance;
                best = candidate;
            }
        }

        if (best != null) {
            return best;
        }

        Slot fallback = null;
        int fallbackPrimary = Integer.MAX_VALUE;
        int fallbackSecondary = Integer.MAX_VALUE;
        for (Slot candidate : slots) {
            if (candidate == null || candidate.id == current.id) {
                continue;
            }
            int candidateCenterX = candidate.x + 8;
            int candidateCenterY = candidate.y + 8;
            int deltaY = candidateCenterY - currentCenterY;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;

            int rowDistance = Math.abs(deltaY);
            int columnDistance = Math.abs(candidateCenterX - currentCenterX);
            if (rowDistance < fallbackPrimary || (rowDistance == fallbackPrimary && columnDistance < fallbackSecondary)) {
                fallbackPrimary = rowDistance;
                fallbackSecondary = columnDistance;
                fallback = candidate;
            }
        }
        return fallback;
    }

    private static Slot findLaneAlignedSlot(List<Slot> slots, Slot current, int directionX, int directionY) {
        if (slots == null || current == null || (directionX == 0 && directionY == 0)) {
            return null;
        }

        int currentCenterX = current.x + 8;
        int currentCenterY = current.y + 8;
        int laneTolerance = 10;

        Slot best = null;
        int bestPrimary = Integer.MAX_VALUE;
        int bestSecondary = Integer.MAX_VALUE;

        for (Slot candidate : slots) {
            if (candidate == null || candidate.id == current.id) {
                continue;
            }

            int deltaX = (candidate.x + 8) - currentCenterX;
            int deltaY = (candidate.y + 8) - currentCenterY;

            if (directionX < 0 && deltaX >= 0) continue;
            if (directionX > 0 && deltaX <= 0) continue;
            if (directionY < 0 && deltaY >= 0) continue;
            if (directionY > 0 && deltaY <= 0) continue;

            int primary;
            int secondary;
            if (directionX != 0) {
                secondary = Math.abs(deltaY);
                if (secondary > laneTolerance) {
                    continue;
                }
                primary = Math.abs(deltaX);
            } else {
                secondary = Math.abs(deltaX);
                if (secondary > laneTolerance) {
                    continue;
                }
                primary = Math.abs(deltaY);
            }

            if (primary < bestPrimary || (primary == bestPrimary && secondary < bestSecondary)) {
                bestPrimary = primary;
                bestSecondary = secondary;
                best = candidate;
            }
        }

        return best;
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

    private static void triggerConfirm(
        GuiFocusElement selected,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        ClickableWidget widget = selected.widget();
        if (widget instanceof PressableWidget pressableWidget) {
            // Use the widget's native press path so option/menu buttons always receive controller confirm.
            pressableWidget.onPress(new KeyInput(GLFW.GLFW_KEY_ENTER, 0, 0));
            return;
        }

        double centerX = selected.centerX();
        double centerY = selected.centerY();
        Click click = new Click(centerX, centerY, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        widget.mouseClicked(click, false);
        widget.mouseReleased(click);
        if (widget instanceof TextFieldWidget && virtualKeyboardRequest != null) {
            virtualKeyboardRequest.accept(true);
        }
    }

    private static void triggerBack(Screen screen) {
        if (screen.shouldCloseOnEsc()) {
            screen.close();
        } else {
            pressKey(screen, GLFW.GLFW_KEY_ESCAPE);
        }
    }

    private static List<Slot> collectNavigableSlots(HandledScreen<?> screen) {
        List<Slot> slots = new ArrayList<>();
        if (screen == null || screen.getScreenHandler() == null) {
            return slots;
        }
        List<Slot> enabledSlots = new ArrayList<>();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null) {
                continue;
            }
            if (slot.isEnabled()) {
                enabledSlots.add(slot);
            }
            if (slot.isEnabled() && slot.canBeHighlighted()) {
                slots.add(slot);
            }
        }
        if (screen instanceof CreativeInventoryScreen) {
            slots = filterVisibleCreativeSlots(slots);
            enabledSlots = filterVisibleCreativeSlots(enabledSlots);
        }
        if (!slots.isEmpty()) {
            return slots;
        }
        if (!enabledSlots.isEmpty()) {
            return enabledSlots;
        }
        return screen.getScreenHandler().slots;
    }

    private static List<Slot> filterVisibleCreativeSlots(List<Slot> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        List<Slot> filtered = new ArrayList<>(source.size());
        for (Slot slot : source) {
            if (slot == null) {
                continue;
            }
            if (slot.x >= 0 && slot.y >= 0 && slot.x <= 400 && slot.y <= 400) {
                filtered.add(slot);
            }
        }
        return filtered.isEmpty() ? source : filtered;
    }

    private static Slot findSlotById(List<Slot> slots, int slotId) {
        for (Slot slot : slots) {
            if (slot != null && slot.id == slotId) {
                return slot;
            }
        }
        return null;
    }

    private static Slot resolveInitialSlot(MinecraftClient client, List<Slot> slots) {
        if (slots.isEmpty()) {
            return null;
        }
        if (client == null || client.player == null) {
            return slots.get(0);
        }

        int selectedHotbar = client.player.getInventory().getSelectedSlot();
        for (Slot slot : slots) {
            if (slot != null
                && slot.inventory == client.player.getInventory()
                && slot.getIndex() == selectedHotbar) {
                return slot;
            }
        }
        return slots.get(0);
    }

    private static void clickHandledSlot(MinecraftClient client, HandledScreen<?> screen, Slot slot) {
        if (client == null || client.player == null || client.interactionManager == null || screen == null || slot == null) {
            return;
        }
        client.interactionManager.clickSlot(
            screen.getScreenHandler().syncId,
            slot.id,
            0,
            SlotActionType.PICKUP,
            client.player
        );
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

    private void updateControllerCursorCapture(
        MinecraftClient client,
        boolean shouldCapture,
        boolean reinforcePosition
    ) {
        if (client == null || client.getWindow() == null) {
            controllerCursorCaptured = false;
            return;
        }

        long handle = client.getWindow().getHandle();
        if (handle == 0L) {
            controllerCursorCaptured = false;
            return;
        }

        if (shouldCapture) {
            if (!controllerCursorCaptured) {
                GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
                GLFW.glfwSetCursorPos(handle, 1.0D, 1.0D);
                controllerCursorCaptured = true;
                return;
            }
            if (reinforcePosition) {
                GLFW.glfwSetCursorPos(handle, 1.0D, 1.0D);
            }
            return;
        }

        releaseControllerCursorCapture(client);
    }

    private void releaseControllerCursorCapture(MinecraftClient client) {
        if (!controllerCursorCaptured) {
            return;
        }
        if (client == null || client.getWindow() == null) {
            controllerCursorCaptured = false;
            return;
        }
        long handle = client.getWindow().getHandle();
        if (handle != 0L) {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        controllerCursorCaptured = false;
    }
}
