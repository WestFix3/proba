package entities.weapons;

import java.util.List;

public class WeaponData {
    public String id;
    public String type; // "Ranged" vagy "Melee"
    public float damage;
    public float cooldown;
    public float range;
    public float speed; // Csak a távolharci fegyvereknél
    public List<String> animationFrames; // A sprite animációk fájlnevei
    public float projectileSize;
}
