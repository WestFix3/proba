package entities;

import physics.Rectangle; // ÚJ IMPORT!

import static org.lwjgl.opengl.GL11.*;

/**
 * Egy nagyon alapvető entitás osztály a pozícióval és mérettel.
 * Most már hitboxot is biztosít.
 */
public abstract class Entity {
    protected float x, y;
    protected float width, height;

    public Entity(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // Absztrakt metódusok, amiket az alosztályoknak implementálniuk kell
    public abstract void update(float deltaTime, Object... args);
    public abstract void render();

    /**
     * Visszaadja az entitás határoló téglalapját (hitboxát).
     * @return Egy Rectangle objektum, ami az entitás hitboxát reprezentálja.
     */
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    // Getterek, setterek (szükség esetén)
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }


    public void setX(float x) { this.x = x; } // ÚJ SETTER
    public void setY(float y) { this.y = y; } // ÚJ SETTER
}
