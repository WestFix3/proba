package world;

import rendering.Texture;
import java.util.*;

public class Room {
    int gridX, gridY;
    int width, height;
    RoomType type;

    private Random random;
    private static final int MAZE_CELL_DIMENSION = 3;

    // ✨ MÓDOSÍTOTT: Seed-et fogadó konstruktor
    public Room(int gridX, int gridY, int width, int height, RoomType type, long seed) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.width = width;
        this.height = height;
        this.type = type;
        this.random = new Random(seed); // ✨ Seed-elt Random
    }

    public enum RoomType {
        SPAWN,
        NORMAL,
        BOSS_ROOM,
        SHOP
    }

    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public RoomType getType() { return type; }
    public int getCenterX() { return gridX + width / 2; }
    public int getCenterY() { return gridY + height / 2; }

    public void draw(Tile[][] tiles, int tileSize, Map<Tile.TileType, Texture> tileTextures, Map<Integer, Texture> boxDamageTextures) {
        for (int x = gridX; x < gridX + width; x++) {
            for (int y = gridY; y < gridY + height; y++) {
                if (!isValidGridCoord(x, y, tiles)) continue;
                tiles[x][y] = new Tile(Tile.TileType.WALL, x, y, tileSize, tileTextures.get(Tile.TileType.WALL));
            }
        }

        for (int x = gridX; x < gridX + width; x++) {
            for (int y = gridY; y < gridY + height; y++) {
                if (x == gridX || x == gridX + width - 1 || y == gridY || y == gridY + height - 1) {
                    tiles[x][y] = new Tile(Tile.TileType.WALL, x, y, tileSize, tileTextures.get(Tile.TileType.WALL));
                }
            }
        }

        if (this.type == Room.RoomType.NORMAL) {
            generateMaze(tiles, tileSize, tileTextures);
            placeDestructibles(tiles, tileSize, tileTextures, boxDamageTextures);
        } else if (this.type == Room.RoomType.SHOP) {
            for (int x = gridX + 1; x < gridX + width - 1; x++) {
                for (int y = gridY + 1; y < gridY + height - 1; y++) {
                    if (isValidGridCoord(x, y, tiles)) {
                        tiles[x][y] = new Tile(Tile.TileType.SHOP_FLOOR, x, y, tileSize, tileTextures.get(Tile.TileType.SHOP_FLOOR));
                    }
                }
            }
        } else {
            for (int x = gridX + 1; x < gridX + width - 1; x++) {
                for (int y = gridY + 1; y < gridY + height - 1; y++) {
                    if (isValidGridCoord(x, y, tiles)) {
                        tiles[x][y] = new Tile(Tile.TileType.FLOOR, x, y, tileSize, tileTextures.get(Tile.TileType.FLOOR));
                    }
                }
            }
        }
    }

    private void placeDestructibles(Tile[][] tiles, int tileSize, Map<Tile.TileType, Texture> tileTextures, Map<Integer, Texture> boxDamageTextures) {
        for (int x = gridX + 1; x < gridX + width - 1; x++) {
            for (int y = gridY + 1; y < gridY + height - 1; y++) {
                if (isValidGridCoord(x, y, tiles) && tiles[x][y].getType() == Tile.TileType.WALL) {
                    if (random.nextDouble() < 0.5) { // ✨ Most már determinisztikus
                        tiles[x][y] = new Tile(
                                Tile.TileType.BOX,
                                x, y,
                                tileSize,
                                tileTextures.get(Tile.TileType.BOX),
                                3.0f,
                                boxDamageTextures
                        );
                    }
                }
            }
        }
    }

    private void generateMaze(Tile[][] tiles, int tileSize, Map<Tile.TileType, Texture> tileTextures) {
        int mazeGridWidth = (width - 2) / MAZE_CELL_DIMENSION;
        int mazeGridHeight = (height - 2) / MAZE_CELL_DIMENSION;

        if (mazeGridWidth < 2 || mazeGridHeight < 2) {
            for (int x = gridX + 1; x < gridX + width - 1; x++) {
                for (int y = gridY + 1; y < gridY + height - 1; y++) {
                    if (isValidGridCoord(x, y, tiles)) {
                        tiles[x][y] = new Tile(Tile.TileType.FLOOR, x, y, tileSize, tileTextures.get(Tile.TileType.FLOOR));
                    }
                }
            }
            return;
        }

        boolean[][] mazeCells = new boolean[mazeGridWidth][mazeGridHeight];
        List<int[]> wallsToDig = new ArrayList<>();
        int startCellX = random.nextInt(mazeGridWidth); // ✨ Most már determinisztikus
        int startCellY = random.nextInt(mazeGridHeight); // ✨ Most már determinisztikus
        mazeCells[startCellX][startCellY] = true;
        addCellWallsToDig(startCellX, startCellY, mazeGridWidth, mazeGridHeight, mazeCells, wallsToDig);
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        while (!wallsToDig.isEmpty()) {
            int wallIndex = random.nextInt(wallsToDig.size()); // ✨ Most már determinisztikus
            int[] wall = wallsToDig.remove(wallIndex);
            int wx = wall[0];
            int wy = wall[1];
            List<int[]> pathNeighbors = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int nx = wx + dx[i];
                int ny = wy + dy[i];
                if (nx >= 0 && nx < mazeGridWidth && ny >= 0 && ny < mazeGridHeight && mazeCells[nx][ny]) {
                    pathNeighbors.add(new int[]{nx, ny});
                }
            }
            if (pathNeighbors.size() == 1) {
                mazeCells[wx][wy] = true;
                addCellWallsToDig(wx, wy, mazeGridWidth, mazeGridHeight, mazeCells, wallsToDig);
            }
        }

        for (int cx = 0; cx < mazeGridWidth; cx++) {
            for (int cy = 0; cy < mazeGridHeight; cy++) {
                int tileX = gridX + 1 + cx * MAZE_CELL_DIMENSION;
                int tileY = gridY + 1 + cy * MAZE_CELL_DIMENSION;
                if (mazeCells[cx][cy]) {
                    for (int x = 0; x < MAZE_CELL_DIMENSION; x++) {
                        for (int y = 0; y < MAZE_CELL_DIMENSION; y++) {
                            int currentTileX = tileX + x;
                            int currentTileY = tileY + y;
                            if (isValidGridCoord(currentTileX, currentTileY, tiles)) {
                                tiles[currentTileX][currentTileY] = new Tile(Tile.TileType.FLOOR, currentTileX, currentTileY, tileSize, tileTextures.get(Tile.TileType.FLOOR));
                            }
                        }
                    }
                }
            }
        }
    }

    private void addCellWallsToDig(int cx, int cy, int mazeGridWidth, int mazeGridHeight, boolean[][] mazeCells, List<int[]> wallsToDig) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int nx = cx + dx[i];
            int ny = cy + dy[i];
            if (nx >= 0 && nx < mazeGridWidth && ny >= 0 && ny < mazeGridHeight && !mazeCells[nx][ny]) {
                int[] newWall = new int[]{nx, ny};
                boolean alreadyAdded = false;
                for(int[] wall : wallsToDig) {
                    if (wall[0] == newWall[0] && wall[1] == newWall[1]) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    wallsToDig.add(newWall);
                }
            }
        }
    }

    private boolean isValidGridCoord(int x, int y, Tile[][] tiles) {
        return x >= 0 && x < tiles.length && y >= 0 && y < tiles[0].length;
    }
}