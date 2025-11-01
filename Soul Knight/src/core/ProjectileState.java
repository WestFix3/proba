package core;

import java.util.Locale;

public class ProjectileState {
    private int projectileId;
    private float x, y;
    private float velocityX, velocityY;
    private int ownerPlayerId;
    private float damage;
    private boolean isActive = true;

    public ProjectileState(int projectileId, float x, float y, float velocityX, float velocityY,
                           int ownerPlayerId, float damage) {
        this.projectileId = projectileId;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.ownerPlayerId = ownerPlayerId;
        this.damage = damage;
    }

    // Getterek/Setterek
    public int getProjectileId() { return projectileId; }
    public void setProjectileId(int projectileId) { this.projectileId = projectileId; }
    public float getX() { return x; }
    public void setX(float x) { this.x = x; }
    public float getY() { return y; }
    public void setY(float y) { this.y = y; }
    public float getVelocityX() { return velocityX; }
    public void setVelocityX(float velocityX) { this.velocityX = velocityX; }
    public float getVelocityY() { return velocityY; }
    public void setVelocityY(float velocityY) { this.velocityY = velocityY; }
    public int getOwnerPlayerId() { return ownerPlayerId; }
    public void setOwnerPlayerId(int ownerPlayerId) { this.ownerPlayerId = ownerPlayerId; }
    public float getDamage() { return damage; }
    public void setDamage(float damage) { this.damage = damage; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }

    public String serialize() {
        // ✨ JAVÍTÁS: Mindig pontot használj tizedeselválasztóként
        return String.format(Locale.US, "%d,%.2f,%.2f,%.2f,%.2f,%d,%.1f,%b",
                projectileId, x, y, velocityX, velocityY, ownerPlayerId, damage, isActive);
    }
}