package core;

import java.util.*;
import java.util.concurrent.*;

public class GameState {
    private Map<Integer, PlayerState> playerStates;
    private List<EnemyState> enemyStates;
    private List<ProjectileState> projectileStates;
    private List<EffectState> effectStates;
    private boolean dungeonGenerated;
    private boolean bossDefeated;
    private int nextProjectileId;
    private int nextEffectId;
    private physics.CollisionManager collisionManager;
    private MultiplayerGameServer server;

    public GameState() {
        this.playerStates = new ConcurrentHashMap<>();
        this.enemyStates = new CopyOnWriteArrayList<>();
        this.projectileStates = new CopyOnWriteArrayList<>();
        this.effectStates = new CopyOnWriteArrayList<>();
        this.dungeonGenerated = false;
        this.bossDefeated = false;
        this.nextProjectileId = 1;
        this.nextEffectId = 1;
    }

    // ✨ COLLISION MANAGER METÓDUSOK - EZEK HIÁNYOZNAK
    public void initializeCollisionManager() {
        this.collisionManager = new physics.CollisionManager(this);
        System.out.println("✅ CollisionManager initialized for GameState");
    }

    public physics.CollisionManager getCollisionManager() {
        return collisionManager;
    }

    public void setServer(MultiplayerGameServer server) {
        this.server = server;
        if (collisionManager != null) {
            collisionManager.setServer(server);
        }
    }

    public MultiplayerGameServer getServer() {
        return server;
    }

    // PlayerState metódusok
    public void addPlayerState(int playerId, PlayerState playerState) {
        playerStates.put(playerId, playerState);
    }

    public void removePlayerState(int playerId) {
        playerStates.remove(playerId);
    }

    public PlayerState getPlayerState(int playerId) {
        return playerStates.get(playerId);
    }

    public Map<Integer, PlayerState> getPlayerStates() {
        return playerStates;
    }

    public void updatePlayerPosition(int playerId, float x, float y) {
        PlayerState player = playerStates.get(playerId);
        if (player != null) {
            player.setX(x);
            player.setY(y);
        }
    }

    // EnemyState metódusok
    public List<EnemyState> getEnemyStates() {
        return enemyStates;
    }

    public void addEnemyState(EnemyState enemy) {
        enemyStates.add(enemy);
    }

    public EnemyState getEnemyState(int enemyId) {
        for (EnemyState enemy : enemyStates) {
            if (enemy.getEnemyId() == enemyId) {
                return enemy;
            }
        }
        return null;
    }

    // ProjectileState metódusok
    public List<ProjectileState> getProjectileStates() {
        return projectileStates;
    }

    public void addProjectileState(ProjectileState projectile) {
        projectileStates.add(projectile);
    }

    public int getNextProjectileId() {
        return nextProjectileId++;
    }

    // EffectState metódusok
    public List<EffectState> getEffectStates() {
        return effectStates;
    }

    public void addEffectState(EffectState effect) {
        effectStates.add(effect);
    }

    public int getNextEffectId() {
        return nextEffectId++;
    }

    // Egyéb metódusok
    public boolean isDungeonGenerated() { return dungeonGenerated; }
    public void setDungeonGenerated(boolean dungeonGenerated) { this.dungeonGenerated = dungeonGenerated; }
    public boolean isBossDefeated() { return bossDefeated; }
    public void setBossDefeated(boolean bossDefeated) { this.bossDefeated = bossDefeated; }

    // Serializáció játékállapothoz
    public String serializeGameState() {
        StringBuilder sb = new StringBuilder();

        // Players
        sb.append("PLAYERS:");
        for (PlayerState player : playerStates.values()) {
            sb.append(player.serialize()).append("|");
        }
        if (!playerStates.isEmpty()) sb.setLength(sb.length() - 1); // Remove last |

        // Enemies
        sb.append(";ENEMIES:");
        for (EnemyState enemy : enemyStates) {
            sb.append(enemy.serialize()).append("|");
        }
        if (!enemyStates.isEmpty()) sb.setLength(sb.length() - 1);

        // Projectiles
        sb.append(";PROJECTILES:");
        for (ProjectileState projectile : projectileStates) {
            sb.append(projectile.serialize()).append("|");
        }
        if (!projectileStates.isEmpty()) sb.setLength(sb.length() - 1);

        // Effects
        sb.append(";EFFECTS:");
        for (EffectState effect : effectStates) {
            sb.append(effect.serialize()).append("|");
        }
        if (!effectStates.isEmpty()) sb.setLength(sb.length() - 1);

        return sb.toString();
    }

    // ✨ JAVÍTOTT: Részletesebb logolás
    public void logEnemyStatus() {
        try {
            System.out.println("=== 🎯 ENEMY STATUS CHECK ===");
            System.out.println("📊 Total enemies in list: " + enemyStates.size());

            if (enemyStates.isEmpty()) {
                System.out.println("⚠️  No enemies in the list!");
                return;
            }

            int aliveCount = 0;
            int deadCount = 0;

            for (int i = 0; i < enemyStates.size(); i++) {
                EnemyState enemy = enemyStates.get(i);
                String status = enemy.isAlive() ? "ALIVE 🟢" : "DEAD 🔴";
                System.out.println("👹 Enemy [" + i + "] - ID: " + enemy.getEnemyId() +
                        " | Type: " + enemy.getEnemyType() +
                        " | Pos: " + String.format("%.1f", enemy.getX()) + "," + String.format("%.1f", enemy.getY()) +
                        " | HP: " + String.format("%.1f", enemy.getHealth()) + "/" + String.format("%.1f", enemy.getMaxHealth()) +
                        " | " + status);

                if (enemy.isAlive()) {
                    aliveCount++;
                } else {
                    deadCount++;
                }
            }

            System.out.println("📈 Summary: " + aliveCount + " alive, " + deadCount + " dead");
            System.out.println("=================================");

        } catch (Exception e) {
            System.err.println("❌ ERROR in logEnemyStatus: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Cleanup inaktív entitások
    public void cleanupInactiveEntities() {
        // Inaktív lövedékek
        projectileStates.removeIf(projectile -> !projectile.isActive());

        // Halott ellenségek
        enemyStates.removeIf(enemy -> !enemy.isAlive());

        // Inaktív effektek
        effectStates.removeIf(effect -> !effect.isActive());
    }

    // Reset játék állapot
    public void resetGameState() {
        projectileStates.clear();
        effectStates.clear();

        // Ellenségek életének visszaállítása
        for (EnemyState enemy : enemyStates) {
            enemy.setAlive(true);
            enemy.setHealth(enemy.getMaxHealth());
        }

        // Játékosok életének visszaállítása
        for (PlayerState player : playerStates.values()) {
            player.setAlive(true);
            player.setHealth(player.getMaxHealth());
            player.setX(100.0f); // Alap pozíció
            player.setY(100.0f);
        }
    }
}