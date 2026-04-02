package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import rendering.TextRenderer;

import java.awt.*;
import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;

public class ServerConnectionScreen {
    private TextRenderer titleRenderer;
    private TextRenderer serverPortPromptRenderer;
    private TextRenderer serverIpPromptRenderer;
    private TextRenderer serverPortRenderer;
    private TextRenderer serverIpRenderer;
    private TextRenderer instructionRenderer;
    private TextRenderer errorRenderer;
    private TextRenderer connectButtonRenderer;

    private String serverPort = "";
    private String serverIp = "";
    private boolean ipActive = true;
    private boolean connectionComplete = false;
    private boolean showError = false;
    private String errorMessage = "";
    private boolean shouldReturnToLobby = false;

    // ✨ JAVÍTOTT: Player információ tárolása
    private String playerName = "Player";
    private String playerAbility = "SPEED";

    private long window;
    private int width;
    private int height;

    private GLFWKeyCallback keyCallback;
    private GLFWCharCallback charCallback;
    private GLFWMouseButtonCallback mouseCallback;

    public ServerConnectionScreen(long window, int width, int height, boolean isHost) {
        this.window = window;
        this.width = width;
        this.height = height;

        this.serverPort = "";
        this.serverIp = "";
        this.ipActive = true;

        initializeTextRenderers(isHost);
        setupInputCallbacks();

        System.out.println("ServerConnectionScreen inicializálva - " + (isHost ? "HOST" : "CLIENT"));
    }

    // ✨ ÚJ: Player információ beállítása
    public void setPlayerInfo(String name, String ability) {
        this.playerName = name != null ? name : "Player";
        this.playerAbility = ability != null ? ability : "SPEED";
        System.out.println("🎮 Player info beállítva: " + this.playerName + " (" + this.playerAbility + ")");
    }

    private void initializeTextRenderers(boolean isHost) {
        Font titleFont = new Font("Arial", Font.BOLD, 36);
        Font mainFont = new Font("Arial", Font.PLAIN, 24);
        Font smallFont = new Font("Arial", Font.PLAIN, 18);
        Font buttonFont = new Font("Arial", Font.BOLD, 20);

        String title = isHost ? "HOST GAME" : "JOIN GAME";
        titleRenderer = new TextRenderer(title, titleFont, Color.WHITE);

        serverPortPromptRenderer = new TextRenderer("Portszám:", mainFont, Color.WHITE);
        serverIpPromptRenderer = new TextRenderer("Szerver IP címe:", mainFont, Color.WHITE);

        updateServerPortRenderer();
        updateServerIpRenderer();

        instructionRenderer = new TextRenderer("Kattints egy mezőre, írd be az adatokat, majd nyomj ENTER-t", smallFont, Color.LIGHT_GRAY);

        String buttonText = isHost ? "SZERVER INDÍTÁSA" : "CSATLAKOZÁS";
        connectButtonRenderer = new TextRenderer(buttonText, buttonFont, Color.WHITE);

        errorRenderer = new TextRenderer("", smallFont, Color.RED);
    }

    private void setupInputCallbacks() {
        cleanupCallbacks();

        charCallback = glfwSetCharCallback(window, (w, codepoint) -> {
            if (connectionComplete) return;

            char character = (char) codepoint;

            if (ipActive && serverIp.length() < 20) {
                if (!Character.isISOControl(character)) {
                    serverIp += character;
                    updateServerIpRenderer();
                    System.out.println("Server IP: " + serverIp);
                }
            } else if (!ipActive) {
                if (Character.isDigit(character) && serverPort.length() < 5) {
                    serverPort += character;
                    updateServerPortRenderer();
                    System.out.println("Server Port: " + serverPort);
                }
            }
        });

        keyCallback = glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                System.out.println("Key pressed: " + key);
                handleKeyPress(key);
            }
        });

        mouseCallback = glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (action == GLFW_PRESS && button == GLFW_MOUSE_BUTTON_LEFT) {
                System.out.println("Mouse clicked");
                handleMouseClick();
            }
        });
    }

    private void handleKeyPress(int key) {
        switch (key) {
            case GLFW_KEY_TAB:
                ipActive = !ipActive;
                updateServerPortRenderer();
                updateServerIpRenderer();
                System.out.println("Field switched to: " + (ipActive ? "Server IP" : "Server Port"));
                break;

            case GLFW_KEY_BACKSPACE:
                if (ipActive && !serverIp.isEmpty()) {
                    serverIp = serverIp.substring(0, serverIp.length() - 1);
                    updateServerIpRenderer();
                    System.out.println("Server IP backspace: " + serverIp);
                } else if (!ipActive && !serverPort.isEmpty()) {
                    serverPort = serverPort.substring(0, serverPort.length() - 1);
                    updateServerPortRenderer();
                    System.out.println("Server Port backspace: " + serverPort);
                }
                break;

            case GLFW_KEY_ENTER:
            case GLFW_KEY_KP_ENTER:
                System.out.println("ENTER pressed - attempting connection");
                attemptConnection();
                break;

            case GLFW_KEY_ESCAPE:
                System.out.println("ESC pressed - returning to lobby");
                shouldReturnToLobby = true;
                connectionComplete = true;
                break;
        }
    }

    private void handleMouseClick() {
        double mouseX = getCursorX();
        double mouseY = getCursorY();

        int ipFieldY = height / 2 + 100;
        int portFieldY = height / 2 + 20;
        int buttonY = height / 2 - 80;

        if (isMouseOverTextField(mouseX, mouseY, ipFieldY)) {
            ipActive = true;
            updateServerPortRenderer();
            updateServerIpRenderer();
            System.out.println("Clicked on Server IP field");
        } else if (isMouseOverTextField(mouseX, mouseY, portFieldY)) {
            ipActive = false;
            updateServerPortRenderer();
            updateServerIpRenderer();
            System.out.println("Clicked on Server Port field");
        } else if (isMouseOverButton(mouseX, mouseY, buttonY)) {
            System.out.println("Clicked on Connect button");
            attemptConnection();
        }
    }

    private boolean isMouseOverTextField(double mouseX, double mouseY, int fieldY) {
        return mouseX >= width / 2 - 150 && mouseX <= width / 2 + 150 &&
                mouseY >= fieldY - 20 && mouseY <= fieldY + 20;
    }

    private boolean isMouseOverButton(double mouseX, double mouseY, int buttonY) {
        return mouseX >= width / 2 - 80 && mouseX <= width / 2 + 80 &&
                mouseY >= buttonY - 25 && mouseY <= buttonY + 25;
    }

    private void attemptConnection() {
        System.out.println("Attempting connection validation...");

        if (serverIp.trim().isEmpty()) {
            showError("Az IP cím nem lehet üres!");
            return;
        }

        if (!isValidIpAddress(serverIp)) {
            showError("Érvénytelen IP cím formátum!");
            return;
        }

        if (serverPort.trim().isEmpty()) {
            showError("A portszám nem lehet üres!");
            return;
        }

        try {
            int port = Integer.parseInt(serverPort.trim());
            if (port <= 0 || port > 65535) {
                showError("Érvénytelen portszám (1-65535)");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Érvénytelen portszám formátum!");
            return;
        }

        // Fontos: itt NEM nyitunk próba socketet, mert az "szellem játékost"
        // hozhat létre a szerveren (PLAYER_ID kiosztás + azonnali disconnect),
        // ami multiplayerben időszakos lobby visszadobást okozhat.
        // A valódi kapcsolatot a GameManager építi fel egyszer, kontrolláltan.
        hideError();
        connectionComplete = true;
        System.out.println("✅ Csatlakozási adatok elfogadva: " + serverIp + ":" + serverPort);
        System.out.println("🎮 Player: " + playerName + " (" + playerAbility + ")");
    }

    private boolean isValidIpAddress(String ip) {
        if (ip.equalsIgnoreCase("localhost")) {
            return true;
        }

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void showError(String message) {
        showError = true;
        errorMessage = message;
        updateErrorRenderer();
        System.out.println("Error: " + message);
    }

    private void hideError() {
        showError = false;
        errorMessage = "";
        updateErrorRenderer();
    }

    private void updateServerPortRenderer() {
        if (serverPortRenderer != null) serverPortRenderer.cleanup();
        serverPortRenderer = new TextRenderer(serverPort, new Font("Arial", Font.PLAIN, 20), Color.BLACK);
    }

    private void updateServerIpRenderer() {
        if (serverIpRenderer != null) serverIpRenderer.cleanup();
        serverIpRenderer = new TextRenderer(serverIp, new Font("Arial", Font.PLAIN, 20), Color.BLACK);
    }

    private void updateErrorRenderer() {
        if (errorRenderer != null) errorRenderer.cleanup();
        errorRenderer = new TextRenderer(errorMessage, new Font("Arial", Font.PLAIN, 18), Color.RED);
    }

    public void update() {
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        setupProjection();
        renderBackground();

        renderTextCentered(titleRenderer, width / 2, height - 80);

        renderServerIpSection();
        renderServerPortSection();
        renderConnectButton();
        renderInstructions();

        if (showError) {
            renderError();
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
        glBegin(GL_QUADS);
        glColor4f(0.1f, 0.1f, 0.3f, 1.0f);
        glVertex2f(0, height);
        glVertex2f(width, height);
        glColor4f(0.15f, 0.15f, 0.4f, 1.0f);
        glVertex2f(width, 0);
        glVertex2f(0, 0);
        glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderServerIpSection() {
        int fieldY = height / 2 + 100;
        int promptX = width / 2 - 330;
        int textboxX = width / 2 - 150;

        serverIpPromptRenderer.render(promptX, fieldY + 5, 1.0f);

        renderTextBox(textboxX, fieldY, 300, 40, ipActive);

        if (serverIpRenderer != null) {
            renderTextInTextBox(serverIpRenderer, textboxX + 10, fieldY);
        }

        if (ipActive) {
            renderCursor(textboxX, fieldY, serverIp);
        }
    }

    private void renderServerPortSection() {
        int fieldY = height / 2 + 20;
        int promptX = width / 2 - 300;
        int textboxX = width / 2 - 150;

        serverPortPromptRenderer.render(promptX, fieldY + 10, 1.0f);

        renderTextBox(textboxX, fieldY, 300, 40, !ipActive);

        if (serverPortRenderer != null) {
            renderTextInTextBox(serverPortRenderer, textboxX + 10, fieldY);
        }

        if (!ipActive) {
            renderCursor(textboxX, fieldY, serverPort);
        }
    }

    private void renderConnectButton() {
        int buttonY = height / 2 - 80;
        boolean isHovered = isMouseOverButton(getCursorX(), getCursorY(), buttonY);

        renderButton(width / 2 - 80, buttonY - 25, 160, 50, isHovered);
        renderTextCentered(connectButtonRenderer, width / 2, buttonY - 5);
    }

    private void renderInstructions() {
        renderTextCentered(instructionRenderer, width / 2, 80);

        String activeInfo = ipActive ?
                "Aktív mező: IP cím" : "Aktív mező: Portszám";
        TextRenderer activeInfoRenderer = new TextRenderer(activeInfo,
                new Font("Arial", Font.PLAIN, 16), Color.CYAN);
        renderTextCentered(activeInfoRenderer, width / 2, 50);
        activeInfoRenderer.cleanup();
    }

    private void renderError() {
        int errorY = height / 2 - 130;
        renderTextCentered(errorRenderer, width / 2, errorY);
    }

    private void renderTextBox(int x, int y, int width, int height, boolean isActive) {
        glColor4f(1.0f, 1.0f, 1.0f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        if (isActive) {
            glColor4f(0.0f, 0.5f, 1.0f, 1.0f);
        } else {
            glColor4f(0.7f, 0.7f, 0.7f, 1.0f);
        }

        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderButton(int x, int y, int width, int height, boolean isHovered) {
        if (isHovered) {
            glColor4f(0.3f, 0.7f, 0.3f, 1.0f);
        } else {
            glColor4f(0.2f, 0.6f, 0.2f, 1.0f);
        }

        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        glColor4f(0.1f, 0.4f, 0.1f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderCursor(int textBoxX, int textBoxY, String text) {
        TextRenderer tempRenderer = new TextRenderer(text, new Font("Arial", Font.PLAIN, 20), Color.BLACK);
        int textWidth = tempRenderer.getWidth();
        tempRenderer.cleanup();

        int cursorX = textBoxX + 10 + textWidth;
        int cursorY = textBoxY + 10;

        long time = System.currentTimeMillis();
        boolean visible = (time / 500) % 2 == 0;

        if (visible) {
            glColor4f(0.0f, 0.0f, 0.0f, 1.0f);
            glLineWidth(2.0f);
            glBegin(GL_LINES);
            glVertex2f(cursorX, cursorY);
            glVertex2f(cursorX, cursorY + 20);
            glEnd();
            glLineWidth(1.0f);
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderTextCentered(TextRenderer textRenderer, int centerX, int centerY) {
        if (textRenderer == null) return;
        int textWidth = textRenderer.getWidth();
        int x = centerX - textWidth / 2;
        textRenderer.render(x, centerY, 1.0f);
    }

    private void renderTextInTextBox(TextRenderer textRenderer, int x, int y) {
        if (textRenderer == null) return;
        textRenderer.render(x, y, 1.0f);
    }

    private double getCursorX() {
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer xPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, xPos, null);
            return xPos.get(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private double getCursorY() {
        try (MemoryStack stack = stackPush()) {
            DoubleBuffer yPos = stack.mallocDouble(1);
            glfwGetCursorPos(window, null, yPos);
            return height - yPos.get(0);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isConnectionComplete() {
        return connectionComplete;
    }

    public boolean shouldReturnToLobby() {
        return shouldReturnToLobby;
    }

    public String getServerPort() {
        return serverPort.trim();
    }

    public String getServerIp() {
        return serverIp.trim();
    }

    // ✨ JAVÍTOTT: Most már a tényleges player nevet adja vissza
    public String getPlayerName() {
        return playerName;
    }

    // ✨ ÚJ: Player ability getter
    public String getPlayerAbility() {
        return playerAbility;
    }

    public void reset() {
        connectionComplete = false;
        shouldReturnToLobby = false;
        showError = false;
        errorMessage = "";
        ipActive = true;
        serverPort = "";
        serverIp = "";
        // ✨ Player információt NEM reseteljük, mert újra felhasználjuk
        updateServerPortRenderer();
        updateServerIpRenderer();
    }

    public void cleanup() {
        System.out.println("ServerConnectionScreen cleaning up...");

        cleanupCallbacks();

        glfwSetKeyCallback(window, null);
        glfwSetCharCallback(window, null);
        glfwSetMouseButtonCallback(window, null);

        System.out.println("ServerConnectionScreen cleaned up");
    }

    private void cleanupCallbacks() {
        if (charCallback != null) {
            charCallback.free();
            charCallback = null;
        }
        if (keyCallback != null) {
            keyCallback.free();
            keyCallback = null;
        }
        if (mouseCallback != null) {
            mouseCallback.free();
            mouseCallback = null;
        }
    }
}