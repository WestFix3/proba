package core;

public class EffectState {
    private int effectId;
    private float x, y;
    private String effectType;
    private boolean isActive = true;

    public EffectState(int effectId, float x, float y, String effectType) {
        this.effectId = effectId;
        this.x = x;
        this.y = y;
        this.effectType = effectType;
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
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }

    public String serialize() {
        return String.format("%d,%.2f,%.2f,%s,%b",
                effectId, x, y, effectType, isActive);
    }
}