package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import rendering.TextRenderer;
import rendering.Texture;
import rendering.TextureLoader;

import java.awt.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Lobby {

    private long window;
    private final int width = 800;
    private final int height = 600;
    private final String title = "Lobby";

    private boolean startSinglePlayer = false;
    private boolean startMultiplayer = false;
    private boolean loadGame = false;
    private static boolean exitGame = false;
    private boolean shouldStartMultiplayerGame = false;
    private String multiplayerPlayerName;
    private String multiplayerAbility;
    private String multiplayerServerIp;
    private String multiplayerServerPort;
    private boolean multiplayerIsHost;
    private boolean showOptions = false;
    private int selectedMenuItem = 0;
    private final String[] menuItems = {"Single Player", "Multiplayer", "Load Game", "Options", "Exit"};

    private boolean showPathDebug = true;
    private int selectedOption = 0;
    private final String[] optionsItems = {"Show Path Debug: ON", "Back"};

    // ÚJ IDŐZÍTÉSI VÁLTOZÓK
    private long lastActionTime = 0;
    private final long actionDelay = 200; // 200 ms késleltetés a dupla kattintás ellen

    private GLFWKeyCallback keyCallback;
    private TextRenderer titleRenderer;
    private TextRenderer[] menuItemRenderers;
    private TextRenderer instructionRenderer;
    private TextRenderer optionsTitleRenderer;
    private TextRenderer[] optionsItemRenderers;
    private TextRenderer optionsInstructionRenderer;

    private Texture backgroundTexture;

    public void run() {
        init();
        loop();
        cleanup();

        if (startSinglePlayer) {
            startGameManager();
        }else if (shouldStartMultiplayerGame) {
            startMultiplayerGame(multiplayerPlayerName, multiplayerAbility, multiplayerServerIp, multiplayerServerPort, multiplayerIsHost);
        }
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (action == GLFW_PRESS) {
                    handleKeyPress(key);
                }
            }
        };

        glfwSetKeyCallback(window, keyCallback);

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - width) / 2, (vidmode.height() - height) / 2);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        glfwShowWindow(window);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        setupLobbyProjection();

        backgroundTexture = TextureLoader.loadTexture("lobby_background.png");

        initTextRenderers();
    }

    private void setupLobbyProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void initTextRenderers() {
        titleRenderer = new TextRenderer("MAIN MENU", new Font("Arial", Font.BOLD, 48), Color.BLACK);

        menuItemRenderers = new TextRenderer[menuItems.length];
        for (int i = 0; i < menuItems.length; i++) {
            menuItemRenderers[i] = new TextRenderer(menuItems[i], new Font("Arial", Font.PLAIN, 32), Color.BLACK);
        }

        instructionRenderer = new TextRenderer("Use UP/DOWN to navigate, ENTER to select, ESC to exit",
                new Font("Arial", Font.PLAIN, 16), Color.LIGHT_GRAY);

        optionsTitleRenderer = new TextRenderer("OPTIONS", new Font("Arial", Font.BOLD, 48), Color.BLACK);

        optionsItemRenderers = new TextRenderer[optionsItems.length];
        for (int i = 0; i < optionsItems.length; i++) {
            optionsItemRenderers[i] = new TextRenderer(optionsItems[i], new Font("Arial", Font.PLAIN, 32), Color.BLACK);
        }

        optionsInstructionRenderer = new TextRenderer("Use UP/DOWN to navigate, ENTER to toggle/select, ESC to back",
                new Font("Arial", Font.PLAIN, 16), Color.LIGHT_GRAY);
    }

    private void handleKeyPress(int key) {
        if (showOptions) {
            handleOptionsKeyPress(key);
        } else {
            handleMainMenuKeyPress(key);
        }
    }

    private void handleMainMenuKeyPress(int key) {
        switch (key) {
            case GLFW_KEY_UP:
                selectedMenuItem = (selectedMenuItem - 1 + menuItems.length) % menuItems.length;
                break;
            case GLFW_KEY_DOWN:
                selectedMenuItem = (selectedMenuItem + 1) % menuItems.length;
                break;
            case GLFW_KEY_ENTER:
            case GLFW_KEY_SPACE:
                executeSelectedMenuAction();
                break;
            case GLFW_KEY_ESCAPE:
                exitGame = true;
                break;
        }
    }

    private void handleOptionsKeyPress(int key) {
        switch (key) {
            case GLFW_KEY_UP:
                selectedOption = (selectedOption - 1 + optionsItems.length) % optionsItems.length;
                break;
            case GLFW_KEY_DOWN:
                selectedOption = (selectedOption + 1) % optionsItems.length;
                break;
            case GLFW_KEY_ENTER:
            case GLFW_KEY_SPACE:
                executeSelectedOptionAction();
                break;
            case GLFW_KEY_ESCAPE:
                showOptions = false;
                selectedOption = 0;
                break;
        }
    }

    private void executeSelectedMenuAction() {
        // AZONNALI IDŐZÍTÉSI ELLENŐRZÉS
        if (System.currentTimeMillis() - lastActionTime < actionDelay) {
            return;
        }
        lastActionTime = System.currentTimeMillis();

        switch (selectedMenuItem) {
            case 0:
                startSinglePlayer = true;
                break;
            case 1:
                startMultiplayer = true;
                handleMultiplayer();
                break;
            case 2:
                loadGame = true;
                handleLoadGame();
                break;
            case 3:
                showOptions = true;
                selectedOption = 0;
                break;
            case 4:
                exitGame = true;
                break;
        }
    }

    private void handleMultiplayer() {
        System.out.println("🔵 [1] handleMultiplayer() ELINDULT");

        try {
            MultiplayerScreen multiplayerScreen = new MultiplayerScreen(window);
            boolean multiplayerScreenActive = true;

            // --- 1. Képernyő: MultiplayerScreen ---
            while (!glfwWindowShouldClose(window) && multiplayerScreenActive && !exitGame) {
                System.out.println("🔵 [2] MultiplayerScreen ciklusban");

                setupLobbyProjection();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                multiplayerScreen.render();
                glfwSwapBuffers(window);
                glfwPollEvents();

                if (multiplayerScreen.shouldReturnToLobby()) {
                    System.out.println("🔵 [3] Vissza a lobbyba");
                    multiplayerScreenActive = false;
                } else if (multiplayerScreen.shouldStartHostGame() || multiplayerScreen.shouldStartJoinGame()) {
                    System.out.println("🔵 [4] Host/Join kiválasztva");
                    boolean isHost = multiplayerScreen.shouldStartHostGame();
                    multiplayerScreenActive = false;

                    // Tovább a következő képernyőre
                    handleConnectionScreen(isHost);
                }
            }
            multiplayerScreen.cleanup();

        } catch (Exception e) {
            System.err.println("❌ HIBA handleMultiplayer-ben: " + e.getMessage());
            e.printStackTrace();
        } finally {
            restoreLobbyCallbacks();
            startMultiplayer = false;
            lastActionTime = System.currentTimeMillis();
        }

        System.out.println("🔵 [5] handleMultiplayer() VÉGET ÉRT");
    }

    private void handleConnectionScreen(boolean isHost) {
        System.out.println("🟡 [6] handleConnectionScreen() ELINDULT, isHost: " + isHost);

        try {
            ServerConnectionScreen connectionScreen = new ServerConnectionScreen(window, width, height, isHost);

            while (!glfwWindowShouldClose(window) && !connectionScreen.isConnectionComplete() && !exitGame) {
                System.out.println("🟡 [7] ConnectionScreen ciklusban");

                setupLobbyProjection();
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                connectionScreen.render();
                glfwSwapBuffers(window);
                glfwPollEvents();
            }

            if (connectionScreen.isConnectionComplete()) {
                System.out.println("🟡 [8] Connection complete");
                String serverPort = connectionScreen.getServerPort();
                String serverIp = connectionScreen.getServerIp();

                // Tovább a ability screen-re
                handleAbilityScreen(serverIp, serverPort, isHost);
            } else {
                System.out.println("🟡 [9] Connection cancelled");
            }

            connectionScreen.cleanup();

        } catch (Exception e) {
            System.err.println("❌ HIBA handleConnectionScreen-ben: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleAbilityScreen(String serverIp, String serverPort, boolean isHost) {
        System.out.println("🟢 [10] handleAbilityScreen() ELINDULT");

        try {
            AbilitySelectionScreen abilityScreen = new AbilitySelectionScreen(window, width, height);

            while (!glfwWindowShouldClose(window) && !abilityScreen.isSelectionComplete() && !exitGame) {
                System.out.println("🟢 [11] AbilityScreen ciklusban");

                setupLobbyProjection();
                abilityScreen.update();
                abilityScreen.render();
                glfwSwapBuffers(window);
                glfwPollEvents();
            }

            if (abilityScreen.isSelectionComplete()) {
                System.out.println("🟢 [12] Ability selection complete - JÁTÉK INDÍTÁS!");

                // ✨ FONTOS: Töröld a régi szálas megoldást!
                // NE használj új szálat itt!

                String playerName = abilityScreen.getPlayerName();
                String ability = abilityScreen.getSelectedAbility().name();

                abilityScreen.cleanup();

                shouldStartMultiplayerGame = true;
                multiplayerPlayerName = playerName;
                multiplayerAbility = ability;
                multiplayerServerIp = serverIp;
                multiplayerServerPort = serverPort;
                multiplayerIsHost = isHost;

                glfwSetWindowShouldClose(window, true);
                
                return;
            }

            abilityScreen.cleanup();

        } catch (Exception e) {
            System.err.println("❌ HIBA handleAbilityScreen-ben: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void restoreLobbyCallbacks() {
        glfwSetKeyCallback(window, null);
        glfwSetCharCallback(window, null);
        glfwSetMouseButtonCallback(window, null);

        glfwSetKeyCallback(window, keyCallback);
    }

    private void handleLoadGame() {
        LoadScreen loadScreen = new LoadScreen(window);

        while (!glfwWindowShouldClose(window) && !loadScreen.shouldReturnToLobby()) {
            loadScreen.update();
            loadScreen.render();

            if (loadScreen.shouldLoadGame()) {
                GameSaveHandler.SavedGame selectedSave = loadScreen.getSelectedSave();
                if (selectedSave != null) {
                    startGameFromSave(selectedSave);
                    break;
                }
            }

            glfwPollEvents();
        }

        loadScreen.cleanup();

        glfwSetKeyCallback(window, keyCallback);

        if (loadScreen.shouldReturnToLobby()) {
            loadGame = false;
        }
    }

    private void startGameFromSave(GameSaveHandler.SavedGame savedGame) {
        System.out.println("Betöltött játék indítása: " + savedGame.getPlayerName() + " (ID: " + savedGame.getSaveId() + ")");

        GameManager gameManager = new GameManager();
        gameManager.setShowPathDebug(showPathDebug);

        gameManager.startGameFromSave(savedGame.getSaveId());
        gameManager.run();
    }

    private void executeSelectedOptionAction() {
        switch (selectedOption) {
            case 0:
                showPathDebug = !showPathDebug;
                updateOptionsText();
                break;
            case 1:
                showOptions = false;
                selectedOption = 0;
                break;
        }
    }

    private void updateOptionsText() {
        optionsItems[0] = "Show Path Debug: " + (showPathDebug ? "ON" : "OFF");
        optionsItemRenderers[0] = new TextRenderer(optionsItems[0], new Font("Arial", Font.PLAIN, 32),
                selectedOption == 0 ? Color.YELLOW : Color.BLACK);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window) && !exitGame && !startSinglePlayer && !startMultiplayer && !loadGame) {

            setupLobbyProjection();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glClearColor(0.1f, 0.1f, 0.2f, 1.0f);

            renderBackground();

            if (showOptions) {
                renderOptionsMenu();
            } else {
                renderMainMenu();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        if (exitGame) {
            glfwSetWindowShouldClose(window, true);
        }
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
        }
    }

    private void renderMainMenu() {
        titleRenderer.render(width / 2 - titleRenderer.getWidth() / 2, height - 100, 1.0f);

        for (int i = 0; i < menuItems.length; i++) {
            Color color = (i == selectedMenuItem) ? Color.YELLOW : Color.BLACK;
            Font font = (i == selectedMenuItem) ? new Font("Arial", Font.BOLD, 32) : new Font("Arial", Font.PLAIN, 32);

            menuItemRenderers[i] = new TextRenderer(menuItems[i], font, color);
            menuItemRenderers[i].render(width / 2 - menuItemRenderers[i].getWidth() / 2, height - (200 + i * 50), 1.0f);
        }

        instructionRenderer.render(50, 30, 1.0f);
    }

    private void renderOptionsMenu() {
        optionsTitleRenderer.render(width / 2 - optionsTitleRenderer.getWidth() / 2, height - 100, 1.0f);

        for (int i = 0; i < optionsItems.length; i++) {
            Color color = (i == selectedOption) ? Color.YELLOW : Color.BLACK;
            Font font = (i == selectedOption) ? new Font("Arial", Font.BOLD, 32) : new Font("Arial", Font.PLAIN, 32);

            optionsItemRenderers[i] = new TextRenderer(optionsItems[i], font, color);
            optionsItemRenderers[i].render(width / 2 - optionsItemRenderers[i].getWidth() / 2, height - (200 + i * 50), 1.0f);
        }

        optionsInstructionRenderer.render(50, 30, 1.0f);
    }

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void startGameManager() {
        System.out.println("Starting GameManager...");
        GameManager gameManager = new GameManager();
        gameManager.setShowPathDebug(showPathDebug);
        gameManager.run();
    }

    private void startMultiplayerGame(String playerName, String ability, String serverIp, String serverPort, boolean isHost) {
        System.out.println("🎯 MULTIPLAYER JÁTÉK INDÍTÁSA - CSAK EGYSZER!");

        try {
            GameManager gameManager = new GameManager();
            gameManager.setShowPathDebug(showPathDebug);

            System.out.println("🔗 KAPCSOLÓDÁS: " + serverIp + ":" + serverPort);
            gameManager.startMultiplayerGame(playerName, ability, serverIp, serverPort, isHost);

            System.out.println("✅ JÁTÉK ELINDULT!");
            gameManager.run(); // Ez blokkol, amíg a játék fut

        } catch (Exception e) {
            System.err.println("❌ HIBA multiplayer játék indításakor: " + e.getMessage());
            e.printStackTrace();

            // Szerver megtelt esetén vissza a lobbyba
            System.out.println("🔁 Szerver megtelt, vissza a lobbyba");
            restartLobby();
        }
    }

    private void restartLobby() {
        System.out.println("🔄 Lobby újraindítása...");

        try {
            // Teljesen új lobby példány
            Lobby newLobby = new Lobby();
            newLobby.run();
        } catch (Exception e) {
            System.err.println("❌ Nem sikerült újraindítani a lobby-t: " + e.getMessage());
            System.exit(0); // Vészhelyzeti kilépés
        }
    }

    public static boolean shouldExitGame(){
        return exitGame;
    }

    public boolean isShowPathDebug() {
        return showPathDebug;
    }
}