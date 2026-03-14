package com.bettercontroller.client.config;

import com.bettercontroller.client.polling.ControllerType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ControllerConfig {
    public int schemaVersion = 5;
    public boolean autoActivateOnController = true;
    public boolean autoSwitchLayoutByControllerType = true;
    public String activeLayout = "xbox";
    public LinkedHashMap<String, String> controllerTypeLayouts = defaultControllerTypeLayouts();

    public float movementDeadzone = 0.14F;
    public float lookDeadzone = 0.07F;
    public float lookSensitivityX = 12.0F;
    public float lookSensitivityY = 11.0F;
    public float lookSpeedMultiplier = 2.25F;
    public String lookResponseCurve = "linear";
    public boolean cameraSmoothing = false;
    public float cameraSmoothingStrength = 0.35F;
    public float triggerThreshold = 0.45F;
    public float menuAxisThreshold = 0.35F;
    public int menuInitialRepeatDelayMs = 140;
    public int menuRepeatIntervalMs = 55;

    public boolean hudHintsEnabled = true;
    public boolean debugOverlayEnabled = true;
    public boolean radialMenuEnabled = true;
    public int radialMenuSlots = 8;
    public boolean radialConfirmOnRelease = true;
    public boolean vibrationEnabled = true;
    public String vibrationIntensity = "medium";
    public boolean virtualKeyboardEnabled = true;

    public AxisBindings axes = defaultAxes(false);
    public LinkedHashMap<String, List<String>> bindings = defaultBindings("xbox");
    public LinkedHashMap<String, ControllerLayout> layouts = defaultLayouts();

    public void ensureDefaults() {
        ControllerConfig fallback = createDefault();

        if (schemaVersion <= 0) {
            schemaVersion = fallback.schemaVersion;
        }
        if (activeLayout == null || activeLayout.isBlank()) {
            activeLayout = fallback.activeLayout;
        }
        if (controllerTypeLayouts == null || controllerTypeLayouts.isEmpty()) {
            controllerTypeLayouts = fallback.controllerTypeLayouts;
        }
        if (axes == null) {
            axes = fallback.axes;
        }
        if (bindings == null || bindings.isEmpty()) {
            bindings = fallback.bindings;
        }
        if (layouts == null || layouts.isEmpty()) {
            layouts = fallback.layouts;
        }
        if (lookResponseCurve == null || lookResponseCurve.isBlank()) {
            lookResponseCurve = fallback.lookResponseCurve;
        }
        if (vibrationIntensity == null || vibrationIntensity.isBlank()) {
            vibrationIntensity = fallback.vibrationIntensity;
        }
        if (lookSpeedMultiplier <= 0.0F) {
            lookSpeedMultiplier = fallback.lookSpeedMultiplier;
        }
        if (menuAxisThreshold <= 0.0F) {
            menuAxisThreshold = fallback.menuAxisThreshold;
        }
        if (menuInitialRepeatDelayMs <= 0) {
            menuInitialRepeatDelayMs = fallback.menuInitialRepeatDelayMs;
        }
        if (menuRepeatIntervalMs <= 0) {
            menuRepeatIntervalMs = fallback.menuRepeatIntervalMs;
        }

        mergeMissingAxes(axes, fallback.axes);
        mergeMissingBindings(bindings, fallback.bindings);
        mergeMissingLayouts(layouts, fallback.layouts);
        mergeMissingControllerTypeLayouts(controllerTypeLayouts, fallback.controllerTypeLayouts);
    }

    public ResolvedLayout resolveLayout(ControllerType controllerType) {
        String layoutName = activeLayout;
        if (autoSwitchLayoutByControllerType) {
            String key = normalizeControllerTypeKey(controllerType);
            String mappedLayout = controllerTypeLayouts.get(key);
            if (mappedLayout != null && !mappedLayout.isBlank()) {
                layoutName = mappedLayout;
            }
        }

        ControllerLayout layout = layouts.get(layoutName);
        if (layout == null && !layouts.isEmpty()) {
            Map.Entry<String, ControllerLayout> first = layouts.entrySet().iterator().next();
            layoutName = first.getKey();
            layout = first.getValue();
        }
        if (layout == null) {
            layout = new ControllerLayout();
        }

        return new ResolvedLayout(layoutName, layout, axes, bindings);
    }

    public static ControllerConfig createDefault() {
        return new ControllerConfig();
    }

    private static LinkedHashMap<String, String> defaultControllerTypeLayouts() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("xbox", "xbox");
        map.put("playstation", "playstation");
        map.put("switch", "switch");
        map.put("generic", "generic");
        map.put("none", "xbox");
        return map;
    }

    private static LinkedHashMap<String, ControllerLayout> defaultLayouts() {
        LinkedHashMap<String, ControllerLayout> map = new LinkedHashMap<>();
        map.put("xbox", createLayout(
            "Xbox style labels.",
            defaultAxes(false),
            defaultBindings("xbox")
        ));
        map.put("playstation", createLayout(
            "PlayStation style labels.",
            defaultAxes(false),
            defaultBindings("playstation")
        ));
        map.put("switch", createLayout(
            "Nintendo Switch style labels.",
            defaultAxes(false),
            defaultBindings("switch")
        ));
        map.put("generic", createLayout(
            "Generic controller layout.",
            defaultAxes(false),
            defaultBindings("generic")
        ));
        return map;
    }

    private static ControllerLayout createLayout(String description, AxisBindings axes, LinkedHashMap<String, List<String>> bindings) {
        ControllerLayout layout = new ControllerLayout();
        layout.description = description;
        layout.axes = copyAxes(axes);
        layout.bindings = copyBindings(bindings);
        return layout;
    }

    private static AxisBindings defaultAxes(boolean invertLookY) {
        AxisBindings axes = new AxisBindings();
        axes.move_x = "LEFT_X";
        axes.move_y = "LEFT_Y";
        axes.look_x = "RIGHT_X";
        axes.look_y = invertLookY ? "-RIGHT_Y" : "RIGHT_Y";
        return axes;
    }

    private static LinkedHashMap<String, List<String>> defaultBindings(String profile) {
        boolean playstation = "playstation".equals(profile);
        boolean switchProfile = "switch".equals(profile);

        String south = playstation ? "CROSS" : (switchProfile ? "SWITCH_A" : "A");
        String east = playstation ? "CIRCLE" : (switchProfile ? "SWITCH_B" : "B");
        String west = playstation ? "SQUARE" : (switchProfile ? "SWITCH_Y" : "X");
        String north = playstation ? "TRIANGLE" : (switchProfile ? "SWITCH_X" : "Y");
        String leftBumper = playstation ? "L1" : "LB";
        String rightBumper = playstation ? "R1" : "RB";
        String leftTrigger = playstation ? "L2" : "LT";
        String rightTrigger = playstation ? "R2" : "RT";

        return mapOfActions(
            entry("jump", south),
            entry("sneak", "R3"),
            entry("sprint", "L3"),
            entry("attack", rightTrigger),
            entry("use", leftTrigger),
            entry("inventory", north),
            entry("drop_item", east),
            entry("swap_hands", west),
            entry("open_chat", "DPAD_UP"),
            entry("toggle_perspective", "DPAD_DOWN"),
            entry("pause", "START"),
            entry("player_list", "BACK"),
            entry("pick_block", rightBumper),
            entry("hotbar_next", rightBumper),
            entry("hotbar_previous", leftBumper),
            entry("hotbar_1"),
            entry("hotbar_2"),
            entry("hotbar_3"),
            entry("hotbar_4"),
            entry("hotbar_5"),
            entry("hotbar_6"),
            entry("hotbar_7"),
            entry("hotbar_8"),
            entry("hotbar_9"),
            entry("menu_up", "DPAD_UP", "-LEFT_Y"),
            entry("menu_down", "DPAD_DOWN", "LEFT_Y"),
            entry("menu_left", "DPAD_LEFT", "-LEFT_X"),
            entry("menu_right", "DPAD_RIGHT", "LEFT_X"),
            entry("menu_confirm", south),
            entry("menu_back", east),
            entry("menu_page_next", rightTrigger),
            entry("menu_page_prev", leftTrigger),
            entry("menu_tab_next", rightBumper),
            entry("menu_tab_prev", leftBumper),
            entry("radial_menu", "BACK")
        );
    }

    private static LinkedHashMap<String, List<String>> mapOfActions(ActionEntry... entries) {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        for (ActionEntry entry : entries) {
            map.put(entry.key, new ArrayList<>(Arrays.asList(entry.bindings)));
        }
        return map;
    }

    private static ActionEntry entry(String key, String... bindings) {
        return new ActionEntry(key, bindings);
    }

    private static void mergeMissingControllerTypeLayouts(
        LinkedHashMap<String, String> target,
        LinkedHashMap<String, String> fallback
    ) {
        for (Map.Entry<String, String> entry : fallback.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static void mergeMissingLayouts(
        LinkedHashMap<String, ControllerLayout> target,
        LinkedHashMap<String, ControllerLayout> fallback
    ) {
        for (Map.Entry<String, ControllerLayout> entry : fallback.entrySet()) {
            target.putIfAbsent(entry.getKey(), copyLayout(entry.getValue()));
        }

        ControllerLayout genericFallback = fallback.get("generic");
        for (Map.Entry<String, ControllerLayout> entry : target.entrySet()) {
            ControllerLayout current = entry.getValue();
            if (current == null) {
                ControllerLayout fallbackLayout = fallback.getOrDefault(entry.getKey(), genericFallback);
                entry.setValue(copyLayout(fallbackLayout));
                continue;
            }

            ControllerLayout fallbackLayout = fallback.getOrDefault(entry.getKey(), genericFallback);
            if (fallbackLayout == null) {
                continue;
            }

            if (current.description == null || current.description.isBlank()) {
                current.description = fallbackLayout.description;
            }
            if (current.axes == null) {
                current.axes = copyAxes(fallbackLayout.axes);
            } else {
                mergeMissingAxes(current.axes, fallbackLayout.axes);
            }
            if (current.bindings == null) {
                current.bindings = copyBindings(fallbackLayout.bindings);
            } else {
                mergeMissingBindings(current.bindings, fallbackLayout.bindings);
            }
        }
    }

    private static void mergeMissingAxes(AxisBindings target, AxisBindings fallback) {
        if (target == null || fallback == null) {
            return;
        }
        if (target.move_x == null || target.move_x.isBlank()) {
            target.move_x = fallback.move_x;
        }
        if (target.move_y == null || target.move_y.isBlank()) {
            target.move_y = fallback.move_y;
        }
        if (target.look_x == null || target.look_x.isBlank()) {
            target.look_x = fallback.look_x;
        }
        if (target.look_y == null || target.look_y.isBlank()) {
            target.look_y = fallback.look_y;
        }
    }

    private static void mergeMissingBindings(
        LinkedHashMap<String, List<String>> target,
        LinkedHashMap<String, List<String>> fallback
    ) {
        for (Map.Entry<String, List<String>> entry : fallback.entrySet()) {
            List<String> current = target.get(entry.getKey());
            if (current == null) {
                target.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    private static ControllerLayout copyLayout(ControllerLayout source) {
        ControllerLayout copy = new ControllerLayout();
        if (source == null) {
            return copy;
        }
        copy.description = source.description == null ? "" : source.description;
        copy.axes = copyAxes(source.axes);
        copy.bindings = copyBindings(source.bindings);
        return copy;
    }

    private static AxisBindings copyAxes(AxisBindings source) {
        AxisBindings copy = new AxisBindings();
        if (source == null) {
            return copy;
        }
        copy.move_x = source.move_x;
        copy.move_y = source.move_y;
        copy.look_x = source.look_x;
        copy.look_y = source.look_y;
        return copy;
    }

    private static LinkedHashMap<String, List<String>> copyBindings(LinkedHashMap<String, List<String>> source) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> value = entry.getValue();
            copy.put(entry.getKey(), value == null ? new ArrayList<>() : new ArrayList<>(value));
        }
        return copy;
    }

    private static String normalizeControllerTypeKey(ControllerType type) {
        if (type == null) {
            return "generic";
        }
        return switch (type) {
            case XBOX -> "xbox";
            case PLAYSTATION -> "playstation";
            case SWITCH -> "switch";
            case NONE -> "none";
            case GENERIC -> "generic";
        };
    }

    private record ActionEntry(String key, String[] bindings) {
    }

    public static final class ResolvedLayout {
        private final String name;
        private final ControllerLayout localLayout;
        private final AxisBindings globalAxes;
        private final LinkedHashMap<String, List<String>> globalBindings;

        private ResolvedLayout(
            String name,
            ControllerLayout localLayout,
            AxisBindings globalAxes,
            LinkedHashMap<String, List<String>> globalBindings
        ) {
            this.name = name;
            this.localLayout = localLayout;
            this.globalAxes = globalAxes;
            this.globalBindings = globalBindings;
        }

        public String name() {
            return name;
        }

        public String description() {
            return localLayout.description == null ? "" : localLayout.description;
        }

        public String axisToken(String axisKey) {
            String localValue = localLayout.axes == null ? null : localLayout.axes.axisToken(axisKey);
            if (localValue != null && !localValue.isBlank()) {
                return localValue;
            }
            if (globalAxes == null) {
                return null;
            }
            return globalAxes.axisToken(axisKey);
        }

        public List<String> actionBindings(String actionKey) {
            if (localLayout.bindings != null) {
                List<String> localValue = localLayout.bindings.get(actionKey);
                if (localValue != null) {
                    return localValue;
                }
            }
            if (globalBindings == null) {
                return List.of();
            }
            List<String> globalValue = globalBindings.get(actionKey);
            return globalValue == null ? List.of() : globalValue;
        }
    }

    public static final class ControllerLayout {
        public String description = "";
        public AxisBindings axes = defaultAxes(false);
        public LinkedHashMap<String, List<String>> bindings = new LinkedHashMap<>();
    }

    public static final class AxisBindings {
        public String move_x = "LEFT_X";
        public String move_y = "LEFT_Y";
        public String look_x = "RIGHT_X";
        public String look_y = "RIGHT_Y";

        public String axisToken(String axisKey) {
            if (axisKey == null) {
                return null;
            }
            return switch (axisKey.toLowerCase(Locale.ROOT)) {
                case "move_x" -> move_x;
                case "move_y" -> move_y;
                case "look_x" -> look_x;
                case "look_y" -> look_y;
                default -> null;
            };
        }
    }
}
