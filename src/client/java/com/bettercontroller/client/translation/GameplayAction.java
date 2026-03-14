package com.bettercontroller.client.translation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum GameplayAction {
    JUMP("jump"),
    SNEAK("sneak"),
    SPRINT("sprint"),
    ATTACK("attack"),
    USE("use"),
    INVENTORY("inventory"),
    DROP_ITEM("drop_item"),
    SWAP_HANDS("swap_hands"),
    OPEN_CHAT("open_chat"),
    TOGGLE_PERSPECTIVE("toggle_perspective"),
    PAUSE("pause"),
    PLAYER_LIST("player_list"),
    PICK_BLOCK("pick_block"),
    HOTBAR_1("hotbar_1"),
    HOTBAR_2("hotbar_2"),
    HOTBAR_3("hotbar_3"),
    HOTBAR_4("hotbar_4"),
    HOTBAR_5("hotbar_5"),
    HOTBAR_6("hotbar_6"),
    HOTBAR_7("hotbar_7"),
    HOTBAR_8("hotbar_8"),
    HOTBAR_9("hotbar_9"),
    HOTBAR_NEXT("hotbar_next"),
    HOTBAR_PREVIOUS("hotbar_previous"),
    MENU_UP("menu_up"),
    MENU_DOWN("menu_down"),
    MENU_LEFT("menu_left"),
    MENU_RIGHT("menu_right"),
    MENU_CONFIRM("menu_confirm"),
    MENU_BACK("menu_back"),
    MENU_PAGE_NEXT("menu_page_next"),
    MENU_PAGE_PREV("menu_page_prev"),
    MENU_TAB_NEXT("menu_tab_next"),
    MENU_TAB_PREV("menu_tab_prev"),
    RADIAL_MENU("radial_menu");

    private static final Map<String, GameplayAction> LOOKUP = new HashMap<>();

    static {
        for (GameplayAction action : values()) {
            LOOKUP.put(action.configKey, action);
        }
    }

    private final String configKey;

    GameplayAction(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<GameplayAction> fromConfigKey(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(token.trim().toLowerCase(Locale.ROOT)));
    }
}
