package core;

import entities.Player;
import entities.Enemy;
import entities.Boss;
import world.Dungeon;
import org.lwjgl.system.MemoryUtil;
import rendering.Texture;
import rendering.TextRenderer;
import rendering.TextureLoader;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import input.InputHandler;
import java.awt.Font;
import java.awt.Color;
import java.nio.DoubleBuffer;

public class UpgradeChoiceScreen {
    private Texture backgroundTexture;
    private Texture[] upgradeIcons;

    private TextRenderer titleRenderer;
    private TextRenderer[] nameRenderers = new TextRenderer[3];
    private TextRenderer[] descriptionRenderers = new TextRenderer[3];
    private TextRenderer[] keyRenderers = new TextRenderer[3];
    private TextRenderer saveRenderer;
    private TextRenderer saveStatusRenderer;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 50;
    private static final int RETURN_BUTTON_X = 540;
    private static final int RETURN_BUTTON_Y = 200;
    private static final int SAVE_BUTTON_X = 540;
    private static final int SAVE_BUTTON_Y = 100;

    private String[] upgradeNames = {
            "Életerő növelése",
            "Sebzés növelése",
            "Crit esély növelése"
    };
    private String[] upgradeDescriptions = {
            "+20 maximum életerő",
            "+10% sebzés növelés",
            "+5% crit esély"
    };

    private int selectedOption = -1;
    private boolean choiceMade = false;
    private boolean gameSaved = false;
    private long window;
    private int width;
    private int height;
    private Player player;
    private Enemy enemy;
    private Enemy boss;

    // Adatok tárolása a mentéshez
    private String playerNameForSave;
    private Player.Ability playerAbilityForSave;
    private float playerHealthForSave;
    private float playerMaxHealthForSave;
    private float playerDamageBoostForSave;
    private float playerCritChanceBoostForSave;
    private boolean hasBossForSave = false;
    private boolean hasNormalEnemyForSave = false;

    public UpgradeChoiceScreen(long window, int width, int height, Player player) {
        this.window = window;
        this.width = width;
        this.height = height;
        this.player = player;
        this.gameSaved = false;

        // Adatok eltárolása a mentéshez - CSAK HA MINDEN ADAT MEGVAN
        if (player != null) {
            storeDataForSaving();
        }

        initialize();
    }

    private void initialize() {
        upgradeIcons = new Texture[3];
        upgradeIcons[0] = TextureLoader.loadTexture("health_upgrade.png");
        upgradeIcons[1] = TextureLoader.loadTexture("damage_upgrade.png");
        upgradeIcons[2] = TextureLoader.loadTexture("crit_upgrade.png");

        Font titleFont = new Font("Arial", Font.BOLD, 36);
        Font mainFont = new Font("Arial", Font.BOLD, 24);
        Font descFont = new Font("Arial", Font.BOLD, 18);
        Font statusFont = new Font("Arial", Font.BOLD, 16);

        titleRenderer = new TextRenderer("Válassz fejlesztést!", titleFont, Color.WHITE);

        for (int i = 0; i < 3; i++) {
            nameRenderers[i] = new TextRenderer(upgradeNames[i], mainFont, Color.WHITE);
            descriptionRenderers[i] = new TextRenderer(upgradeDescriptions[i], descFont, Color.WHITE);
            keyRenderers[i] = new TextRenderer("Nyomj " + (i + 1) + "-est", descFont, Color.WHITE);
        }

        saveRenderer = new TextRenderer("Játék mentése (S)", mainFont, Color.BLACK);
        saveStatusRenderer = new TextRenderer("", statusFont, Color.GREEN);
    }

    // Player beállítása később
    public void setPlayer(Player player) {
        this.player = player;
        this.gameSaved = false; // Reseteljük a mentés állapotot új player esetén

        // Adatok eltárolása a mentéshez
        storeDataForSaving();
    }

    // Dungeon beállítása
    public void setDungeon(Enemy enemy, Enemy boss) {
        this.enemy = enemy;
        this.boss = boss;

        // Adatok frissítése dungeon esetén
        if (player != null) {
            storeDataForSaving();
        }
    }

    private void storeDataForSaving() {
        // FONTOS: NULL CHECK!
        if (player != null) {
            playerNameForSave = player.getName();
            playerAbilityForSave = player.getAbility();
            playerHealthForSave = player.getHealth();
            playerMaxHealthForSave = player.getMaxHealth();
            playerDamageBoostForSave = player.getDamageBoost();
            playerCritChanceBoostForSave = player.getCritChanceBoost();
        }
    }

    public void update(InputHandler inputHandler) {
        // Upgrade választás
        if (inputHandler.isKeyJustPressed(GLFW_KEY_1)) {
            selectedOption = 0;
            choiceMade = true;
        } else if (inputHandler.isKeyJustPressed(GLFW_KEY_2)) {
            selectedOption = 1;
            choiceMade = true;
        } else if (inputHandler.isKeyJustPressed(GLFW_KEY_3)) {
            selectedOption = 2;
            choiceMade = true;
        }

        // Mentés S billentyűvel
        if (inputHandler.isKeyJustPressed(GLFW_KEY_S) && !gameSaved && player != null) {
            saveGame();
        }

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                double mouseX = getCursorX();
                double mouseY = getCursorY();
                if (isMouseOverSaveButton(mouseX, mouseY) && !gameSaved) {
                    saveGame();
                    System.out.println("Játék mentése aktiválva");
                }
            }
        });
    }

    private boolean isMouseOverSaveButton(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY, SAVE_BUTTON_X, SAVE_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render() {
        setupProjectionForGraphics();

        glClear(GL_COLOR_BUFFER_BIT);
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(1280, 0);
        glVertex2f(1280, 720);
        glVertex2f(0, 720);
        glEnd();

        int iconSize = 128;
        int borderWidth = 10;

        for (int i = 0; i < 3; i++) {
            int x = 200 + i * 350;
            int y = 250;

            glColor3f(0.3f, 0.3f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(x - borderWidth, y - borderWidth);
            glVertex2f(x + iconSize + borderWidth, y - borderWidth);
            glVertex2f(x + iconSize + borderWidth, y + iconSize + borderWidth);
            glVertex2f(x - borderWidth, y + iconSize + borderWidth);
            glEnd();

            if (upgradeIcons[i] != null) {
                glEnable(GL_TEXTURE_2D);
                upgradeIcons[i].bind();
                glColor3f(1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 1); glVertex2f(x, y);
                glTexCoord2f(1, 1); glVertex2f(x + 128, y);
                glTexCoord2f(1, 0); glVertex2f(x + 128, y + 128);
                glTexCoord2f(0, 0); glVertex2f(x, y + 128);
                glEnd();
                upgradeIcons[i].unbind();
                glDisable(GL_TEXTURE_2D);
            }
        }

        setupProjectionForText();

        titleRenderer.render(500, 600, 1.0f);

        for (int i = 0; i < 3; i++) {
            int x = 200 + i * 350;
            int iconY = 250;
            int textY = iconY + 150 + 20;

            nameRenderers[i].render(x, 720 - (textY), 0.8f);
            descriptionRenderers[i].render(x, 720 - (textY + 30), 0.6f);
            keyRenderers[i].render(x, 970 - (textY + 60), 0.6f);
        }

        // Mentés gomb renderelése
        renderSaveButton();
    }

    private void renderSaveButton() {
        int saveButtonX = 540;
        int saveButtonY = 80;
        int saveButtonWidth = 200;
        int saveButtonHeight = 50;

        // Gomb háttér színe - állapotfüggő
        if (gameSaved || player == null) {
            glColor3f(0.5f, 0.5f, 0.5f); // Szürke - letiltva
        } else {
            glColor3f(0.2f, 0.7f, 0.3f); // Zöld - aktív
        }

        glBegin(GL_QUADS);
        glVertex2f(saveButtonX, saveButtonY);
        glVertex2f(saveButtonX + saveButtonWidth, saveButtonY);
        glVertex2f(saveButtonX + saveButtonWidth, saveButtonY + saveButtonHeight);
        glVertex2f(saveButtonX, saveButtonY + saveButtonHeight);
        glEnd();

        // Gomb szövegének frissítése állapot alapján
        String saveText;
        if (player == null) {
            saveText = "Nincs player";
        } else if (gameSaved) {
            saveText = "✓ Mentve!";
        } else {
            saveText = "Játék mentése (S)";
        }

        saveRenderer.cleanup();
        saveRenderer = new TextRenderer(saveText, new Font("Arial", Font.BOLD, 24), Color.BLACK);

        int saveTextX = saveButtonX + (saveButtonWidth - saveRenderer.getWidth()) / 2;
        int saveTextY = saveButtonY + (saveButtonHeight - 20) / 2;
        saveRenderer.render(saveTextX, saveTextY, 0.8f);

        // Mentés státusz üzenet
        if (gameSaved) {
            saveStatusRenderer.render(saveButtonX, saveButtonY + saveButtonHeight + 10, 0.6f);
        }
    }

    private void saveGame() {
        if (!gameSaved && player != null) {
            try {
                System.out.println("Mentés indítása az eltárolt adatokkal (UpgradeChoiceScreen)...");

                // Játékos mentése és ID lekérése
                int playerSaveId = GameSaveHandler.savePlayer(player);

                if (playerSaveId == -1) {
                    throw new Exception("Nem sikerült a player mentése");
                }

                GameSaveHandler.saveBoss((Boss) boss, playerSaveId);
                GameSaveHandler.saveEnemy(enemy, playerSaveId);

                gameSaved = true;

                // Státusz üzenet frissítése
                saveStatusRenderer.cleanup();
                saveStatusRenderer = new TextRenderer("✓ Játék mentve!", new Font("Arial", Font.BOLD, 16), Color.GREEN);

                System.out.println("✅ Játék sikeresen mentve az UpgradeChoiceScreen-ben! Player ID: " + playerSaveId);

            } catch (Exception e) {
                System.err.println("❌ Hiba a mentés során: " + e.getMessage());
                e.printStackTrace();

                // Hiba üzenet
                saveStatusRenderer.cleanup();
                saveStatusRenderer = new TextRenderer("❌ Mentés sikertelen", new Font("Arial", Font.BOLD, 16), Color.RED);
            }
        } else if (gameSaved) {
            System.out.println("⚠️ A játék már mentve van!");
        } else if (player == null) {
            System.err.println("❌ Nincs player objektum a mentéshez!");
        }
    }

    private void setupProjectionForGraphics() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 1280, 720, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void setupProjectionForText() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 1280, 0, 720, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public boolean isChoiceMade() {
        return choiceMade;
    }

    public int getSelectedOption() {
        return selectedOption;
    }

    public boolean isGameSaved() {
        return gameSaved;
    }

    public void reset() {
        selectedOption = -1;
        choiceMade = false;
        gameSaved = false;

        // Státusz üzenet visszaállítása
        saveStatusRenderer.cleanup();
        saveStatusRenderer = new TextRenderer("", new Font("Arial", Font.BOLD, 16), Color.GREEN);

        // Gomb szövegének visszaállítása
        saveRenderer.cleanup();
        saveRenderer = new TextRenderer("Játék mentése (S)", new Font("Arial", Font.BOLD, 24), Color.BLACK);
    }

    /**
     * Felszabadítja az UpgradeChoiceScreen által használt erőforrásokat.
     */
    public void cleanup() {
        // Ikon textúrák felszabadítása
        for (Texture icon : upgradeIcons) {
            if (icon != null) {
                icon.delete();
            }
        }

        // TextRenderer erőforrások felszabadítása
        if (titleRenderer != null) titleRenderer.cleanup();
        if (saveRenderer != null) saveRenderer.cleanup();
        if (saveStatusRenderer != null) saveStatusRenderer.cleanup();

        for (TextRenderer renderer : nameRenderers) {
            if (renderer != null) renderer.cleanup();
        }
        for (TextRenderer renderer : descriptionRenderers) {
            if (renderer != null) renderer.cleanup();
        }
        for (TextRenderer renderer : keyRenderers) {
            if (renderer != null) renderer.cleanup();
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
}