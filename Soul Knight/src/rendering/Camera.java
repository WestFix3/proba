package rendering;

import entities.Player;
import static org.lwjgl.opengl.GL11.*;

public class Camera {
    private float x, y; // Kamera bal felső sarkának pozíciója
    private final int screenWidth, screenHeight;

    public Camera(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.x = 0;
        this.y = 0;
    }

    public void applyTransform() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // Fontos: y+screenHeight az ALUL lesz a világban
        glOrtho(x, x + screenWidth, y + screenHeight, y, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void follow(Player player, int dungeonWidth, int dungeonHeight) {
        // Játékos középre igazítása
        float targetX = player.getX() + player.getWidth()/2 - screenWidth/2;
        float targetY = player.getY() + player.getHeight()/2 - screenHeight/2;

        // X tengely korlátai
        this.x = Math.max(0, Math.min(targetX, dungeonWidth - screenWidth));

        // Javított Y tengely kezelés:
        // 1. Számold ki a maximális engedélyezett Y értéket
        float maxCameraY = dungeonHeight - screenHeight;

        // 2. Ha a pálya kisebb mint a képernyő, középre igazít
        if (dungeonHeight < screenHeight) {
            this.y = (dungeonHeight - screenHeight) / 2f;
        }
        // 3. Egyébként korlátozd a kamerát
        else {
            this.y = Math.max(0, Math.min(targetY, maxCameraY));
        }

        // Hibakereséshez:
        //System.out.printf("PlayerY: %.1f | TargetY: %.1f | CameraY: %.1f | MaxY: %.1f%n",
                //player.getY(), targetY, y, maxCameraY);
    }

    // Getterek
    public float getX() { return x; }
    public float getY() { return y; }
}