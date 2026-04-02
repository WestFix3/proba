package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import rendering.Texture;
import rendering.TextRenderer;
import rendering.TextureLoader;
import entities.Player;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class AbilitySelectionScreen {
    private Texture speedIcon;
    private Texture blockIcon;
    private Texture dodgeIcon;
    private Texture textboxTexture;

    private TextRenderer titleRenderer;
    private TextRenderer namePromptRenderer;
    private TextRenderer speedTextRenderer;
    private TextRenderer blockTextRenderer;
    private TextRenderer dodgeTextRenderer;
    private TextRenderer playerNameRenderer;
    private TextRenderer instructionRenderer;
    private TextRenderer welcomeRenderer;
    private TextRenderer speedDescriptionRenderer;
    private TextRenderer blockDescriptionRenderer;
    private TextRenderer dodgeDescriptionRenderer;

    private String playerName = "";
    private Player.Ability selectedAbility = null;
    private boolean nameConfirmed = false;
    private boolean abilitySelected = false;
    private boolean selectionComplete = false;

    private long window;
    private int width;
    private int height;

    // Statikus változó a név tárolására
    private static String savedPlayerName = "";

    public AbilitySelectionScreen(long window, int width, int height) {
        this.window = window;
        this.width = width;
        this.height = height;

        // Ha már van mentett név, automatikusan beállítjuk
        if (!savedPlayerName.isEmpty()) {
            this.playerName = savedPlayerName;
            this.nameConfirmed = true;
        }

        initializeTextRenderers();
        loadTextures();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        setupInputCallbacks();
    }

    private void initializeTextRenderers() {
        Font titleFont = new Font("Arial", Font.BOLD, 36);
        Font mainFont = new Font("Arial", Font.PLAIN, 24);
        Font smallFont = new Font("Arial", Font.PLAIN, 18);
        Font welcomeFont = new Font("Arial", Font.BOLD, 28);
        Font descriptionFont = new Font("Arial", Font.PLAIN, 14);

        titleRenderer = new TextRenderer("Válassz képességet", titleFont, Color.WHITE);

        namePromptRenderer = new TextRenderer("Add meg a felhasználóneved:", mainFont, Color.WHITE);

        speedTextRenderer = new TextRenderer(Player.Ability.SPEED.getDisplayName(), mainFont, Color.WHITE);
        blockTextRenderer = new TextRenderer(Player.Ability.BLOCK.getDisplayName(), mainFont, Color.WHITE);
        dodgeTextRenderer = new TextRenderer(Player.Ability.DODGE.getDisplayName(), mainFont, Color.WHITE);

        instructionRenderer = new TextRenderer("Írd be a neved, majd nyomj Entert", smallFont, Color.LIGHT_GRAY);

        playerNameRenderer = new TextRenderer(playerName, mainFont, Color.BLACK);

        // Képesség leírások
        speedDescriptionRenderer = new TextRenderer("30%-al gyorsabb a player", descriptionFont, Color.CYAN);
        blockDescriptionRenderer = new TextRenderer("50%-os sebzés csökkentés", descriptionFont, Color.CYAN);
        dodgeDescriptionRenderer = new TextRenderer("20%-os sebzés elkerülés", descriptionFont, Color.CYAN);

        // Üdvözlő szöveg - csak akkor jelenik meg, ha van mentett név
        if (!savedPlayerName.isEmpty()) {
            welcomeRenderer = new TextRenderer("Üdvözöljük " + savedPlayerName + "!", welcomeFont, Color.YELLOW);
        }
    }

    private void setupInputCallbacks() {
        // Callback a karakterbevitelhez (név beírása) - csak ha nincs mentett név
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (!nameConfirmed && savedPlayerName.isEmpty()) {
                char character = (char) codepoint;
                if (Character.isISOControl(character)) return;

                if (playerName.length() < 15 && Character.isDefined(character)) {
                    playerName += character;
                    updatePlayerNameRenderer();
                }
            }
        });

        // Callback a billentyű lenyomásához
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (!nameConfirmed && savedPlayerName.isEmpty()) {
                    // Név bevitel kezelése
                    if (key == GLFW_KEY_BACKSPACE && !playerName.isEmpty()) {
                        playerName = playerName.substring(0, playerName.length() - 1);
                        updatePlayerNameRenderer();
                    } else if ((key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) && !playerName.trim().isEmpty()) {
                        nameConfirmed = true;
                        savedPlayerName = playerName.trim(); // Név mentése
                        updateRenderersAfterNameConfirmation();
                    }
                } else if (nameConfirmed) {
                    // Képesség választás kezelése
                    if (key == GLFW_KEY_1) {
                        selectedAbility = Player.Ability.SPEED;
                        abilitySelected = true;
                        selectionComplete = true;
                    } else if (key == GLFW_KEY_2) {
                        selectedAbility = Player.Ability.BLOCK;
                        abilitySelected = true;
                        selectionComplete = true;
                    } else if (key == GLFW_KEY_3) {
                        selectedAbility = Player.Ability.DODGE;
                        abilitySelected = true;
                        selectionComplete = true;
                    } else if (key == GLFW_KEY_ENTER && abilitySelected) {
                        selectionComplete = true;
                    }
                }

                // Ha már van mentett név, automatikusan nameConfirmed = true
                if (!savedPlayerName.isEmpty() && !nameConfirmed) {
                    nameConfirmed = true;
                    playerName = savedPlayerName;
                    updateRenderersAfterNameConfirmation();
                }
            }
        });

        // Hozzáadott Callback az egérkattintáshoz (képességválasztás)
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                if (nameConfirmed) {
                    handleMouseSelection();
                }
            }
        });
    }

    private void updateRenderersAfterNameConfirmation() {
        // Frissítjük az üdvözlő szöveget
        if (welcomeRenderer != null) welcomeRenderer.cleanup();
        welcomeRenderer = new TextRenderer("Üdvözöljük " + savedPlayerName + "!",
                new Font("Arial", Font.BOLD, 28), Color.YELLOW);

        // Megtartjuk az eredeti instruction szöveget, csak hozzáadjuk az üdvözlőt
        if (instructionRenderer != null) instructionRenderer.cleanup();
        instructionRenderer = new TextRenderer("Válassz képességet a folytatáshoz",
                new Font("Arial", Font.PLAIN, 18), Color.LIGHT_GRAY);
    }

    private void loadTextures() {
        speedIcon = loadTextureOrPlaceholder("speed_icon.png", 64, 64, Color.BLUE);
        blockIcon = loadTextureOrPlaceholder("block_icon.png", 64, 64, Color.RED);
        dodgeIcon = loadTextureOrPlaceholder("dodge_icon.png", 64, 64, Color.GREEN);
        textboxTexture = loadTextureOrPlaceholder("textbox.png", 200, 30, Color.WHITE);
    }

    private Texture loadTextureOrPlaceholder(String path, int width, int height, Color color) {
        Texture texture = TextureLoader.loadTexture(path);
        return texture != null ? texture : createPlaceholderTexture(width, height, color);
    }

    private Texture createPlaceholderTexture(int width, int height, Color color) {
        ByteBuffer buffer = memAlloc(width * height * 4);
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = color.getAlpha();

        for (int i = 0; i < width * height; i++) {
            buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        }
        buffer.flip();

        Texture texture = TextureLoader.createTextureFromBuffer(width, height, buffer);
        memFree(buffer);
        return texture;
    }

    private void updatePlayerNameRenderer() {
        if (playerNameRenderer != null) playerNameRenderer.cleanup();
        playerNameRenderer = new TextRenderer(playerName, new Font("Arial", Font.PLAIN, 20), Color.BLACK);
    }

    public void update() {
        // Ha már van mentett név, automatikusan nameConfirmed = true
        if (!savedPlayerName.isEmpty() && !nameConfirmed) {
            nameConfirmed = true;
            playerName = savedPlayerName;
            updateRenderersAfterNameConfirmation();
        }
    }

    private void handleMouseSelection() {
        double mouseX = getCursorX(), mouseY = getCursorY();
        int abilityY = height / 2 - 50;
        int speedX = width / 2 - 150, blockX = width / 2, dodgeX = width / 2 + 150;

        if (isMouseOver(mouseX, mouseY, speedX, abilityY, 64)) {
            selectedAbility = Player.Ability.SPEED;
            abilitySelected = true;
            selectionComplete = true;
        } else if (isMouseOver(mouseX, mouseY, blockX, abilityY, 64)) {
            selectedAbility = Player.Ability.BLOCK;
            abilitySelected = true;
            selectionComplete = true;
        } else if (isMouseOver(mouseX, mouseY, dodgeX, abilityY, 64)) {
            selectedAbility = Player.Ability.DODGE;
            abilitySelected = true;
            selectionComplete = true;
        }
    }

    private boolean isMouseOver(double mouseX, double mouseY, int centerX, int centerY, int size) {
        return mouseX >= centerX - size/2 && mouseX <= centerX + size/2 &&
                mouseY >= centerY - size/2 && mouseY <= centerY + size/2;
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        setupProjection();
        renderBackground();

        renderTextCentered(titleRenderer, width / 2, height - 100);

        // Ha van mentett név, megjelenítjük az üdvözlő üzenetet
        if (!savedPlayerName.isEmpty() && welcomeRenderer != null) {
            renderTextCentered(welcomeRenderer, width / 2, height - 150);
        }

        if (!nameConfirmed && savedPlayerName.isEmpty()) {
            renderNameInputSection();
        } else if (!abilitySelected) {
            renderAbilitySelectionSection();
        } else {
            renderConfirmationSection();
        }
    }

    private void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void renderBackground() {
        glColor4f(0.1f, 0.1f, 0.2f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(width, 0);
        glVertex2f(width, height); glVertex2f(0, height);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderNameInputSection() {
        renderTextCentered(namePromptRenderer, width / 2, height - 200);
        renderTextCentered(instructionRenderer, width / 2, height - 240);

        renderTexture(textboxTexture, width / 2 - 100, height - 280, 200, 40);

        if (playerNameRenderer != null) {
            renderTextCentered(playerNameRenderer, width / 2, height - 270);
        }

        renderCursor();
    }

    private void renderCursor() {
        if (playerName.isEmpty()) {
            glColor4f(0.8f, 0.8f, 0.8f, 0.9f);
            glBegin(GL_QUADS);
            glVertex2f(width / 2 - 1, height - 285);
            glVertex2f(width / 2 + 1, height - 285);
            glVertex2f(width / 2 + 1, height - 265);
            glVertex2f(width / 2 - 1, height - 265);
            glEnd();
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderAbilitySelectionSection() {
        // Ha nincs mentett név, megjelenítjük az eredeti promptot
        if (savedPlayerName.isEmpty()) {
            renderTextCentered(namePromptRenderer, width / 2, height - 200);
        }

        renderTextCentered(instructionRenderer, width / 2, height - 240);

        int abilityY = height / 2 - 50;
        int speedX = width / 2 - 150, blockX = width / 2, dodgeX = width / 2 + 150;

        renderAbilityIcons(abilityY, speedX, blockX, dodgeX);
        renderAbilityLabels(abilityY, speedX, blockX, dodgeX);
        renderAbilityDescriptions(abilityY, speedX, blockX, dodgeX);
        renderInstructions();
    }

    private void renderAbilityIcons(int abilityY, int speedX, int blockX, int dodgeX) {
        renderAbilityIcon(speedIcon, speedX - 50, abilityY, Player.Ability.SPEED);
        renderAbilityIcon(blockIcon, blockX, abilityY, Player.Ability.BLOCK);
        renderAbilityIcon(dodgeIcon, dodgeX + 50, abilityY, Player.Ability.DODGE);
    }

    private void renderAbilityLabels(int abilityY, int speedX, int blockX, int dodgeX) {
        renderTextCentered(speedTextRenderer, speedX - 50, abilityY + 50);
        renderTextCentered(blockTextRenderer, blockX, abilityY + 50);
        renderTextCentered(dodgeTextRenderer, dodgeX + 50, abilityY + 50);
    }

    // Módosított: Leírások lejjebb kerültek
    private void renderAbilityDescriptions(int abilityY, int speedX, int blockX, int dodgeX) {
        renderTextCentered(speedDescriptionRenderer, speedX - 50, abilityY + -70);  // +90 helyett +80
        renderTextCentered(blockDescriptionRenderer, blockX, abilityY + -70);  // +90 helyett +80
        renderTextCentered(dodgeDescriptionRenderer, dodgeX + 50, abilityY + -70);  // +90 helyett +80
    }

    private void renderInstructions() {
        renderTextCentered(new TextRenderer("Kattints egy ikonra vagy nyomj 1-2-3-at",
                new Font("Arial", Font.PLAIN, 16), Color.LIGHT_GRAY), width / 2, 100);

        renderTextCentered(new TextRenderer("(1 = Gyorsaság, 2 = Blokkolás, 3 = Kitérés)",
                new Font("Arial", Font.PLAIN, 14), Color.GRAY), width / 2, 80);
    }

    private void renderAbilityIcon(Texture icon, int x, int y, Player.Ability ability) {
        renderIconBackground(x, y, ability);
        renderIconImage(icon, x, y);
        renderHoverEffect(x, y);
    }

    private void renderIconBackground(int x, int y, Player.Ability ability) {
        boolean isSelected = selectedAbility == ability;
        glColor4f(0.2f, 0.2f, 0.3f, isSelected ? 0.5f : 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(x - 40, y - 40);
        glVertex2f(x + 40, y - 40);
        glVertex2f(x + 40, y + 40);
        glVertex2f(x - 40, y + 40);
        glEnd();
    }

    private void renderIconImage(Texture icon, int x, int y) {
        if (icon != null) {
            renderTexture(icon, x - 32, y + 32, 64, -64);
        }
    }

    private void renderHoverEffect(int x, int y) {
        if (isMouseOver(getCursorX(), getCursorY(), x, y, 84)) {
            glColor4f(1.0f, 1.0f, 1.0f, 0.1f);
            glBegin(GL_QUADS);
            glVertex2f(x - 42, y - 42);
            glVertex2f(x + 42, y - 42);
            glVertex2f(x + 42, y + 42);
            glVertex2f(x - 42, y + 42);
            glEnd();
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderConfirmationSection() {
        renderTextCentered(new TextRenderer("Kiválasztva: " + selectedAbility.getDisplayName(),
                new Font("Arial", Font.BOLD, 28), new Color(100, 255, 100)), width / 2, height / 2 + 10);

        renderTextCentered(new TextRenderer("Játékos: " + playerName,
                new Font("Arial", Font.PLAIN, 22), Color.WHITE), width / 2, height / 2 - 30);

        renderTextCentered(new TextRenderer("Nyomj Entert a játék indításához",
                new Font("Arial", Font.PLAIN, 18), Color.YELLOW), width / 2, height / 2 - 70);
    }

    private void renderTexture(Texture tex, int x, int y, int width, int height) {
        if (tex == null) return;

        float alpha = (selectedAbility != null && !isAbilityTextureSelected(tex)) ? 0.7f : 1.0f;
        glColor4f(1.0f, 1.0f, 1.0f, alpha);

        glEnable(GL_TEXTURE_2D);
        tex.bind();
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 0); glVertex2f(x, y + height);
        glEnd();
        tex.unbind();
        glDisable(GL_TEXTURE_2D);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private boolean isAbilityTextureSelected(Texture tex) {
        return (selectedAbility == Player.Ability.SPEED && tex == speedIcon) ||
                (selectedAbility == Player.Ability.BLOCK && tex == blockIcon) ||
                (selectedAbility == Player.Ability.DODGE && tex == dodgeIcon);
    }

    private void renderTextCentered(TextRenderer textRenderer, int centerX, int centerY) {
        if (textRenderer == null) return;
        int textWidth = textRenderer.getWidth();
        int x = centerX - textWidth / 2;
        textRenderer.render(x, centerY, 1.0f);
    }

    private double getCursorX() {
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer xPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, xPos, null);
            return xPos.get(0);
        }
    }

    private double getCursorY() {
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer yPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, null, yPos);
            return height - yPos.get(0);
        }
    }

    public boolean isSelectionComplete() { return selectionComplete; }
    public String getPlayerName() { return playerName.trim(); }
    public Player.Ability getSelectedAbility() { return selectedAbility; }

    // Statikus metódus a név törléséhez (pl. amikor teljesen kilép a játékos)
    public static void clearSavedName() {
        savedPlayerName = "";
    }

    public void reset() {
        abilitySelected = selectionComplete = false;
        selectedAbility = null;
        // A nevet nem reseteljük, csak a képességet
        updatePlayerNameRenderer();
    }

    public void cleanup() {
        Texture[] textures = {speedIcon, blockIcon, dodgeIcon, textboxTexture};
        for (Texture tex : textures) {
            if (tex != null) tex.delete();
        }

        TextRenderer[] renderers = {titleRenderer, namePromptRenderer, speedTextRenderer,
                blockTextRenderer, dodgeTextRenderer, playerNameRenderer, instructionRenderer,
                welcomeRenderer, speedDescriptionRenderer, blockDescriptionRenderer, dodgeDescriptionRenderer};
        for (TextRenderer renderer : renderers) {
            if (renderer != null) renderer.cleanup();
        }

        glfwSetCharCallback(window, null);
        glfwSetKeyCallback(window, null);
        glfwSetMouseButtonCallback(window, null);
        glDisable(GL_BLEND);
    }
}