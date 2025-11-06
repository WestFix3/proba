package entities;

import input.InputHandler;
import rendering.Texture;
import rendering.Sprite;
import physics.CollisionManager;
import entities.weapons.WeaponInterface;
import entities.weapons.Weapon;
import entities.weapons.MeleeWeapon;
import entities.weapons.WeaponFactory;
import world.Dungeon;
import world.Tile;
import world.Tile.TileType;
import rendering.TextRenderer;

import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import java.nio.DoubleBuffer;
import org.lwjgl.system.MemoryStack;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.awt.Font;
import java.awt.Color;

public class Player extends Entity {

    public enum Ability {
        SPEED("Gyorsaság"),
        BLOCK("Blokkolás"),
        DODGE("Kitérés");

        private final String displayName;

        Ability(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private int playerId = -1;
    private float targetX;
    private float targetY;
    private boolean hasTargetPosition = false;

    private float moveSpeed;
    private final float baseMoveSpeed = 200.0f;
    private final float baseDamage = 10.0f;

    private float health;
    private float maxHealth = 100.0f;

    private Texture idleTexture;
    private Sprite activeWalkSprite;
    private WeaponInterface currentWeapon;
    private long windowHandle;

    private Sprite walkSprite;
    private Sprite currentWeaponSprite;

    private boolean isWalking = false;
    private boolean isAttacking = false;
    private boolean isAbilityActive = false;
    private float networkWalkHoldTime = 0f;
    private static final float NETWORK_WALK_HOLD_DURATION = 0.2f;

    private WeaponFactory weaponFactory;
    private String currentWeaponId;
    private String name;
    private Ability selectedAbility;

    private static final float INTERACTION_DISTANCE = 200.0f;
    private final Texture floorTexture;
    private Dungeon dungeon;

    private boolean isGateAnimating = false;
    private long gateAnimationStartTime = 0;
    private int gateAnimationPhase = -1;
    private static final long GATE_PHASE_DURATION_NANO = 200_000_000L;
    private List<Tile> currentAnimatingGateGroup = null;
    private transient Consumer<List<Tile>> gateOpenListener;

    private float CRIT_CHANCE = 0.05f;
    private static final float CRIT_MULTIPLIER = 2.0f;

//    private float CRIT_CHANCE = 1.00f;
//    private static final float CRIT_MULTIPLIER = 1000000.0f;

    private float critIndicatorTime = 0.0f;
    private static final float CRIT_DISPLAY_DURATION = 0.8f;
    private boolean showCritIndicator = false;

    private boolean showHitIndicator = false;
    private float hitIndicatorTime = 0.0f;
    private static final float HIT_DISPLAY_DURATION = 0.2f;

    private TextRenderer textRenderer;
    private TextRenderer nameRenderer;

    private float healthBoost = 0f;
    private float damageBoost = 0f;
    private float critChanceBoost = 0f;

    private float abilityMoveSpeedMultiplier = 1.0f;
    private float blockChance = 0.0f;
    private float dodgeChance = 0.0f;
    private static final float DODGE_CHANCE_BASE = 0.20f;
    private static final float BLOCK_CHANCE_BASE = 0.50f;
    private static final float SPEED_BOOST_MULTIPLIER = 1.30f;
    private static final float BLOCK_DAMAGE_REDUCTION = 0.50f;

    public Player(float x, float y, float width, float height, Texture idleTexture, long windowHandle, WeaponFactory weaponFactory, Texture floorTexture, TextRenderer textRenderer) {
        super(x, y, width, height);
        this.idleTexture = idleTexture;
        this.windowHandle = windowHandle;
        this.weaponFactory = weaponFactory;
        this.moveSpeed = baseMoveSpeed;
        this.health = maxHealth;
        this.floorTexture = floorTexture;
        this.textRenderer = textRenderer;
        this.nameRenderer = new TextRenderer("", new Font("Arial", Font.PLAIN, 16), Color.WHITE);
    }

    public void setSprites(Sprite walkSprite) {
        this.walkSprite = walkSprite;
    }

    public void setActiveSprites(Sprite activeWalkSprite) {
        this.activeWalkSprite = activeWalkSprite;
    }

    public void setWeapon(WeaponInterface newWeapon, String weaponId) {
        this.currentWeapon = newWeapon;
        this.currentWeaponId = weaponId;
        if (this.currentWeapon != null) {
            float newDamage = this.currentWeapon.getBaseDamage() * (1 + damageBoost);
            this.currentWeapon.setDamage(newDamage);
        }
    }

    public void setGateOpenListener(Consumer<List<Tile>> listener) {
        this.gateOpenListener = listener;
    }

    public void setDungeon(Dungeon dungeon) {
        this.dungeon = dungeon;
    }

    public void startAttackAnimation() {
        if (currentWeaponId != null) {
            currentWeaponSprite = weaponFactory.getWeaponSprite(currentWeaponId);
            if (currentWeaponSprite != null) {
                currentWeaponSprite.reset();
                isAttacking = true;
            } else {
                isAttacking = false;
            }
        }
    }

    private void applyAbility(boolean isActive) {
        abilityMoveSpeedMultiplier = 1.0f;
        dodgeChance = 0.0f;
        blockChance = 0.0f;

        if (isActive && selectedAbility != null) {
            switch (selectedAbility) {
                case SPEED:
                    abilityMoveSpeedMultiplier = SPEED_BOOST_MULTIPLIER;
                    System.out.println("Speed képesség aktiválva: +" + (int)((SPEED_BOOST_MULTIPLIER - 1) * 100) + "% mozgási sebesség.");
                    break;
                case DODGE:
                    dodgeChance = DODGE_CHANCE_BASE;
                    System.out.println("Dodge képesség aktiválva: +" + (int)(DODGE_CHANCE_BASE * 100) + "% kitérési esély.");
                    break;
                case BLOCK:
                    blockChance = BLOCK_CHANCE_BASE;
                    System.out.println("Block képesség aktiválva: -" + (int)((1 - BLOCK_DAMAGE_REDUCTION) * 100) + "% sebzés.");
                    break;
            }
        }
    }

    private Tile getNearestInteractableObject() {
        if (dungeon == null) return null;

        Tile nearestTile = null;
        float minDistance = INTERACTION_DISTANCE;

        Tile[][] tiles = dungeon.getTiles();
        for (int x = 0; x < dungeon.getWidthTiles(); x++) {
            for (int y = 0; y < dungeon.getHeightTiles(); y++) {
                Tile tile = tiles[x][y];
                if (tile != null && (tile.getType() == TileType.GATE || tile.getType() == TileType.WEAPON_CRATE)) {
                    float playerCenterX = this.x + width / 2;
                    float playerCenterY = this.y + height / 2;
                    float tileCenterX = tile.getX() + tile.getWidth() / 2;
                    float tileCenterY = tile.getY() + tile.getHeight() / 2;

                    float distance = (float) Math.sqrt(
                            Math.pow(playerCenterX - tileCenterX, 2) + Math.pow(playerCenterY - tileCenterY, 2)
                    );

                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestTile = tile;
                    }
                }
            }
        }
        return nearestTile;
    }

    private List<Tile> getNearestGateGroup() {
        if (dungeon == null || dungeon.gateCorridorGroups.isEmpty()) {
            return null;
        }

        List<Tile> nearestGroup = null;
        float minDistance = Float.MAX_VALUE;

        for (List<Tile> group : dungeon.gateCorridorGroups) {
            if (group.isEmpty()) continue;

            Tile gate = group.get(0);
            if (gate.getType() != TileType.GATE) {
                continue;
            }

            float playerCenterX = x + width / 2;
            float playerCenterY = y + height / 2;
            float gateCenterX = gate.getX() + gate.getWidth() / 2;
            float gateCenterY = gate.getY() + gate.getHeight() / 2;

            float distance = (float) Math.sqrt(Math.pow(playerCenterX - gateCenterX, 2) + Math.pow(playerCenterY - gateCenterY, 2));

            if (distance < minDistance) {
                minDistance = distance;
                nearestGroup = group;
            }
        }

        return (minDistance <= INTERACTION_DISTANCE) ? nearestGroup : null;
    }

    private void startGateAnimation(List<Tile> gateGroup) {
        if (gateGroup == null || isGateAnimating) {
            return;
        }
        this.currentAnimatingGateGroup = gateGroup;
        this.isGateAnimating = true;
        this.gateAnimationStartTime = System.nanoTime();
        this.gateAnimationPhase = 0;
    }

    public void triggerGateAnimation(List<Tile> gateGroup) {
        startGateAnimation(gateGroup);
    }

    private void updateGateAnimation() {
        if (!isGateAnimating || currentAnimatingGateGroup == null) {
            return;
        }

        long elapsedTime = System.nanoTime() - gateAnimationStartTime;

        if (elapsedTime >= GATE_PHASE_DURATION_NANO) {
            gateAnimationPhase++;
            gateAnimationStartTime = System.nanoTime();
        }

        if (gateAnimationPhase == 0) {
            for (Tile gate : currentAnimatingGateGroup) {
                gate.updateGateAnimation(0);
            }
        } else if (gateAnimationPhase == 1) {
            for (Tile gate : currentAnimatingGateGroup) {
                gate.updateGateAnimation(1);
            }
        } else if (gateAnimationPhase == 2) {
            for (Tile gate : currentAnimatingGateGroup) {
                if (dungeon != null) {
                    dungeon.getTiles()[gate.getGridX()][gate.getGridY()] = new Tile(
                            Tile.TileType.FLOOR,
                            gate.getGridX(),
                            gate.getGridY(),
                            gate.getTileSize(),
                            floorTexture
                    );
                }
            }
            dungeon.gateCorridorGroups.remove(currentAnimatingGateGroup);
            isGateAnimating = false;
            currentAnimatingGateGroup = null;
            gateAnimationPhase = -1;
        }
    }

    @Override
    public void update(float deltaTime, Object... args) {
        InputHandler inputHandler = null;
        CollisionManager collisionManager = null;
        double currentTime = 0;

        for (Object arg : args) {
            if (arg instanceof InputHandler) {
                inputHandler = (InputHandler) arg;
            } else if (arg instanceof CollisionManager) {
                collisionManager = (CollisionManager) arg;
            } else if (arg instanceof Double) {
                currentTime = (Double) arg;
            }
        }

        if (inputHandler == null) return;

        isAbilityActive = inputHandler.isKeyDown(GLFW_KEY_Q);
        applyAbility(isAbilityActive);

        Tile nearestInteractable = getNearestInteractableObject();

        if (nearestInteractable != null && nearestInteractable.getType() == TileType.WEAPON_CRATE) {
            if (inputHandler.isKeyJustPressed(GLFW_KEY_E) && !nearestInteractable.isOpen()) {
                nearestInteractable.openCrate();
                System.out.println("DEBUG: Láda kinyitva, fegyver látható.");
            } else if (inputHandler.isKeyJustPressed(GLFW_KEY_F) && nearestInteractable.isOpen()) {
                String newWeaponId = nearestInteractable.getWeaponId();
                if (newWeaponId != null) {
                    this.setWeapon(weaponFactory.createWeapon(newWeaponId), newWeaponId);
                    nearestInteractable.takeWeapon();
                }
            }
        } else if (inputHandler.isKeyJustPressed(GLFW_KEY_E) && !isGateAnimating) {
            if (nearestInteractable != null && nearestInteractable.getType() == TileType.GATE) {
                List<Tile> nearestGateGroup = getNearestGateGroup();
                if (nearestGateGroup != null) {
                    if (gateOpenListener != null) {
                        gateOpenListener.accept(nearestGateGroup);
                    } else {
                        startGateAnimation(nearestGateGroup);
                    }
                }
            }
        }

        updateGateAnimation();

        if (showCritIndicator) {
            critIndicatorTime += deltaTime;
        }
        if (showHitIndicator) {
            hitIndicatorTime += deltaTime;
            if (hitIndicatorTime >= HIT_DISPLAY_DURATION) {
                showHitIndicator = false;
            }
        }

        float prevX = x;
        float prevY = y;
        float dx = 0;
        float dy = 0;

        float effectiveMoveSpeed = baseMoveSpeed * abilityMoveSpeedMultiplier;

        if (inputHandler.isKeyDown(GLFW_KEY_W)) {
            dy -= effectiveMoveSpeed * deltaTime;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_S)) {
            dy += effectiveMoveSpeed * deltaTime;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_A)) {
            dx -= effectiveMoveSpeed * deltaTime;
        }
        if (inputHandler.isKeyDown(GLFW_KEY_D)) {
            dx += effectiveMoveSpeed * deltaTime;
        }

        this.x += dx;
        this.y += dy;

        this.isWalking = (dx != 0 || dy != 0);

        if (collisionManager != null) {
            collisionManager.resolvePlayerTileCollisions(this, prevX, prevY);
        }

        if (isAttacking) {
            if (currentWeaponSprite != null) {
                currentWeaponSprite.update(deltaTime);
                if (currentWeaponSprite.isFinished()) {
                    isAttacking = false;
                    currentWeaponSprite = null;
                }
            }
        } else if (isWalking && !isAbilityActive) {
            if (walkSprite != null) {
                walkSprite.update(deltaTime);
            }
        } else if (isAbilityActive && activeWalkSprite != null) {
            activeWalkSprite.update(deltaTime);
        }
    }

    public void attack(Enemy enemy, float baseDamage) {
        float finalDamage = calculateFinalDamage(baseDamage, enemy);
        enemy.takeDamage(finalDamage);
    }

    public float calculateFinalDamage(float baseDamage, Enemy targetEnemy) {
        float actualCritChance = CRIT_CHANCE + critChanceBoost;
        float boostedBaseDamage = baseDamage * (1 + damageBoost);

        if (Math.random() < actualCritChance) {
            float finalDamage = boostedBaseDamage * CRIT_MULTIPLIER;
            if (targetEnemy != null) {
                targetEnemy.setCritIndicator();
            }
            showCritIndicator = true;
            critIndicatorTime = 0.0f;
            return finalDamage;
        }
        return boostedBaseDamage;
    }

    @Override
    public void render() {
        Texture currentTexture = idleTexture;

        if (isAttacking && currentWeaponSprite != null) {
            currentTexture = currentWeaponSprite.getCurrentFrame();
        } else if (isAbilityActive && activeWalkSprite != null) {
            currentTexture = activeWalkSprite.getCurrentFrame();
        } else if (isWalking && walkSprite != null) {
            currentTexture = walkSprite.getCurrentFrame();
        }

        if (currentTexture != null) {
            currentTexture.bind();

            if (showHitIndicator) {
                glColor3f(1.0f, 0.5f, 0.5f);
            } else {
                glColor3f(1.0f, 1.0f, 1.0f);
            }

            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(x, y);
            glTexCoord2f(1, 1); glVertex2f(x + width, y);
            glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
            glTexCoord2f(0, 0); glVertex2f(x, y + height);
            glEnd();
            currentTexture.unbind();
        } else {
            glColor3f(1.0f, 0.0f, 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + height);
            glVertex2f(x, y + height);
            glEnd();
        }

        renderHealthBar();
        renderPlayerName();

        if (showCritIndicator && critIndicatorTime < CRIT_DISPLAY_DURATION) {
            glPushMatrix();
            glTranslatef(this.x, this.y - 30, 0);
            glScalef(0.5f, 0.5f, 1.0f);
            glColor3f(1.0f, 0.0f, 0.0f);
            if (textRenderer != null) {
                // textRenderer.renderText("CRIT", 0, 0, 1.0f);
            }
            glPopMatrix();
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        } else if (critIndicatorTime >= CRIT_DISPLAY_DURATION) {
            showCritIndicator = false;
        }
    }

    // Új metódus: játékos nevének renderelése
    private void renderPlayerName() {
        if (name != null && !name.trim().isEmpty() && nameRenderer != null) {
            // Pozíció beállítása: játékos feje fölé
            float nameX = this.x + this.width / 2 - nameRenderer.getWidth() / 2;
            float nameY = this.y - 15; // Életerő sáv felett

            // Előző OpenGL állapotok mentése
            glPushAttrib(GL_ALL_ATTRIB_BITS);
            glPushMatrix();

            // Textúrák kikapcsolása a szöveg rendereléséhez
            glDisable(GL_TEXTURE_2D);

            glTranslatef(nameX, nameY, 0);

            glScalef(1, -1, 1);

            // A TextRenderer saját maga állítja be a színeket, ne állítsunk itt globális színt
            nameRenderer.render(0, 0, 1.0f);

            glPopMatrix();
            glPopAttrib();

            // Textúrák visszakapcsolása
            glEnable(GL_TEXTURE_2D);
        }
    }

    private void renderHealthBar() {
        float barWidth = width;
        float barHeight = 5.0f;
        float barYOffset = 10.0f;

        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(x, y - barYOffset);
        glVertex2f(x + barWidth, y - barYOffset);
        glVertex2f(x + barWidth, y - barYOffset + barHeight);
        glVertex2f(x, y - barYOffset + barHeight);
        glEnd();

        float healthPercentage = health / maxHealth;
        float filledWidth = barWidth * healthPercentage;

        if (healthPercentage > 0.6f) {
            glColor3f(0.0f, 1.0f, 0.0f);
        } else if (healthPercentage > 0.3f) {
            glColor3f(1.0f, 1.0f, 0.0f);
        } else {
            glColor3f(1.0f, 0.0f, 0.0f);
        }

        glBegin(GL_QUADS);
        glVertex2f(x, y - barYOffset);
        glVertex2f(x + filledWidth, y - barYOffset);
        glVertex2f(x + filledWidth, y - barYOffset + barHeight);
        glVertex2f(x, y - barYOffset + barHeight);
        glEnd();

        // Szín visszaállítása fehérre
        glColor3f(1.0f, 1.0f, 1.0f);
    }

    public void takeDamage(float amount) {
        float effectiveDamage = amount;

        if (isAbilityActive) {
            if (dodgeChance > 0.0f && Math.random() < dodgeChance) {
                effectiveDamage = 0;
                System.out.println("Kitérés sikeres!");
            } else if (blockChance > 0.0f && Math.random() < blockChance) {
                effectiveDamage *= BLOCK_DAMAGE_REDUCTION;
                System.out.println("Blokkolás sikeres! Sebzés csökkentve 50%-kal.");
            }
        }

        if (effectiveDamage > 0) {
            this.health -= effectiveDamage;
            if (this.health < 0) {
                this.health = 0;
            }

            this.showHitIndicator = true;
            this.hitIndicatorTime = 0.0f;

            if (damageListener != null) {
                damageListener.onPlayerDamaged(this, effectiveDamage, this.health, this.health > 0);
            }
        }

        if (this.health <= 0) {
            System.out.println("A játékos elhunyt!");
        }
    }

    public void heal(float amount) {
        this.health = Math.min(maxHealth, this.health + amount);
    }

    public float getBaseMoveSpeed() {
        return baseMoveSpeed;
    }

    public interface DamageListener {
        void onPlayerDamaged(Player player, float damageAmount, float newHealth, boolean isAlive);
    }

    private DamageListener damageListener;

    public void setDamageListener(DamageListener damageListener) {
        this.damageListener = damageListener;
    }

    public void setMoveSpeed(float newSpeed) {
        this.moveSpeed = newSpeed;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public void setDamage(float newDamage) {
        if (this.currentWeapon != null) {
            this.currentWeapon.setDamage(newDamage);
        }
    }

    public WeaponInterface getCurrentWeapon() {
        return currentWeapon;
    }

    public boolean isAttacking() {
        return this.isAttacking;
    }

    public void setName(String name) {
        this.name = name;
        // Név változásakor frissítjük a renderert
        if (nameRenderer != null) {
            nameRenderer.cleanup();
        }
        this.nameRenderer = new TextRenderer(name, new Font("Arial", Font.PLAIN, 16), Color.WHITE);
    }

    public void setAbility(Ability ability) {
        this.selectedAbility = ability;
        applyAbility(isAbilityActive);
        if (this.selectedAbility != null) {
            System.out.println("--- KÉPESSÉG VÁLTOZÁS ---");
            System.out.println("A játékos képessége most: " + this.selectedAbility.getDisplayName());
            System.out.println("Nyomd meg a Q gombot az aktiváláshoz!");
            System.out.println("-------------------------");
        }
    }

    public Ability getAbility() {
        return selectedAbility;
    }

    public void addHealthBoost(float amount) {
        this.healthBoost += amount;
        this.maxHealth += amount;
        this.health = Math.min(this.health + amount, this.maxHealth);
        System.out.println("Életerő növelve: +" + amount + " HP");
    }

    public void addDamageBoost(float amount) {
        this.damageBoost += amount;
        if (currentWeapon != null) {
            this.currentWeapon.setDamage(this.currentWeapon.getBaseDamage() * (1 + damageBoost));
        }
        System.out.println("Sebzés növelve: +" + (amount * 100) + "%");
    }

    public void addCritChanceBoost(float amount) {
        this.critChanceBoost += amount;
        System.out.println("Crit esély növelve: +" + (amount * 100) + "%");
    }

    public void resetAllStats() {
        healthBoost = 0f;
        damageBoost = 0f;
        critChanceBoost = 0f;
        maxHealth = 100.0f;
        health = maxHealth;
        moveSpeed = baseMoveSpeed;
        applyAbility(isAbilityActive);
        if (currentWeapon != null) {
            currentWeapon.setDamage(currentWeapon.getBaseDamage());
        }
    }

    public String getName() {
        return name;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealth() {
        return health;
    }

    public boolean isAlive() {
        return this.health > 0;
    }

    public float getDamageBoost() {
        return damageBoost;
    }

    public float getCritChanceBoost() {
        return critChanceBoost;
    }

    public float getMoveSpeed() {
        return this.moveSpeed;
    }

    public float getAbilityMoveSpeedMultiplier() {
        return abilityMoveSpeedMultiplier;
    }

    public float getEffectiveMoveSpeed() {
        return baseMoveSpeed * abilityMoveSpeedMultiplier;
    }

    public boolean isAbilityActive() {
        return isAbilityActive;
    }

    public void setHealth(float health){
        float clampedHealth = Math.max(0f, Math.min(health, maxHealth));
        this.health = clampedHealth;
        if (this.health <= 0f) {
            this.isWalking = false;
        }
    }

    public void setMaxHealth(float maxHealth){
        this.maxHealth = maxHealth;
        this.health = Math.min(this.health, maxHealth);
    }

    public void setDamageBoost(float damageBoost){
        this.damageBoost = damageBoost;
    }

    public void setCritChanceBoost(float critChanceBoost){
        this.critChanceBoost = critChanceBoost;
    }

    public float getCritChance() {
        return CRIT_CHANCE + critChanceBoost;
    }

    public void setTargetX(float targetX) {
        this.targetX = targetX;
        this.hasTargetPosition = true;
    }

    public void setTargetY(float targetY) {
        this.targetY = targetY;
        this.hasTargetPosition = true;
    }

    public void applyNetworkMovement(float newX, float newY, float deltaTime) {
        float dx = newX - this.x;
        float dy = newY - this.y;

        this.x = newX;
        this.y = newY;

        if (hasTargetPosition && Math.abs(targetX - newX) <= 0.5f && Math.abs(targetY - newY) <= 0.5f) {
            this.x = targetX;
            this.y = targetY;
            this.hasTargetPosition = false;
            dx = 0f;
            dy = 0f;
        }

        float animationDelta = deltaTime > 0f ? deltaTime : (1f / 60f);
        boolean moving = Math.abs(dx) > 0.1f || Math.abs(dy) > 0.1f;

        if (moving) {
            networkWalkHoldTime = NETWORK_WALK_HOLD_DURATION;
        } else if (networkWalkHoldTime > 0f) {
            networkWalkHoldTime = Math.max(0f, networkWalkHoldTime - animationDelta);
            moving = true;
        }

        this.isWalking = moving;

        if (moving) {
            if (isAbilityActive && activeWalkSprite != null) {
                activeWalkSprite.update(animationDelta);
            } else if (walkSprite != null) {
                walkSprite.update(animationDelta);
            }
        } else {
            if (walkSprite != null) {
                walkSprite.reset();
            }
            if (activeWalkSprite != null) {
                activeWalkSprite.reset();
            }
        }
    }

    public float getTargetX() {
        return targetX;
    }

    public float getTargetY() {
        return targetY;
    }

    public boolean hasTargetPosition() {
        return hasTargetPosition;
    }

    public void setAlive(boolean alive) {
        if (alive) {
            if (this.health <= 0) {
                this.health = this.maxHealth; // Ha él, állítsuk vissza az életerőt
            }
        } else {
            this.health = 0; // Ha nem él, állítsuk az életerőt 0-ra
        }
        System.out.println("DEBUG: Player setAlive: " + alive + ", Health: " + this.health);
    }

    public int getId() {
        return playerId;
    }

    public void setId(int id) {
        this.playerId = id;
    }
}