package core.ProbaOsztalyok;

public class ProjectileState {
    private int projectileId;
    private float x, y;
    private float velocityX, velocityY;
    private int ownerPlayerId;
    private float damage;
    private boolean isActive = true;
    private String projectileType;

    // Konstruktor
    public ProjectileState(int projectileId, float x, float y, float velocityX, float velocityY,
                           int ownerPlayerId, float damage, String projectileType) {
        this.projectileId = projectileId;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.ownerPlayerId = ownerPlayerId;
        this.damage = damage;
        this.projectileType = projectileType;
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

    public String getProjectileType() { return projectileType; }
    public void setProjectileType(String projectileType) { this.projectileType = projectileType; }

    // Szinkronizálás
    public String serialize() {
        return String.format("%d,%.2f,%.2f,%.2f,%.2f,%d,%.1f,%s,%b",
                projectileId, x, y, velocityX, velocityY, ownerPlayerId,
                damage, projectileType, isActive);
    }

    public static ProjectileState deserialize(String data) {
        String[] parts = data.split(",");
        if (parts.length < 9) return null;

        try {
            int projectileId = Integer.parseInt(parts[0]);
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float velX = Float.parseFloat(parts[3]);
            float velY = Float.parseFloat(parts[4]);
            int ownerId = Integer.parseInt(parts[5]);
            float damage = Float.parseFloat(parts[6]);
            String type = parts[7];
            boolean active = Boolean.parseBoolean(parts[8]);

            return new ProjectileState(projectileId, x, y, velX, velY, ownerId, damage, type);
        } catch (Exception e) {
            System.err.println("❌ Error deserializing ProjectileState: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("Projectile[%d] Pos(%.1f,%.1f) Vel(%.1f,%.1f) Owner:%d Damage:%.1f",
                projectileId, x, y, velocityX, velocityY, ownerPlayerId, damage);
    }
}