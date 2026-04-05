package entities;

import core.ProjectileState;
import rendering.Texture;
import physics.Rectangle;

import static org.lwjgl.opengl.GL11.*;

/**
 * Lövedék entitás, ami sebzést okozhat és mozog.
 */
public class Projectile extends Entity {

    private int id = -1;
    private float damage;
    private float speed;
    private float dirX, dirY;
    private Entity owner;
    private boolean alive = true;
    private float lifetime = 3.0f;
    private float currentLifetime = 0.0f;

    // ✨ CSAK EZEKET ADJUK HOZZÁ A SZERIALIZÁCIÓHOZ
    private float velocityX;
    private float velocityY;
    private Texture texture;

    public Projectile(float x, float y, float width, float height, float damage, float speed, float dirX, float dirY, Entity owner) {
        super(x, y, width, height);
        this.damage = damage;
        this.speed = speed;
        this.dirX = dirX;
        this.dirY = dirY;
        this.owner = owner;

        // ✨ AUTOMATIKUS VELOCITY SZÁMÍTÁS
        this.velocityX = dirX * speed;
        this.velocityY = dirY * speed;
    }

    @Override
    public void update(float deltaTime, Object... args) {
        if (!alive) return;

        // Mozgás - használjuk a meglévő dirX, dirY-t
        x += dirX * speed * deltaTime;
        y += dirY * speed * deltaTime;

        // ✨ VELOCITY FRISSÍTÉSE (opcionális)
        this.velocityX = dirX * speed;
        this.velocityY = dirY * speed;

        // Élettartam csökkentése
        currentLifetime += deltaTime;
        if (currentLifetime >= lifetime) {
            alive = false;
        }
    }

    @Override
    public void render() {
        if (!alive) return;

        // Lövedék rajzolása
        if (texture != null) {
            texture.bind();
            glColor3f(1.0f, 1.0f, 1.0f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(x, y);
            glTexCoord2f(1, 0); glVertex2f(x + width, y);
            glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
            glTexCoord2f(0, 1); glVertex2f(x, y + height);
            glEnd();
            texture.unbind();
        } else {
            glColor3f(1.0f, 1.0f, 0.0f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + height);
            glVertex2f(x, y + height);
            glEnd();
        }
        glColor3f(1.0f, 1.0f, 1.0f);
    }

    // ✨ GETTEREK/SETTEREK A VELOCITY-HOZ
    public float getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(float velocityX) {
        this.velocityX = velocityX;
        // Automatikusan frissíti a dirX-et és speed-et
        updateDirectionAndSpeed();
    }

    public float getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(float velocityY) {
        this.velocityY = velocityY;
        // Automatikusan frissíti a dirY-et és speed-et
        updateDirectionAndSpeed();
    }

    private void updateDirectionAndSpeed() {
        this.speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (this.speed > 0) {
            this.dirX = velocityX / this.speed;
            this.dirY = velocityY / this.speed;
        }
    }

    public Texture getTexture() { return texture; }
    public void setTexture(Texture texture) { this.texture = texture; }

    // ✨ SZERIALIZÁCIÓ - használjuk a meglévő 'alive' mezőt
    public String serialize() {
        return String.format("%d,%.2f,%.2f,%.2f,%.2f,%.1f,%b",
                id, x, y, velocityX, velocityY, damage, alive);
    }

    // ✨ DESZERIALIZÁCIÓ
    public static Projectile deserialize(String data, Entity owner) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 7) {
                int id = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float velocityX = Float.parseFloat(parts[3]);
                float velocityY = Float.parseFloat(parts[4]);
                float damage = Float.parseFloat(parts[5]);
                boolean alive = Boolean.parseBoolean(parts[6]);

                // Számítsuk ki a direction-t és speed-et
                float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
                float dirX = speed > 0 ? velocityX / speed : 0;
                float dirY = speed > 0 ? velocityY / speed : 0;

                Projectile projectile = new Projectile(x, y, 10, 10, damage, speed, dirX, dirY, owner);
                projectile.setId(id);
                projectile.setAlive(alive);

                return projectile;
            }
        } catch (Exception e) {
            System.err.println("❌ Error deserializing projectile: " + e.getMessage());
        }
        return null;
    }

    // ✨ MEGLÉVŐ GETTEREK/SETTEREK (maradnak változatlanok)
    public float getDamage() { return damage; }
    public Entity getOwner() { return owner; }
    public boolean isAlive() { return alive; }
    public void setDamage(float damage) { this.damage = damage; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setId(int id) { this.id = id; }
    public int getId() { return id; }

    public static ProjectileState deserialize(String data) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 8) {
                int projectileId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float velocityX = Float.parseFloat(parts[3]);
                float velocityY = Float.parseFloat(parts[4]);
                int ownerPlayerId = Integer.parseInt(parts[5]);
                float damage = Float.parseFloat(parts[6]);
                boolean isActive = Boolean.parseBoolean(parts[7]);

                ProjectileState state = new ProjectileState(projectileId, x, y, velocityX, velocityY, ownerPlayerId, damage);
                state.setActive(isActive);
                return state;
            }
        } catch (Exception e) {
            System.err.println("❌ Error deserializing ProjectileState: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ✨ SPEED ÉS DIRECTION GETTEREK/SETTEREK
    public float getSpeed() { return speed; }
    public void setSpeed(float speed) {
        this.speed = speed;
        this.velocityX = dirX * speed;
        this.velocityY = dirY * speed;
    }

    public float getDirX() { return dirX; }
    public void setDirX(float dirX) {
        this.dirX = dirX;
        this.velocityX = dirX * speed;
    }

    public float getDirY() { return dirY; }
    public void setDirY(float dirY) {
        this.dirY = dirY;
        this.velocityY = dirY * speed;
    }
}