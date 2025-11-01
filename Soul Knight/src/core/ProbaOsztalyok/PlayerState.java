package core.ProbaOsztalyok;

public class PlayerState {
    private int enemyId;
    private String enemyType;
    private float x, y;
    private float health;
    private float maxHealth;
    private boolean isAlive = true;
    private int targetPlayerId = -1;

    // Konstruktor
    public PlayerState(int enemyId, String enemyType, float x, float y, float health, float maxHealth) {
        this.enemyId = enemyId;
        this.enemyType = enemyType;
        this.x = x;
        this.y = y;
        this.health = health;
        this.maxHealth = maxHealth;
    }

    // Getterek/Setterek
    public int getEnemyId() { return enemyId; }
    public void setEnemyId(int enemyId) { this.enemyId = enemyId; }

    public String getEnemyType() { return enemyType; }
    public void setEnemyType(String enemyType) { this.enemyType = enemyType; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getHealth() { return health; }
    public void setHealth(float health) { this.health = health; }

    public float getMaxHealth() { return maxHealth; }
    public void setMaxHealth(float maxHealth) { this.maxHealth = maxHealth; }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { this.isAlive = alive; }

    public int getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(int targetPlayerId) { this.targetPlayerId = targetPlayerId; }

    // Szinkronizálás
    public String serialize() {
        return String.format("%d,%s,%.2f,%.2f,%.1f,%.1f,%b,%d",
                enemyId, enemyType, x, y, health, maxHealth, isAlive, targetPlayerId);
    }

    public static PlayerState deserialize(String data) {
        String[] parts = data.split(",");
        if (parts.length < 8) return null;

        try {
            int enemyId = Integer.parseInt(parts[0]);
            String type = parts[1];
            float x = Float.parseFloat(parts[2]);
            float y = Float.parseFloat(parts[3]);
            float health = Float.parseFloat(parts[4]);
            float maxHealth = Float.parseFloat(parts[5]);
            boolean alive = Boolean.parseBoolean(parts[6]);
            int targetPlayerId = Integer.parseInt(parts[7]);

            PlayerState state = new PlayerState(enemyId, type, x, y, health, maxHealth);
            state.setAlive(alive);
            state.setTargetPlayerId(targetPlayerId);

            return state;
        } catch (Exception e) {
            System.err.println("❌ Error deserializing EnemyState: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("Enemy[%d:%s] Pos(%.1f,%.1f) HP:%.1f/%.1f Target:%d",
                enemyId, enemyType, x, y, health, maxHealth, targetPlayerId);
    }
}