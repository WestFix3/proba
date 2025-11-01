package core;

public class PlayerState {
    private int playerId;
    private String playerName;
    private float x, y;

    // ✨ ÚJ: Width és height - EZEK HIÁNYOZNAK
    private float width = 50.0f;
    private float height = 50.0f;

    private float health;
    private float maxHealth;
    private String currentWeapon;
    private String ability;
    private boolean isAlive = true;
    private long lastUpdateTime;

    // Konstruktor
    public PlayerState(int playerId, String playerName, float x, float y, float health, float maxHealth) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.health = health;
        this.maxHealth = maxHealth;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // Getterek/Setterek
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public float getX() { return x; }
    public void setX(float x) {
        this.x = x;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public float getY() { return y; }
    public void setY(float y) {
        this.y = y;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // ✨ ÚJ: Width és height getterek - EZEK HIÁNYOZNAK
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }

    public float getHealth() { return health; }
    public void setHealth(float health) {
        this.health = health;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public float getMaxHealth() { return maxHealth; }
    public void setMaxHealth(float maxHealth) { this.maxHealth = maxHealth; }

    public String getCurrentWeapon() { return currentWeapon; }
    public void setCurrentWeapon(String currentWeapon) { this.currentWeapon = currentWeapon; }

    public String getAbility() { return ability; }
    public void setAbility(String ability) {
        this.ability = ability;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) {
        this.isAlive = alive;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public long getLastUpdateTime() { return lastUpdateTime; }

    // Szinkronizálás
    public String serialize() {
        return String.format("%d,%s,%.2f,%.2f,%.1f,%.1f,%s,%s,%b,%.1f,%.1f",
                playerId, playerName, x, y, health, maxHealth,
                currentWeapon != null ? currentWeapon : "none",
                ability != null ? ability : "none",
                isAlive, width, height);
    }

    public static PlayerState deserialize(String data) {
        String[] parts = data.split(",");
        if (parts.length < 11) return null;

        try {
            int playerId = Integer.parseInt(parts[0]);
            String name = parts[1];
            float x = Float.parseFloat(parts[2]);
            float y = Float.parseFloat(parts[3]);
            float health = Float.parseFloat(parts[4]);
            float maxHealth = Float.parseFloat(parts[5]);
            String weapon = parts[6].equals("none") ? null : parts[6];
            String ability = parts[7].equals("none") ? null : parts[7];
            boolean alive = Boolean.parseBoolean(parts[8]);
            float width = Float.parseFloat(parts[9]);
            float height = Float.parseFloat(parts[10]);

            PlayerState state = new PlayerState(playerId, name, x, y, health, maxHealth);
            state.setCurrentWeapon(weapon);
            state.setAbility(ability);
            state.setAlive(alive);
            state.setWidth(width);
            state.setHeight(height);

            return state;
        } catch (Exception e) {
            System.err.println("❌ Error deserializing PlayerState: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("Player[%d:%s] Pos(%.1f,%.1f) HP:%.1f/%.1f Weapon:%s Ability:%s",
                playerId, playerName, x, y, health, maxHealth,
                currentWeapon != null ? currentWeapon : "none",
                ability != null ? ability : "none");
    }
}