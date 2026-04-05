package entities;

import rendering.Texture;
import rendering.TextureLoader;
import rendering.TextRenderer;
import world.Dungeon;
import physics.CollisionManager;
import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Boss entitás speciális képességekkel
 */
public class Boss extends Enemy {

    // --- BOSS SPECIFIC ATTRIBUTES ---
    private CollisionManager collisionManager;
    private Texture texture;
    private Texture minionTexture;
    private TextRenderer textRenderer;
    private BossState currentState = BossState.NORMAL;
    private float stateTimer = 0.0f;
    private float specialAbilityCooldown = 0.0f;

    // --- MOVEMENT PATTERNS ---
    private float movementPatternTimer = 0.0f;
    private MovementPattern currentPattern = MovementPattern.RANDOM;
    private float patternDuration = 3.0f;

    // --- SPECIAL ABILITIES ---
    private boolean isCharging = false;
    private float chargeTimer = 0.0f;
    private float chargeDuration = 1.5f;
    private float chargeSpeed = 150.0f;
    private float chargeDirectionX = 0.0f;
    private float chargeDirectionY = 0.0f;

    private boolean isSpinning = false;
    private float spinTimer = 0.0f;
    private float spinDuration = 2.0f;
    private float spinDamage = 25.0f;

    private boolean isSummoning = false;
    private float summonTimer = 0.0f;
    private float summonDuration = 2.0f;

    // --- BOSS STATS ---
    private float enrageThreshold = 0.5f; // 50% health
    private boolean isEnraged = false;
    private float enragedDamageMultiplier = 1.5f;
    private float enragedSpeedMultiplier = 1.3f;

    // --- VISUAL EFFECTS ---
    private boolean showEnrageEffect = false;
    private float enrageEffectTimer = 0.0f;
    private static final float ENRAGE_EFFECT_DURATION = 1.0f;

    private Random random = new Random();

    public Boss(float x, float y, float width, float height, Texture texture, float initialHealth,
                TextRenderer textRenderer, Dungeon dungeon, CollisionManager collisionManager, Player targetPlayer) {
        super(x, y, width, height, texture, initialHealth, textRenderer, dungeon, collisionManager, targetPlayer);

        this.collisionManager = collisionManager;
        this.textRenderer = textRenderer;
        this.texture = texture;
        this.minionTexture = TextureLoader.loadTexture("boss_minion.png");
        if (this.minionTexture == null) {
            this.minionTexture = texture;
        }
        
        // Boss specific settings
        this.moveSpeed = 40.0f; // Slower base speed
        this.attackDamage = 30.0f; // Higher base damage
        this.attackCooldown = 2.0f; // Slower attacks but more powerful
    }

    @Override
    public void update(float deltaTime, Object... args) {
        if (dungeon == null) return;

        // Update target player reference
        if (targetPlayer == null && args != null && args.length > 0 && args[0] instanceof Player) {
            this.targetPlayer = (Player) args[0];
        }

        // Check for enrage state
        checkEnrageState();

        // Update timers
        stateTimer += deltaTime;
        specialAbilityCooldown -= deltaTime;
        movementPatternTimer += deltaTime;

        // Handle current state
        switch (currentState) {
            case NORMAL:
                updateNormalState(deltaTime);
                break;
            case CHARGING:
                updateChargingState(deltaTime);
                break;
            case SPINNING:
                updateSpinningState(deltaTime);
                break;
            case SUMMONING:
                updateSummoningState(deltaTime);
                break;
        }

        // Update visual effects
        updateVisualEffects(deltaTime);

        // Call parent update for basic functionality
        super.update(deltaTime, args);
    }

    /**
     * Normal state - movement and ability decisions
     */
    private void updateNormalState(float deltaTime) {
        // Change movement pattern periodically
        if (movementPatternTimer >= patternDuration) {
            changeMovementPattern();
            movementPatternTimer = 0.0f;
        }

        // Execute current movement pattern
        executeMovementPattern(deltaTime);

        // Decide on special abilities
        if (specialAbilityCooldown <= 0.0f) {
            decideSpecialAbility();
        }

        // Basic attack
        if (targetPlayer != null && targetPlayer.isAlive()) {
            checkAndAttackPlayer();
        }
    }

    /**
     * Charging state - dash in a direction
     */
    private void updateChargingState(float deltaTime) {
        chargeTimer += deltaTime;

        if (chargeTimer < chargeDuration) {
            // Continue charging
            float chargeMoveX = chargeDirectionX * chargeSpeed * deltaTime;
            float chargeMoveY = chargeDirectionY * chargeSpeed * deltaTime;

            float newX = getX() + chargeMoveX;
            float newY = getY() + chargeMoveY;

            // Check collision before moving
            if (!checkCollision(newX, newY)) {
                setX(newX);
                setY(newY);
            } else {
                // Hit wall, stop charge early
                chargeTimer = chargeDuration;
            }

            // Damage player if colliding during charge
            if (targetPlayer != null && checkPlayerCollision()) {
                float actualDamage = isEnraged ? attackDamage * 1.5f * enragedDamageMultiplier : attackDamage * 1.5f;
                targetPlayer.takeDamage(actualDamage);
            }
        } else {
            // End charge
            isCharging = false;
            currentState = BossState.NORMAL;
            specialAbilityCooldown = 4.0f; // Cooldown after charge
        }
    }

    /**
     * Spinning state - damage all nearby
     */
    private void updateSpinningState(float deltaTime) {
        spinTimer += deltaTime;

        // Visual spinning effect (you can add rotation rendering here)
        if (spinTimer < spinDuration) {
            // Damage nearby players continuously
            if (targetPlayer != null && checkPlayerCollision()) {
                float actualDamage = isEnraged ? spinDamage * enragedDamageMultiplier : spinDamage;
                targetPlayer.takeDamage(actualDamage * deltaTime);
            }
        } else {
            // End spin
            isSpinning = false;
            currentState = BossState.NORMAL;
            specialAbilityCooldown = 3.0f;
        }
    }

    /**
     * Summoning state - prepare to summon minions
     */
    private void updateSummoningState(float deltaTime) {
        summonTimer += deltaTime;

        if (summonTimer >= summonDuration) {
            // Actually summon minions (this would need your minion creation logic)
            summonMinions();

            // End summoning
            isSummoning = false;
            currentState = BossState.NORMAL;
            specialAbilityCooldown = 8.0f; // Long cooldown for summoning
        }
    }

    /**
     * Execute current movement pattern
     */
    private void executeMovementPattern(float deltaTime) {
        if (targetPlayer == null) return;

        float moveX = 0, moveY = 0;

        switch (currentPattern) {
            case RANDOM:
                // Random directional movement
                if (movementPatternTimer < 1.0f) {
                    moveX = (random.nextFloat() - 0.5f) * 2.0f;
                    moveY = (random.nextFloat() - 0.5f) * 2.0f;
                }
                break;

            case CIRCLE_PLAYER:
                // Circle around player
                float angle = movementPatternTimer * 2.0f; // Rotation speed
                float radius = 100.0f; // Circle radius

                moveX = (float) Math.cos(angle) * radius - getX() + targetPlayer.getX();
                moveY = (float) Math.sin(angle) * radius - getY() + targetPlayer.getY();
                break;

            case APPROACH:
                // Move towards player
                float dx = targetPlayer.getX() - getX();
                float dy = targetPlayer.getY() - getY();
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance > 0) {
                    moveX = dx / distance;
                    moveY = dy / distance;
                }
                break;

            case RETREAT:
                // Move away from player
                float dx2 = targetPlayer.getX() - getX();
                float dy2 = targetPlayer.getY() - getY();
                float distance2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);

                if (distance2 > 0) {
                    moveX = -dx2 / distance2;
                    moveY = -dy2 / distance2;
                }
                break;
        }

        // Normalize and apply movement
        float moveLength = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (moveLength > 0) {
            moveX /= moveLength;
            moveY /= moveLength;

            float actualSpeed = isEnraged ? moveSpeed * enragedSpeedMultiplier : moveSpeed;
            float actualMoveX = moveX * actualSpeed * deltaTime;
            float actualMoveY = moveY * actualSpeed * deltaTime;

            float newX = getX() + actualMoveX;
            float newY = getY() + actualMoveY;

            if (!checkCollision(newX, newY)) {
                setX(newX);
                setY(newY);
            }
        }
    }

    /**
     * Change to a random movement pattern
     */
    private void changeMovementPattern() {
        MovementPattern[] patterns = MovementPattern.values();
        currentPattern = patterns[random.nextInt(patterns.length)];
        System.out.println("Boss changed to pattern: " + currentPattern);
    }

    /**
     * Decide which special ability to use
     */
    private void decideSpecialAbility() {
        if (targetPlayer == null || !targetPlayer.isAlive()) return;

        float abilityRoll = random.nextFloat();

        if (abilityRoll < 0.4f) {
            // Charge attack
            startCharge();
        } else if (abilityRoll < 0.7f) {
            // Spin attack
            startSpin();
        } else if (abilityRoll < 0.9f && !isEnraged) {
            // Summon minions (only when not enraged)
            startSummoning();
        }
        // 10% chance to do nothing
    }

    /**
     * Start charge ability
     */
    private void startCharge() {
        if (targetPlayer == null) return;

        // Calculate direction towards player
        float dx = targetPlayer.getX() - getX();
        float dy = targetPlayer.getY() - getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 0) {
            chargeDirectionX = dx / distance;
            chargeDirectionY = dy / distance;
        } else {
            chargeDirectionX = 1.0f; // Default direction
            chargeDirectionY = 0.0f;
        }

        isCharging = true;
        chargeTimer = 0.0f;
        currentState = BossState.CHARGING;
        //System.out.println("Boss starts charging!");
    }

    /**
     * Start spin ability
     */
    private void startSpin() {
        isSpinning = true;
        spinTimer = 0.0f;
        currentState = BossState.SPINNING;
        System.out.println("Boss starts spinning!");
    }

    /**
     * Start summoning ability
     */
    private void startSummoning() {
        isSummoning = true;
        summonTimer = 0.0f;
        currentState = BossState.SUMMONING;
        System.out.println("Boss starts summoning minions!");
    }

    /**
     * Summon minions around the boss
     */
    private void summonMinions() {
        if (dungeon == null || !isAlive()) return;

        System.out.println("BOSS: Szörnyeket hívok segítségül!");

        int minionsToSpawn = 2;
        float spawnRadius = 80.0f;

        // 💡 SYNCHRONIZED blokk a biztonságos hozzáadáshoz
        synchronized(dungeon.getEnemies()) {
            for (int i = 0; i < minionsToSpawn; i++) {
                try {
                    double angle = (2 * Math.PI * i) / minionsToSpawn;
                    float spawnX = getX() + (float)(Math.cos(angle) * spawnRadius);
                    float spawnY = getY() + (float)(Math.sin(angle) * spawnRadius);

                    if (!checkCollision(spawnX, spawnY)) {
                    	Texture minionSpawnTexture = minionTexture != null ? minionTexture : texture;
                        Enemy minion = new Enemy(
                                spawnX, spawnY,
                                40, 40,
                                minionSpawnTexture,
                                30.0f,
                                textRenderer, dungeon, collisionManager, targetPlayer
                        );

                        dungeon.getEnemies().add(minion);
                        System.out.println("Minion spawned at: " + spawnX + ", " + spawnY);
                    }
                } catch (Exception e) {
                    System.err.println("Hiba minion spawn: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Check if boss should become enraged
     */
    private void checkEnrageState() {
        if (!isEnraged && getHealth() / getInitialHealth() <= enrageThreshold) {
            isEnraged = true;
            showEnrageEffect = true;
            enrageEffectTimer = 0.0f;
            System.out.println("BOSS IS ENRAGED!");
        }
    }

    /**
     * Update visual effects
     */
    private void updateVisualEffects(float deltaTime) {
        if (showEnrageEffect) {
            enrageEffectTimer += deltaTime;
            if (enrageEffectTimer >= ENRAGE_EFFECT_DURATION) {
                showEnrageEffect = false;
            }
        }
    }

    /**
     * Check collision with player
     */
    private boolean checkPlayerCollision() {
        if (targetPlayer == null) return false;

        return (getX() < targetPlayer.getX() + targetPlayer.getWidth() &&
                getX() + getWidth() > targetPlayer.getX() &&
                getY() < targetPlayer.getY() + targetPlayer.getHeight() &&
                getY() + getHeight() > targetPlayer.getY());
    }

    @Override
    public void render() {
        // Apply enrage color effect
        if (isEnraged) {
            glColor3f(1.0f, 0.3f, 0.3f); // Red tint when enraged
        } else if (showEnrageEffect) {
            // Flashing effect during enrage transition
            float flash = (float) Math.sin(enrageEffectTimer * 20.0f) * 0.5f + 0.5f;
            glColor3f(1.0f, flash, flash);
        }

        // Call parent render
        super.render();

        // Reset color
        glColor3f(1.0f, 1.0f, 1.0f);

        // Render ability indicators
        renderAbilityIndicators();
    }

    /**
     * Render visual indicators for current abilities
     */
    private void renderAbilityIndicators() {
        glDisable(GL_TEXTURE_2D);

        switch (currentState) {
            case CHARGING:
                // Charge direction indicator
                glColor3f(1.0f, 0.0f, 0.0f);
                glBegin(GL_LINES);
                glVertex2f(getX() + getWidth() / 2, getY() + getHeight() / 2);
                glVertex2f(getX() + getWidth() / 2 + chargeDirectionX * 50,
                        getY() + getHeight() / 2 + chargeDirectionY * 50);
                glEnd();
                break;

            case SPINNING:
                // Spin radius indicator
                glColor3f(1.0f, 1.0f, 0.0f);
                glBegin(GL_LINE_LOOP);
                int segments = 16;
                float radius = Math.max(getWidth(), getHeight()) * 0.8f;
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / segments);
                    glVertex2f(getX() + getWidth() / 2 + (float) Math.cos(angle) * radius,
                            getY() + getHeight() / 2 + (float) Math.sin(angle) * radius);
                }
                glEnd();
                break;

            case SUMMONING:
                // Summoning circle
                glColor3f(0.0f, 1.0f, 1.0f);
                glBegin(GL_LINE_LOOP);
                int summonSegments = 32;
                float summonRadius = Math.max(getWidth(), getHeight()) * 1.2f;
                for (int i = 0; i < summonSegments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / summonSegments);
                    glVertex2f(getX() + getWidth() / 2 + (float) Math.cos(angle) * summonRadius,
                            getY() + getHeight() / 2 + (float) Math.sin(angle) * summonRadius);
                }
                glEnd();
                break;
        }

        glEnable(GL_TEXTURE_2D);
        glColor3f(1.0f, 1.0f, 1.0f);
    }

    @Override
    public void takeDamage(float damage) {
        // Boss takes reduced damage when charging or spinning
        float actualDamage = damage;
        if (currentState == BossState.CHARGING || currentState == BossState.SPINNING) {
            actualDamage *= 0.5f; // 50% damage reduction
        }

        super.takeDamage(actualDamage);

        // Show crit indicator occasionally
        if (random.nextFloat() < 0.3f) {
            activateCritIndicator();
        }
    }

    // --- ENUMS ---

    private enum BossState {
        NORMAL,
        CHARGING,
        SPINNING,
        SUMMONING
    }

    private enum MovementPattern {
        RANDOM,
        CIRCLE_PLAYER,
        APPROACH,
        RETREAT
    }

    /**
     * Klónozás metódus - csak a legszükségesebb adatok másolása
     */
    /**
     * Klónozás metódus - csak a legszükségesebb adatok másolása
     */
    public Boss clone() {
        Boss clone = new Boss(
                this.x, this.y,
                this.width, this.height,
                this.texture,
                this.maxHealth,
                this.textRenderer,
                this.dungeon,
                this.collisionManager,
                this.targetPlayer
        );

        // CSAK A LÉNYEGES ÁLLAPOTOK - getter/setter használata
        clone.setHealth(this.getHealth());
        clone.setMoveSpeed(this.getMoveSpeed());
        clone.setAttackDamage(this.getAttackDamage());

        return clone;
    }

    // --- GETTERS FOR EXTERNAL USE ---

    public float getDamage(){
        return attackDamage;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public boolean isSpinning() {
        return isSpinning;
    }

    public boolean isSummoning() {
        return isSummoning;
    }

    public boolean isEnraged() {
        return isEnraged;
    }

    public BossState getCurrentState() {
        return currentState;
    }

    public void setMoveSpeed(float speed) {
        this.moveSpeed = speed;
    }

    public void setAttackDamage(float damage) {
        this.attackDamage = damage;
    }

    @Override
    public float getInitialHealth() {
        return this.maxHealth;
    }
}