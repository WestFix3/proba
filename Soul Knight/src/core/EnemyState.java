package core;

public class EnemyState {
    private int enemyId;
    private String enemyType;
    private float x, y;
    private float health;
    private float maxHealth;
    private boolean isAlive = true;
    private float damage = 10.0f;

    public EnemyState(int enemyId, String enemyType, float x, float y, float health, float maxHealth) {
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
    public float getDamage() { return damage; }
    public void setDamage(float damage) { this.damage = damage; }

    // ✨ JAVÍTOTT SERIALIZÁLÁS - 8 mezős formátum
    public String serialize() {
        return enemyId + "," +
                String.format(java.util.Locale.US, "%.2f", x) + "," +
                String.format(java.util.Locale.US, "%.2f", y) + "," +
                String.format(java.util.Locale.US, "%.2f", health) + "," +
                String.format(java.util.Locale.US, "%.2f", maxHealth) + "," +
                isAlive + "," +
                String.format(java.util.Locale.US, "%.2f", damage) + "," +
                enemyType;
    }

    // ✨ ÚJ: Deserializáló metódus
    public static EnemyState deserialize(String data) {
        try {
            String[] parts = data.split(",");
            if (parts.length >= 8) {
                int enemyId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float health = Float.parseFloat(parts[3]);
                float maxHealth = Float.parseFloat(parts[4]);
                boolean isAlive = Boolean.parseBoolean(parts[5]);
                float damage = Float.parseFloat(parts[6]);
                String enemyType = parts[7];

                EnemyState state = new EnemyState(enemyId, enemyType, x, y, health, maxHealth);
                state.setAlive(isAlive);
                state.setDamage(damage);
                return state;
            } else {
                System.err.println("❌ Invalid EnemyState data: " + data + " (expected 8 parts, got " + parts.length + ")");
            }
        } catch (Exception e) {
            System.err.println("❌ Error deserializing EnemyState: " + e.getMessage());
            System.err.println("   Data: " + data);
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("EnemyState[ID:%d, Type:%s, Pos:(%.1f,%.1f), HP:%.1f/%.1f, Alive:%s, Damage:%.1f]",
                enemyId, enemyType, x, y, health, maxHealth, isAlive, damage);
    }
}