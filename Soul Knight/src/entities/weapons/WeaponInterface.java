package entities.weapons;

import entities.Entity;
import entities.Enemy;
import entities.Projectile;
import java.util.List;

public interface WeaponInterface {

    float getDamage();
    float getBaseDamage();
    void setDamage(float newDamage);

    // Ez a metódus a távolharci fegyverekhez van. A közelharci fegyvereknél a GameManager kezeli a támadást.
    Projectile shoot(Entity shooter, float startX, float startY, float targetX, float targetY, float currentTime);

    // Ez a metódus a közelharci fegyverekhez van. A távolharci fegyvereknél üresen marad.
    void attack(Entity attacker, List<Enemy> enemies);
}