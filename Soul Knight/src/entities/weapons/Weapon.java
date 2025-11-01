package entities.weapons;

import entities.Entity;
import entities.Projectile;
import java.util.List;
import entities.Enemy;

public class Weapon implements WeaponInterface {

    private float damage;
    private float baseDamage;
    private float fireRate;
    private float lastShotTime;

    private float projectileSpeed;
    private float projectileSize;

    public Weapon(float damage, float fireRate, float projectileSpeed, float projectileSize) {
        this.baseDamage = damage;
        this.damage = damage;
        this.fireRate = fireRate;
        this.projectileSpeed = projectileSpeed;
        this.projectileSize = projectileSize;
        this.lastShotTime = Float.NEGATIVE_INFINITY;
    }

    @Override
    public Projectile shoot(Entity shooter, float startX, float startY, float targetX, float targetY, float currentTime) {
        float requiredTime = 1.0f / fireRate;
        float timeSinceLastShot = currentTime - lastShotTime;

        if (timeSinceLastShot < requiredTime) {
            return null;
        }

        float dirX = targetX - startX;
        float dirY = targetY - startY;
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY);

        if (length == 0) {
            return null;
        }

        dirX /= length;
        dirY /= length;

        lastShotTime = currentTime;

        float projX = startX + shooter.getWidth() / 2 - projectileSize / 2;
        float projY = startY + shooter.getHeight() / 2 - projectileSize / 2;

        return new Projectile(projX, projY, projectileSize, projectileSize, damage, projectileSpeed, dirX, dirY, shooter);
    }

    @Override
    public void attack(Entity attacker, List<Enemy> enemies) {
        // Ez egy távolharci fegyver, nem csinál semmit a közelharci támadásra.
        return;
    }

    @Override
    public float getBaseDamage() {
        return baseDamage;
    }

    @Override
    public float getDamage() {
        return damage;
    }

    @Override
    public void setDamage(float newDamage) {
        this.damage = newDamage;
    }
}