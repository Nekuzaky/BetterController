package com.bettercontroller.client.gui;

import com.bettercontroller.BetterControllerMod;
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
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GuiNavigationController {
    private static final int SLOT_ROW_TOLERANCE = 10;
    private static final int SLOT_TARGET_ROW_TOLERANCE = 6;
    private static final long CONTROLLER_CURSOR_CAPTURE_TIMEOUT_MS = 2200L;
    private static Field creativeSelectedTabField;
    private static Method creativeSetSelectedTabMethod;
    private static boolean creativeReflectionInitialized;
    private static boolean creativeReflectionUnavailableLogged;
    private final ControllerInventorySelectionState inventorySelectionState;
    private Screen lastScreen;
    private int selectedIndex = -1;
    private int selectedHandledSlotId = -1;
    private boolean preferListFocus;
    private boolean controllerCursorCaptured;
    private long lastControllerUiInputMs;
    private NavigationMode navigationMode = NavigationMode.WIDGETS;
    private NavigationMode lastLoggedNavigationMode;
    private Element lastFocusedAtTickEnd;

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
        Element focusedAtStart = screen.getFocused();
        boolean screenChanged = screen != lastScreen;
        if (screenChanged) {
            selectedIndex = -1;
            selectedHandledSlotId = -1;
            inventorySelectionState.onScreenChanged(screen);
            lastFocusedAtTickEnd = null;
        }
        lastScreen = screen;
        if (inputFrame.anyNavigation()) {
            lastControllerUiInputMs = System.currentTimeMillis();
        }

        AlwaysSelectedEntryListWidget<?> listWidget = findListWidget(screen);
        List<GuiFocusElement> focusElements = collectFocusElements(screen);
        if (screenChanged && !focusElements.isEmpty()) {
            selectedIndex = findInitialSelectionIndex(screen, focusElements);
            if (screen instanceof BetterControllerSettingsScreen) {
                selectedIndex = preferNonSliderSelection(focusElements, selectedIndex);
            }
        }
        selectedIndex = ensureValidSelection(screen, focusElements, selectedIndex);

        HandledScreen<?> handledScreen = screen instanceof HandledScreen<?> candidate ? candidate : null;
        List<Slot> handledSlots = handledScreen == null ? List.of() : collectNavigableSlots(handledScreen);
        boolean hasHandledSlots = !handledSlots.isEmpty();
        if (hasHandledSlots) {
            initializeHandledSlotSelection(client, handledSlots, screenChanged);
        }

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

        navigationMode = resolveNavigationMode(
            screen,
            listWidget,
            focusElements,
            handledScreen,
            handledSlots,
            inputFrame,
            screenChanged
        );

        if (navigationMode != NavigationMode.INVENTORY) {
            releaseControllerCursorCapture(client);
        }

        NavigationDirection directionalInput = resolveDirectionalInput(inputFrame);
        boolean sliderEditUsed = false;
        boolean inventoryConsumed = false;
        boolean listConsumed = false;

        switch (navigationMode) {
            case INVENTORY -> {
                if (handledScreen == null || handledSlots.isEmpty()) {
                    navigationMode = resolveFallbackMode(screen, listWidget, focusElements, hasHandledSlots);
                    WidgetNavigationResult fallbackResult = handleWidgetOrListNavigation(
                        screen,
                        listWidget,
                        focusElements,
                        inputFrame,
                        virtualKeyboardRequest
                    );
                    sliderEditUsed = fallbackResult.sliderEditUsed();
                    listConsumed = fallbackResult.listConsumed();
                    // Inventory mode could not run; fallback widget/list path already handled this tick.
                } else {
                    boolean captureCursor = (System.currentTimeMillis() - lastControllerUiInputMs) <= CONTROLLER_CURSOR_CAPTURE_TIMEOUT_MS;
                    inventoryConsumed = handleHandledScreenNavigation(
                        client,
                        handledScreen,
                        handledSlots,
                        inputFrame,
                        screenChanged,
                        captureCursor
                    );
                }
            }
            case LIST -> listConsumed = handleListNavigation(screen, listWidget, inputFrame);
            case TEXT_INPUT -> handleTextInputNavigation(screen, inputFrame, virtualKeyboardRequest);
            case WIDGETS -> {
                WidgetNavigationResult widgetResult = handleWidgetOrListNavigation(
                    screen,
                    listWidget,
                    focusElements,
                    inputFrame,
                    virtualKeyboardRequest
                );
                sliderEditUsed = widgetResult.sliderEditUsed();
                listConsumed = widgetResult.listConsumed();
            }
        }

        Element focusedAtEnd = screen.getFocused();
        boolean externalFocusChange = !screenChanged
            && !inputFrame.anyNavigation()
            && lastFocusedAtTickEnd != null
            && focusedAtStart != lastFocusedAtTickEnd;
        lastFocusedAtTickEnd = focusedAtEnd;

        String selectedWidgetClass = "none";
        int selectedWidgetIndex = -1;
        if (!focusElements.isEmpty() && selectedIndex >= 0 && selectedIndex < focusElements.size()) {
            selectedWidgetIndex = selectedIndex;
            selectedWidgetClass = focusElements.get(selectedIndex).widget().getClass().getSimpleName();
        }

        boolean shouldLog = inputFrame.anyNavigation()
            || screenChanged
            || externalFocusChange
            || inventoryConsumed
            || listConsumed
            || sliderEditUsed
            || navigationMode != lastLoggedNavigationMode;
        if (shouldLog) {
            BetterControllerMod.LOGGER.info(
                "[GUI-HOTFIX] nav screen={} mode={} dir={} widgetIndex={} widgetClass={} slotId={} sliderEdit={} invConsumed={} listConsumed={} focusStart={} focusEnd={} externalFocusChange={}",
                screen.getClass().getName(),
                navigationMode,
                directionalInput == null ? "none" : directionalInput.name(),
                selectedWidgetIndex,
                selectedWidgetClass,
                selectedHandledSlotId,
                sliderEditUsed,
                inventoryConsumed,
                listConsumed,
                focusedAtStart == null ? "none" : focusedAtStart.getClass().getSimpleName(),
                focusedAtEnd == null ? "none" : focusedAtEnd.getClass().getSimpleName(),
                externalFocusChange
            );
            lastLoggedNavigationMode = navigationMode;
        }
    }

    public void reset() {
        lastScreen = null;
        selectedIndex = -1;
        selectedHandledSlotId = -1;
        preferListFocus = false;
        controllerCursorCaptured = false;
        lastControllerUiInputMs = 0L;
        navigationMode = NavigationMode.WIDGETS;
        lastLoggedNavigationMode = null;
        lastFocusedAtTickEnd = null;
        inventorySelectionState.clear();
    }

    private NavigationMode resolveNavigationMode(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        List<GuiFocusElement> focusElements,
        HandledScreen<?> handledScreen,
        List<Slot> handledSlots,
        GuiInputFrame inputFrame,
        boolean screenChanged
    ) {
        boolean hasHandledSlots = handledScreen != null && handledSlots != null && !handledSlots.isEmpty();
        boolean hasFocusableWidgets = focusElements != null && !focusElements.isEmpty();
        NavigationDirection direction = resolveDirectionalInput(inputFrame);

        if (screenChanged) {
            navigationMode = resolveInitialMode(screen, listWidget, hasFocusableWidgets, hasHandledSlots);
        }

        boolean focusedTextInput = screen.getFocused() instanceof TextFieldWidget;
        if (focusedTextInput && hasHandledSlots && direction != null) {
            // Keep the existing handled-screen behavior: directional input can reclaim slot navigation
            // from focused text fields such as Creative search.
            screen.setFocused(null);
            focusedTextInput = false;
            if (navigationMode == NavigationMode.TEXT_INPUT) {
                navigationMode = NavigationMode.INVENTORY;
            }
        }
        if (focusedTextInput) {
            return NavigationMode.TEXT_INPUT;
        }

        if (!hasHandledSlots && navigationMode == NavigationMode.INVENTORY) {
            navigationMode = resolveFallbackMode(screen, listWidget, focusElements, false);
        }
        if (listWidget == null && navigationMode == NavigationMode.LIST) {
            navigationMode = resolveFallbackMode(screen, null, focusElements, hasHandledSlots);
        }

        if (screen.getFocused() == listWidget && listWidget != null) {
            navigationMode = NavigationMode.LIST;
        } else if (screen.getFocused() instanceof ClickableWidget && hasFocusableWidgets) {
            navigationMode = NavigationMode.WIDGETS;
        }

        if (navigationMode == NavigationMode.INVENTORY) {
            if (!hasHandledSlots) {
                return resolveFallbackMode(screen, listWidget, focusElements, false);
            }
            if ((inputFrame.tabNext() || inputFrame.tabPrev()) && (hasFocusableWidgets || listWidget != null)) {
                return resolveFallbackMode(screen, listWidget, focusElements, true);
            }
            if (direction != null
                && !canMoveInventorySelection(handledSlots, direction)
                && (hasFocusableWidgets || listWidget != null)) {
                return resolveFallbackMode(screen, listWidget, focusElements, true);
            }
            return NavigationMode.INVENTORY;
        }

        if (navigationMode == NavigationMode.LIST && listWidget != null) {
            if (!preferListFocus && direction != null && direction.horizontal()) {
                if (hasHandledSlots) {
                    return NavigationMode.INVENTORY;
                }
                return hasFocusableWidgets ? NavigationMode.WIDGETS : NavigationMode.LIST;
            }
            return NavigationMode.LIST;
        }

        if (hasFocusableWidgets) {
            if (hasHandledSlots
                && navigationMode == NavigationMode.WIDGETS
                && direction != null
                && !canMoveWidgetSelection(screen, focusElements, direction)) {
                return NavigationMode.INVENTORY;
            }
            return NavigationMode.WIDGETS;
        }

        if (listWidget != null && preferListFocus) {
            return NavigationMode.LIST;
        }
        if (hasHandledSlots) {
            return NavigationMode.INVENTORY;
        }
        return NavigationMode.WIDGETS;
    }

    private static NavigationMode resolveInitialMode(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        boolean hasFocusableWidgets,
        boolean hasHandledSlots
    ) {
        if (screen != null && screen.getFocused() instanceof TextFieldWidget) {
            return NavigationMode.TEXT_INPUT;
        }
        if (screen != null && listWidget != null && screen.getFocused() == listWidget) {
            return NavigationMode.LIST;
        }
        if (hasHandledSlots) {
            return NavigationMode.INVENTORY;
        }
        if (hasFocusableWidgets) {
            return NavigationMode.WIDGETS;
        }
        if (listWidget != null) {
            return NavigationMode.LIST;
        }
        return NavigationMode.WIDGETS;
    }

    private NavigationMode resolveFallbackMode(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        List<GuiFocusElement> focusElements,
        boolean hasHandledSlots
    ) {
        if (screen != null && screen.getFocused() instanceof TextFieldWidget) {
            return NavigationMode.TEXT_INPUT;
        }
        if (listWidget != null && (preferListFocus || screen.getFocused() == listWidget)) {
            return NavigationMode.LIST;
        }
        if (focusElements != null && !focusElements.isEmpty()) {
            return NavigationMode.WIDGETS;
        }
        return hasHandledSlots ? NavigationMode.INVENTORY : NavigationMode.WIDGETS;
    }

    private boolean canMoveInventorySelection(List<Slot> slots, NavigationDirection direction) {
        if (slots == null || slots.isEmpty() || direction == null) {
            return false;
        }
        Slot current = findSlotById(slots, selectedHandledSlotId);
        if (current == null) {
            return true;
        }
        int nextSlotId = findDirectionalSlotId(slots, current.id, direction.x(), direction.y());
        return nextSlotId != current.id;
    }

    private boolean canMoveWidgetSelection(
        Screen screen,
        List<GuiFocusElement> focusElements,
        NavigationDirection direction
    ) {
        if (direction == null || focusElements == null || focusElements.isEmpty()) {
            return false;
        }

        if (selectedIndex < 0 || selectedIndex >= focusElements.size()) {
            return false;
        }

        if (screen instanceof BetterControllerSettingsScreen) {
            if (direction.vertical()) {
                return focusElements.size() > 1;
            }
            ClickableWidget selectedWidget = focusElements.get(selectedIndex).widget();
            return selectedWidget instanceof SliderWidget;
        }

        ClickableWidget selectedWidget = focusElements.get(selectedIndex).widget();
        if (selectedWidget instanceof SliderWidget && direction.horizontal()) {
            return true;
        }
        int nextIndex = findDirectionalSelection(focusElements, selectedIndex, direction.x(), direction.y());
        return nextIndex != selectedIndex;
    }

    private static NavigationDirection resolveDirectionalInput(GuiInputFrame inputFrame) {
        if (inputFrame == null) {
            return null;
        }
        if (inputFrame.up()) {
            return NavigationDirection.UP;
        }
        if (inputFrame.down()) {
            return NavigationDirection.DOWN;
        }
        if (inputFrame.left()) {
            return NavigationDirection.LEFT;
        }
        if (inputFrame.right()) {
            return NavigationDirection.RIGHT;
        }
        return null;
    }

    private void initializeHandledSlotSelection(MinecraftClient client, List<Slot> slots, boolean screenChanged) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        if (screenChanged || findSlotById(slots, selectedHandledSlotId) == null) {
            Slot initial = resolveInitialSlot(client, slots);
            if (initial != null) {
                selectedHandledSlotId = initial.id;
            }
        }
        if (findSlotById(slots, selectedHandledSlotId) == null) {
            selectedHandledSlotId = slots.get(0).id;
        }
    }

    private void handleTextInputNavigation(
        Screen screen,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (screen == null || inputFrame == null) {
            return;
        }

        if (inputFrame.confirm() && screen.getFocused() instanceof TextFieldWidget textFieldWidget) {
            triggerConfirm(GuiFocusElement.fromWidget(textFieldWidget), virtualKeyboardRequest);
        }
        if (inputFrame.back()) {
            triggerBack(screen);
        }
        if (inputFrame.tabNext()) {
            pressKey(screen, GLFW.GLFW_KEY_TAB);
        }
        if (inputFrame.tabPrev()) {
            pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        }
        if (inputFrame.pageNext()) {
            pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
        }
        if (inputFrame.pagePrev()) {
            pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
        }
    }

    private WidgetNavigationResult handleWidgetOrListNavigation(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        List<GuiFocusElement> focusElements,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (screen == null || inputFrame == null) {
            return WidgetNavigationResult.NONE;
        }

        if (navigationMode == NavigationMode.LIST && listWidget != null) {
            boolean consumed = handleListNavigation(screen, listWidget, inputFrame);
            return new WidgetNavigationResult(false, consumed);
        }

        if (focusElements == null || focusElements.isEmpty()) {
            if (inputFrame.back()) {
                triggerBack(screen);
            }
            if (inputFrame.tabNext()) {
                pressKey(screen, GLFW.GLFW_KEY_TAB);
            }
            if (inputFrame.tabPrev()) {
                pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
            }
            if (inputFrame.pageNext()) {
                pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
            }
            if (inputFrame.pagePrev()) {
                pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
            }
            return WidgetNavigationResult.NONE;
        }

        selectedIndex = ensureValidSelection(screen, focusElements, selectedIndex);
        applySelection(screen, focusElements, selectedIndex);

        if (screen instanceof BetterControllerSettingsScreen) {
            LinearSettingsResult settingsResult = handleLinearSettingsNavigation(
                screen,
                focusElements,
                selectedIndex,
                inputFrame,
                virtualKeyboardRequest
            );
            selectedIndex = settingsResult.selectedIndex();
            return new WidgetNavigationResult(settingsResult.sliderEditUsed(), false);
        }

        GuiFocusElement selected = focusElements.get(selectedIndex);
        ClickableWidget selectedWidget = selected.widget();
        NavigationDirection direction = resolveDirectionalInput(inputFrame);
        boolean sliderEditUsed = false;
        if (direction != null) {
            if (selectedWidget instanceof SliderWidget sliderWidget && direction.horizontal()) {
                pressKey(sliderWidget, direction.x() < 0 ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT);
                sliderEditUsed = true;
            } else {
                selectedIndex = findDirectionalSelection(focusElements, selectedIndex, direction.x(), direction.y());
                applySelection(screen, focusElements, selectedIndex);
                selected = focusElements.get(selectedIndex);
            }
        }

        if (inputFrame.confirm()) {
            triggerConfirm(selected, virtualKeyboardRequest);
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
        return new WidgetNavigationResult(sliderEditUsed, false);
    }

    private boolean handleHandledScreenNavigation(
        MinecraftClient client,
        HandledScreen<?> handledScreen,
        List<Slot> slots,
        GuiInputFrame inputFrame,
        boolean screenChanged,
        boolean captureCursor
    ) {
        if (slots.isEmpty()) {
            releaseControllerCursorCapture(client);
            return false;
        }

        updateControllerCursorCapture(client, captureCursor, inputFrame.anyNavigation());
        initializeHandledSlotSelection(client, slots, screenChanged);

        Slot selectedSlot = findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.id;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);

        boolean consumed = false;
        boolean creativeScreen = handledScreen instanceof CreativeInventoryScreen;

        NavigationDirection direction = resolveDirectionalInput(inputFrame);
        if (direction != null) {
            int nextSlotId = findDirectionalSlotId(slots, selectedHandledSlotId, direction.x(), direction.y());
            if (nextSlotId != selectedHandledSlotId) {
                selectedHandledSlotId = nextSlotId;
                consumed = true;
            } else if (creativeScreen && direction.horizontal()) {
                int step = direction == NavigationDirection.RIGHT ? 1 : -1;
                if (cycleCreativeTab((CreativeInventoryScreen) handledScreen, step)) {
                    selectedHandledSlotId = -1;
                    consumed = true;
                }
            }
        }

        selectedSlot = findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.id;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);
        if (inputFrame.confirm()) {
            clickHandledSlot(client, handledScreen, selectedSlot);
            consumed = true;
        }
        if (inputFrame.tabNext()) {
            if (creativeScreen && cycleCreativeTab((CreativeInventoryScreen) handledScreen, 1)) {
                selectedHandledSlotId = -1;
                consumed = true;
            } else {
                consumed = pressKey(handledScreen, GLFW.GLFW_KEY_TAB) || consumed;
            }
        }
        if (inputFrame.tabPrev()) {
            if (creativeScreen && cycleCreativeTab((CreativeInventoryScreen) handledScreen, -1)) {
                selectedHandledSlotId = -1;
                consumed = true;
            } else {
                consumed = pressKey(handledScreen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT) || consumed;
            }
        }
        if (inputFrame.pageNext()) {
            if (creativeScreen && cycleCreativeTab((CreativeInventoryScreen) handledScreen, 1)) {
                selectedHandledSlotId = -1;
                consumed = true;
            } else {
                consumed = pressKey(handledScreen, GLFW.GLFW_KEY_PAGE_DOWN) || consumed;
            }
        }
        if (inputFrame.pagePrev()) {
            if (creativeScreen && cycleCreativeTab((CreativeInventoryScreen) handledScreen, -1)) {
                selectedHandledSlotId = -1;
                consumed = true;
            } else {
                consumed = pressKey(handledScreen, GLFW.GLFW_KEY_PAGE_UP) || consumed;
            }
        }
        if (inputFrame.back()) {
            triggerBack(handledScreen);
            consumed = true;
        }
        return consumed;
    }

    private static boolean handleListNavigation(
        Screen screen,
        AlwaysSelectedEntryListWidget<?> listWidget,
        GuiInputFrame inputFrame
    ) {
        if (screen == null || listWidget == null || inputFrame == null) {
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

        if (inputFrame.tabNext()) {
            used = pressKey(screen, GLFW.GLFW_KEY_TAB) || used;
        }
        if (inputFrame.tabPrev()) {
            used = pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT) || used;
        }
        if (inputFrame.back()) {
            triggerBack(screen);
            used = true;
        }

        return used;
    }

    private static LinearSettingsResult handleLinearSettingsNavigation(
        Screen screen,
        List<GuiFocusElement> focusElements,
        int selectedIndex,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (focusElements.isEmpty()) {
            return new LinearSettingsResult(selectedIndex, false);
        }

        if (inputFrame.down()) {
            selectedIndex = Math.floorMod(selectedIndex + 1, focusElements.size());
        } else if (inputFrame.up()) {
            selectedIndex = Math.floorMod(selectedIndex - 1, focusElements.size());
        }

        applySelection(screen, focusElements, selectedIndex);
        GuiFocusElement selected = focusElements.get(selectedIndex);
        ClickableWidget widget = selected.widget();
        boolean sliderEditUsed = false;

        if (widget instanceof SliderWidget sliderWidget) {
            if (inputFrame.left()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_LEFT);
                sliderEditUsed = true;
            }
            if (inputFrame.right()) {
                pressKey(sliderWidget, GLFW.GLFW_KEY_RIGHT);
                sliderEditUsed = true;
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
        return new LinearSettingsResult(selectedIndex, sliderEditUsed);
    }

    private enum NavigationMode {
        WIDGETS,
        LIST,
        INVENTORY,
        TEXT_INPUT
    }

    private enum NavigationDirection {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        private final int x;
        private final int y;

        NavigationDirection(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        boolean horizontal() {
            return x != 0;
        }

        boolean vertical() {
            return y != 0;
        }
    }

    private record WidgetNavigationResult(boolean sliderEditUsed, boolean listConsumed) {
        private static final WidgetNavigationResult NONE = new WidgetNavigationResult(false, false);
    }

    private record LinearSettingsResult(int selectedIndex, boolean sliderEditUsed) {
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

    private static boolean cycleCreativeTab(CreativeInventoryScreen screen, int step) {
        if (screen == null || step == 0) {
            return false;
        }
        if (!initCreativeReflection()) {
            return false;
        }

        List<ItemGroup> groups = ItemGroups.getGroupsToDisplay();
        if (groups == null || groups.isEmpty()) {
            return false;
        }

        ItemGroup current = null;
        try {
            if (creativeSelectedTabField != null) {
                current = (ItemGroup) creativeSelectedTabField.get(null);
            }
        } catch (ReflectiveOperationException exception) {
            BetterControllerMod.LOGGER.warn("[GUI-HOTFIX] creative selected tab reflection read failed: {}", exception.getMessage());
            return false;
        }

        int currentIndex = groups.indexOf(current);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = Math.floorMod(currentIndex + (step > 0 ? 1 : -1), groups.size());
        ItemGroup target = groups.get(nextIndex);
        if (target == null || target == current) {
            return false;
        }

        try {
            creativeSetSelectedTabMethod.invoke(screen, target);
            BetterControllerMod.LOGGER.info(
                "[GUI-HOTFIX] creative tab switched step={} from={} to={}",
                step,
                current == null ? "none" : current.getDisplayName().getString(),
                target.getDisplayName().getString()
            );
            return true;
        } catch (ReflectiveOperationException exception) {
            BetterControllerMod.LOGGER.warn("[GUI-HOTFIX] creative tab reflection invoke failed: {}", exception.getMessage());
            return false;
        }
    }

    private static boolean initCreativeReflection() {
        if (creativeReflectionInitialized) {
            return creativeSetSelectedTabMethod != null && creativeSelectedTabField != null;
        }
        creativeReflectionInitialized = true;
        try {
            creativeSelectedTabField = CreativeInventoryScreen.class.getDeclaredField("selectedTab");
            creativeSelectedTabField.setAccessible(true);
            creativeSetSelectedTabMethod = CreativeInventoryScreen.class.getDeclaredMethod("setSelectedTab", ItemGroup.class);
            creativeSetSelectedTabMethod.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException exception) {
            if (!creativeReflectionUnavailableLogged) {
                creativeReflectionUnavailableLogged = true;
                BetterControllerMod.LOGGER.warn("[GUI-HOTFIX] creative tab reflection unavailable: {}", exception.getMessage());
            }
            creativeSelectedTabField = null;
            creativeSetSelectedTabMethod = null;
            return false;
        }
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
