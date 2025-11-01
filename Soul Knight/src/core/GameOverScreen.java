package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import rendering.TextRenderer;
import rendering.Texture;
import rendering.TextureLoader;
import entities.Player;
import entities.Enemy;
import entities.Boss;
import world.Dungeon;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class GameOverScreen {
    private long window;
    private int width;
    private int height;
    private Texture backgroundTexture;
    private TextRenderer gameOverRenderer;
    private TextRenderer statsRenderer;
    private TextRenderer returnButtonRenderer;
    private TextRenderer saveButtonRenderer;
    private boolean returnToLobby;
    private boolean saveGame;
    private Player player;
    private Dungeon currentDungeon;
    private boolean gameSaved;

    // Adatok tárolása a mentéshez
    private String playerNameForSave;
    private Player.Ability playerAbilityForSave;
    private float playerHealthForSave;
    private float playerMaxHealthForSave;
    private float playerDamageBoostForSave;
    private float playerCritChanceBoostForSave;
    private boolean hasBossForSave = false;
    private boolean hasNormalEnemyForSave = false;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 50;
    private static final int RETURN_BUTTON_X = 540;
    private static final int RETURN_BUTTON_Y = 200;
    private static final int SAVE_BUTTON_X = 540;
    private static final int SAVE_BUTTON_Y = 100;

    public GameOverScreen(long window, int width, int height, Player player, Dungeon currentDungeon) {
        this.window = window;
        this.width = width;
        this.height = height;
        this.player = player;
        this.currentDungeon = currentDungeon;
        this.returnToLobby = false;
        this.saveGame = false;
        this.gameSaved = false;

        // Adatok eltárolása a mentéshez
        storeDataForSaving();

        initializeTextRenderers();
        loadTextures();
        setupInputCallbacks();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void storeDataForSaving() {
        if (player != null) {
            playerNameForSave = player.getName();
            playerAbilityForSave = player.getAbility();
            playerHealthForSave = player.getHealth();
            playerMaxHealthForSave = player.getMaxHealth();
            playerDamageBoostForSave = player.getDamageBoost();
            playerCritChanceBoostForSave = player.getCritChanceBoost();
        }

        if (currentDungeon != null) {
            for (Enemy enemy : currentDungeon.getEnemies()) {
                if (enemy instanceof Boss) {
                    hasBossForSave = true;
                    for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                        if (!(normalEnemy instanceof Boss)) {
                            hasNormalEnemyForSave = true;
                            break;
                        }
                    }
                    break;
                }
            }
        }

        System.out.println("Adatok eltárolva a mentéshez:");
        System.out.println("  - Játékos: " + playerNameForSave);
        System.out.println("  - Képesség: " + playerAbilityForSave);
        System.out.println("  - Életerő: " + playerHealthForSave + "/" + playerMaxHealthForSave);
        System.out.println("  - Boss: " + hasBossForSave);
        System.out.println("  - Normál ellenség: " + hasNormalEnemyForSave);
    }

    private void initializeTextRenderers() {
        Font titleFont = new Font("Arial", Font.BOLD, 48);
        Font mainFont = new Font("Arial", Font.BOLD, 24);
        Font descFont = new Font("Arial", Font.PLAIN, 18);

        gameOverRenderer = new TextRenderer("JÁTÉK VÉGE", titleFont, Color.RED);
        String statsText = String.format(
                "Játékos: %s\nKépesség: %s\nÉleterő: %.0f/%.0f\nSebzés növelés: +%.0f%%\nKritikus esély: +%.0f%%",
                playerNameForSave,
                playerAbilityForSave != null ? playerAbilityForSave.getDisplayName() : "Nincs",
                playerHealthForSave,
                playerMaxHealthForSave,
                playerDamageBoostForSave * 100,
                playerCritChanceBoostForSave * 100
        );
        statsRenderer = new TextRenderer(statsText, descFont, Color.WHITE);

        returnButtonRenderer = new TextRenderer("Vissza a Lobbyba", mainFont, Color.BLACK);
        saveButtonRenderer = new TextRenderer("Játék mentése", mainFont, Color.BLACK);
    }

    private void loadTextures() {
        backgroundTexture = loadTextureOrPlaceholder("game_over_background.png", width, height, new Color(0.1f, 0.1f, 0.1f, 1.0f));
    }

    private Texture loadTextureOrPlaceholder(String path, int width, int height, Color color) {
        Texture texture = TextureLoader.loadTexture(path);
        return texture != null ? texture : createPlaceholderTexture(width, height, color);
    }

    private Texture createPlaceholderTexture(int width, int height, Color color) {
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue(), a = color.getAlpha();

        for (int i = 0; i < width * height; i++) {
            buffer.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        }
        buffer.flip();

        Texture texture = TextureLoader.createTextureFromBuffer(width, height, buffer);
        MemoryUtil.memFree(buffer);
        return texture;
    }

    private void setupInputCallbacks() {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ENTER) {
                    returnToLobby = true;
                } else if (key == GLFW_KEY_S  && !gameSaved) {
                    saveGame();
                    gameSaved = true;
                    System.out.println("Játék mentése aktiválva");
                }
            }
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                double mouseX = getCursorX();
                double mouseY = getCursorY();
                if (isMouseOverReturnButton(mouseX, mouseY)) {
                    returnToLobby = true;
                    System.out.println("Vissza a Lobbyba (egér)");
                } else if (isMouseOverSaveButton(mouseX, mouseY) && !gameSaved) {
                    saveGame();
                    System.out.println("Játék mentése aktiválva");
                }
            }
        });
    }

    private boolean isMouseOverReturnButton(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY, RETURN_BUTTON_X, RETURN_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private boolean isMouseOverSaveButton(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY, SAVE_BUTTON_X, SAVE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void update() {
        // Üres, mert az inputot a callback-ek kezelik
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        setupProjection();
        renderBackground();
        renderText();
        renderButtons();
    }

    private void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void renderBackground() {
        if (backgroundTexture != null) {
            glEnable(GL_TEXTURE_2D);
            backgroundTexture.bind();
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(0, 0);
            glTexCoord2f(1, 0); glVertex2f(width, 0);
            glTexCoord2f(1, 1); glVertex2f(width, height);
            glTexCoord2f(0, 1); glVertex2f(0, height);
            glEnd();
            backgroundTexture.unbind();
            glDisable(GL_TEXTURE_2D);
        } else {
            glColor4f(0.1f, 0.1f, 0.1f, 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(0, 0); glVertex2f(width, 0);
            glVertex2f(width, height); glVertex2f(0, height);
            glEnd();
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderText() {
        renderTextCentered(gameOverRenderer, width / 2, height - 100);
        renderTextCentered(statsRenderer, width / 2, height - 250);
    }

    private void renderButtons() {
        double mouseX = getCursorX();
        double mouseY = getCursorY();
        boolean isReturnHovered = isMouseOverReturnButton(mouseX, mouseY);
        boolean isSaveHovered = isMouseOverSaveButton(mouseX, mouseY) && !gameSaved;

        renderButton(RETURN_BUTTON_X, RETURN_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                isReturnHovered, returnButtonRenderer,
                new Color(0.8f, 0.6f, 0.2f, 1.0f), new Color(1.0f, 0.8f, 0.3f, 1.0f));

        Color saveNormalColor, saveHoverColor;
        if (gameSaved) {
            saveNormalColor = new Color(0.5f, 0.5f, 0.5f, 0.7f);
            saveHoverColor = saveNormalColor;
        } else {
            saveNormalColor = new Color(0.2f, 0.7f, 0.3f, 1.0f);
            saveHoverColor = new Color(0.3f, 0.9f, 0.4f, 1.0f);
        }

        renderButton(SAVE_BUTTON_X, SAVE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                isSaveHovered, saveButtonRenderer, saveNormalColor, saveHoverColor);
    }

    private void renderButton(int x, int y, int width, int height, boolean isHovered,
                              TextRenderer buttonText, Color normalColor, Color hoverColor) {

        Color buttonColor = isHovered ? hoverColor : normalColor;
        glColor4f(buttonColor.getRed(), buttonColor.getGreen(),
                buttonColor.getBlue(), buttonColor.getAlpha());
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        if (buttonText != null) {
            int textWidth = buttonText.getWidth();
            int textX = x + (width - textWidth) / 2;
            int textY = y + (height - 20) / 2 + 8;
            buttonText.render(textX, textY, 1.0f);
        }
    }

    private void renderTextCentered(TextRenderer renderer, int centerX, int centerY) {
        if (renderer != null) {
            int textWidth = renderer.getWidth();
            renderer.render(centerX - textWidth / 2, centerY, 1.0f);
        }
    }

    private double getCursorX() {
        DoubleBuffer xPos = MemoryUtil.memAllocDouble(1);
        glfwGetCursorPos(window, xPos, null);
        double x = xPos.get(0);
        MemoryUtil.memFree(xPos);
        return x;
    }

    private double getCursorY() {
        DoubleBuffer yPos = MemoryUtil.memAllocDouble(1);
        glfwGetCursorPos(window, null, yPos);
        double y = height - yPos.get(0);
        MemoryUtil.memFree(yPos);
        return y;
    }

    private void saveGame() {
        if (!gameSaved) {
            try {
                System.out.println("Mentés indítása az eltárolt adatokkal...");

                // Játékos mentése és ID lekérése
                int playerSaveId = GameSaveHandler.savePlayer(player);

                if (playerSaveId == -1) {
                    throw new Exception("Nem sikerült a player mentése");
                }

                // Ellenségek mentése az eltárolt információk alapján
                for (Enemy enemy : currentDungeon.getEnemies()) {
                    if (enemy instanceof Boss) {
                        GameSaveHandler.saveBoss((Boss) enemy, playerSaveId); // playerSaveId hozzáadva
                        for (Enemy normalEnemy : currentDungeon.getEnemies()) {
                            if (!(normalEnemy instanceof Boss)) {
                                GameSaveHandler.saveEnemy(normalEnemy, playerSaveId); // playerSaveId hozzáadva
                                break;
                            }
                        }
                    }
                }

                gameSaved = true;
                System.out.println("Játék sikeresen mentve a Game Over állapotban! Player ID: " + playerSaveId);

                // Gomb szövegének frissítése
                Font mainFont = new Font("Arial", Font.BOLD, 24);
                saveButtonRenderer.cleanup();
                saveButtonRenderer = new TextRenderer("✓ Mentve!", mainFont, Color.BLACK);

            } catch (Exception e) {
                System.err.println("Hiba a mentés során: " + e.getMessage());

                // Hiba esetén is frissítsd a szöveget
                Font mainFont = new Font("Arial", Font.BOLD, 24);
                saveButtonRenderer.cleanup();
                saveButtonRenderer = new TextRenderer("❌ Mentés sikertelen", mainFont, Color.BLACK);
            }
        }
    }

    public boolean isReturnToLobby() {
        return returnToLobby;
    }

    public boolean isSaveGame() {
        return saveGame;
    }

    public void resetSaveFlag() {
        saveGame = false;
    }

    public void resetReturnFlag() {
        returnToLobby = false;
    }

    public void cleanup() {
        if (backgroundTexture != null) {
            backgroundTexture.delete();
        }
        if (gameOverRenderer != null) {
            gameOverRenderer.cleanup();
        }
        if (statsRenderer != null) {
            statsRenderer.cleanup();
        }
        if (returnButtonRenderer != null) {
            returnButtonRenderer.cleanup();
        }
        if (saveButtonRenderer != null) {
            saveButtonRenderer.cleanup();
        }
        glfwSetKeyCallback(window, null);
        glfwSetMouseButtonCallback(window, null);
        glDisable(GL_BLEND);
    }
}