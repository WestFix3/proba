package core.ProbaOsztalyok;

public class EffectState {
    private int effectId;
    private float x, y;
    private String effectType;
    private boolean isActive = true;
    private long creationTime;
    private float duration;

    // Konstruktor
    public EffectState(int effectId, float x, float y, String effectType, float duration) {
        this.effectId = effectId;
        this.x = x;
        this.y = y;
        this.effectType = effectType;
        this.duration = duration;
        this.creationTime = System.currentTimeMillis();
    }

    // Getterek/Setterek
    public int getEffectId() { return effectId; }
    public void setEffectId(int effectId) { this.effectId = effectId; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public String getEffectType() { return effectType; }
    public void setEffectType(String effectType) { this.effectType = effectType; }

    public boolean isActive() {
        if (duration > 0) {
            long currentTime = System.currentTimeMillis();
            return (currentTime - creationTime) < (duration * 1000);
        }
        return isActive;
    }

    public void setActive(boolean active) { this.isActive = active; }

    public long getCreationTime() { return creationTime; }
    public void setCreationTime(long creationTime) { this.creationTime = creationTime; }

    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = duration; }

    // Szinkronizálás
    public String serialize() {
        return String.format("%d,%.2f,%.2f,%s,%b,%.1f",
                effectId, x, y, effectType, isActive, duration);
    }

    public static EffectState deserialize(String data) {
        String[] parts = data.split(",");
        if (parts.length < 6) return null;

        try {
            int effectId = Integer.parseInt(parts[0]);
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            String type = parts[3];
            boolean active = Boolean.parseBoolean(parts[4]);
            float duration = Float.parseFloat(parts[5]);

            EffectState state = new EffectState(effectId, x, y, type, duration);
            state.setActive(active);

            return state;
        } catch (Exception e) {
            System.err.println("❌ Error deserializing EffectState: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("Effect[%d:%s] Pos(%.1f,%.1f) Duration:%.1fs",
                effectId, effectType, x, y, duration);
    }
}