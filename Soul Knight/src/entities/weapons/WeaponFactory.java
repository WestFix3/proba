package entities.weapons;

import entities.weapons.Weapon;
import entities.weapons.MeleeWeapon;
import entities.weapons.WeaponInterface;
import rendering.Texture;
import rendering.TextureLoader;
import rendering.Sprite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Random;

public class WeaponFactory {

    private Map<String, WeaponData> weaponConfigurations = new HashMap<>();
    private Map<String, Sprite> weaponSprites = new HashMap<>();

    public WeaponFactory() {
        // TODO: Itt kellene betölteni a konfigurációs fájlokból az adatokat
        // Most példaként hardcode-olva vannak.

        // Alap fegyver (például egy pisztoly)
        WeaponData pistolData = new WeaponData();
        pistolData.id = "pistol";
        pistolData.type = "Ranged";
        pistolData.damage = 10.0f;
        pistolData.cooldown = 0.5f;
        pistolData.range = 400.0f;
        pistolData.speed = 400.0f; // Feltételezve, hogy ez a lövedék sebessége
        pistolData.projectileSize = 10.0f; // Ez a sor hiányzik!
        pistolData.animationFrames = List.of("ranged_attack1.png", "ranged_attack2.png");
        weaponConfigurations.put("pistol", pistolData);

        // Alap közelharci fegyver (például egy kard)
        WeaponData swordData = new WeaponData();
        swordData.id = "sword";
        swordData.type = "Melee";
        swordData.damage = 25.0f;
        swordData.cooldown = 0.5f;
        swordData.range = 70.0f;
        swordData.animationFrames = List.of("melee_attack1.png", "melee_attack2.png");
        weaponConfigurations.put("sword", swordData);
    }

    // A fegyverekhez tartozó sprite-okat is be kell tölteni a játék elején.
    public void loadWeaponSprites() {
        System.out.println("DEBUG: loadWeaponSprites() metódus fut...");
        for(WeaponData data : weaponConfigurations.values()) {
            System.out.println("DEBUG: Fegyver sprite-jainak betöltése: " + data.id);
            List<Texture> frames = data.animationFrames.stream()
                    .map(TextureLoader::loadTexture)
                    .collect(Collectors.toList());

            if (!frames.isEmpty() && frames.stream().allMatch(t -> t != null)) {
                float animationDuration = data.cooldown;
                float frameDuration = animationDuration / frames.size();

                System.out.println("DEBUG: A fegyver animációjának időtartama: " + animationDuration + "s. Egy képkocka időtartama: " + frameDuration + "s.");

                Sprite sprite = new Sprite(frames, frameDuration, false);
                weaponSprites.put(data.id, sprite);
            } else {
                System.err.println("HIBA: Nem sikerült betölteni a fegyver sprite-jait: " + data.id);
            }
        }
    }

    public WeaponInterface createWeapon(String id) {
        WeaponData data = weaponConfigurations.get(id);
        if (data == null) {
            System.err.println("Ismeretlen fegyver ID: " + id);
            return null;
        }

        if (data.type.equals("Ranged")) {
            float fireRate = 1.0f / data.cooldown;
            // Feltételezve, hogy a Weapon konstruktora a következőket várja:
            // damage, fireRate, projectileSpeed, projectileSize
            return new Weapon(data.damage, fireRate, data.speed, data.projectileSize);
        } else if (data.type.equals("Melee")) {
            return new MeleeWeapon(data.damage, data.range);
        }
        return null;
    }

    public Sprite getWeaponSprite(String id) {
        return weaponSprites.get(id);
    }

    // Új metódus, ami visszaad egy véletlenszerű fegyver ID-t.
    public String getRandomWeaponId() {
        List<String> weaponIds = new ArrayList<>(weaponConfigurations.keySet());
        if (weaponIds.isEmpty()) {
            return null;
        }
        return weaponIds.get(new Random().nextInt(weaponIds.size()));
    }

    public void cleanup() {
        System.out.println("DEBUG: WeaponFactory tisztítása...");
        // Minden sprite textúráinak felszabadítása
        for (Sprite sprite : weaponSprites.values()) {
            sprite.cleanup();
        }
        // A tárolók kiürítése
        weaponSprites.clear();
        weaponConfigurations.clear();
    }
}