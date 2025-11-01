package physics;

import core.*;
import entities.Entity;
import entities.Enemy;
import entities.Player;
import world.Dungeon;
import world.Tile;
import java.util.*;

/**
 * Kezeli az entitások közötti ütközéseket és a falakkal való ütközések feloldását.
 */
public class CollisionManager {

    private Dungeon dungeon;
    private GameState gameState;
    private MultiplayerGameServer server;

    public CollisionManager(Dungeon dungeon) {
        this.dungeon = dungeon;
    }

    public CollisionManager(GameState gameState) {
        this.gameState = gameState;
    }

    public CollisionManager(GameState gameState, MultiplayerGameServer server) {
        this.gameState = gameState;
        this.server = server;
    }

    public void setServer(MultiplayerGameServer server) {
        this.server = server;
    }

    /**
     * Ellenőrzi, hogy két entitás metszi-e egymást a hitboxuk alapján.
     */
    public boolean checkCollision(Entity a, Entity b) {
        if (a == null || b == null) return false;
        return checkBoundsIntersection(a.getBounds(), b.getBounds());
    }

    /**
     * Ellenőrzi, hogy egy entitás ütközik-e egy adott csempével.
     */
    public boolean checkTileCollision(Entity entity, Tile tile, int tileX, int tileY) {
        if (entity == null || tile == null || dungeon == null) return false;
        physics.Rectangle tileBounds = getTileBounds(tileX, tileY, dungeon.getTileSize());
        return checkBoundsIntersection(entity.getBounds(), tileBounds);
    }

    /**
     * Általános bounds intersection ellenőrzés
     */
    private boolean checkBoundsIntersection(physics.Rectangle rect1, physics.Rectangle rect2) {
        if (rect1 == null || rect2 == null) return false;
        return rect1.x < rect2.x + rect2.width &&
                rect1.x + rect1.width > rect2.x &&
                rect1.y < rect2.y + rect2.height &&
                rect1.y + rect1.height > rect2.y;
    }

    /**
     * Tile bounds kiszámítása koordináták alapján
     */
    private physics.Rectangle getTileBounds(int tileX, int tileY, int tileSize) {
        return new physics.Rectangle(tileX * tileSize, tileY * tileSize, tileSize, tileSize);
    }

    // --- SINGLEPLAYER TILE COLLISION ---
    private boolean checkEntityCollidesWithSolidTiles(Entity entity, Dungeon dungeon) {
        if (entity == null || dungeon == null) return false;

        physics.Rectangle entityBounds = entity.getBounds();
        int tileSize = dungeon.getTileSize();

        int minTileX = (int) Math.floor(entityBounds.x / tileSize);
        int maxTileX = (int) Math.floor((entityBounds.x + entityBounds.width) / tileSize);
        int minTileY = (int) Math.floor(entityBounds.y / tileSize);
        int maxTileY = (int) Math.floor((entityBounds.y + entityBounds.height) / tileSize);

        if (minTileX > maxTileX || minTileY > maxTileY) {
            return false;
        }

        int dungeonWidth = dungeon.getWidthTiles();
        int dungeonHeight = dungeon.getHeightTiles();

        minTileX = Math.max(0, minTileX);
        maxTileX = Math.min(dungeonWidth - 1, maxTileX);
        minTileY = Math.max(0, minTileY);
        maxTileY = Math.min(dungeonHeight - 1, maxTileY);

        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                Tile tile = dungeon.getTile(x, y);
                if (tile != null && tile.isSolid()) {
                    physics.Rectangle tileBounds = getTileBounds(x, y, tileSize);
                    if (checkBoundsIntersection(entityBounds, tileBounds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- MULTIPLAYER-SPECIFIC PLAYER COLLISION ---
    private boolean checkMultiplayerPlayerCollision(Entity entity, Dungeon dungeon) {
        if (entity == null || dungeon == null || !(entity instanceof Player)) return false;

        physics.Rectangle originalBounds = entity.getBounds();
        int tileSize = dungeon.getTileSize();

        // MULTIPLAYER: Kisebb collision box a playernek, hogy ne ütközzön az alatta lévő BOX-okkal
        float playerHeight = 30f; // Kisebb magasság multiplayerben
        physics.Rectangle multiplayerBounds = new physics.Rectangle(
                originalBounds.x,
                originalBounds.y + (originalBounds.height - playerHeight), // Offset alulról
                originalBounds.width,
                playerHeight
        );

        int minTileX = (int) Math.floor(multiplayerBounds.x / tileSize);
        int maxTileX = (int) Math.floor((multiplayerBounds.x + multiplayerBounds.width) / tileSize);
        int minTileY = (int) Math.floor(multiplayerBounds.y / tileSize);
        int maxTileY = (int) Math.floor((multiplayerBounds.y + multiplayerBounds.height) / tileSize);

        if (minTileX > maxTileX || minTileY > maxTileY) {
            return false;
        }

        int dungeonWidth = dungeon.getWidthTiles();
        int dungeonHeight = dungeon.getHeightTiles();

        minTileX = Math.max(0, minTileX);
        maxTileX = Math.min(dungeonWidth - 1, maxTileX);
        minTileY = Math.max(0, minTileY);
        maxTileY = Math.min(dungeonHeight - 1, maxTileY);

        for (int y = minTileY; y <= maxTileY; y++) {
            for (int x = minTileX; x <= maxTileX; x++) {
                Tile tile = dungeon.getTile(x, y);
                if (tile != null && tile.isSolid()) {
                    // MULTIPLAYER: BOX-okkal ne ütközzön a player (opcionális)
                    if (tile.getType() != null && tile.getType().equals("BOX")) {
                        continue;
                    }

                    physics.Rectangle tileBounds = getTileBounds(x, y, tileSize);
                    if (checkBoundsIntersection(multiplayerBounds, tileBounds)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- ÁLTALÁNOS ÜTKÖZÉSFELOLDÁS ---
    private void resolveEntityTileCollision(Entity entity, float prevX, float prevY) {
        if (entity == null || dungeon == null) return;

        float newX = entity.getX();
        float newY = entity.getY();

        entity.setX(prevX);
        entity.setY(prevY);

        entity.setX(newX);
        if (checkEntityCollidesWithSolidTiles(entity, dungeon)) {
            entity.setX(prevX);
        }

        float resolvedX = entity.getX();
        entity.setY(newY);
        if (checkEntityCollidesWithSolidTiles(entity, dungeon)) {
            entity.setY(prevY);
        }
    }

    // --- MULTIPLAYER PLAYER COLLISION RESOLUTION ---
    private void resolveMultiplayerPlayerTileCollision(Player player, float prevX, float prevY) {
        if (player == null || dungeon == null) return;

        float newX = player.getX();
        float newY = player.getY();

        player.setX(prevX);
        player.setY(prevY);

        player.setX(newX);
        if (checkMultiplayerPlayerCollision(player, dungeon)) {
            player.setX(prevX);
        }

        player.setY(newY);
        if (checkMultiplayerPlayerCollision(player, dungeon)) {
            player.setY(prevY);
        }
    }

    /**
     * Feloldja a játékos és a falak közötti ütközéseket.
     */
    public void resolvePlayerTileCollisions(Player player, float prevX, float prevY) {
        if (gameState != null) {
            // MULTIPLAYER - speciális collision
            resolveMultiplayerPlayerTileCollision(player, prevX, prevY);
        } else {
            // SINGLEPLAYER - normál collision
            resolveEntityTileCollision(player, prevX, prevY);
        }
    }

    /**
     * Feloldja az ellenség és a falak közötti ütközéseket.
     */
    public void resolveEnemyTileCollisions(Enemy enemy, float prevX, float prevY) {
        resolveEntityTileCollision(enemy, prevX, prevY);
    }

    // --- MULTIPLAYER ÜTKÖZÉSEK ---
    public void checkAllCollisions() {
        if (gameState != null) {
            checkProjectileCollisions();
            checkPlayerEnemyCollisions();
            checkPlayerCollisions();
        }
    }

    private void checkProjectileCollisions() {
        if (gameState == null) return;

        List<ProjectileState> projectilesToRemove = new ArrayList<>();

        for (ProjectileState projectile : gameState.getProjectileStates()) {
            if (!projectile.isActive()) continue;

            for (EnemyState enemy : gameState.getEnemyStates()) {
                if (!enemy.isAlive()) continue;

                if (isColliding(projectile, enemy)) {
                    handleProjectileHit(projectile, enemy);
                    projectilesToRemove.add(projectile);
                    break;
                }
            }

            for (PlayerState player : gameState.getPlayerStates().values()) {
                if (!player.isAlive() || player.getPlayerId() == projectile.getOwnerPlayerId()) continue;

                if (isColliding(projectile, player)) {
                    handlePlayerHit(projectile, player);
                    projectilesToRemove.add(projectile);
                    break;
                }
            }

            if (isOutOfBounds(projectile)) {
                projectilesToRemove.add(projectile);
                broadcastEvent("PROJECTILE_WALL:" + projectile.getProjectileId());
            }
        }

        gameState.getProjectileStates().removeAll(projectilesToRemove);
    }

    private boolean isColliding(ProjectileState projectile, EnemyState enemy) {
        float dx = projectile.getX() - enemy.getX();
        float dy = projectile.getY() - enemy.getY();
        return Math.sqrt(dx * dx + dy * dy) < 50.0f;
    }

    private boolean isColliding(ProjectileState projectile, PlayerState player) {
        float dx = projectile.getX() - player.getX();
        float dy = projectile.getY() - player.getY();
        return Math.sqrt(dx * dx + dy * dy) < 45.0f;
    }

    private boolean isColliding(PlayerState player1, PlayerState player2) {
        float dx = player1.getX() - player2.getX();
        float dy = player1.getY() - player2.getY();
        return Math.sqrt(dx * dx + dy * dy) < 80.0f;
    }

    private boolean isColliding(PlayerState player, EnemyState enemy) {
        float dx = player.getX() - enemy.getX();
        float dy = player.getY() - enemy.getY();
        return Math.sqrt(dx * dx + dy * dy) < 80.0f;
    }

    private boolean isOutOfBounds(ProjectileState projectile) {
        return projectile.getX() < 0 || projectile.getX() > 2000 ||
                projectile.getY() < 0 || projectile.getY() > 2000;
    }

    private void handleProjectileHit(ProjectileState projectile, EnemyState enemy) {
        float damage = projectile.getDamage();
        enemy.setHealth(enemy.getHealth() - damage);

        System.out.println("Projectile " + projectile.getProjectileId() +
                " hit enemy " + enemy.getEnemyId() + " for " + damage + " damage");

        if (enemy.getHealth() <= 0) {
            enemy.setAlive(false);
            System.out.println("Enemy " + enemy.getEnemyId() + " defeated");
        }

        broadcastEvent("PROJECTILE_ENEMY:" +
                projectile.getProjectileId() + ":" +
                enemy.getEnemyId() + ":" +
                damage + ":" +
                enemy.getHealth() + ":" +
                enemy.isAlive());
    }

    private void handlePlayerHit(ProjectileState projectile, PlayerState player) {
        float damage = projectile.getDamage();
        player.setHealth(player.getHealth() - damage);

        System.out.println("Projectile " + projectile.getProjectileId() +
                " hit player " + player.getPlayerId() + " for " + damage + " damage");

        if (player.getHealth() <= 0) {
            player.setAlive(false);
            System.out.println("Player " + player.getPlayerId() + " died");
        }

        broadcastEvent("PROJECTILE_PLAYER:" +
                projectile.getProjectileId() + ":" +
                player.getPlayerId() + ":" +
                damage + ":" +
                player.getHealth() + ":" +
                player.isAlive());
    }

    private void checkPlayerEnemyCollisions() {
        if (gameState == null) return;

        for (PlayerState player : gameState.getPlayerStates().values()) {
            if (!player.isAlive()) continue;

            for (EnemyState enemy : gameState.getEnemyStates()) {
                if (!enemy.isAlive()) continue;

                if (isColliding(player, enemy)) {
                    handlePlayerEnemyCollision(player, enemy);
                }
            }
        }
    }

    private void handlePlayerEnemyCollision(PlayerState player, EnemyState enemy) {
        float damage = 10.0f;
        player.setHealth(player.getHealth() - damage);

        System.out.println("Enemy " + enemy.getEnemyId() +
                " hit player " + player.getPlayerId() + " for " + damage + " damage");

        if (player.getHealth() <= 0) {
            player.setAlive(false);
            System.out.println("Player " + player.getPlayerId() + " died");
        }

        broadcastEvent("PLAYER_ENEMY:" +
                player.getPlayerId() + ":" +
                enemy.getEnemyId() + ":" +
                damage + ":" +
                player.getHealth() + ":" +
                player.isAlive());
    }

    private void checkPlayerCollisions() {
        if (gameState == null || gameState.getPlayerStates().size() < 2) return;

        List<PlayerState> players = new ArrayList<>(gameState.getPlayerStates().values());

        for (int i = 0; i < players.size(); i++) {
            PlayerState player1 = players.get(i);
            if (!player1.isAlive()) continue;

            for (int j = i + 1; j < players.size(); j++) {
                PlayerState player2 = players.get(j);
                if (!player2.isAlive()) continue;

                if (isColliding(player1, player2)) {
                    handlePlayerCollision(player1, player2);
                }
            }
        }
    }

    private void handlePlayerCollision(PlayerState player1, PlayerState player2) {
        System.out.println("Players " + player1.getPlayerId() + " and " + player2.getPlayerId() + " collided");

        float dx = player1.getX() - player2.getX();
        float dy = player1.getY() - player2.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 0.001f) {
            float pushDistance = 5.0f;
            float pushX = (dx / distance) * pushDistance;
            float pushY = (dy / distance) * pushDistance;

            player1.setX(player1.getX() + pushX);
            player1.setY(player1.getY() + pushY);
            player2.setX(player2.getX() - pushX);
            player2.setY(player2.getY() - pushY);
        }

        broadcastEvent("PLAYER_PLAYER:" + player1.getPlayerId() + ":" + player2.getPlayerId());
    }

    private void broadcastEvent(String message) {
        if (server != null) {
            server.broadcastCollisionEvent(message);
        }
    }

    // --- TOVÁBBI SEGÉDMETÓDUSOK ---
    public void cleanupInactiveEntities() {
        if (gameState != null) {
            gameState.getProjectileStates().removeIf(p -> !p.isActive());
            gameState.getEnemyStates().removeIf(e -> !e.isAlive());
        }
    }

    public void resetAllCollisions() {
        if (gameState != null) {
            gameState.getProjectileStates().clear();
            for (EnemyState e : gameState.getEnemyStates()) {
                e.setAlive(true);
                e.setHealth(100.0f);
            }
            for (PlayerState p : gameState.getPlayerStates().values()) {
                p.setAlive(true);
                p.setHealth(100.0f);
            }
        }
    }

    public void runCollisionTest() {
        System.out.println("COLLISION TEST RUNNING...");
        if (gameState != null) {
            System.out.println("Players: " + gameState.getPlayerStates().size());
            System.out.println("Enemies: " + gameState.getEnemyStates().size());
            System.out.println("Projectiles: " + gameState.getProjectileStates().size());
        }
        System.out.println("COLLISION TEST COMPLETE");
    }

    public List<String> checkEnvironmentCollisions(float x, float y, float radius) {
        List<String> collisions = new ArrayList<>();
        if (gameState == null) return collisions;

        for (EnemyState e : gameState.getEnemyStates()) {
            if (!e.isAlive()) continue;
            float d = (float) Math.hypot(x - e.getX(), y - e.getY());
            if (d < radius + 40.0f) {
                collisions.add("ENEMY:" + e.getEnemyId() + ":" + d);
            }
        }

        for (PlayerState p : gameState.getPlayerStates().values()) {
            if (!p.isAlive() || (x == p.getX() && y == p.getY())) continue;
            float d = (float) Math.hypot(x - p.getX(), y - p.getY());
            if (d < radius + 40.0f) {
                collisions.add("PLAYER:" + p.getPlayerId() + ":" + d);
            }
        }
        return collisions;
    }

    public Map<String, Object> getCollisionReport() {
        Map<String, Object> report = new HashMap<>();
        if (gameState != null) {
            report.put("totalProjectiles", gameState.getProjectileStates().size());
            report.put("totalEnemies", gameState.getEnemyStates().size());
            report.put("totalPlayers", gameState.getPlayerStates().size());

            long active = gameState.getProjectileStates().stream().filter(ProjectileState::isActive).count();
            long aliveE = gameState.getEnemyStates().stream().filter(EnemyState::isAlive).count();
            long aliveP = gameState.getPlayerStates().values().stream().filter(PlayerState::isAlive).count();

            report.put("activeProjectiles", active);
            report.put("aliveEnemies", aliveE);
            report.put("alivePlayers", aliveP);
        }
        report.put("timestamp", System.currentTimeMillis());
        return report;
    }

    public void printCollisionStats() {
        System.out.println("=== COLLISION MANAGER STATS ===");
        if (gameState != null) {
            System.out.println("Players: " + gameState.getPlayerStates().size());
            System.out.println("Enemies: " + gameState.getEnemyStates().size());
            System.out.println("Projectiles: " + gameState.getProjectileStates().size());
            System.out.println("Alive Enemies: " + gameState.getEnemyStates().stream().filter(EnemyState::isAlive).count());
            System.out.println("Active Projectiles: " + gameState.getProjectileStates().stream().filter(ProjectileState::isActive).count());
            System.out.println("Alive Players: " + gameState.getPlayerStates().values().stream().filter(PlayerState::isAlive).count());
        } else {
            System.out.println("GameState: NULL");
        }
        System.out.println("===============================");
    }

    public void cleanup() { cleanupInactiveEntities(); }
    public void printStats() { printCollisionStats(); }
}