package world;

import entities.Enemy;
import entities.Boss;
import physics.CollisionManager;
import rendering.Texture;
import rendering.TextRenderer;
import entities.weapons.WeaponFactory;
import entities.Effect;

import java.util.*;

public class DungeonGenerator {

    private static int enemyIdCounter = 0;

    // ✨ ÚJ: Seed-el ellátott verzió
    public static Dungeon generateRandomDungeonWithSeed(
            long seed,
            int tileSize,
            Map<Tile.TileType, Texture> tileTextures,
            Texture enemyTexture,
            Map<Integer, Texture> boxDamageTextures,
            Map<Integer, Texture> gateAnimationTextures,
            Texture weaponCrateTexture,
            Texture openCrateTexture,
            Texture emptyCrateTexture,
            WeaponFactory weaponFactory,
            TextRenderer textRenderer,
            Map<Effect.EffectType, Texture> effectTextures,
            Texture teleportPadTexture) {

        System.out.println("🎲 DUNGEON GENERÁLÁS SEED-DEL: " + seed);

        final Random random = new Random(seed);

        return generateRandomDungeonImplementation(
                tileSize, tileTextures, enemyTexture, boxDamageTextures,
                gateAnimationTextures, weaponCrateTexture, openCrateTexture,
                emptyCrateTexture, weaponFactory, textRenderer, effectTextures,
                teleportPadTexture, random, seed
        );
    }

    private static Dungeon generateRandomDungeonImplementation(
            int tileSize,
            Map<Tile.TileType, Texture> tileTextures,
            Texture enemyTexture,
            Map<Integer, Texture> boxDamageTextures,
            Map<Integer, Texture> gateAnimationTextures,
            Texture weaponCrateTexture,
            Texture openCrateTexture,
            Texture emptyCrateTexture,
            WeaponFactory weaponFactory,
            TextRenderer textRenderer,
            Map<Effect.EffectType, Texture> effectTextures,
            Texture teleportPadTexture,
            Random random, long seed) {

        enemyIdCounter = 0;
        final int TILE_SIZE = tileSize;

        gateCorridorGroups.clear();

        List<Room> rooms = new ArrayList<>();
        List<Enemy> enemies = new java.util.concurrent.CopyOnWriteArrayList<>();
        Set<OccupiedTile> occupiedPositions = new HashSet<>();

        Map<Integer, Texture> gateDestructionTextures = gateAnimationTextures;

        int maxGridWidthTiles = ROOM_GRID_COLS * ROOM_GRID_CELL_TILE_WIDTH;
        int maxGridHeightTiles = ROOM_GRID_ROWS * ROOM_GRID_CELL_TILE_HEIGHT;

        Tile[][] tempTiles = new Tile[maxGridWidthTiles][maxGridHeightTiles];

        for (int x = 0; x < maxGridWidthTiles; x++) {
            for (int y = 0; y < maxGridHeightTiles; y++) {
                tempTiles[x][y] = new Tile(Tile.TileType.WALL, x, y, TILE_SIZE, tileTextures.get(Tile.TileType.WALL));
            }
        }

        Map<String, Room> roomGridMap = new HashMap<>();

        int numRoomsToGenerate = random.nextInt(5) + 10;
        int spawnGridCol = random.nextInt(ROOM_GRID_COLS);
        int spawnGridRow = random.nextInt(ROOM_GRID_ROWS);

        int spawnRoomX = spawnGridCol * ROOM_GRID_CELL_TILE_WIDTH + random.nextInt(ROOM_GRID_CELL_TILE_WIDTH - MAX_ROOM_WIDTH_TILES);
        int spawnRoomY = spawnGridRow * ROOM_GRID_CELL_TILE_HEIGHT + random.nextInt(ROOM_GRID_CELL_TILE_HEIGHT - MAX_ROOM_HEIGHT_TILES);

        // ✨ JAVÍTOTT: Seed-et adunk a Room-nak
        Room spawnRoom = new Room(spawnRoomX, spawnRoomY, MAX_ROOM_WIDTH_TILES, MAX_ROOM_HEIGHT_TILES, Room.RoomType.SPAWN, seed + 1);
        rooms.add(spawnRoom);
        roomGridMap.put(spawnGridCol + "," + spawnGridRow, spawnRoom);

        for (int x = spawnRoom.getGridX() + 1; x < spawnRoom.getGridX() + spawnRoom.getWidth() - 1; x++) {
            for (int y = spawnRoom.getGridY() + 1; y < spawnRoom.getGridY() + spawnRoom.getHeight() - 1; y++) {
                if (isValidGridCoord(x, y, tempTiles)) {
                    tempTiles[x][y] = new Tile(Tile.TileType.FLOOR, x, y, TILE_SIZE, tileTextures.get(Tile.TileType.FLOOR));
                }
            }
        }

        int minUsedX = spawnRoom.getGridX();
        int maxUsedX = spawnRoom.getGridX() + spawnRoom.getWidth();
        int minUsedY = spawnRoom.getGridY();
        int maxUsedY = spawnRoom.getGridY() + spawnRoom.getHeight();

        Queue<Room> roomsQueue = new LinkedList<>();
        roomsQueue.add(spawnRoom);
        Set<String> visitedRoomGridCells = new HashSet<>();
        visitedRoomGridCells.add(spawnGridCol + "," + spawnGridRow);
        int roomsPlacedCount = 1;

        while (!roomsQueue.isEmpty() && roomsPlacedCount < numRoomsToGenerate) {
            Room current = roomsQueue.poll();

            int currentCol = -1, currentRow = -1;
            for (Map.Entry<String, Room> entry : roomGridMap.entrySet()) {
                if (entry.getValue().equals(current)) {
                    String[] coords = entry.getKey().split(",");
                    currentCol = Integer.parseInt(coords[0]);
                    currentRow = Integer.parseInt(coords[1]);
                    break;
                }
            }
            if (currentCol == -1 || currentRow == -1) continue;

            int[][] directions = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

            // ✨ JAVÍTOTT: Fix sorrend mindig (fel, jobb, le, bal)
            List<int[]> fixedDirections = Arrays.asList(
                    new int[]{0, -1},  // fel
                    new int[]{1, 0},   // jobb
                    new int[]{0, 1},   // le
                    new int[]{-1, 0}   // bal
            );

            for (int[] dir : fixedDirections) {
                if (roomsPlacedCount >= numRoomsToGenerate) break;

                int newGridCol = currentCol + dir[0];
                int newGridRow = currentRow + dir[1];
                String cellKey = newGridCol + "," + newGridRow;

                if (newGridCol >= 0 && newGridCol < ROOM_GRID_COLS &&
                        newGridRow >= 0 && newGridRow < ROOM_GRID_ROWS &&
                        !visitedRoomGridCells.contains(cellKey) && !roomGridMap.containsKey(cellKey)) {

                    int newRoomWidth = MAX_ROOM_WIDTH_TILES;
                    int newRoomHeight = MAX_ROOM_HEIGHT_TILES;

                    int newRoomX = newGridCol * ROOM_GRID_CELL_TILE_WIDTH + random.nextInt(ROOM_GRID_CELL_TILE_WIDTH - newRoomWidth);
                    int newRoomY = newGridRow * ROOM_GRID_CELL_TILE_HEIGHT + random.nextInt(ROOM_GRID_CELL_TILE_HEIGHT - newRoomHeight);

                    if (newRoomX + newRoomWidth < maxGridWidthTiles && newRoomY + newRoomHeight < maxGridHeightTiles) {
                        // ✨ JAVÍTOTT: Seed-et adunk az új Room-nak
                        Room newRoom = new Room(newRoomX, newRoomY, newRoomWidth, newRoomHeight, Room.RoomType.NORMAL, seed + roomsPlacedCount + 1000);
                        rooms.add(newRoom);
                        roomGridMap.put(cellKey, newRoom);
                        visitedRoomGridCells.add(cellKey);

                        newRoom.draw(tempTiles, TILE_SIZE, tileTextures, boxDamageTextures);

                        minUsedX = Math.min(minUsedX, newRoom.getGridX());
                        maxUsedX = Math.max(maxUsedX, newRoom.getGridX() + newRoom.getWidth());
                        minUsedY = Math.min(minUsedY, newRoom.getGridY());
                        maxUsedY = Math.max(maxUsedY, newRoom.getGridY() + newRoom.getHeight());

                        roomsQueue.add(newRoom);
                        roomsPlacedCount++;
                    }
                }
            }
        }

        Map<Room, Integer> distances = new HashMap<>();
        Queue<Room> roomDistanceQueue = new LinkedList<>();

        distances.put(spawnRoom, 0);
        roomDistanceQueue.add(spawnRoom);

        while(!roomDistanceQueue.isEmpty()) {
            Room currentDistRoom = roomDistanceQueue.poll();
            int currentDist = distances.get(currentDistRoom);

            int currentDistCol = -1, currentDistRow = -1;
            for (Map.Entry<String, Room> entry : roomGridMap.entrySet()) {
                if (entry.getValue().equals(currentDistRoom)) {
                    String[] coords = entry.getKey().split(",");
                    currentDistCol = Integer.parseInt(coords[0]);
                    currentDistRow = Integer.parseInt(coords[1]);
                    break;
                }
            }
            if (currentDistCol == -1 || currentDistRow == -1) continue;

            int[][] dirNeighbours = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int[] dir : dirNeighbours) {
                int neighborCol = currentDistCol + dir[0];
                int neighborRow = currentDistRow + dir[1];
                String neighborKey = neighborCol + "," + neighborRow;

                if (roomGridMap.containsKey(neighborKey)) {
                    Room neighborRoom = roomGridMap.get(neighborKey);
                    if (!distances.containsKey(neighborRoom)) {
                        distances.put(neighborRoom, currentDist + 1);
                        roomDistanceQueue.add(neighborRoom);
                    }
                }
            }
        }

        Room bossRoomCandidate = null;
        Room actualBossRoom = null;
        List<Room> potentialBossRooms = new ArrayList<>(rooms);
        potentialBossRooms.sort(Comparator.comparingInt(r -> distances.getOrDefault(r, 0)).reversed());

        for (Room room : potentialBossRooms) {
            if (room.getType() == Room.RoomType.NORMAL) {
                bossRoomCandidate = room;
                break;
            }
        }

        if (bossRoomCandidate == null) {
            bossRoomCandidate = spawnRoom;
            bossRoomCandidate.type = Room.RoomType.BOSS_ROOM;
            actualBossRoom = bossRoomCandidate;
            System.out.println("Warning: No suitable boss room found, using spawn room as fallback boss room.");
        } else {
            rooms.remove(bossRoomCandidate);
            int newBossWidth = (int)(bossRoomCandidate.getWidth() * BOSS_ROOM_SCALE_FACTOR);
            int newBossHeight = (int)(bossRoomCandidate.getHeight() * BOSS_ROOM_SCALE_FACTOR);
            newBossWidth = Math.max(newBossWidth, 3);
            newBossHeight = Math.max(newBossHeight, 3);
            int proposedNewBossX = bossRoomCandidate.getCenterX() - newBossWidth / 2;
            int proposedNewBossY = bossRoomCandidate.getCenterY() - newBossHeight / 2;
            proposedNewBossX = Math.max(0, proposedNewBossX);
            proposedNewBossY = Math.max(0, proposedNewBossY);
            newBossWidth = Math.min(newBossWidth, maxGridWidthTiles - proposedNewBossX);
            newBossHeight = Math.min(newBossHeight, maxGridHeightTiles - proposedNewBossY);

            // ✨ JAVÍTOTT: Boss room seed-del
            actualBossRoom = new Room(proposedNewBossX, proposedNewBossY, newBossWidth, newBossHeight, Room.RoomType.BOSS_ROOM, seed + 9999);

            String bossRoomKey = null;
            for(Map.Entry<String, Room> entry : roomGridMap.entrySet()) {
                if (entry.getValue().equals(bossRoomCandidate)) {
                    bossRoomKey = entry.getKey();
                    break;
                }
            }
            if (bossRoomKey != null) {
                roomGridMap.put(bossRoomKey, actualBossRoom);
            }
            rooms.add(actualBossRoom);

            actualBossRoom.draw(tempTiles, TILE_SIZE, tileTextures, boxDamageTextures);

            minUsedX = Math.min(minUsedX, actualBossRoom.getGridX());
            maxUsedX = Math.max(maxUsedX, actualBossRoom.getGridX() + actualBossRoom.getWidth());
            minUsedY = Math.min(minUsedY, actualBossRoom.getGridY());
            maxUsedY = Math.max(maxUsedY, actualBossRoom.getGridY() + actualBossRoom.getHeight());
        }

        // Shop room javítása
        Room shopRoom = null;
        if (random.nextDouble() < 0.3) {
            int shopRoomMinX = spawnRoom.getGridX() + SHOP_WALL_OFFSET;
            int shopRoomMinY = spawnRoom.getGridY() + SHOP_WALL_OFFSET;
            int shopInnerWidth = SHOP_INNER_ROOM_SIZE;
            int shopInnerHeight = SHOP_INNER_ROOM_SIZE;
            if (shopRoomMinX + shopInnerWidth < spawnRoom.getGridX() + spawnRoom.getWidth() &&
                    shopRoomMinY + shopInnerHeight < spawnRoom.getGridY() + spawnRoom.getHeight()) {
                // ✨ JAVÍTOTT: Shop room seed-del
                shopRoom = new Room(shopRoomMinX, shopRoomMinY, shopInnerWidth, shopInnerHeight, Room.RoomType.SHOP, seed + 8888);
                for (int x = shopRoomMinX; x < shopRoomMinX + shopInnerWidth; x++) {
                    for (int y = shopRoomMinY; y < shopRoomMinY + shopInnerHeight; y++) {
                        if (isValidGridCoord(x, y, tempTiles)) {
                            tempTiles[x][y] = new Tile(Tile.TileType.SHOP_FLOOR, x, y, TILE_SIZE, tileTextures.get(Tile.TileType.SHOP_FLOOR));
                        }
                    }
                }
            } else {
                System.out.println("Warning: Shop area does not fit in spawn room.");
            }
        }

        Set<Room> mstRooms = new HashSet<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>();

        mstRooms.add(spawnRoom);
        addEdgesToPriorityQueue(spawnRoom, rooms, pq, roomGridMap);

        while (!pq.isEmpty() && mstRooms.size() < rooms.size()) {
            Edge lightestEdge = pq.poll();
            Room r1 = lightestEdge.source;
            Room r2 = lightestEdge.destination;
            boolean r1InMst = mstRooms.contains(r1);
            boolean r2InMst = mstRooms.contains(r2);
            if (r1InMst && r2InMst) {
                continue;
            }
            if (r1InMst || r2InMst) {
                Room roomToAddToMst = r1InMst ? r2 : r1;
                Room connectedRoom = r1InMst ? r1 : r2;
                mstRooms.add(roomToAddToMst);
                List<Tile> newGateGroup = new ArrayList<>();
                generateCorridorBetweenRooms(tempTiles, connectedRoom, roomToAddToMst, tileTextures, gateDestructionTextures, newGateGroup);
                gateCorridorGroups.add(newGateGroup);
                addEdgesToPriorityQueue(roomToAddToMst, rooms, pq, roomGridMap);
            }
        }

        int margin = 5;
        minUsedX = Math.max(0, minUsedX - margin);
        maxUsedX = Math.min(maxGridWidthTiles - 1, maxUsedX + margin);
        minUsedY = Math.max(0, minUsedY - margin);
        maxUsedY = Math.min(maxGridHeightTiles - 1, maxUsedY + margin);

        int finalWidthTiles = maxUsedX - minUsedX + 1;
        int finalHeightTiles = maxUsedY - minUsedY + 1;
        Tile[][] finalTiles = new Tile[finalWidthTiles][finalHeightTiles];

        Map<String, Tile> coordinateToFinalGateTiles = new HashMap<>();

        for (int x = 0; x < finalWidthTiles; x++) {
            for (int y = 0; y < finalHeightTiles; y++) {
                int originalX = minUsedX + x;
                int originalY = minUsedY + y;

                if (isValidGridCoord(originalX, originalY, tempTiles) && tempTiles[originalX][originalY] != null) {
                    Tile originalTile = tempTiles[originalX][originalY];

                    if (originalTile.getType() == Tile.TileType.GATE) {
                        Tile finalGateTile = new Tile(
                                Tile.TileType.GATE,
                                x, y,
                                TILE_SIZE,
                                originalTile.getTexture(),
                                originalTile.getDamageTextures()
                        );
                        finalTiles[x][y] = finalGateTile;

                        String coordKey = originalX + "," + originalY;
                        coordinateToFinalGateTiles.put(coordKey, finalGateTile);

                        System.out.println("Mapped gate by coordinates: " + coordKey + " -> final " + x + "," + y);

                    } else if (originalTile.getType() == Tile.TileType.BOX || originalTile.getType() == Tile.TileType.DESTRUCTIBLE_WALL) {
                        finalTiles[x][y] = new Tile(
                                originalTile.getType(), x, y, TILE_SIZE,
                                originalTile.getTexture(), originalTile.getHealth(), originalTile.getDamageTextures()
                        );
                    } else if (originalTile.getType() == Tile.TileType.WEAPON_CRATE) {
                        finalTiles[x][y] = new Tile(
                                originalTile.getType(), x, y, TILE_SIZE,
                                originalTile.getTexture(), originalTile.getEmptyTexture(), originalTile.getWeaponId()
                        );
                    } else {
                        finalTiles[x][y] = new Tile(
                                originalTile.getType(), x, y, TILE_SIZE, originalTile.getTexture()
                        );
                    }
                } else {
                    finalTiles[x][y] = new Tile(Tile.TileType.WALL, x, y, TILE_SIZE, tileTextures.get(Tile.TileType.WALL));
                }
            }
        }

        List<List<Tile>> correctedGateCorridorGroups = new ArrayList<>();
        for (List<Tile> originalGateGroup : gateCorridorGroups) {
            List<Tile> correctedGroup = new ArrayList<>();
            for (Tile originalGateTile : originalGateGroup) {
                String coordKey = originalGateTile.getGridX() + "," + originalGateTile.getGridY();
                Tile correctedTile = coordinateToFinalGateTiles.get(coordKey);

                if (correctedTile != null) {
                    correctedGroup.add(correctedTile);
                    System.out.println("✓ Found gate by coordinates: " + coordKey + " -> final " + correctedTile.getGridX() + "," + correctedTile.getGridY());
                } else {
                    System.out.println("❌ WARNING: Gate tile not found by coordinates: " + coordKey);
                }
            }

            if (!correctedGroup.isEmpty()) {
                correctedGateCorridorGroups.add(correctedGroup);
                System.out.println("✓ Corrected gate group has " + correctedGroup.size() + " tiles");
            } else {
                System.out.println("❌ WARNING: Empty corrected gate group!");
            }
        }
        gateCorridorGroups = correctedGateCorridorGroups;

        System.out.println("🔍 SPAWN CALCULATION DEBUG:");
        System.out.println("   - SpawnRoom grid: " + spawnRoom.getGridX() + "," + spawnRoom.getGridY());
        System.out.println("   - SpawnRoom center (grid): " + spawnRoom.getCenterX() + "," + spawnRoom.getCenterY());
        System.out.println("   - minUsedX: " + minUsedX + ", minUsedY: " + minUsedY);
        System.out.println("   - TILE_SIZE: " + TILE_SIZE);

// Számítsuk ki a spawn grid pozíciót
        int spawnGridX = spawnRoom.getCenterX() - minUsedX;
        int spawnGridY = spawnRoom.getCenterY() - minUsedY;

        System.out.println("   - Spawn grid: " + spawnGridX + "," + spawnGridY);

// Ellenőrizzük a spawn tile-t
        if (spawnGridX >= 0 && spawnGridX < finalTiles.length &&
                spawnGridY >= 0 && spawnGridY < finalTiles[0].length) {

            Tile spawnTile = finalTiles[spawnGridX][spawnGridY];
            System.out.println("   - Spawn tile type: " + (spawnTile != null ? spawnTile.getType() : "NULL"));
            System.out.println("   - Spawn tile is solid: " + (spawnTile != null ? spawnTile.isSolid() : "N/A"));

            // Ha a spawn tile solid, keressünk nem-solid tile-t a közelben
            if (spawnTile != null && spawnTile.isSolid()) {
                System.out.println("⚠️  Spawn tile is solid, finding nearby floor...");
                boolean foundFloor = false;
                for (int offset = 1; offset <= 3 && !foundFloor; offset++) {
                    for (int dx = -offset; dx <= offset; dx++) {
                        for (int dy = -offset; dy <= offset; dy++) {
                            int checkX = spawnGridX + dx;
                            int checkY = spawnGridY + dy;
                            if (checkX >= 0 && checkX < finalTiles.length &&
                                    checkY >= 0 && checkY < finalTiles[0].length) {
                                Tile checkTile = finalTiles[checkX][checkY];
                                if (checkTile != null && !checkTile.isSolid()) {
                                    spawnGridX = checkX;
                                    spawnGridY = checkY;
                                    System.out.println("✅ Found floor at: " + spawnGridX + "," + spawnGridY);
                                    foundFloor = true;
                                    break;
                                }
                            }
                        }
                        if (foundFloor) break;
                    }
                    if (foundFloor) break;
                }
            }
        }

// ✨ JAVÍTOTT SPAWN SZÁMÍTÁS:
// A player középe legyen a tile közepén, de vegyük figyelembe a player méretét (50x50)
        float playerSpawnPixelX = (float)spawnGridX * TILE_SIZE + (TILE_SIZE / 2f) - 25f;  // 25 = 50/2
        float playerSpawnPixelY = (float)spawnGridY * TILE_SIZE + (TILE_SIZE / 2f) - 25f;  // 25 = 50/2

        System.out.println("✅ FINAL SPAWN POSITION: " + playerSpawnPixelX + "," + playerSpawnPixelY);

        int correctedBossGridX = 0;
        int correctedBossGridY = 0;
        int bossRoomWidth = 0;
        int bossRoomHeight = 0;

        if (actualBossRoom != null) {
            correctedBossGridX = actualBossRoom.getGridX() - minUsedX;
            correctedBossGridY = actualBossRoom.getGridY() - minUsedY;
            bossRoomWidth = actualBossRoom.getWidth();
            bossRoomHeight = actualBossRoom.getHeight();
        }

        Dungeon dungeon = new Dungeon(
                finalTiles,
                TILE_SIZE,
                enemies,
                playerSpawnPixelX,
                playerSpawnPixelY,
                gateCorridorGroups,
                correctedBossGridX,
                correctedBossGridY,
                bossRoomWidth,
                bossRoomHeight
        );

        CollisionManager collisionManager = new CollisionManager(dungeon);

        System.out.println("=== FINAL GATE VERIFICATION ===");
        System.out.println("minUsedX: " + minUsedX + ", minUsedY: " + minUsedY);
        System.out.println("finalTiles size: " + finalTiles.length + " x " + finalTiles[0].length);

        int finalGateCount = 0;
        for (int x = 0; x < finalTiles.length; x++) {
            for (int y = 0; y < finalTiles[0].length; y++) {
                if (finalTiles[x][y].getType() == Tile.TileType.GATE) {
                    finalGateCount++;
                }
            }
        }
        System.out.println("FINAL GATE COUNT: " + finalGateCount);
        System.out.println("Gate groups: " + gateCorridorGroups.size());

        if (actualBossRoom != null) {
            int bossSpawnX = correctedBossGridX + actualBossRoom.getWidth() / 2;
            int bossSpawnY = correctedBossGridY + actualBossRoom.getHeight() / 2;
            float finalBossSpawnX = (float)bossSpawnX * TILE_SIZE + TILE_SIZE / 2.0f - actualBossRoom.getWidth() / 2.0f;
            float finalBossSpawnY = (float)bossSpawnY * TILE_SIZE + TILE_SIZE / 2.0f - actualBossRoom.getHeight() / 2.0f;

            if (isValidGridCoord(bossSpawnX, bossSpawnY, finalTiles) &&
                    finalTiles[bossSpawnX][bossSpawnY].getType() == Tile.TileType.FLOOR &&
                    !occupiedPositions.contains(new OccupiedTile(bossSpawnX, bossSpawnY))) {

                Boss bossEnemy = new Boss(
                        finalBossSpawnX,
                        finalBossSpawnY,
                        TILE_SIZE * 2, TILE_SIZE * 2,
                        enemyTexture,
                        500,
                        textRenderer,
                        dungeon,
                        collisionManager,
                        null
                );

                // ✨ FONTOS: BOSS ID BEÁLLÍTÁSA
                bossEnemy.setId(enemyIdCounter++);

                bossEnemy.setMoveSpeed(35.0f);
                bossEnemy.setAttackDamage(40.0f);

                enemies.add(bossEnemy);
                occupiedPositions.add(new OccupiedTile(bossSpawnX, bossSpawnY));
                System.out.println("✅ Generated BOSS with ID: " + bossEnemy.getId() + " at corrected grid: " + bossSpawnX + ", " + bossSpawnY);
            } else {
                System.out.println("Warning: Boss entity could not be placed in boss room (corrected: " + bossSpawnX + "," + bossSpawnY + ") - already occupied or invalid.");
            }
        }

        for (Room room : rooms) {
            if (room.getType() == Room.RoomType.SPAWN || room.getType() == Room.RoomType.BOSS_ROOM || room.getType() == Room.RoomType.SHOP) {
                continue;
            }
            if (room.getType() == Room.RoomType.NORMAL) {
                int numEnemies = random.nextInt(3) + 2;
                for (int i = 0; i < numEnemies; i++) {
                    int correctedEnemyGridX = -1;
                    int correctedEnemyGridY = -1;
                    boolean placed = false;
                    int placementAttempts = 0;
                    final int MAX_PLACEMENT_ATTEMPTS = 50;

                    while (!placed && placementAttempts < MAX_PLACEMENT_ATTEMPTS) {
                        int originalEnemyGridX = room.getGridX() + 1 + random.nextInt(room.getWidth() - 2);
                        int originalEnemyGridY = room.getGridY() + 1 + random.nextInt(room.getHeight() - 2);

                        correctedEnemyGridX = originalEnemyGridX - minUsedX;
                        correctedEnemyGridY = originalEnemyGridY - minUsedY;

                        if (isValidGridCoord(correctedEnemyGridX, correctedEnemyGridY, finalTiles) &&
                                finalTiles[correctedEnemyGridX][correctedEnemyGridY].getType() == Tile.TileType.FLOOR &&
                                !occupiedPositions.contains(new OccupiedTile(correctedEnemyGridX, correctedEnemyGridY))) {
                            float enemyWidth = TILE_SIZE;
                            float enemyHeight = TILE_SIZE;
                            float finalEnemySpawnX = (float)correctedEnemyGridX * TILE_SIZE + TILE_SIZE / 2.0f - enemyWidth / 2.0f;
                            float finalEnemySpawnY = (float)correctedEnemyGridY * TILE_SIZE + TILE_SIZE / 2.0f - enemyHeight / 2.0f;

                            int initialHealth = 100;

                            Enemy enemy = new Enemy(
                                    finalEnemySpawnX,
                                    finalEnemySpawnY,
                                    enemyWidth, enemyHeight, enemyTexture, initialHealth,
                                    textRenderer,
                                    dungeon,
                                    collisionManager,
                                    null
                            );

                            // ✨ FONTOS: BEÁLLÍTJUK AZ ID-T
                            enemy.setId(enemyIdCounter++);

                            enemies.add(enemy);
                            occupiedPositions.add(new OccupiedTile(correctedEnemyGridX, correctedEnemyGridY));
                            placed = true;
                            System.out.println("✅ Generated ENEMY with ID: " + enemy.getId() + " at " + correctedEnemyGridX + "," + correctedEnemyGridY);
                        }
                        placementAttempts++;
                    }
                    if (!placed) {
                        System.out.println("Warning: Could not place enemy in room after " + MAX_PLACEMENT_ATTEMPTS + " attempts.");
                    }
                }
            }
        }

        for (Room room : rooms) {
            if (room.getType() == Room.RoomType.SPAWN || room.getType() == Room.RoomType.SHOP) {
                continue;
            }
            if (room.getType() == Room.RoomType.NORMAL || room.getType() == Room.RoomType.BOSS_ROOM) {
                int numBoxes = random.nextInt(3) + 1;
                for (int i = 0; i < numBoxes; i++) {
                    int correctedBoxGridX = -1;
                    int correctedBoxGridY = -1;
                    boolean placed = false;
                    int placementAttempts = 0;
                    final int MAX_PLACEMENT_ATTEMPTS = 50;

                    while (!placed && placementAttempts < MAX_PLACEMENT_ATTEMPTS) {
                        int originalBoxGridX = room.getGridX() + 1 + random.nextInt(room.getWidth() - 2);
                        int originalBoxGridY = room.getGridY() + 1 + random.nextInt(room.getHeight() - 2);

                        correctedBoxGridX = originalBoxGridX - minUsedX;
                        correctedBoxGridY = originalBoxGridY - minUsedY;

                        if (isValidGridCoord(correctedBoxGridX, correctedBoxGridY, finalTiles) &&
                                (finalTiles[correctedBoxGridX][correctedBoxGridY].getType() == Tile.TileType.FLOOR || finalTiles[correctedBoxGridX][correctedBoxGridY].getType() == Tile.TileType.SHOP_FLOOR) &&
                                !occupiedPositions.contains(new OccupiedTile(correctedBoxGridX, correctedBoxGridY))) {

                            finalTiles[correctedBoxGridX][correctedBoxGridY] = new Tile(
                                    Tile.TileType.BOX,
                                    correctedBoxGridX,
                                    correctedBoxGridY,
                                    TILE_SIZE,
                                    tileTextures.get(Tile.TileType.BOX),
                                    3.0f,
                                    boxDamageTextures
                            );
                            occupiedPositions.add(new OccupiedTile(correctedBoxGridX, correctedBoxGridY));
                            placed = true;
                        }
                        placementAttempts++;
                    }
                    if (!placed) {
                        System.out.println("Warning: Could not place box in room after " + MAX_PLACEMENT_ATTEMPTS + " attempts.");
                    }
                }
            }
        }

        if (shopRoom != null) {
            int shopCenterX = shopRoom.getGridX() - minUsedX + shopRoom.getWidth() / 2;
            int shopCenterY = shopRoom.getGridY() - minUsedY + shopRoom.getHeight() / 2;

            int crate1X = shopCenterX - 2;
            int crate1Y = shopCenterY;
            int crate2X = shopCenterX + 2;
            int crate2Y = shopCenterY;

            if (isValidGridCoord(crate1X, crate1Y, finalTiles) && (finalTiles[crate1X][crate1Y].getType() == Tile.TileType.FLOOR || finalTiles[crate1X][crate1Y].getType() == Tile.TileType.SHOP_FLOOR)) {
                String randomWeaponId1 = weaponFactory.getRandomWeaponId();
                finalTiles[crate1X][crate1Y] = new Tile(
                        Tile.TileType.WEAPON_CRATE, crate1X, crate1Y, TILE_SIZE, weaponCrateTexture, emptyCrateTexture, randomWeaponId1
                );
                occupiedPositions.add(new OccupiedTile(crate1X, crate1Y));
            } else {
                System.out.println("Warning: Could not place weapon crate 1 in shop room.");
            }

            if (isValidGridCoord(crate2X, crate2Y, finalTiles) && (finalTiles[crate2X][crate2Y].getType() == Tile.TileType.FLOOR || finalTiles[crate2X][crate2Y].getType() == Tile.TileType.SHOP_FLOOR)) {
                String randomWeaponId2 = weaponFactory.getRandomWeaponId();
                finalTiles[crate2X][crate2Y] = new Tile(
                        Tile.TileType.WEAPON_CRATE, crate2X, crate2Y, TILE_SIZE, weaponCrateTexture, emptyCrateTexture, randomWeaponId2
                );
                occupiedPositions.add(new OccupiedTile(crate2X, crate2Y));
            } else {
                System.out.println("Warning: Could not place weapon crate 2 in shop room.");
            }
        }

        return dungeon;
    }

    // ✨ MEGLÉVŐ: Háttérkompatibilitás
    public static Dungeon generateRandomDungeon(int tileSize, Map<Tile.TileType, Texture> tileTextures,
                                                Texture enemyTexture, Map<Integer, Texture> boxDamageTextures,
                                                Map<Integer, Texture> gateAnimationTextures, Texture weaponCrateTexture,
                                                Texture openCrateTexture, Texture emptyCrateTexture, WeaponFactory weaponFactory,
                                                TextRenderer textRenderer, Map<Effect.EffectType, Texture> effectTextures,
                                                Texture teleportPadTexture) {

        long seed = System.currentTimeMillis();
        return generateRandomDungeonWithSeed(seed, tileSize, tileTextures, enemyTexture,
                boxDamageTextures, gateAnimationTextures, weaponCrateTexture,
                openCrateTexture, emptyCrateTexture, weaponFactory, textRenderer,
                effectTextures, teleportPadTexture);
    }

    // ⬇️ AZ ALÁBBI METÓDUSOK VÁLTOZATLANOK MARADNAK ⬇️
    // (csak a generateRandomDungeon metódus változott)

    private static final int TILE_SIZE = 53;
    private static final int MIN_ROOM_WIDTH_TILES = 20;
    private static final int MAX_ROOM_WIDTH_TILES = 20;
    private static final int MIN_ROOM_HEIGHT_TILES = 20;
    private static final int MAX_ROOM_HEIGHT_TILES = 20;
    private static final double BOSS_ROOM_SCALE_FACTOR = 0.5;
    private static final int CORRIDOR_WIDTH_TILES = 3;
    private static final int SHOP_INNER_ROOM_SIZE = 4;
    private static final int SHOP_WALL_OFFSET = 1;
    private static final int ROOM_GRID_COLS = 5;
    private static final int ROOM_GRID_ROWS = 5;
    private static final int ROOM_GRID_CELL_TILE_WIDTH = (MAX_ROOM_WIDTH_TILES + CORRIDOR_WIDTH_TILES + 4);
    private static final int ROOM_GRID_CELL_TILE_HEIGHT = (MAX_ROOM_HEIGHT_TILES + CORRIDOR_WIDTH_TILES + 4);

    public static List<List<Tile>> gateCorridorGroups = new ArrayList<>();

    private static class Edge implements Comparable<Edge> {
        Room source;
        Room destination;
        int weight;

        public Edge(Room source, Room destination, int weight) {
            this.source = source;
            this.destination = destination;
            this.weight = weight;
        }
        @Override
        public int compareTo(Edge other) {
            return Integer.compare(this.weight, other.weight);
        }
    }

    private static class OccupiedTile {
        int x, y;
        public OccupiedTile(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OccupiedTile that = (OccupiedTile) o;
            return x == that.x && y == that.y;
        }
        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    private static void addEdgesToPriorityQueue(Room startRoom, List<Room> allRooms, PriorityQueue<Edge> pq, Map<String, Room> roomGridMap) {
        int startCol = -1, startRow = -1;
        for (Map.Entry<String, Room> entry : roomGridMap.entrySet()) {
            if (entry.getValue().equals(startRoom)) {
                String[] coords = entry.getKey().split(",");
                startCol = Integer.parseInt(coords[0]);
                startRow = Integer.parseInt(coords[1]);
                break;
            }
        }
        if (startCol == -1 || startRow == -1) return;

        for (Room otherRoom : allRooms) {
            if (!otherRoom.equals(startRoom)) {
                int otherCol = -1, otherRow = -1;
                for (Map.Entry<String, Room> entry : roomGridMap.entrySet()) {
                    if (entry.getValue().equals(otherRoom)) {
                        String[] coords = entry.getKey().split(",");
                        otherCol = Integer.parseInt(coords[0]);
                        otherRow = Integer.parseInt(coords[1]);
                        break;
                    }
                }
                if (otherCol != -1 && otherRow != -1) {
                    int dx = Math.abs(startCol - otherCol);
                    int dy = Math.abs(startRow - otherRow);
                    int weight = dx + dy;
                    pq.add(new Edge(startRoom, otherRoom, weight));
                }
            }
        }
    }

    private static void generateCorridorBetweenRooms(Tile[][] tiles, Room r1, Room r2,
                                                     Map<Tile.TileType, Texture> tileTextures,
                                                     Map<Integer, Texture> gateDestructionTextures,
                                                     List<Tile> gateGroup) {
        int startX = r1.getCenterX();
        int startY = r1.getCenterY();
        int endX = r2.getCenterX();
        int endY = r2.getCenterY();

        boolean horizontalFirst = Math.abs(startX - endX) > Math.abs(startY - endY);

        int gateX, gateY;

        if (horizontalFirst) {
            drawCorridorSegment(tiles, startX, startY, endX, startY, tileTextures, CORRIDOR_WIDTH_TILES);
            drawCorridorSegment(tiles, endX, startY, endX, endY, tileTextures, CORRIDOR_WIDTH_TILES);
            int horizontalMidX = startX + (endX - startX) / 2;
            gateX = horizontalMidX;
            gateY = startY;
        } else {
            drawCorridorSegment(tiles, startX, startY, startX, endY, tileTextures, CORRIDOR_WIDTH_TILES);
            drawCorridorSegment(tiles, startX, endY, endX, endY, tileTextures, CORRIDOR_WIDTH_TILES);
            int verticalMidY = startY + (endY - startY) / 2;
            gateX = startX;
            gateY = verticalMidY;
        }

        System.out.println("Gate center calculated at: " + gateX + "," + gateY);
        drawGateSegment(tiles, gateX, gateY, tileTextures, CORRIDOR_WIDTH_TILES, gateDestructionTextures, gateGroup);
    }

    private static void drawCorridorSegment(Tile[][] tiles, int x1, int y1, int x2, int y2,
                                            Map<Tile.TileType, Texture> tileTextures, int width) {
        int halfWidth = width / 2;
        int tilesChanged = 0;
        int gatesPreserved = 0;

        if (x1 == x2) {
            int startY = Math.min(y1, y2);
            int endY = Math.max(y1, y2);
            for (int y = startY; y <= endY; y++) {
                for (int xOffset = -halfWidth; xOffset <= halfWidth; xOffset++) {
                    int currentX = x1 + xOffset;
                    if (isValidGridCoord(currentX, y, tiles)) {
                        if (tiles[currentX][y].getType() == Tile.TileType.GATE) {
                            gatesPreserved++;
                            continue;
                        }
                        if (tiles[currentX][y].getType() == Tile.TileType.WALL) {
                            tiles[currentX][y] = new Tile(Tile.TileType.FLOOR, currentX, y, TILE_SIZE, tileTextures.get(Tile.TileType.FLOOR));
                            tilesChanged++;
                        }
                    }
                }
            }
        } else if (y1 == y2) {
            int startX = Math.min(x1, x2);
            int endX = Math.max(x1, x2);
            for (int x = startX; x <= endX; x++) {
                for (int yOffset = -halfWidth; yOffset <= halfWidth; yOffset++) {
                    int currentY = y1 + yOffset;
                    if (isValidGridCoord(x, currentY, tiles)) {
                        if (tiles[x][currentY].getType() == Tile.TileType.GATE) {
                            gatesPreserved++;
                            continue;
                        }
                        if (tiles[x][currentY].getType() == Tile.TileType.WALL) {
                            tiles[x][currentY] = new Tile(Tile.TileType.FLOOR, x, currentY, TILE_SIZE, tileTextures.get(Tile.TileType.FLOOR));
                            tilesChanged++;
                        }
                    }
                }
            }
        }

        System.out.println("Corridor segment drawn: " + tilesChanged + " WALL->FLOOR, " + gatesPreserved + " gates preserved");
    }

    private static void drawGateSegment(Tile[][] tiles, int centerX, int centerY,
                                        Map<Tile.TileType, Texture> tileTextures, int width,
                                        Map<Integer, Texture> gateDestructionTextures, List<Tile> gateGroup) {
        int totalWidth = width;
        int totalHeight = width;

        System.out.println("Drawing gate segment at center: " + centerX + "," + centerY + " with size: " + totalWidth + "x" + totalHeight);

        int startX = centerX - totalWidth / 2;
        int startY = centerY - totalHeight / 2;

        int gatesPlaced = 0;

        for (int xOffset = 0; xOffset < totalWidth; xOffset++) {
            for (int yOffset = 0; yOffset < totalHeight; yOffset++) {
                int currentX = startX + xOffset;
                int currentY = startY + yOffset;

                if (isValidGridCoord(currentX, currentY, tiles)) {
                    Texture currentGateTexture = gateDestructionTextures.get(0);
                    Tile gateTile = new Tile(
                            Tile.TileType.GATE,
                            currentX, currentY,
                            TILE_SIZE,
                            currentGateTexture,
                            gateDestructionTextures
                    );
                    gateGroup.add(gateTile);
                    tiles[currentX][currentY] = gateTile;
                    gatesPlaced++;
                    System.out.println("  Placed GATE at: " + currentX + "," + currentY);
                }
            }
        }

        System.out.println("Total gates placed: " + gatesPlaced + " (expected: " + (totalWidth * totalHeight) + ")");

        int verifiedGates = 0;
        for (int xOffset = 0; xOffset < totalWidth; xOffset++) {
            for (int yOffset = 0; yOffset < totalHeight; yOffset++) {
                int checkX = startX + xOffset;
                int checkY = startY + yOffset;
                if (isValidGridCoord(checkX, checkY, tiles) && tiles[checkX][checkY].getType() == Tile.TileType.GATE) {
                    verifiedGates++;
                }
            }
        }
        System.out.println("Verified gates in area: " + verifiedGates);

        if (verifiedGates != totalWidth * totalHeight) {
            System.out.println("❌ ERROR: Gate placement incomplete! Expected " + (totalWidth * totalHeight) + ", got " + verifiedGates);
        }
    }

    private static boolean isValidGridCoord(int x, int y, Tile[][] tiles) {
        return x >= 0 && x < tiles.length && y >= 0 && y < tiles[0].length;
    }
}