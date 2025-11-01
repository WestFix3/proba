package world;

import entities.Enemy;
import java.util.List;

public class Dungeon {
    private Tile[][] tiles;
    private int tileSize;
    private List<Enemy> enemies;

    private float playerSpawnX, playerSpawnY;

    public final List<List<Tile>> gateCorridorGroups;

    private int bossRoomGridX;
    private int bossRoomGridY;
    private int bossRoomWidth;
    private int bossRoomHeight;


    public Dungeon(Tile[][] tiles, int tileSize, List<Enemy> enemies, float playerSpawnX, float playerSpawnY, List<List<Tile>> gateCorridorGroups, int bossRoomGridX, int bossRoomGridY, int bossRoomWidth, int bossRoomHeight) {
        this.tiles = tiles;
        this.tileSize = tileSize;
        this.enemies = enemies;
        this.playerSpawnX = playerSpawnX;
        this.playerSpawnY = playerSpawnY;
        this.gateCorridorGroups = gateCorridorGroups;
        this.bossRoomGridX = bossRoomGridX;
        this.bossRoomGridY = bossRoomGridY;
        this.bossRoomWidth = bossRoomWidth;
        this.bossRoomHeight = bossRoomHeight;
    }

    // ÚJ: getTile metódus hozzáadása
    public Tile getTile(int x, int y) {
        if (x >= 0 && x < tiles.length && y >= 0 && y < tiles[0].length) {
            return tiles[x][y];
        }
        return null; // Visszatér null-lal, ha a koordináták érvénytelenek
    }

    public Tile[][] getTiles() {
        return tiles;
    }

    public int getWidthTiles() {
        return tiles.length;
    }

    public int getHeightTiles() {
        return tiles[0].length;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public float getPlayerSpawnX() {
        return playerSpawnX;
    }

    public float getPlayerSpawnY() {
        return playerSpawnY;
    }

    public int getTileSize() {
        return tileSize;
    }

    // Új getterek
    public int getBossRoomGridX() {
        return bossRoomGridX;
    }

    public int getBossRoomGridY() {
        return bossRoomGridY;
    }

    public int getBossRoomWidth() {
        return bossRoomWidth;
    }

    public int getBossRoomHeight() {
        return bossRoomHeight;
    }

    public void cleanup() {
        System.out.println("DEBUG: Dungeon tisztítása...");
        // A csempék (tiles) erőforrásainak felszabadítása
        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[0].length; y++) {
                if (tiles[x][y] != null) {
                    tiles[x][y].cleanup(); // Feltételezi, hogy a Tile osztálynak is van cleanup() metódusa
                }
            }
        }

        // Az ellenségek erőforrásainak felszabadítása
        if (enemies != null) {
            for (Enemy enemy : enemies) {
                enemy.cleanup(); // Feltételezi, hogy az Enemy osztálynak is van cleanup() metódusa
            }
            enemies.clear();
        }

        // A listák nullázása a memóriafelszabadítás érdekében
        tiles = null;
        enemies = null;
        gateCorridorGroups.clear();
    }
}