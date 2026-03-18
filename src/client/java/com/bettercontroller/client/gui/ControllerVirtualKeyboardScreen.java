package com.bettercontroller.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller-first virtual keyboard with cursor-aware editing.
 */
public final class ControllerVirtualKeyboardScreen extends Screen {
    private static final int KEY_HEIGHT = 20;
    private static final int KEY_GAP = 4;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int PREVIEW_MAX_CHARS = 64;

    private final Screen parentScreen;
    private final TextFieldWidget originalTargetField;
    private final List<CharacterKey> characterKeys = new ArrayList<>();
    private String workingText;
    private int cursorIndex;
    private boolean upperCase = true;
    private boolean applyOnClose;

    private ButtonWidget caseToggleButton;
    private ClickableWidget lastFocusedWidget;
    private long focusChangedMs;

    public ControllerVirtualKeyboardScreen(Screen parentScreen, TextFieldWidget targetField) {
        super(Text.literal("Virtual Keyboard"));
        this.parentScreen = parentScreen;
        this.originalTargetField = targetField;
        this.workingText = targetField == null ? "" : targetField.getText();
        this.cursorIndex = this.workingText.length();
    }

    @Override
    protected void init() {
        characterKeys.clear();
        lastFocusedWidget = null;
        focusChangedMs = System.currentTimeMillis();

        int top = 58;
        top = addCharacterRow(new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}, top);
        top = addCharacterRow(new String[] {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"}, top);
        top = addCharacterRow(new String[] {"A", "S", "D", "F", "G", "H", "J", "K", "L"}, top);
        top = addCharacterRow(new String[] {"Z", "X", "C", "V", "B", "N", "M"}, top);

        int actionY = top + 4;
        int actionWidth = 86;
        int actionGap = 6;
        int actionX = (this.width - ((actionWidth * 4) + (actionGap * 3))) / 2;

        caseToggleButton = addDrawableChild(ButtonWidget.builder(
            Text.literal(caseLabel()),
            button -> {
                upperCase = !upperCase;
                refreshCharacterLabels();
            }
        ).dimensions(actionX, actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Backspace"),
            button -> backspace()
        ).dimensions(actionX + (actionWidth + actionGap), actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Delete"),
            button -> deleteForward()
        ).dimensions(actionX + ((actionWidth + actionGap) * 2), actionY, actionWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Space"),
            button -> insertText(" ")
        ).dimensions(actionX + ((actionWidth + actionGap) * 3), actionY, actionWidth, KEY_HEIGHT).build());

        int bottomY = actionY + KEY_HEIGHT + 8;
        int bottomWidth = 96;
        int bottomGap = 8;
        int bottomX = (this.width - ((bottomWidth * 4) + (bottomGap * 3))) / 2;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cursor <"),
            button -> moveCursor(-1)
        ).dimensions(bottomX, bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cursor >"),
            button -> moveCursor(1)
        ).dimensions(bottomX + (bottomWidth + bottomGap), bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cancel"),
            button -> {
                applyOnClose = false;
                close();
            }
        ).dimensions(bottomX + ((bottomWidth + bottomGap) * 2), bottomY, bottomWidth, KEY_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Enter"),
            button -> {
                applyOnClose = true;
                close();
            }
        ).dimensions(bottomX + ((bottomWidth + bottomGap) * 3), bottomY, bottomWidth, KEY_HEIGHT).build());

        if (!characterKeys.isEmpty()) {
            ButtonWidget first = characterKeys.get(0).button();
            setFocused(first);
            first.setFocused(true);
            lastFocusedWidget = first;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xD010131A);
        context.fill(0, 0, this.width, 30, 0xAA1D2633);
        renderKeyboardPanels(context);
        super.render(context, mouseX, mouseY, delta);

        trackFocusChange();

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 10, 0xFFFFFFFF);

        String preview = buildPreviewWithCursor();
        context.fill(centerX - 230, 30, centerX + 230, 44, 0xB5000000);
        context.drawCenteredTextWithShadow(this.textRenderer, preview, centerX, 33, 0xFFE8EEF7);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Cursor " + cursorIndex + "/" + workingText.length()),
            centerX,
            46,
            0xFF9FB3C9
        );

        renderFocusedKeyHighlight(context);
        renderFocusedKeyDetails(context, centerX);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("A: Confirm key (hold for repeat)  B: Cancel"),
            centerX,
            this.height - 18,
            0xFFB6C2D2
        );
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            applyOnClose = false;
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_DELETE) {
            deleteForward();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_LEFT) {
            moveCursor(-1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT) {
            moveCursor(1);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            applyOnClose = true;
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (this.client == null) {
            return;
        }

        this.client.setScreen(parentScreen);
        if (!applyOnClose || parentScreen == null) {
            return;
        }

        TextFieldWidget target = resolveTargetField(parentScreen);
        if (target == null) {
            return;
        }
        target.setText(workingText);
        target.setFocused(true);
    }

    private int addCharacterRow(String[] row, int y) {
        int keyWidth = 24;
        int totalWidth = (row.length * keyWidth) + ((row.length - 1) * KEY_GAP);
        int x = (this.width - totalWidth) / 2;
        for (String key : row) {
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(
                Text.literal(displayKey(key)),
                widget -> insertText(displayKey(key))
            ).dimensions(x, y, keyWidth, KEY_HEIGHT).build());
            characterKeys.add(new CharacterKey(key, button));
            x += keyWidth + KEY_GAP;
        }
        return y + KEY_HEIGHT + KEY_GAP;
    }

    private void insertText(String token) {
        if (token == null || token.isEmpty() || workingText.length() >= MAX_TEXT_LENGTH) {
            return;
        }
        String left = workingText.substring(0, cursorIndex);
        String right = workingText.substring(cursorIndex);
        workingText = left + token + right;
        cursorIndex = Math.min(workingText.length(), cursorIndex + token.length());
    }

    private void backspace() {
        if (workingText.isEmpty() || cursorIndex <= 0) {
            return;
        }
        String left = workingText.substring(0, cursorIndex - 1);
        String right = workingText.substring(cursorIndex);
        workingText = left + right;
        cursorIndex = Math.max(0, cursorIndex - 1);
    }

    private void deleteForward() {
        if (workingText.isEmpty() || cursorIndex >= workingText.length()) {
            return;
        }
        String left = workingText.substring(0, cursorIndex);
        String right = workingText.substring(cursorIndex + 1);
        workingText = left + right;
    }

    private void moveCursor(int delta) {
        if (delta == 0) {
            return;
        }
        cursorIndex = Math.max(0, Math.min(workingText.length(), cursorIndex + delta));
    }

    private void refreshCharacterLabels() {
        for (CharacterKey key : characterKeys) {
            key.button().setMessage(Text.literal(displayKey(key.baseToken())));
        }
        if (caseToggleButton != null) {
            caseToggleButton.setMessage(Text.literal(caseLabel()));
        }
    }

    private String displayKey(String key) {
        if (key == null || key.length() != 1 || !Character.isLetter(key.charAt(0))) {
            return key;
        }
        return upperCase ? key.toUpperCase() : key.toLowerCase();
    }

    private String caseLabel() {
        return upperCase ? "Lowercase" : "Uppercase";
    }

    private String buildPreviewWithCursor() {
        String withCursor = workingText.substring(0, cursorIndex) + "|" + workingText.substring(cursorIndex);
        if (withCursor.length() <= PREVIEW_MAX_CHARS) {
            return withCursor.isBlank() ? "|" : withCursor;
        }

        int windowHalf = PREVIEW_MAX_CHARS / 2;
        int start = Math.max(0, cursorIndex - windowHalf);
        int end = Math.min(withCursor.length(), start + PREVIEW_MAX_CHARS);
        start = Math.max(0, end - PREVIEW_MAX_CHARS);
        String clipped = withCursor.substring(start, end);
        if (start > 0) {
            clipped = "." + clipped.substring(1);
        }
        if (end < withCursor.length()) {
            clipped = clipped.substring(0, clipped.length() - 1) + ".";
        }
        return clipped;
    }

    private void renderFocusedKeyHighlight(DrawContext context) {
        ClickableWidget focusedWidget = focusedWidget();
        if (focusedWidget == null) {
            return;
        }

        int left = focusedWidget.getX() - 2;
        int top = focusedWidget.getY() - 2;
        int width = focusedWidget.getWidth() + 4;
        int height = focusedWidget.getHeight() + 4;
        long now = System.currentTimeMillis();
        double pulse = 0.5D + (0.5D * Math.sin(now * 0.016D));
        double settle = Math.min(1.0D, Math.max(0.0D, (now - focusChangedMs) / 180.0D));
        int grow = (int) Math.round((1.0D - settle) * 1.4D);
        int alpha = (int) (158 + (pulse * 82));
        int glowAlpha = (int) (50 + (pulse * 25));

        context.fill(left - 2, top - 2, left + width + 2, top + height + 2, (glowAlpha << 24) | 0x6FCFFF);
        context.drawStrokedRectangle(
            left - grow,
            top - grow,
            width + (grow * 2),
            height + (grow * 2),
            (alpha << 24) | 0xE7F8FF
        );
    }

    private void renderKeyboardPanels(DrawContext context) {
        int centerX = this.width / 2;
        context.fill(centerX - 248, 52, centerX + 248, 156, 0x3A0E1622);
        context.fill(centerX - 248, 52, centerX + 248, 53, 0x66587A9B);
        context.fill(centerX - 248, 159, centerX + 248, 183, 0x34131C29);
        context.fill(centerX - 248, 187, centerX + 248, 214, 0x30131C29);
    }

    private void renderFocusedKeyDetails(DrawContext context, int centerX) {
        ClickableWidget focusedWidget = focusedWidget();
        if (focusedWidget == null) {
            return;
        }
        String focusedLabel = focusedWidget.getMessage().getString();
        if (focusedLabel == null || focusedLabel.isBlank()) {
            return;
        }
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Selected Key: " + focusedLabel),
            centerX,
            this.height - 30,
            0xFFD6E4F4
        );
    }

    private void trackFocusChange() {
        ClickableWidget currentFocused = focusedWidget();
        if (currentFocused != lastFocusedWidget) {
            lastFocusedWidget = currentFocused;
            focusChangedMs = System.currentTimeMillis();
        }
    }

    private ClickableWidget focusedWidget() {
        return this.getFocused() instanceof ClickableWidget widget ? widget : null;
    }

    private TextFieldWidget resolveTargetField(Screen parent) {
        if (parent == null) {
            return null;
        }
        if (parent.getFocused() instanceof TextFieldWidget focusedField) {
            return focusedField;
        }

        TextFieldWidget firstField = null;
        for (Element element : parent.children()) {
            if (element instanceof TextFieldWidget textField) {
                if (firstField == null) {
                    firstField = textField;
                }
                if (isSameGeometry(textField, originalTargetField)) {
                    return textField;
                }
            }
        }
        return firstField;
    }

    private static boolean isSameGeometry(TextFieldWidget a, TextFieldWidget b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getWidth() == b.getWidth()
            && a.getHeight() == b.getHeight();
    }

    private record CharacterKey(String baseToken, ButtonWidget button) {
    }
}
