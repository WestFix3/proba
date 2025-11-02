package entities;

import rendering.Texture;
import static org.lwjgl.opengl.GL11.*;

// Ide fogjuk beágyazni az EffectType enumot és a PlayerEffect osztályt is.
public class Effect extends Entity {

    // --- ENUM: Az effektusok típusai ---
    public enum EffectType {
        SPEED_BOOST,
        DAMAGE_BOOST,
        HEALTH_REGEN
        // Ide jöhet minden további effektus
    }

    // --- INNER CLASS: A játékosra ható, időzített effektus ---
    public static class PlayerEffect {

        public EffectType type;
        public float duration;

        public PlayerEffect(EffectType type, float duration) {
            this.type = type;
            this.duration = duration;
        }
    }

    private Texture texture;
    private EffectType type;
    private int id = -1;
    public boolean isCollected = false;

    // A konstruktorban megkapja az effektus típusát
    public Effect(float x, float y, float width, float height, Texture texture, EffectType type) {
        super(x, y, width, height);
        this.texture = texture;
        this.type = type;
    }

    @Override
    public void update(float deltaTime, Object... args) {
        // Az effekteknek nincs saját frissítési logikájuk, csak a helyükön állnak.
    }

    @Override
    public void render() {
        if (!isCollected) { // Csak akkor rajzoljuk ki, ha még nem lett felvéve
            if (texture != null) {
                texture.bind();
                glColor3f(1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 1); glVertex2f(x, y);
                glTexCoord2f(1, 1); glVertex2f(x + width, y);
                glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
                glTexCoord2f(0, 0); glVertex2f(x, y + height);
                glEnd();
                texture.unbind();
            } else {
                glColor3f(1.0f, 0.0f, 1.0f);
                glBegin(GL_QUADS);
                glVertex2f(x, y);
                glVertex2f(x + width, y);
                glVertex2f(x + width, y + height);
                glVertex2f(x, y + height);
                glEnd();
            }
        }
    }

    public EffectType getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}