package input;

import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;

import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {

    private long window;
    private boolean[] keys;
    private boolean[] keysLastFrame;
    private boolean[] mouseButtons;
    private boolean[] mouseButtonsLastFrame;

    // 1. Tagváltozók hozzáadva a callback objektumok tárolásához
    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;

    // 2. Új: Mozgás változók multiplayerhez
    private float movementX = 0;
    private float movementY = 0;

    public InputHandler(long window) {
        this.window = window;
        this.keys = new boolean[GLFW_KEY_LAST + 1];
        this.keysLastFrame = new boolean[GLFW_KEY_LAST + 1];
        this.mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
        this.mouseButtonsLastFrame = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

        // 3. Callback-ek LÉTREHOZÁSA és ELTÁROLÁSA
        this.keyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key >= 0 && key <= GLFW_KEY_LAST) {
                    keys[key] = action != GLFW_RELEASE;
                }
            }
        };

        this.mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST) {
                    mouseButtons[button] = action != GLFW_RELEASE;
                }
            }
        };

        // Beállítja a tárolt callback-eket
        setupCallbacks();
    }

    public void setupCallbacks() {
        // A tárolt objektumok beállítása a GLFW-nek
        glfwSetKeyCallback(window, this.keyCallback);
        glfwSetMouseButtonCallback(window, this.mouseButtonCallback);
    }

    public void update() {
        // 4. Reset movement values minden frissítéskor
        movementX = 0;
        movementY = 0;

        // Mozgás inputok - WASD
        if (isKeyDown(GLFW_KEY_W)) {
            movementY -= 1;
        }
        if (isKeyDown(GLFW_KEY_S)) {
            movementY += 1;
        }
        if (isKeyDown(GLFW_KEY_A)) {
            movementX -= 1;
        }
        if (isKeyDown(GLFW_KEY_D)) {
            movementX += 1;
        }

        // Normalizálás (átlós mozgás esetén)
        if (movementX != 0 || movementY != 0) {
            float length = (float) Math.sqrt(movementX * movementX + movementY * movementY);
            movementX /= length;
            movementY /= length;
        }

        // Frissíti a 'last frame' állapotokat a következő ciklushoz
        System.arraycopy(mouseButtons, 0, mouseButtonsLastFrame, 0, mouseButtons.length);
        System.arraycopy(keys, 0, keysLastFrame, 0, keys.length);
    }

    // 5. ÚJ METÓDUSOK MULTIPLAYERHEZ
    /**
     * Visszaadja a vízszintes mozgás irányt (-1 balra, 0 nincs, 1 jobbra)
     * @return A vízszintes mozgás értéke
     */
    public float getMovementX() {
        return movementX;
    }

    /**
     * Visszaadja a függőleges mozgás irányt (-1 fel, 0 nincs, 1 le)
     * @return A függőleges mozgás értéke
     */
    public float getMovementY() {
        return movementY;
    }

    /**
     * Visszaadja a mozgás vektor hosszát (0-1 között)
     * @return A mozgás intenzitása
     */
    public float getMovementMagnitude() {
        return (float) Math.sqrt(movementX * movementX + movementY * movementY);
    }

    /**
     * Ellenőrzi, hogy a játékos mozog-e
     * @return true ha mozog, false ha áll
     */
    public boolean isMoving() {
        return movementX != 0 || movementY != 0;
    }

    // Meglévő metódusok
    public boolean isKeyDown(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) {
            return false;
        }
        return keys[keyCode];
    }

    public boolean isMouseButtonDown(int buttonCode) {
        if (buttonCode < 0 || buttonCode > GLFW_MOUSE_BUTTON_LAST) {
            return false;
        }
        return mouseButtons[buttonCode];
    }

    public boolean isMouseButtonPressed(int buttonCode) {
        if (buttonCode < 0 || buttonCode > GLFW_MOUSE_BUTTON_LAST) {
            return false;
        }
        return mouseButtons[buttonCode] && !mouseButtonsLastFrame[buttonCode];
    }

    // isKeyJustPressed (egyszeri lenyomás érzékelése)
    public boolean isKeyJustPressed(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) {
            return false;
        }
        return keys[keyCode] && !keysLastFrame[keyCode];
    }

    // 6. Callback getter metódusok
    /**
     * Visszaadja a tárolt billentyűzet callback objektumot,
     * amelyet a GameManager használ a callback-ek visszaállításához.
     * @return A GLFWKeyCallback objektum.
     */
    public GLFWKeyCallback getKeyCallback() {
        return keyCallback;
    }

    /**
     * Visszaadja a tárolt egér gomb callback objektumot,
     * amelyet a GameManager használ a callback-ek visszaállításához.
     * @return A GLFWMouseButtonCallback objektum.
     */
    public GLFWMouseButtonCallback getMouseButtonCallback() {
        return mouseButtonCallback;
    }

    // 7. Debug információk
    /**
     * Kiírja a jelenlegi mozgás állapotot (debug célokra)
     */
    public void printMovementState() {
        System.out.printf("Movement - X: %.2f, Y: %.2f, Magnitude: %.2f%n",
                movementX, movementY, getMovementMagnitude());
    }

    /**
     * Visszaadja a mozgás adatokat string formátumban
     * @return Mozgás információk stringként
     */
    public String getMovementInfo() {
        return String.format("Move(%.2f, %.2f)", movementX, movementY);
    }
}