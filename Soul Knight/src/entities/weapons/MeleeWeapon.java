package entities.weapons;

import entities.Entity;
import entities.Projectile;
import java.util.List;
import entities.Enemy;

public class MeleeWeapon implements WeaponInterface {

    private float damage;
    private float range;
    private float baseDamage;

    public MeleeWeapon(float damage, float range) { // Visszaállítva 2 paraméterre
        this.baseDamage = damage;
        this.damage = damage;
        this.range = range;
    }

    @Override
    public float getDamage() {
        return damage;
    }

    @Override
    public float getBaseDamage() {
        return baseDamage;
    }

    @Override
    public void setDamage(float newDamage) {
        this.damage = newDamage;
    }

    @Override
    public Projectile shoot(Entity shooter, float startX, float startY, float targetX, float targetY, float currentTime) {
        return null;
    }

    @Override
    public void attack(Entity attacker, List<Enemy> enemies) {
        // A közelharci támadás logikáját a GameManager végzi.
    }

    public float getRange() {
        return range;
    }
}