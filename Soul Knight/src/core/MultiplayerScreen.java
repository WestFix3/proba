package core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import rendering.TextRenderer;
import rendering.Texture;
import rendering.TextureLoader;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class MultiplayerScreen {
    private long window;
    private final int width = 800;
    private final int height = 600;

    private boolean returnToLobby = false;
    private boolean startHostGame = false;
    private boolean startJoinGame = false;

    private int selectedMenuItem = 0;
    private final String[] menuItems = {"Host Game", "Join Game", "Back to Lobby"};

    private TextRenderer titleRenderer;
    private TextRenderer[] menuItemRenderers;
    private TextRenderer instructionRenderer;
    private TextRenderer statusRenderer;

    private GLFWKeyCallback keyCallback;

    public MultiplayerScreen(long window) {
        this.window = window;
        init();
    }

    private void init() {
        // Text rendererek inicializálása
        titleRenderer = new TextRenderer("MULTIPLAYER", new Font("Arial", Font.BOLD, 48), Color.WHITE);

        menuItemRenderers = new TextRenderer[menuItems.length];
        for (int i = 0; i < menuItems.length; i++) {
            menuItemRenderers[i] = new TextRenderer(menuItems[i], new Font("Arial", Font.PLAIN, 32), Color.WHITE);
        }

        instructionRenderer = new TextRenderer("Use UP/DOWN to navigate, ENTER to select, ESC to return",
                new Font("Arial", Font.PLAIN, 16), Color.LIGHT_GRAY);

        statusRenderer = new TextRenderer("Select an option", new Font("Arial", Font.PLAIN, 20), Color.CYAN);

        // Billentyűzet callback beállítása
        setupKeyCallback();

        System.out.println("MultiplayerScreen initialized");
    }

    private void setupKeyCallback() {
        keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (action == GLFW_PRESS) {
                    System.out.println("MultiplayerScreen key pressed: " + key);
                    handleKeyPress(key);
                }
            }
        };
        glfwSetKeyCallback(window, keyCallback);
    }

    private void handleKeyPress(int key) {
        switch (key) {
            case GLFW_KEY_UP:
                selectedMenuItem = (selectedMenuItem - 1 + menuItems.length) % menuItems.length;
                System.out.println("Selected: " + menuItems[selectedMenuItem]);
                break;
            case GLFW_KEY_DOWN:
                selectedMenuItem = (selectedMenuItem + 1) % menuItems.length;
                System.out.println("Selected: " + menuItems[selectedMenuItem]);
                break;
            case GLFW_KEY_ENTER:
            case GLFW_KEY_SPACE:
                System.out.println("ENTER pressed on: " + menuItems[selectedMenuItem]);
                executeSelectedAction();
                break;
            case GLFW_KEY_ESCAPE:
                System.out.println("ESC pressed - returning to lobby");
                returnToLobby = true;
                break;
        }
    }

    private void executeSelectedAction() {
        switch (selectedMenuItem) {
            case 0: // Host Game
                System.out.println("Host Game selected");
                startHostGame = true;
                break;
            case 1: // Join Game
                System.out.println("Join Game selected");
                startJoinGame = true;
                break;
            case 2: // Back to Lobby
                System.out.println("Back to Lobby selected");
                returnToLobby = true;
                break;
        }
    }

    public void update() {
        // Egyszerű frissítés
    }

    public void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        setupProjection();
        renderBackground();
        renderMenu();
    }

    private void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, 0, height, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void renderBackground() {
        // Háttér OpenGL primitívekkel
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

    private void renderMenu() {
        // Cím
        titleRenderer.render(width / 2 - titleRenderer.getWidth() / 2, height - 100, 1.0f);

        // Menüpontok
        for (int i = 0; i < menuItems.length; i++) {
            Color color = (i == selectedMenuItem) ? Color.YELLOW : Color.WHITE;
            Font font = (i == selectedMenuItem) ? new Font("Arial", Font.BOLD, 32) : new Font("Arial", Font.PLAIN, 32);

            menuItemRenderers[i] = new TextRenderer(menuItems[i], font, color);
            menuItemRenderers[i].render(width / 2 - menuItemRenderers[i].getWidth() / 2, height - (200 + i * 50), 1.0f);
        }

        // Státusz üzenet
        statusRenderer.render(width / 2 - statusRenderer.getWidth() / 2, 150, 1.0f);

        // Utasítás
        instructionRenderer.render(50, 30, 1.0f);
    }

    public boolean shouldReturnToLobby() {
        return returnToLobby;
    }

    public boolean shouldStartHostGame() {
        return startHostGame;
    }

    public boolean shouldStartJoinGame() {
        return startJoinGame;
    }

    public void cleanup() {
        System.out.println("MultiplayerScreen cleaning up...");
        
        // Callback-ek leválasztása (ne free-zzük kézzel, mert dupla felszabadításba futhat)
        keyCallback = null;
        
        // Minden más callback nullázása
        // Minden callback nullázása
        glfwSetKeyCallback(window, null);
        glfwSetCharCallback(window, null);
        glfwSetMouseButtonCallback(window, null);

        System.out.println("MultiplayerScreen cleaned up");
    }
}