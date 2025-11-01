package physics;

/**
 * Egyszerű Axis-Aligned Bounding Box (AABB) reprezentáció ütközés detektáláshoz.
 */
public class Rectangle {
    public float x, y, width, height;

    public Rectangle(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    /**
     * Ellenőrzi, hogy ez a téglalap metszi-e egy másik téglalapot.
     * @param other A másik téglalap.
     * @return Igaz, ha metszi, hamis, ha nem.
     */
    public boolean intersects(Rectangle other) {
        // Egyszerű AABB ütközés ellenőrzés
        return x < other.x + other.width &&
                x + width > other.x &&
                y < other.y + other.height &&
                y + height > other.y;
    }
}
