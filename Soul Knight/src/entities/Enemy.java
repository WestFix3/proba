package entities;

import rendering.Texture;
import rendering.TextRenderer;
import world.Dungeon;
import world.Tile;
import physics.CollisionManager;

import static org.lwjgl.opengl.GL11.*;

import java.util.*;

/**
 * Ellenség entitás megbízható útvonaltervezéssel
 */
public class Enemy extends Entity {
    private int id;

    private float health;
    private boolean alive = true;
    protected float maxHealth;
    private Texture texture;
    protected float moveSpeed = 50.0f;
    private float initialX, initialY;

    protected float attackDamage = 15.0f;
    protected float attackCooldown = 1.2f;
    private float lastAttackTime = 0.0f;

    // --- DEPENDENCIES ---
    private CollisionManager collisionManager;
    protected Dungeon dungeon;
    private TextRenderer textRenderer;
    private TextRenderer critTextRenderer;
    protected Player targetPlayer;

    // --- PATHFINDING ---
    private List<Node> currentPath;
    private int currentPathIndex;
    private float targetX, targetY;
    private boolean hasTarget = false;
    private static final float NODE_REACH_DISTANCE = 20.0f;
    private long lastPathCalculationTime = 0;
    private static final long PATH_CALCULATION_COOLDOWN = 1000;
    private final Random pathRandom = new Random();
    private float baseMoveSpeed = moveSpeed;
    private float baseAttackDamage = attackDamage;
    private float pathDeviationChance = 0.0f;
    private float pathDeviationRadius = 0.0f;

    // --- STUCK DETECTION ---
    private float stuckTimer = 0.0f;
    private static final float STUCK_THRESHOLD = 2.0f;
    private float lastX, lastY;

    // --- CRIT INDICATOR ---
    private boolean showCritIndicator = false;
    private float critIndicatorTime = 0.0f;
    private static final float CRIT_DISPLAY_DURATION = 0.8f;
    private static final float INTERPOLATION_SPEED = 5.0f;

    private boolean showPathDebug = true;
    private boolean networkControlled = false;

    public Enemy(float x, float y, float width, float height, Texture texture, float initialHealth,
                 TextRenderer textRenderer, Dungeon dungeon, CollisionManager collisionManager, Player targetPlayer) {
        super(x, y, width, height);
        this.initialX = x;
        this.initialY = y;
        this.texture = texture;
        this.health = initialHealth;
        this.maxHealth = initialHealth;

        this.textRenderer = textRenderer;
        this.dungeon = dungeon;
        this.collisionManager = collisionManager;
        this.targetPlayer = targetPlayer;

        this.currentPath = new ArrayList<>();
        this.currentPathIndex = 0;
        this.lastX = x;
        this.lastY = y;

        this.critTextRenderer = new TextRenderer("CRIT", new java.awt.Font("Arial", java.awt.Font.BOLD, 24), java.awt.Color.RED);

        this.baseMoveSpeed = this.moveSpeed;
        this.baseAttackDamage = this.attackDamage;
    }

    public float getInitialHealth() {
        return this.maxHealth;
    }

    // ÚJ: Setter metódus a path debug beállításhoz
    public void setShowPathDebug(boolean showPathDebug) {
        this.showPathDebug = showPathDebug;
        System.out.println("Enemy path debug set to: " + showPathDebug); // Debug kiírás
    }

    @Override
    public void update(float deltaTime, Object... args) {
        if (networkControlled) {
            updateNetworkMovement(deltaTime);
            return;
        }
        if (dungeon == null) return;

        this.lastAttackTime += deltaTime;

        // Player keresés
        if (targetPlayer == null && args != null && args.length > 0 && args[0] instanceof Player) {
            this.targetPlayer = (Player) args[0];
        }

        if (targetPlayer != null && !targetPlayer.isAlive()) {
            setTargetPlayer(null);
        }

        long currentTime = System.currentTimeMillis();
        boolean cooldownReady = (currentTime - lastPathCalculationTime > PATH_CALCULATION_COOLDOWN);

        // Stuck detection
        float distanceMoved = (float)Math.sqrt(Math.pow(x - lastX, 2) + Math.pow(y - lastY, 2));
        if (distanceMoved < 2.0f && hasTarget) {
            stuckTimer += deltaTime;
        } else {
            stuckTimer = 0.0f;
        }
        lastX = x;
        lastY = y;

        // Path calculation decision
        boolean needsNewPath = cooldownReady || currentPath.isEmpty() || stuckTimer > STUCK_THRESHOLD;

        if (targetPlayer != null && needsNewPath) {
        	calculatePathToTarget(
                    targetPlayer.getX() + targetPlayer.getWidth() / 2.0f,
                    targetPlayer.getY() + targetPlayer.getHeight() / 2.0f
            );
            this.hasTarget = true;
            this.lastPathCalculationTime = currentTime;
            this.stuckTimer = 0.0f;
        } else if (needsNewPath && currentPath.isEmpty()) {
            setRandomTarget();
        }

        // Movement
        if (currentPath != null && !currentPath.isEmpty() && currentPathIndex < currentPath.size()) {
            followPath(deltaTime);
        }

        // Attack
        if (targetPlayer != null && targetPlayer.isAlive()) {
            checkAndAttackPlayer();
        }

        // Crit indicator
        if (showCritIndicator) {
            critIndicatorTime += deltaTime;
            if (critIndicatorTime >= CRIT_DISPLAY_DURATION) {
                showCritIndicator = false;
            }
        }
    }

    /**
     * Megbízható útvonaltervezés több stratégiával
     */
    private void calculatePathToTarget(float targetX, float targetY) {
        if (dungeon == null || currentPath == null) return;

        int tileSize = dungeon.getTileSize();
        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        int startTileX = (int)(centerX / tileSize);
        int startTileY = (int)(centerY / tileSize);
        int targetTileX = (int)(targetX / tileSize);
        int targetTileY = (int)(targetY / tileSize);

        if (pathDeviationChance > 0.0f && pathDeviationRadius > 0.0f && pathRandom.nextFloat() < pathDeviationChance) {
            int maxDeviation = Math.max(1, Math.round(pathDeviationRadius));
            int offsetX = pathRandom.nextInt(maxDeviation * 2 + 1) - maxDeviation;
            int offsetY = pathRandom.nextInt(maxDeviation * 2 + 1) - maxDeviation;
            if (offsetX != 0 || offsetY != 0) {
                targetTileX += offsetX;
                targetTileY += offsetY;
            }
        }

        if (!isValidTile(startTileX, startTileY)) {
            currentPath.clear();
            return;
        }

        // 1. PRÓBÁLJUK MEG AZ ALAP A* ÚTVONALT
        List<Node> basePath = calculateBasePath(startTileX, startTileY, targetTileX, targetTileY);

        if (!basePath.isEmpty()) {
            // 2. HA VAN ALAP ÚTVONAL, PRÓBÁLJUK MEG SIMÍTANI
            List<Node> smoothedPath = attemptPathSmoothing(basePath);
            currentPath = smoothedPath;
            System.out.println("Path found: " + basePath.size() + " -> " + smoothedPath.size() + " nodes");
        } else {
            // 3. HA NINCS ALAP ÚTVONAL, PRÓBÁLJUNK KÖZELÍTŐ CÉLPONTOT
            currentPath = findFallbackPath(startTileX, startTileY, targetTileX, targetTileY);
            if (!currentPath.isEmpty()) {
                //System.out.println("Fallback path found: " + currentPath.size() + " nodes");
            } else {
                //System.out.println("No path found to target");
            }
        }

        currentPathIndex = 0;
        lastPathCalculationTime = System.currentTimeMillis();
    }

    /**
     * ÚTVONAL SIMÍTÁSI KÍSÉRLET - biztonságosabb változat
     */
    private List<Node> attemptPathSmoothing(List<Node> basePath) {
        if (basePath.size() <= 3) {
            return basePath;
        }

        try {
            List<Node> smoothedPath = new ArrayList<>();
            smoothedPath.add(basePath.get(0));

            int currentIndex = 0;
            while (currentIndex < basePath.size() - 1) {
                int furthestVisible = currentIndex + 1;

                for (int i = currentIndex + 2; i < basePath.size(); i++) {
                    if (hasLineOfSight(basePath.get(currentIndex), basePath.get(i))) {
                        furthestVisible = i;
                    } else {
                        break;
                    }
                }

                smoothedPath.add(basePath.get(furthestVisible));
                currentIndex = furthestVisible;
            }

            // Biztosítsuk, hogy a célpont benne van
            if (!smoothedPath.get(smoothedPath.size() - 1).equals(basePath.get(basePath.size() - 1))) {
                smoothedPath.add(basePath.get(basePath.size() - 1));
            }

            return smoothedPath;
        } catch (Exception e) {
            System.err.println("Path smoothing failed, using base path");
            return basePath;
        }
    }

    /**
     * Egyszerű vonalbelátás ellenőrzés
     */
    private boolean hasLineOfSight(Node from, Node to) {
        int x0 = from.x;
        int y0 = from.y;
        int x1 = to.x;
        int y1 = to.y;

        // Egyszerű egyenes vonal esetén
        if (x0 == x1) {
            int startY = Math.min(y0, y1);
            int endY = Math.max(y0, y1);
            for (int y = startY + 1; y < endY; y++) {
                if (isObstacle(x0, y)) return false;
            }
            return true;
        }

        if (y0 == y1) {
            int startX = Math.min(x0, x1);
            int endX = Math.max(x0, x1);
            for (int x = startX + 1; x < endX; x++) {
                if (isObstacle(x, y0)) return false;
            }
            return true;
        }

        // Átlós eset - Bresenham algoritmus
        return hasDiagonalLineOfSight(from, to);
    }

    /**
     * Átlós vonalbelátás ellenőrzés
     */
    private boolean hasDiagonalLineOfSight(Node from, Node to) {
        int x0 = from.x;
        int y0 = from.y;
        int x1 = to.x;
        int y1 = to.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int currentX = x0;
        int currentY = y0;

        while (true) {
            // Ellenőrizzük az aktuális tile-t (kivéve a kiindulási és cél pontot)
            if ((currentX != x0 || currentY != y0) &&
                    (currentX != x1 || currentY != y1) &&
                    isObstacle(currentX, currentY)) {
                return false;
            }

            if (currentX == x1 && currentY == y1) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currentX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currentY += sy;
            }
        }

        return true;
    }

    /**
     * TARTALÉK ÚTVONAL KERESÉS - ha az eredeti cél nem elérhető
     */
    private List<Node> findFallbackPath(int startX, int startY, int targetX, int targetY) {
        // Keressünk egy közeli elérhető tile-t a célhoz
        int[][] directions = {{0,1}, {1,0}, {0,-1}, {-1,0}, {1,1}, {1,-1}, {-1,1}, {-1,-1}};

        // Próbáljuk a közvetlen szomszédokat
        for (int[] dir : directions) {
            int fallbackX = targetX + dir[0];
            int fallbackY = targetY + dir[1];

            if (isValidTile(fallbackX, fallbackY) && !isObstacle(fallbackX, fallbackY)) {
                List<Node> fallbackPath = calculateBasePath(startX, startY, fallbackX, fallbackY);
                if (!fallbackPath.isEmpty()) {
                    return fallbackPath;
                }
            }
        }

        // Ha nincs közeli cél, próbáljunk random elérhető tile-t
        return findRandomReachablePath(startX, startY);
    }

    /**
     * VÉLETLENSZERŰ ELÉRHETŐ ÚTVONAL
     */
    private List<Node> findRandomReachablePath(int startX, int startY) {
        Random rand = new Random();
        int attempts = 0;
        int maxAttempts = 15;

        while (attempts < maxAttempts) {
            int randomX = rand.nextInt(dungeon.getWidthTiles());
            int randomY = rand.nextInt(dungeon.getHeightTiles());

            if (!isObstacle(randomX, randomY)) {
                List<Node> path = calculateBasePath(startX, startY, randomX, randomY);
                if (!path.isEmpty()) {
                    return path;
                }
            }
            attempts++;
        }

        return new ArrayList<>();
    }

    /**
     * Alap A* útvonal keresés 8 irányban
     */
    private List<Node> calculateBasePath(int startX, int startY, int targetX, int targetY) {
        if (!isValidTile(startX, startY) || !isValidTile(targetX, targetY)) {
            return new ArrayList<>();
        }

        if (isObstacle(targetX, targetY)) {
            return new ArrayList<>();
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<String, Node> allNodes = new HashMap<>();

        Node startNode = new Node(startX, startY);
        startNode.gCost = 0;
        startNode.hCost = calculateHeuristic(startX, startY, targetX, targetY);
        startNode.calculateFCost();

        openSet.add(startNode);
        allNodes.put(startX + "," + startY, startNode);

        int maxIterations = 500;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            Node currentNode = openSet.poll();

            if (currentNode == null) continue;

            if (currentNode.x == targetX && currentNode.y == targetY) {
                return retracePath(currentNode);
            }

            // 8 irányú szomszéd keresés
            int[] dx = {0, 0, 1, -1, 1, -1, 1, -1};
            int[] dy = {1, -1, 0, 0, 1, 1, -1, -1};
            final float[] costs = {1.0f, 1.0f, 1.0f, 1.0f, 1.4f, 1.4f, 1.4f, 1.4f};

            for (int k = 0; k < dx.length; k++) {
                int neighborX = currentNode.x + dx[k];
                int neighborY = currentNode.y + dy[k];

                if (isValidTile(neighborX, neighborY) && !isObstacle(neighborX, neighborY)) {
                    // Átlós mozgás ellenőrzés - csak akkor blokkoljuk, ha mindkét oldal fal
                    if (k >= 4) {
                        boolean bothSidesBlocked = isObstacle(currentNode.x + dx[k], currentNode.y) &&
                                isObstacle(currentNode.x, currentNode.y + dy[k]);
                        if (bothSidesBlocked) {
                            continue;
                        }
                    }

                    String neighborKey = neighborX + "," + neighborY;
                    Node neighborNode = allNodes.getOrDefault(neighborKey, new Node(neighborX, neighborY));

                    float tentativeGCost = currentNode.gCost + costs[k];

                    if (tentativeGCost < neighborNode.gCost) {
                        neighborNode.parent = currentNode;
                        neighborNode.gCost = tentativeGCost;
                        neighborNode.hCost = calculateHeuristic(neighborX, neighborY, targetX, targetY);
                        neighborNode.calculateFCost();

                        if (!allNodes.containsKey(neighborKey)) {
                            allNodes.put(neighborKey, neighborNode);
                            openSet.add(neighborNode);
                        }
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * Útvonal követése saját ütközésdetektálással
     */
    private void followPath(float deltaTime) {
        if (dungeon == null || currentPath == null || currentPath.isEmpty()) return;

        if (currentPathIndex >= currentPath.size()) {
            currentPath.clear();
            hasTarget = false;
            return;
        }

        Node targetNode = currentPath.get(currentPathIndex);
        int tileSize = dungeon.getTileSize();

        float targetWorldX = targetNode.x * tileSize + tileSize / 2.0f;
        float targetWorldY = targetNode.y * tileSize + tileSize / 2.0f;

        float centerX = x + width / 2.0f;
        float centerY = y + height / 2.0f;

        float dx = targetWorldX - centerX;
        float dy = targetWorldY - centerY;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);

        if (distance == 0.0f) {
            currentPathIndex++;
            return;
        }

        // Node váltás
        if (distance < NODE_REACH_DISTANCE) {
            currentPathIndex++;
            return;
        }

        // Mozgás
        float totalMoveDistance = moveSpeed * deltaTime;
        if (totalMoveDistance > distance) {
            totalMoveDistance = distance;
        }

        float moveX = (dx / distance) * totalMoveDistance;
        float moveY = (dy / distance) * totalMoveDistance;

        float newX = x + moveX;
        float newY = y + moveY;

        // Ütközésdetektálás
        if (!checkCollision(newX, newY)) {
            setX(newX);
            setY(newY);
        } else {
            // Ütközés történt, próbáljuk meg korrigálni
            boolean moved = attemptCollisionRecovery(moveX, moveY, newX, newY);

            if (!moved) {
                stuckTimer += deltaTime;
                if (stuckTimer > STUCK_THRESHOLD) {
                    currentPath.clear();
                    hasTarget = false;
                    stuckTimer = 0.0f;
                }
            }
        }
    }

    /**
     * Saját ütközésdetektálás
     */
    protected boolean checkCollision(float testX, float testY) {
        if (dungeon == null) return true;

        int tileSize = dungeon.getTileSize();

        // Számoljuk ki a bounding box tile koordinátáit
        int leftTile = (int)(testX / tileSize);
        int rightTile = (int)((testX + width - 1) / tileSize);
        int topTile = (int)(testY / tileSize);
        int bottomTile = (int)((testY + height - 1) / tileSize);

        // Ellenőrizzük az összes tile-t ami érinti az entitást
        for (int x = leftTile; x <= rightTile; x++) {
            for (int y = topTile; y <= bottomTile; y++) {
                if (isValidTile(x, y) && isObstacle(x, y)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Ütközés utáni helyreállítási kísérlet
     */
    private boolean attemptCollisionRecovery(float moveX, float moveY, float intendedX, float intendedY) {
        // Próbáljunk meg csak X irányban mozogni
        if (!checkCollision(intendedX, y)) {
            setX(intendedX);
            return true;
        }

        // Próbáljunk meg csak Y irányban mozogni
        if (!checkCollision(x, intendedY)) {
            setY(intendedY);
            return true;
        }

        // Próbáljunk meg kisebb lépéssel
        float halfMoveX = moveX * 0.5f;
        float halfMoveY = moveY * 0.5f;

        if (!checkCollision(x + halfMoveX, y + halfMoveY)) {
            setX(x + halfMoveX);
            setY(y + halfMoveY);
            return true;
        }

        return false;
    }

    private void setRandomTarget() {
        if (dungeon == null) return;

        Random rand = new Random();
        int maxAttempts = 10;
        int attempt = 0;

        while (attempt < maxAttempts) {
            int targetTileX = rand.nextInt(dungeon.getWidthTiles());
            int targetTileY = rand.nextInt(dungeon.getHeightTiles());

            if (!isObstacle(targetTileX, targetTileY)) {
                int tileSize = dungeon.getTileSize();
                this.targetX = targetTileX * tileSize + tileSize / 2;
                this.targetY = targetTileY * tileSize + tileSize / 2;
                this.hasTarget = true;
                calculatePathToTarget(targetX, targetY);
                lastPathCalculationTime = System.currentTimeMillis();
                return;
            }
            attempt++;
        }
    }

    protected void checkAndAttackPlayer() {
        if (targetPlayer == null || targetPlayer.getHealth() <= 0) return;
        if (targetPlayer.hasSpawnProtection()) return;

        boolean collision = (
                this.x < targetPlayer.getX() + targetPlayer.getWidth() &&
                        this.x + this.width > targetPlayer.getX() &&
                        this.y < targetPlayer.getY() + targetPlayer.getHeight() &&
                        this.y + this.height > targetPlayer.getY()
        );

        if (collision && lastAttackTime >= attackCooldown) {
            targetPlayer.takeDamage(attackDamage);
            lastAttackTime = 0.0f;
        }
    }

    public void takeDamage(float damage) {
        if (!alive) {
            return;
        }

        this.health -= damage;
        if (this.health <= 0) {
            this.health = 0;
            this.alive = false;
            System.out.println("Enemy died!");
        }
    }

    public void activateCritIndicator() {
        this.showCritIndicator = true;
        this.critIndicatorTime = 0.0f;
    }

    public boolean isAlive() {
        return alive && health > 0f;
    }

    public float getHealth() {
        return health;
    }

    @Override
    public void render() {
        if (texture == null) {
            glColor3f(1.0f, 0.0f, 0.0f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + height);
            glVertex2f(x, y + height);
            glEnd();
            glColor3f(1.0f, 1.0f, 1.0f);
        } else {
            texture.bind();
            glColor3f(1.0f, 1.0f, 1.0f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(x, y);
            glTexCoord2f(1, 0); glVertex2f(x + width, y);
            glTexCoord2f(1, 1); glVertex2f(x + width, y + height);
            glTexCoord2f(0, 1); glVertex2f(x, y + height);
            glEnd();
            texture.unbind();
        }

        renderHealthBar();

        if (showCritIndicator && critIndicatorTime < CRIT_DISPLAY_DURATION && critTextRenderer != null) {
            glPushAttrib(GL_ALL_ATTRIB_BITS);
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();

            float offsetX = this.width / 2.0f;
            float offsetY = -30.0f;

            glTranslatef(this.x + offsetX, this.y + offsetY, 0);
            glScalef(0.5f, 0.5f, 1.0f);
            glScalef(1.0f, -1.0f, 1.0f);

            glColor3f(1.0f, 0.2f, 0.2f);
            critTextRenderer.render(0, 0, 1.0f);

            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            glPopAttrib();

            glColor3f(1.0f, 1.0f, 1.0f);
        }

        // Csak akkor jeleníti meg az útvonalat, ha a showPathDebug true
        if (showPathDebug) {
            renderPathDebug();
        }
    }

    /**
     * ÚTVONAL DEBUG MEGJELENÍTÉS - MINDIG A TELJES ÚTVONALAT MEGJELENÍTI
     */
    private void renderPathDebug() {
        if (currentPath != null && !currentPath.isEmpty() && dungeon != null) {
            glDisable(GL_TEXTURE_2D);

            // 1. A TELJES ÚTVONAL ZÖLD SZÍNNEL
            glBegin(GL_LINE_STRIP);
            glColor3f(0.0f, 1.0f, 0.0f); // Zöld szín a teljes útvonalnak

            int tileSize = dungeon.getTileSize();

            // MINDEN NODE-OT MEGJELENÍTÜNK, nem csak a currentPathIndex-től
            for (int i = 0; i < currentPath.size(); i++) {
                Node node = currentPath.get(i);
                float worldX = node.x * tileSize + tileSize / 2;
                float worldY = node.y * tileSize + tileSize / 2;
                glVertex2f(worldX, worldY);
            }
            glEnd();

            // 2. NODE PONTOK MEGJELENÍTÉSE
            glPointSize(6.0f);
            glBegin(GL_POINTS);

            for (int i = 0; i < currentPath.size(); i++) {
                Node node = currentPath.get(i);
                float worldX = node.x * tileSize + tileSize / 2;
                float worldY = node.y * tileSize + tileSize / 2;

                // Aktuális node sárga színnel, a többi piros
                if (i == currentPathIndex) {
                    glColor3f(1.0f, 1.0f, 0.0f); // Sárga - aktuális node
                } else {
                    glColor3f(1.0f, 0.0f, 0.0f); // Piros - többi node
                }

                glVertex2f(worldX, worldY);
            }
            glEnd();

            glEnable(GL_TEXTURE_2D);
        }
    }

    private void renderHealthBar() {
        float barWidth = width;
        float barHeight = 5.0f;
        float barYOffset = 10.0f;

        if (health > 0) {
            // Background
            glColor3f(0.5f, 0.5f, 0.5f);
            glDisable(GL_TEXTURE_2D);
            glBegin(GL_QUADS);
            glVertex2f(x, y - barYOffset);
            glVertex2f(x + barWidth, y - barYOffset);
            glVertex2f(x + barWidth, y - barYOffset + barHeight);
            glVertex2f(x, y - barYOffset + barHeight);
            glEnd();

            // Health fill
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

            glEnable(GL_TEXTURE_2D);
            glColor3f(1.0f, 1.0f, 1.0f);
        }
    }

    public void setCritIndicator() {
        this.showCritIndicator = true;
        this.critIndicatorTime = 0.0f;
    }

    public void setTargetPlayer(Player player) {
        this.targetPlayer = player;
        if (player != null) {
        	calculatePathToTarget(
                    player.getX() + player.getWidth() / 2.0f,
                    player.getY() + player.getHeight() / 2.0f
            );
            this.hasTarget = true;
            this.lastPathCalculationTime = System.currentTimeMillis();
        } else {
            this.hasTarget = false;
            if (currentPath != null) {
                currentPath.clear();
            }
        }
    }

    public void cleanup() {
        if (this.texture != null) {
            this.texture.delete();
        }
        if (this.critTextRenderer != null) {
            this.critTextRenderer.cleanup();
        }
    }

    // --- PATHFINDING HELPER METHODS ---

    private float calculateHeuristic(int startX, int startY, int targetX, int targetY) {
        return (float)Math.sqrt(Math.pow(startX - targetX, 2) + Math.pow(startY - targetY, 2));
    }

    private List<Node> retracePath(Node endNode) {
        if (endNode == null) return new ArrayList<>();

        List<Node> path = new ArrayList<>();
        Node currentNode = endNode;

        while (currentNode != null) {
            path.add(currentNode);
            currentNode = currentNode.parent;
        }

        Collections.reverse(path);
        return path;
    }

    private boolean isValidTile(int x, int y) {
        return dungeon != null && x >= 0 && x < dungeon.getWidthTiles() && y >= 0 && y < dungeon.getHeightTiles();
    }

    private boolean isObstacle(int tileX, int tileY) {
        if (dungeon == null) return true;
        Tile tile = dungeon.getTile(tileX, tileY);
        return tile == null || tile.isSolid();
    }

    /**
     * Node class for A* pathfinding
     */
    private static class Node implements Comparable<Node> {
        int x, y;
        float gCost;
        float hCost;
        float fCost;
        Node parent;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
            this.gCost = Float.MAX_VALUE;
        }

        void calculateFCost() {
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Float.compare(this.fCost, other.fCost);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    /**
     * Klónozás metódus - csak a legszükségesebb adatok másolása
     */
    public Enemy clone() {
        Enemy clone = new Enemy(
                this.x, this.y,
                this.width, this.height,
                this.texture,
                this.maxHealth,
                this.textRenderer,
                this.dungeon,
                this.collisionManager,
                this.targetPlayer
        );

        // CSAK A LÉNYEGES ÁLLAPOTOK
        clone.health = this.health;
        clone.alive = this.alive;
        clone.moveSpeed = this.moveSpeed;
        clone.attackDamage = this.attackDamage;
        clone.baseMoveSpeed = this.baseMoveSpeed;
        clone.baseAttackDamage = this.baseAttackDamage;
        clone.pathDeviationChance = this.pathDeviationChance;
        clone.pathDeviationRadius = this.pathDeviationRadius;

        return clone;
    }

    public void setMoveSpeed(float speed) {
        this.moveSpeed = speed;
    }

    public void setAttackDamage(float damage) {
        this.attackDamage = damage;
    }

    public void setAttackCooldown(float cooldown) {
        this.attackCooldown = cooldown;
    }

    public float getAttackDamage(){
        return attackDamage;
    }

    public float getMoveSpeed() {
        return this.moveSpeed;
    }

    public void setHealth(float health) {
        this.health = Math.max(0f, health);
        this.alive = this.health > 0f;
    }

    // ÚJ: Setter metódus a maximális HP beállításához
    public void setInitialHealth(float health) {
        this.maxHealth = health;
    }

    public void setDamage(float damage) {
        this.attackDamage = damage;
    }

    public void applyDifficultyMultipliers(float damageMultiplier, float speedMultiplier) {
        this.attackDamage = baseAttackDamage * damageMultiplier;
        this.moveSpeed = baseMoveSpeed * speedMultiplier;
    }

    public void setPathDeviation(float chance, float radius) {
        this.pathDeviationChance = Math.max(0.0f, chance);
        this.pathDeviationRadius = Math.max(0.0f, radius);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
        if (!alive) {
            this.health = 0f;
        }
    }

    public void setNetworkControlled(boolean controlled) {
        this.networkControlled = controlled;
        if (controlled) {
            // ✨ Ha szerver irányítja, tiltsuk le a lokális AI-t
            this.currentPath.clear();
            this.hasTarget = false;
        }
    }

    public boolean isNetworkControlled() {
        return networkControlled;
    }

    private void updateNetworkMovement(float deltaTime) {
        // SIMA INTERPOLÁCIÓ a célpozíció felé
        float dx = targetX - this.x;
        float dy = targetY - this.y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 2.0f) {
            if (distance > 0) {
                dx /= distance;
                dy /= distance;
            }

            float moveDistance = INTERPOLATION_SPEED * distance * deltaTime;
            this.x += dx * Math.min(moveDistance, distance);
            this.y += dy * Math.min(moveDistance, distance);
        } else {
            this.x = targetX;
            this.y = targetY;
        }

        // ✨ NINCS LOKÁLIS PATHFINDING
        // ✨ NINCS LOKÁLIS COLLISION DETECTION
    }

    public void setTargetPosition(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    public boolean isCritIndicatorActive() {
        return showCritIndicator && critIndicatorTime < CRIT_DISPLAY_DURATION;
    }

    public void syncCritIndicator(boolean active) {
        if (active) {
            setCritIndicator();
        } else {
            this.showCritIndicator = false;
            this.critIndicatorTime = 0.0f;
        }
    }

    public Player getTargetPlayer() {
        return this.targetPlayer;
    }
}