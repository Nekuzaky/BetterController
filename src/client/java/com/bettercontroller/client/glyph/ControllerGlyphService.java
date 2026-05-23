package com.bettercontroller.client.glyph;

import com.bettercontroller.client.polling.ControllerAxis;
import com.bettercontroller.client.polling.ControllerButton;
import com.bettercontroller.client.polling.ControllerType;

import java.util.Locale;

// Resolves player-facing glyph labels from logical bindings and the current controller family.
public final class ControllerGlyphService {
    private ControllerType activeControllerType = ControllerType.NONE;

    public void updateControllerType(ControllerType controllerType) {
        activeControllerType = controllerType == null ? ControllerType.NONE : controllerType;
    }

    public ControllerType activeControllerType() {
        return activeControllerType;
    }

    public ControllerGlyphSet activeGlyphSet() {
        return glyphSetFor(activeControllerType);
    }

    public String activeGlyphSetName() {
        return activeGlyphSet().displayName();
    }

    public String glyphForBinding(String bindingToken) {
        if (bindingToken == null || bindingToken.isBlank()) {
            return "?";
        }

        String normalized = bindingToken.trim().toUpperCase(Locale.ROOT);
        boolean inverted = normalized.startsWith("-");
        if (inverted) {
            normalized = normalized.substring(1);
        }

        ControllerButton button = ControllerButton.fromTokenOrNull(normalized);
        if (button != null) {
            return glyphForButton(button);
        }

        ControllerAxis axis = ControllerAxis.fromTokenOrNull(normalized);
        if (axis != null) {
            return glyphForAxis(axis, inverted);
        }

        return formatUnknown(normalized);
    }

    public String glyphForButton(ControllerButton button) {
        if (button == null) {
            return "?";
        }

        return switch (activeGlyphSet()) {
            case XBOX -> xboxButtonGlyph(button);
            case PLAYSTATION -> playStationButtonGlyph(button);
            case SWITCH -> switchButtonGlyph(button);
            case GENERIC -> genericButtonGlyph(button);
        };
    }

    private String glyphForAxis(ControllerAxis axis, boolean inverted) {
        String axisLabel = switch (axis) {
            case LEFT_TRIGGER -> switch (activeGlyphSet()) {
                case PLAYSTATION -> "L2";
                case SWITCH -> "ZL";
                default -> "LT";
            };
            case RIGHT_TRIGGER -> switch (activeGlyphSet()) {
                case PLAYSTATION -> "R2";
                case SWITCH -> "ZR";
                default -> "RT";
            };
            case LEFT_X -> "LS X";
            case LEFT_Y -> "LS Y";
            case RIGHT_X -> "RS X";
            case RIGHT_Y -> "RS Y";
        };
        if (!inverted) {
            return axisLabel;
        }
        return switch (axis) {
            case LEFT_X, RIGHT_X -> axisLabel + "-";
            case LEFT_Y, RIGHT_Y -> axisLabel + " Up";
            default -> axisLabel;
        };
    }

    public static ControllerGlyphSet glyphSetFor(ControllerType type) {
        if (type == null) {
            return ControllerGlyphSet.GENERIC;
        }
        return switch (type) {
            case XBOX -> ControllerGlyphSet.XBOX;
            case PLAYSTATION -> ControllerGlyphSet.PLAYSTATION;
            case SWITCH -> ControllerGlyphSet.SWITCH;
            case NONE, GENERIC -> ControllerGlyphSet.GENERIC;
        };
    }

    private static String xboxButtonGlyph(ControllerButton button) {
        return switch (button) {
            case SOUTH -> "A";
            case EAST -> "B";
            case WEST -> "X";
            case NORTH -> "Y";
            case LEFT_BUMPER -> "LB";
            case RIGHT_BUMPER -> "RB";
            case BACK -> "View";
            case START -> "Menu";
            case GUIDE -> "Guide";
            case LEFT_STICK -> "L3";
            case RIGHT_STICK -> "R3";
            case DPAD_UP -> "D-Up";
            case DPAD_RIGHT -> "D-Right";
            case DPAD_DOWN -> "D-Down";
            case DPAD_LEFT -> "D-Left";
        };
    }

    private static String playStationButtonGlyph(ControllerButton button) {
        return switch (button) {
            case SOUTH -> "Cross";
            case EAST -> "Circle";
            case WEST -> "Square";
            case NORTH -> "Triangle";
            case LEFT_BUMPER -> "L1";
            case RIGHT_BUMPER -> "R1";
            case BACK -> "Share";
            case START -> "Options";
            case GUIDE -> "PS";
            case LEFT_STICK -> "L3";
            case RIGHT_STICK -> "R3";
            case DPAD_UP -> "D-Up";
            case DPAD_RIGHT -> "D-Right";
            case DPAD_DOWN -> "D-Down";
            case DPAD_LEFT -> "D-Left";
        };
    }

    private static String switchButtonGlyph(ControllerButton button) {
        return switch (button) {
            case SOUTH -> "B";
            case EAST -> "A";
            case WEST -> "Y";
            case NORTH -> "X";
            case LEFT_BUMPER -> "L";
            case RIGHT_BUMPER -> "R";
            case BACK -> "-";
            case START -> "+";
            case GUIDE -> "Home";
            case LEFT_STICK -> "L3";
            case RIGHT_STICK -> "R3";
            case DPAD_UP -> "D-Up";
            case DPAD_RIGHT -> "D-Right";
            case DPAD_DOWN -> "D-Down";
            case DPAD_LEFT -> "D-Left";
        };
    }

    private static String genericButtonGlyph(ControllerButton button) {
        return switch (button) {
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            case NORTH -> "North";
            case LEFT_BUMPER -> "LB";
            case RIGHT_BUMPER -> "RB";
            case BACK -> "Back";
            case START -> "Start";
            case GUIDE -> "Guide";
            case LEFT_STICK -> "L3";
            case RIGHT_STICK -> "R3";
            case DPAD_UP -> "D-Up";
            case DPAD_RIGHT -> "D-Right";
            case DPAD_DOWN -> "D-Down";
            case DPAD_LEFT -> "D-Left";
        };
    }

    private static String formatUnknown(String token) {
        return token
            .replace("SWITCH_", "")
            .replace("NINTENDO_", "")
            .replace('_', ' ');
    }
}
