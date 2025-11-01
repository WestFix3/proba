package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import rendering.TextRenderer;
import rendering.Texture;
import rendering.TextureLoader;
import core.GameSaveHandler.SavedGame;

import java.awt.*;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class LoadScreen {

    private long window;
    private final int width = 800;
    private final int height = 600;
    private final String title = "Load Game";

    private boolean returnToLobby = false;
    private boolean loadSelectedGame = false;
    private int selectedSaveIndex = 0;
    private List<GameSaveHandler.SavedGame> savedGames;
    private TextRenderer[] saveGameRenderers;

    private TextRenderer titleRenderer;
    private TextRenderer instructionRenderer;
    private TextRenderer noSavesRenderer;
    private TextRenderer backRenderer;
    private TextRenderer warningRenderer; // Új: figyelmeztetés renderer

    private Texture backgroundTexture;

    // Új: figyelmeztetés állapot
    private boolean showWarning = false;
    private String warningMessage = "";

    public LoadScreen(long window) {
        this.window = window;
        initialize();
    }

    private void initialize() {
        // Betöltjük a mentett játékokat
        loadSavedGames();

        // Inicializáljuk a text renderereket
        initTextRenderers();

        // Betöltjük a háttértextúrát
        backgroundTexture = TextureLoader.loadTexture("lobby_background.png");

        // Beállítjuk a billentyűzet kezelőt
        setupInputCallbacks();
    }

    private void loadSavedGames() {
        savedGames = GameSaveHandler.listSavedGames();
        System.out.println("Betöltött mentések: " + (savedGames != null ? savedGames.size() : 0));

        // Ellenőrizzük az életerőket
        if (hasSaves()) {
            for (SavedGame savedGame : savedGames) {
                if (savedGame.getHealth() <= 0) {
                    System.out.println("FIGYELMEZTETÉS: " + savedGame.getPlayerName() +
                            " életereje negatív: " + savedGame.getHealth());
                }
            }
        }
    }

    private void initTextRenderers() {
        titleRenderer = new TextRenderer("LOAD GAME", new Font("Arial", Font.BOLD, 48), Color.WHITE);
        instructionRenderer = new TextRenderer("Use UP/DOWN to navigate, ENTER to load, ESC to back",
                new Font("Arial", Font.PLAIN, 16), Color.LIGHT_GRAY);
        noSavesRenderer = new TextRenderer("No saved games found",
                new Font("Arial", Font.PLAIN, 32), Color.YELLOW);
        backRenderer = new TextRenderer("Back to Lobby",
                new Font("Arial", Font.PLAIN, 32), Color.WHITE);
        warningRenderer = new TextRenderer("", new Font("Arial", Font.BOLD, 20), Color.RED);

        // Mentett játékok rendererei
        if (hasSaves()) {
            saveGameRenderers = new TextRenderer[savedGames.size()];
            for (int i = 0; i < savedGames.size(); i++) {
                SavedGame savedGame = savedGames.get(i);
                String saveText = String.format("%s - %s (HP: %.0f/%.0f)",
                        savedGame.getPlayerName(),
                        savedGame.getAbility(),
                        savedGame.getHealth(),
                        savedGame.getMaxHealth());
                saveGameRenderers[i] = new TextRenderer(saveText, new Font("Arial", Font.PLAIN, 24), Color.WHITE);
            }
        }
    }

    private void setupInputCallbacks() {
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                handleKeyPress(key);
            }
        });
    }

    private void handleKeyPress(int key) {
        // Ha figyelmeztetés van, csak az ESC fogadható el
        if (showWarning) {
            if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE) {
                showWarning = false;
                warningMessage = "";
            }
            return;
        }

        switch (key) {
            case GLFW_KEY_UP:
                if (hasSaves() || !hasSaves()) {
                    selectedSaveIndex = (selectedSaveIndex - 1 + getTotalItems()) % getTotalItems();
                }
                break;
            case GLFW_KEY_DOWN:
                if (hasSaves() || !hasSaves()) {
                    selectedSaveIndex = (selectedSaveIndex + 1) % getTotalItems();
                }
                break;
            case GLFW_KEY_ENTER:
            case GLFW_KEY_SPACE:
                executeSelectedAction();
                break;
            case GLFW_KEY_ESCAPE:
                returnToLobby = true;
                break;
            case GLFW_KEY_DELETE:
                if (hasSaves() && selectedSaveIndex < savedGames.size()) {
                    deleteSelectedSave();
                }
                break;
        }
    }

    private boolean hasSaves() {
        return savedGames != null && !savedGames.isEmpty();
    }

    private int getTotalItems() {
        if (!hasSaves()) {
            return 1; // Csak a "Back" gomb
        }
        return savedGames.size() + 1; // Mentések + Back gomb
    }

    private void executeSelectedAction() {
        if (!hasSaves()) {
            // Nincs mentés, csak a Back gomb van
            returnToLobby = true;
            return;
        }

        if (selectedSaveIndex < savedGames.size()) {
            // Mentés kiválasztva - ELLENŐRZÉS
            SavedGame selectedSave = savedGames.get(selectedSaveIndex);

            // Ellenőrizzük az életerőt
            if (selectedSave.getHealth() <= 0) {
                showWarning = true;
                warningMessage = "WARNING: This save has invalid health (" + selectedSave.getHealth() +
                        "). Cannot load this game!";
                System.out.println("❌ Nem lehet betölteni: " + selectedSave.getPlayerName() +
                        " - Érvénytelen HP: " + selectedSave.getHealth());
                return; // Nem engedjük betölteni
            }

            // További ellenőrzések (opcionális)
            if (selectedSave.getMaxHealth() <= 0) {
                showWarning = true;
                warningMessage = "WARNING: This save has invalid max health (" + selectedSave.getMaxHealth() +
                        "). Cannot load this game!";
                System.out.println("❌ Nem lehet betölteni: " + selectedSave.getPlayerName() +
                        " - Érvénytelen MaxHP: " + selectedSave.getMaxHealth());
                return;
            }

            // Ha minden rendben, betöltjük
            loadSelectedGame = true;
            System.out.println("✅ Kiválasztott mentés betöltése: " + selectedSave.getPlayerName() +
                    " (HP: " + selectedSave.getHealth() + ")");
        } else {
            // Back gomb
            returnToLobby = true;
        }
    }

    private void deleteSelectedSave() {
        if (selectedSaveIndex < savedGames.size()) {
            SavedGame selectedSave = savedGames.get(selectedSaveIndex);

            // Ellenőrizzük, mielőtt törölnénk
            if (selectedSave.getHealth() <= 0) {
                showWarning = true;
                warningMessage = "WARNING: This save has invalid health (" + selectedSave.getHealth() +
                        "). It's recommended to delete it.";
            }

            boolean success = GameSaveHandler.deleteSave(selectedSave.getSaveId());
            if (success) {
                System.out.println("Mentés törölve: " + selectedSave.getPlayerName());
                // Újratöltjük a listát
                loadSavedGames();
                initTextRenderers();
                // Index korrigálása
                if (selectedSaveIndex >= savedGames.size()) {
                    selectedSaveIndex = Math.max(0, savedGames.size() - 1);
                }
            }
        }
    }

    public void update() {
        // Frissítjük a státuszt
        if (returnToLobby || loadSelectedGame) {
            return;
        }

        glfwPollEvents();
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(0.1f, 0.1f, 0.2f, 1.0f);

        renderBackground();
        renderContent();

        // Figyelmeztetés renderelése (ha kell)
        if (showWarning) {
            renderWarning();
        }

        glfwSwapBuffers(window);
    }

    private void renderBackground() {
        if (backgroundTexture != null) {
            glEnable(GL_TEXTURE_2D);
            backgroundTexture.bind();

            glBegin(GL_QUADS);
            glTexCoord2f(0, 0);
            glVertex2f(0, 0);
            glTexCoord2f(1, 0);
            glVertex2f(width, 0);
            glTexCoord2f(1, 1);
            glVertex2f(width, height);
            glTexCoord2f(0, 1);
            glVertex2f(0, height);
            glEnd();

            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        } else {
            // Alternatív háttér, ha nincs textúra
            glColor4f(0.1f, 0.1f, 0.2f, 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(0, 0);
            glVertex2f(width, 0);
            glVertex2f(width, height);
            glVertex2f(0, height);
            glEnd();
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderContent() {
        // Cím
        titleRenderer.render(width / 2 - titleRenderer.getWidth() / 2, height - 80, 1.0f);

        if (!hasSaves()) {
            // Nincs mentett játék
            noSavesRenderer.render(width / 2 - noSavesRenderer.getWidth() / 2, height - 200, 1.0f);

            // Back gomb
            Color backColor = (selectedSaveIndex == 0) ? Color.YELLOW : Color.WHITE;
            Font backFont = (selectedSaveIndex == 0) ? new Font("Arial", Font.BOLD, 32) : new Font("Arial", Font.PLAIN, 32);
            backRenderer = new TextRenderer("Back to Lobby", backFont, backColor);
            backRenderer.render(width / 2 - backRenderer.getWidth() / 2, height - 280, 1.0f);
        } else {
            // Mentett játékok listája
            int startY = height - 180;
            int itemSpacing = 40;

            for (int i = 0; i < savedGames.size(); i++) {
                SavedGame savedGame = savedGames.get(i);

                // Szín beállítása az életerő alapján
                Color color;
                if (savedGame.getHealth() <= 0) {
                    color = Color.RED; // Piros, ha negatív az életerő
                } else if (savedGame.getHealth() < 30) {
                    color = Color.ORANGE; // Narancs, ha alacsony
                } else {
                    color = (i == selectedSaveIndex) ? Color.YELLOW : Color.WHITE;
                }

                Font font = (i == selectedSaveIndex) ? new Font("Arial", Font.BOLD, 24) : new Font("Arial", Font.PLAIN, 24);

                String saveText = String.format("%s - %s (HP: %.0f/%.0f)",
                        savedGame.getPlayerName(),
                        savedGame.getAbility(),
                        savedGame.getHealth(),
                        savedGame.getMaxHealth());

                TextRenderer renderer = new TextRenderer(saveText, font, color);
                renderer.render(width / 2 - renderer.getWidth() / 2, startY - i * itemSpacing, 1.0f);
            }

            // Back gomb
            int backIndex = savedGames.size();
            Color backColor = (selectedSaveIndex == backIndex) ? Color.YELLOW : Color.WHITE;
            Font backFont = (selectedSaveIndex == backIndex) ? new Font("Arial", Font.BOLD, 32) : new Font("Arial", Font.PLAIN, 32);
            backRenderer = new TextRenderer("Back to Lobby", backFont, backColor);
            backRenderer.render(width / 2 - backRenderer.getWidth() / 2,
                    startY - backIndex * itemSpacing, 1.0f);
        }

        // Utasítások
        instructionRenderer.render(50, 30, 1.0f);

        // További utasítás (törléshez)
        if (hasSaves() && selectedSaveIndex < savedGames.size()) {
            TextRenderer deleteInstruction = new TextRenderer("Press DELETE to delete selected save",
                    new Font("Arial", Font.PLAIN, 14), Color.LIGHT_GRAY);
            deleteInstruction.render(50, 60, 1.0f);
        }
    }

    private void renderWarning() {
        // Átlátszó fekete háttér
        glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Figyelmeztető üzenet
        warningRenderer = new TextRenderer(warningMessage, new Font("Arial", Font.BOLD, 20), Color.RED);
        warningRenderer.render(width / 2 - warningRenderer.getWidth() / 2, height / 2, 1.0f);

        // Utasítás
        TextRenderer instruction = new TextRenderer("Press ESC or ENTER to continue",
                new Font("Arial", Font.PLAIN, 16), Color.WHITE);
        instruction.render(width / 2 - instruction.getWidth() / 2, height / 2 - 40, 1.0f);
    }

    public boolean shouldReturnToLobby() {
        return returnToLobby;
    }

    public boolean shouldLoadGame() {
        return loadSelectedGame;
    }

    public SavedGame getSelectedSave() {
        if (hasSaves() && selectedSaveIndex < savedGames.size()) {
            return savedGames.get(selectedSaveIndex);
        }
        return null;
    }

    /**
     * Visszaadja a kiválasztott mentés ID-ját
     */
    public int getSelectedSaveId() {
        if (hasSaves() && selectedSaveIndex < savedGames.size()) {
            SavedGame selectedSave = savedGames.get(selectedSaveIndex);

            // Ellenőrizzük az életerőt
            if (selectedSave.getHealth() <= 0) {
                System.out.println("❌ Érvénytelen mentés: " + selectedSave.getPlayerName() +
                        " HP: " + selectedSave.getHealth());
                return -1; // -1 jelzi, hogy nem betölthető
            }

            return selectedSave.getSaveId();
        }
        return -1;
    }

    /**
     * Visszaállítja a LoadScreen állapotát
     */
    public void reset() {
        returnToLobby = false;
        loadSelectedGame = false;
        selectedSaveIndex = 0;
        showWarning = false;
        warningMessage = "";
        // Újratöltjük a mentéseket
        loadSavedGames();
        initTextRenderers();
    }

    public void cleanup() {
        // TextRenderer-ek felszabadítása
        if (titleRenderer != null) {
            titleRenderer.cleanup();
        }
        if (instructionRenderer != null) {
            instructionRenderer.cleanup();
        }
        if (noSavesRenderer != null) {
            noSavesRenderer.cleanup();
        }
        if (backRenderer != null) {
            backRenderer.cleanup();
        }
        if (warningRenderer != null) {
            warningRenderer.cleanup();
        }

        if (saveGameRenderers != null) {
            for (TextRenderer renderer : saveGameRenderers) {
                if (renderer != null) {
                    renderer.cleanup();
                }
            }
        }

        if (backgroundTexture != null) {
            backgroundTexture.delete();
        }

        // Callback reset
        glfwSetKeyCallback(window, null);
    }
}