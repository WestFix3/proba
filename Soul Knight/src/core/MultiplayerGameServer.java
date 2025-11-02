package core;

import physics.CollisionManager;
import  world. DungeonGenerator;

import java.io.*;
import java.net.*;
import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiplayerGameServer {
    private static final int TCP_PORT = 5555;
    private static final int UDP_PORT = 5556;
    private static final int MAX_PLAYERS = 2;

    private ServerSocket tcpServerSocket;
    private DatagramSocket udpSocket;
    private ExecutorService threadPool;

    private Map<Integer, PlayerSession> connectedPlayers;
    private AtomicInteger playerIdCounter;

    // Játék állapot
    private GameState gameState;
    private boolean gameRunning = false;
    private long sharedDungeonSeed = -1;
    private float sharedSpawnX = 100f;
    private float sharedSpawnY = 100f;
    private AtomicInteger projectileIdCounter = new AtomicInteger(1);
    private Map<Integer, ProjectileState> activeProjectiles = new ConcurrentHashMap<>();
    private Set<Integer> activeEffectIds = ConcurrentHashMap.newKeySet();
    private Set<Integer> consumedEffectIds = ConcurrentHashMap.newKeySet();

    public MultiplayerGameServer() {
        this.connectedPlayers = new ConcurrentHashMap<>();
        this.playerIdCounter = new AtomicInteger(1);
        this.threadPool = Executors.newCachedThreadPool();
        this.gameState = new GameState();

        // ✨ JAVÍTÁS: Csak akkor inicializáljuk, ha a GameState-ben van ilyen metódus
        if (gameState != null) {
            try {
                gameState.initializeCollisionManager();
                gameState.setServer(this);
                //System.out.println("✅ CollisionManager successfully initialized");
            } catch (Exception e) {
                System.err.println("❌ Error initializing CollisionManager: " + e.getMessage());
            }
        }
    }

    public void startServer() {
        try {
            // TCP szerver a kapcsolatok kezelésére
            tcpServerSocket = new ServerSocket(TCP_PORT);
            //System.out.println("🎮 TCP Server started on port " + TCP_PORT);

            // UDP socket a gyors játékadatokra
            udpSocket = new DatagramSocket(UDP_PORT);
            //System.out.println("🎮 UDP Server started on port " + UDP_PORT);

            // TCP kapcsolatokat fogadó szál
            threadPool.execute(this::acceptTCPConnections);

            // UDP csomagokat fogadó szál
            threadPool.execute(this::handleUDPPackets);

            // Játék loop
            threadPool.execute(this::gameLoop);

            //System.out.println("✅ Multiplayer Game Server is running!");

        } catch (IOException e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
        }
    }

    private void acceptTCPConnections() {
        while (!tcpServerSocket.isClosed()) {
            try {
                Socket clientSocket = tcpServerSocket.accept();

                if (connectedPlayers.size() >= MAX_PLAYERS) {
                    sendTCPResponse(clientSocket, "SERVER_FULL");
                    clientSocket.close();
                    continue;
                }

                int playerId = playerIdCounter.getAndIncrement();
                PlayerSession session = new PlayerSession(playerId, clientSocket);
                connectedPlayers.put(playerId, session);

                //System.out.println("🔗 Player " + playerId + " connected from " +
                //        clientSocket.getInetAddress().getHostAddress());

                // ✨ AZONNAL KÜLDJÜK A PLAYER_ID-T
                sendTCPResponse(clientSocket, "PLAYER_ID:" + playerId);
                //System.out.println("📤 Sent PLAYER_ID:" + playerId + " to new player");

                // Kezeld a játékos sessiont külön szálon
                threadPool.execute(() -> handlePlayerSession(session));

            } catch (IOException e) {
                if (!tcpServerSocket.isClosed()) {
                    System.err.println("❌ Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    private void handleUDPPackets() {
        byte[] buffer = new byte[1024];

        while (!udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                // Feldolgozzuk az UDP csomagot külön szálon
                threadPool.execute(() -> processUDPPacket(packet));

            } catch (IOException e) {
                if (!udpSocket.isClosed()) {
                    System.err.println("❌ UDP receive error: " + e.getMessage());
                }
            }
        }
    }

    private void handlePlayerSession(PlayerSession session) {
        try {
            // ✨ JAVÍTÁS: Socket timeout beállítása (30 másodperc)
            session.getClientSocket().setSoTimeout(30000);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(session.getClientSocket().getInputStream()));
            PrintWriter out = new PrintWriter(session.getClientSocket().getOutputStream(), true);

            String message;
            while (!session.getClientSocket().isClosed()) {
                try {
                    message = in.readLine();
                    if (message == null) {
                        // Kapcsolat bezárása a kliens oldalról
                        //System.out.println("🔌 Player " + session.getPlayerId() + " closed connection");
                        break;
                    }

                    //System.out.println("📨 TCP from Player " + session.getPlayerId() + ": " + message);
                    processTCPMessage(session, message);

                } catch (SocketTimeoutException e) {
                    // ✨ JAVÍTÁS: Timeout kezelése - küldjünk pinget
                    sendTCPResponse(session.getClientSocket(), "PING");
                    //System.out.println("⏰ Ping sent to player " + session.getPlayerId());

                    // Ellenőrizzük, hogy a kliens még él-e
                    if (!isClientAlive(session)) {
                        //System.out.println("💀 Player " + session.getPlayerId() + " appears dead, disconnecting");
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Player session error for player " + session.getPlayerId() + ": " + e.getMessage());
        } finally {
            disconnectPlayer(session.getPlayerId());
        }
    }

    // ✨ ÚJ METÓDUS: Add hozzá a PlayerSession osztály után
    private boolean isClientAlive(PlayerSession session) {
        try {
            // Próbáljunk meg írni a socketre
            session.getClientSocket().getOutputStream().write(0);
            session.getClientSocket().getOutputStream().flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void processTCPMessage(PlayerSession session, String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "ENEMY_UPDATE":
                //System.out.println("👹 [SERVER] Enemy update from player " + session.getPlayerId() + ": " + data);
                handleEnemyUpdateFromClient(data);
                break;
            case "ENEMY_DAMAGE":
                handleEnemyDamage(data);
                break;
            case "PLAYER_DAMAGE":
                handlePlayerDamage(data);
                break;
            case "EFFECT_SPAWN":
                handleEffectSpawn(session, data);
                break;
            case "EFFECT_PICKUP":
                handleEffectPickup(session, data);
                break;
            case "PLAYER_COLLISION":
                handlePlayerCollision(data);
                break;
            case "PLAYER_ENEMY_COLLISION":
                handlePlayerEnemyCollision(data);
                break;
            case "PROJECTILE_CREATE":
                handleProjectileCreate(session, data);
                break;
            case "JOIN_GAME":
                handlePlayerJoin(session, data);
                break;
            case "PLAYER_READY":
                handlePlayerReady(session);
                break;
            case "CHAT_MESSAGE":
                broadcastTCPMessage("CHAT:" + session.getPlayerId() + ":" + data);
                break;
            case "BROADCAST":
                // ✨ HOST ÜZENETÉNEK TOVÁBBÍTÁSA MINDENKINEK
                if (session.isHost()) {
                    broadcastTCPMessage(data);
                    if (data.startsWith("DUNGEON_SEED:")) {
                        activeEffectIds.clear();
                        consumedEffectIds.clear();
                    }
                }
                break;
            case "DISCONNECT":
                disconnectPlayer(session.getPlayerId());
                break;
            case "UDP_REGISTER":
                handleUDPRegistration(session, data);
                break;
            case "PLAYER_STATE":
                handlePlayerState(session, data);
                break;
            case "GATE_TRIGGER":
                handleGateTrigger(session, data);
                break;
            case "TILE_UPDATE":
                handleTileUpdate(session, data);
                break;
            default:
                System.out.println("⚠️ Unknown TCP command from player " + session.getPlayerId() + ": " + command);
        }
    }

    private String createPositionMessage(int playerId, float x, float y) {  // ✨ Eltávolítottuk az isAlive paramétert
        return playerId + ":PLAYER_POSITION:" + x + "," + y;
    }

    private void handlePlayerState(PlayerSession session, String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length >= 3) {  // ✨ Csak 3 rész: id,x,y
                int playerId = Integer.parseInt(parts[0]);
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);

                // ✨ EGYSZERŰ FORMÁTUM - csak pozíció
                String positionMessage = createPositionMessage(playerId, x, y);
                broadcastUDPToOthers(playerId, positionMessage);

            } else {
                System.err.println("❌ Invalid PLAYER_STATE format: " + data);
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling PLAYER_STATE: " + e.getMessage());
        }
    }

    // Add a MultiplayerGameServer osztályhoz:

    private void handleEnemyDamage(String data) {
        try {
            String[] parts = data.split(":");
            int enemyId = Integer.parseInt(parts[0]);
            float damage = Float.parseFloat(parts[1]);
            float newHealth = Float.parseFloat(parts[2]);
            boolean isAlive = Boolean.parseBoolean(parts[3]);

            // Update enemy state
            for (EnemyState enemy : gameState.getEnemyStates()) {
                if (enemy.getEnemyId() == enemyId) {
                    enemy.setHealth(newHealth);
                    enemy.setAlive(isAlive);
                    break;
                }
            }

            // Broadcast to all clients
            broadcastUDPToAll("ENEMY_DAMAGE:" + data);

        } catch (Exception e) {
            System.err.println("❌ Error handling enemy damage: " + e.getMessage());
        }
    }

    // MultiplayerGameServer.java - handlePlayerDamage metódus
    private void handlePlayerDamage(String data) {
        try {
            String[] parts = data.split(":");

            int playerId;
            String playerName;
            float damage;
            float newHealth;
            boolean isAlive;

            if (parts.length >= 5) {
                playerId = Integer.parseInt(parts[0]);
                playerName = parts[1];
                damage = Float.parseFloat(parts[2]);
                newHealth = Float.parseFloat(parts[3]);
                isAlive = Boolean.parseBoolean(parts[4]);

                System.out.println("🩸 SERVER: Damage from player " + playerId +
                        " - Health: " + newHealth + ", Alive: " + isAlive);

                // ✨ FONTOS: MINDENKINEK KÜLDJÜK, BELEÉRVE A KÜLDŐT IS!
                // NE szűrjük ki a saját üzeneteket!
                String broadcastData = String.format(Locale.US, "%d:%s:%.2f:%.2f:%b",
                        playerId,
                        playerName,
                        damage,
                        newHealth,
                        isAlive);

                System.out.println("📤 SERVER: Broadcasting damage to ALL players: " + broadcastData);
                broadcastUDPToAll("PLAYER_DAMAGE:" + broadcastData);

            } else {
                System.err.println("❌ Invalid PLAYER_DAMAGE data: " + data);
                return;
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling player damage: " + e.getMessage());
        }
    }

    private void handleEffectSpawn(PlayerSession session, String data) {
        try {
            if (!session.isHost()) {
                return;
            }

            String[] parts = data.split(":");
            if (parts.length < 4) {
                return;
            }

            int effectId = Integer.parseInt(parts[0]);
            if (consumedEffectIds.contains(effectId) || !activeEffectIds.add(effectId)) {
                return;
            }

            broadcastTCPMessage("EFFECT_SPAWN:" + data);
        } catch (Exception e) {
            System.err.println("❌ Error handling effect spawn: " + e.getMessage());
        }
    }

    private void handleEffectPickup(PlayerSession session, String data) {
        try {
            if (!session.isHost()) {
                return;
            }

            String[] parts = data.split(":");
            if (parts.length < 3) {
                return;
            }

            int effectId = Integer.parseInt(parts[0]);
            if (consumedEffectIds.contains(effectId)) {
                return;
            }

            consumedEffectIds.add(effectId);
            activeEffectIds.remove(effectId);
            broadcastTCPMessage("EFFECT_PICKUP:" + data);
        } catch (Exception e) {
            System.err.println("❌ Error handling effect pickup: " + e.getMessage());
        }
    }

    // Segédmetódus: PlayerState keresése név alapján
    private PlayerState findPlayerStateByName(String playerName) {
        for (PlayerState playerState : gameState.getPlayerStates().values()) {
            if (playerName.equals(playerState.getPlayerName())) {
                return playerState;
            }
        }
        return null;
    }

    private void handlePlayerCollision(String data) {
        // Player-player collision - csak logoljuk
        //System.out.println("👥 Player-Player collision: " + data);
        broadcastUDPToAll("PLAYER_COLLISION:" + data);
    }

    private void handlePlayerEnemyCollision(String data) {
        // Player-enemy collision - logolás
        //System.out.println("👹 Player-Enemy collision: " + data);
        broadcastUDPToAll("PLAYER_ENEMY_COLLISION:" + data);
    }

    private void handleProjectileCreate(PlayerSession session, String data) {
        try {
            //System.out.println("🎯 [SERVER] Projectile create received from player " + session.getPlayerId());
            //System.out.println("#0 PROJECTILE DATA: " + data);

            // ✨ MEGOLDÁS: Használj US locale-t a parse-oláshoz (pontos formátum)
            String[] parts = data.split(":");

            // Scannerrel parse-olás, ami automatikusan kezeli a locale-t
            java.util.Scanner scanner = new java.util.Scanner(parts[0]);
            scanner.useLocale(java.util.Locale.US);
            float startX = scanner.nextFloat();
            scanner.close();

            scanner = new java.util.Scanner(parts[1]);
            scanner.useLocale(java.util.Locale.US);
            float startY = scanner.nextFloat();
            scanner.close();

            scanner = new java.util.Scanner(parts[2]);
            scanner.useLocale(java.util.Locale.US);
            float velocityX = scanner.nextFloat();
            scanner.close();

            scanner = new java.util.Scanner(parts[3]);
            scanner.useLocale(java.util.Locale.US);
            float velocityY = scanner.nextFloat();
            scanner.close();

            scanner = new java.util.Scanner(parts[4]);
            scanner.useLocale(java.util.Locale.US);
            float damage = scanner.nextFloat();
            scanner.close();

//            System.out.println("#1 Player ID: " + session.getPlayerId() +
//                    " | startX: " + startX + " | startY: " + startY);
//            System.out.println("#2 velocityX: " + velocityX + " | velocityY: " + velocityY);
//            System.out.println("#3 DAMAGE: " + damage);

            int playerId = session.getPlayerId();

            // Create projectile on server
            createProjectile(playerId, startX, startY, velocityX, velocityY, damage);

//            System.out.println("🎯 Projectile created by player " + playerId +
//                    " at (" + startX + "," + startY + ")");

        } catch (Exception e) {
            System.err.println("❌ Error handling projectile create: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleUDPRegistration(PlayerSession session, String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length >= 2) {
                int udpPort = Integer.parseInt(parts[1]);
                // ✨ HASZNÁLD A PACKETBŐL SZÁRMAZÓ IP-CÍMET
                // Ne: session.setUdpAddress(session.getClientSocket().getInetAddress());
                session.setUdpPort(udpPort);
                System.out.println("📡 Player " + session.getPlayerId() + " UDP registered on port " + udpPort);
            }
        } catch (Exception e) {
            System.err.println("❌ UDP registration error: " + e.getMessage());
        }
    }

    private void processUDPPacket(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");

            // ✨ JAVÍTÁS: Ellenőrizzük az üzenet érvényességét
            if (!isValidPositionMessage(message)) {
                System.err.println("❌ Érvénytelen UDP üzenet: " + message);
                return;
            }

            String[] parts = message.split(":", 3); // ✨ CSAK 3 részre!

            if (parts.length < 3) {
                System.err.println("❌ Hiányos UDP üzenet: " + message);
                return;
            }

            int playerId = Integer.parseInt(parts[0]);
            String command = parts[1];
            String data = parts[2];

            PlayerSession session = connectedPlayers.get(playerId);
            if (session != null) {
                // ✨ MINDIG frissítsd az UDP címet
                session.setUdpAddress(packet.getAddress());
                session.setUdpPort(packet.getPort());

                // ✨ CSAK PLAYER_POSITION esetén dolgozzuk fel külön
                if ("PLAYER_POSITION".equals(command)) {
                    handlePlayerPosition(playerId, data);  // ✨ Csak 2 paraméter
                } else {
                    // Egyéb command-ok továbbítása
                    broadcastUDPToOthers(playerId, message);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ UDP packet processing error: " + e.getMessage());
        }
    }

    private boolean isValidPositionMessage(String message) {
        // ✨ JAVÍTÁS: engedélyezzük a VESSZŐT is a számokban
        return message.matches("^\\d+:PLAYER_POSITION:-?\\d+[,.]?\\d*,-?\\d+[,.]?\\d*$");
    }

    private void handlePlayerJoin(PlayerSession session, String playerData) {
        String[] playerInfo = playerData.split(":");
        if (playerInfo.length >= 3) {
            String playerName = playerInfo[0];
            String playerAbility = playerInfo[1];
            boolean showPathDebug = Boolean.parseBoolean(playerInfo[2]);

            session.setPlayerName(playerName);        // ✨ A VALÓDI NEVET
            session.setPlayerAbility(playerAbility);
            session.setShowPathDebug(showPathDebug);

            // ✨ AZ ELSŐ CSATLAKOZÓ LEGYEN A HOST
            if (connectedPlayers.size() == 1) {
                session.setHost(true);
                System.out.println("👑 Player " + session.getPlayerId() + " is the HOST");
            }

            System.out.println("🎯 Player " + session.getPlayerId() + " joined: " +
                    playerName + " (" + playerAbility + ")");  // ✨ DEBUG: valós név

            if (sharedDungeonSeed != -1) {
                updateSharedSpawnFromSeed();
            }

            // Játékos állapot létrehozása
            if (sharedDungeonSeed != -1) {
                updateSharedSpawnFromSeed();
            }

            PlayerState playerState = new PlayerState(
                    session.getPlayerId(),
                    playerName,  // ✨ A VALÓDI NEVET
                    sharedSpawnX, sharedSpawnY,
                    100.0f, 100.0f
            );
            playerState.setAbility(playerAbility);
            gameState.addPlayerState(session.getPlayerId(), playerState);

            // ✨ FONTOS: KÜLDJÜK EL A VALÓDI NEVET!
            broadcastTCPMessage(String.format(Locale.US,
                    "PLAYER_JOINED:%d:%s:%s:%.2f:%.2f",
                    session.getPlayerId(),
                    playerName,
                    playerAbility,
                    sharedSpawnX,
                    sharedSpawnY));

            //System.out.println("📤 Sent PLAYER_JOINED: " + session.getPlayerId() + ":" + playerName + ":" + playerAbility);

            // ✨ HA MINDENKI CSATLAKOZOTT, KÜLDJÜK A DUNGEON SEED-ET
            if (connectedPlayers.size() >= 1) {
                broadcastTCPMessage("ALL_PLAYERS_READY");
                //System.out.println("🚀 All players ready - starting game setup");
                broadcastDungeonData();
            }
        } else {
            System.err.println("❌ Invalid JOIN_GAME data: " + playerData);
        }
    }

    private void handlePlayerReady(PlayerSession session) {
        session.setReady(true);
        broadcastTCPMessage("PLAYER_READY:" + session.getPlayerId());
        //System.out.println("✅ Player " + session.getPlayerId() + " is ready");

        // Ellenőrizzük, hogy mindenki kész van-e
        checkAllPlayersReady();
    }

    private void checkAllPlayersReady() {
        boolean allReady = connectedPlayers.values().stream()
                .allMatch(PlayerSession::isReady);

        if (allReady && connectedPlayers.size() >= 1) {
            startGame();
        }
    }

    private void startGame() {
        //System.out.println("🚀 Starting multiplayer game with " + connectedPlayers.size() + " players!");

        // ✨ GENERÁLJUK A SHARED DUNGEON-T
        generateSharedDungeon();

        gameRunning = true;

        // ✨ JAVÍTOTT: KÜLDJÜK EL A DUNGEON ADATOKAT
        broadcastDungeonData();

        // ✨ ÉRTESÍTJÜK A JÁTÉKOSOKAT
        broadcastTCPMessage("GAME_STARTING");

        System.out.println("✅ Multiplayer game started!");
    }

    private void generateSharedDungeon() {
        //System.out.println("🏰 Generating shared dungeon...");

        // Mock dungeon generálás
        gameState.setDungeonGenerated(true);
        gameState.setBossDefeated(false);

        // ✨ JAVÍTÁS: NE hozzunk létre saját ellenségeket
        // A kliensek generálják a dungeon-t a seed alapján
        gameState.getEnemyStates().clear(); // Ürítsük ki a régi ellenségeket
        activeEffectIds.clear();
        consumedEffectIds.clear();

        System.out.println("✅ Shared dungeon ready for sync - clients will generate enemies");
    }

    private void handleEnemyUpdateFromClient(String data) {
        try {
            //System.out.println("👹 [SERVER] Processing enemy update from HOST: " + data);

            String[] parts = data.split(",");
            if (parts.length >= 7) {
                int enemyId = Integer.parseInt(parts[0]);

                // ✨ CSAK TOVÁBBÍTJUK A HOST UPDATE-ÉT MINDEN KLIENSNEK
                // NEM tároljuk el a szerveren, mert a kliensek már rendelkeznek az enemy-kkel
                String enemyMessage = "ENEMY_UPDATE:" + data;
                broadcastUDPToAll(enemyMessage);

                //System.out.println("✅ [SERVER] Enemy " + enemyId + " update broadcasted to all clients");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling enemy update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private EnemyState findOrCreateEnemy(int enemyId) {
        // ✨ JAVÍTÁS: CSAK LOGOLJUK, DE NE HOZZUNK LÉTRE ENEMY-T
        //System.out.println("🔍 [SERVER] Received update for enemy ID: " + enemyId);

        // ✨ CSAK NULL-T RETURNÖLJÜK - A SZERVER NEM KEZEL ENEMY-KET
        return null;
    }

    private void gameLoop() {
        final long TICK_RATE = 60;
        final long TICK_TIME_NS = 1000000000 / TICK_RATE;

        long lastTime = System.nanoTime();
        long timer = System.currentTimeMillis();
        int ticks = 0;
        int collisionChecks = 0;

        while (true) {
            long now = System.nanoTime();
            long deltaTime = now - lastTime;

            if (deltaTime >= TICK_TIME_NS) {
                lastTime = now;

                if (gameRunning) {
                    updateGameState(deltaTime / 1000000000.0f);
                    ticks++;
                    collisionChecks++;
                }

                // Másodpercenkénti statisztika + collision report
                if (System.currentTimeMillis() - timer > 1000) {
                    timer += 1000;
//                    System.out.println("🎮 Game TPS: " + ticks +
//                            ", Collision Checks: " + collisionChecks +
//                            ", Players: " + connectedPlayers.size());

                    // Collision statisztikák
                    if (collisionChecks > 0) {
                        printServerCollisionStats();
                    }

                    ticks = 0;
                    collisionChecks = 0;
                }
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void updateGameState(float deltaTime) {
        if (!gameRunning) return;

        // Frissítsd a játék állapotát
        updatePlayerPositions(deltaTime);
        updateProjectiles(deltaTime);
        updateEnemies(deltaTime);
        checkCollisions();
    }

    private void updatePlayerPositions(float deltaTime) {
        for (PlayerSession session : connectedPlayers.values()) {
            if (session.hasPendingInput()) {
                String input = session.getNextInput();
                if (input != null) {
                    processPlayerMovement(session.getPlayerId(), input);
                }
            }
        }
    }

    private void processPlayerMovement(int playerId, String input) {
        try {
            if (input.startsWith("PLAYER_POSITION:")) {
                String[] coords = input.substring("PLAYER_POSITION:".length()).split(",");
                if (coords.length >= 2) {
                    float x = Float.parseFloat(coords[0]);
                    float y = Float.parseFloat(coords[1]);

                    gameState.updatePlayerPosition(playerId, x, y);

                    // ✨ EGYSZERŰ FORMÁTUM - csak pozíció
                    String updateMessage = createPositionMessage(playerId, x, y);
                    broadcastUDPToAll(updateMessage);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("❌ Invalid player movement data: " + input);
        }
    }

    private void updateProjectiles(float deltaTime) {
        List<Integer> projectilesToRemove = new ArrayList<>();

        for (ProjectileState projectile : activeProjectiles.values()) {
            if (!projectile.isActive()) {
                projectilesToRemove.add(projectile.getProjectileId());
                continue;
            }

            // Update position
            float newX = projectile.getX() + projectile.getVelocityX() * deltaTime;
            float newY = projectile.getY() + projectile.getVelocityY() * deltaTime;
            projectile.setX(newX);
            projectile.setY(newY);

            // Check bounds
            if (Math.abs(projectile.getX()) > 5000 || Math.abs(projectile.getY()) > 5000) {
                projectile.setActive(false);
                projectilesToRemove.add(projectile.getProjectileId());
                //System.out.println("📭 Projectile " + projectile.getProjectileId() + " out of bounds");
            }

            // Check collisions
            checkProjectileCollisions(projectile);
        }

        // Remove inactive projectiles
        for (int projectileId : projectilesToRemove) {
            activeProjectiles.remove(projectileId);
            // Notify clients about removal
            broadcastUDPToAll("PROJECTILE_REMOVED:" + projectileId);
        }
    }

    private void checkProjectileCollisions(ProjectileState projectile) {
        if (!projectile.isActive()) return;

        // Check collision with enemies
        for (EnemyState enemy : gameState.getEnemyStates()) {
            if (!enemy.isAlive()) continue;

            // Simple circle collision check
            float dx = projectile.getX() - enemy.getX();
            float dy = projectile.getY() - enemy.getY();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance < 50) { // Collision radius
//                System.out.println("💥 COLLISION! Projectile " + projectile.getProjectileId() +
//                        " hit enemy " + enemy.getEnemyId());

                // Apply damage
                enemy.setHealth(enemy.getHealth() - projectile.getDamage());
                projectile.setActive(false);

                if (enemy.getHealth() <= 0) {
                    enemy.setAlive(false);
                    //System.out.println("💀 Enemy " + enemy.getEnemyId() + " defeated");
                }

                // Broadcast collision to all clients
                broadcastUDPToAll("PROJECTILE_HIT:" +
                        projectile.getProjectileId() + ":" +
                        enemy.getEnemyId() + ":" +
                        projectile.getDamage() + ":" +
                        enemy.getHealth() + ":" +
                        enemy.isAlive());

                break;
            }
        }
    }

    public void createProjectile(int playerId, float startX, float startY,
                                 float velocityX, float velocityY, float damage) {
        int projectileId = projectileIdCounter.getAndIncrement();
        ProjectileState projectile = new ProjectileState(
                projectileId,
                startX, startY,
                velocityX, velocityY,
                playerId,
                damage
        );

        activeProjectiles.put(projectileId, projectile);

        // Broadcast to all clients
        String projectileMessage = "PROJECTILE_CREATED:" + projectile.serialize();
        //System.out.println("🎯 [SERVER] Sending projectile: " + projectileMessage);
        broadcastUDPToAll(projectileMessage);
    }

    private void updateEnemies(float deltaTime) {
        for (EnemyState enemy : gameState.getEnemyStates()) {
            if (!enemy.isAlive()) continue;

            // CSAK A SZERVER számolja az AI-t
            PlayerState target = findClosestPlayer(enemy.getX(), enemy.getY());
            if (target != null) {
                calculateEnemyMovement(enemy, target, deltaTime);
            }

            // Minden frissítés küldése a klienseknek
            broadcastEnemyState(enemy);
        }
    }

    private void calculateEnemyMovement(EnemyState enemy, PlayerState target, float deltaTime) {
        float enemySpeed = 50.0f;

        // Irányvektor a cél felé
        float dx = target.getX() - enemy.getX();
        float dy = target.getY() - enemy.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > 0) {
            dx /= distance;
            dy /= distance;

            // Új pozíció számítása
            float newX = enemy.getX() + dx * enemySpeed * deltaTime;
            float newY = enemy.getY() + dy * enemySpeed * deltaTime;

            enemy.setX(newX);
            enemy.setY(newY);
        }
    }

    private void broadcastEnemyState(EnemyState enemy) {
        String enemyData = String.format(Locale.US, "%d,%.2f,%.2f,%.2f,%s,%.2f",
                enemy.getEnemyId(),
                enemy.getX(),
                enemy.getY(),
                enemy.getHealth(),
                enemy.isAlive(),
                enemy.getDamage());

        String enemyMessage = "ENEMY_UPDATE:" + enemyData;
        broadcastUDPToAll(enemyMessage);
    }

    private PlayerState findClosestPlayer(float enemyX, float enemyY) {
        PlayerState closest = null;
        float minDistance = Float.MAX_VALUE;

        for (PlayerState player : gameState.getPlayerStates().values()) {
            if (player.isAlive()) {
                float dx = player.getX() - enemyX;
                float dy = player.getY() - enemyY;
                float distance = dx * dx + dy * dy; // Négyzetes távolság (gyökvonás nélkül)

                if (distance < minDistance) {
                    minDistance = distance;
                    closest = player;
                }
            }
        }

        return closest;
    }

    private void checkCollisions() {
        // ✨ CSÖKKENTSD a logolást!
        int totalChecks = gameState.getProjectileStates().size() * gameState.getEnemyStates().size();

        for (ProjectileState projectile : gameState.getProjectileStates()) {
            if (!projectile.isActive()) continue;

            for (EnemyState enemy : gameState.getEnemyStates()) {
                if (!enemy.isAlive()) continue;

                // Egyszerű kör ütközésvizsgálat
                float dx = projectile.getX() - enemy.getX();
                float dy = projectile.getY() - enemy.getY();
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                // ✨ CSAK AKKOR LOGOLJ, HA TÉNYLEG VAN ÜTKÖZÉS
                if (distance < 50) {
//                    System.out.println("💥 COLLISION! Projectile " + projectile.getProjectileId() +
//                            " hit enemy " + enemy.getEnemyId());

                    // Sebzés
                    enemy.setHealth(enemy.getHealth() - projectile.getDamage());
                    projectile.setActive(false);

                    if (enemy.getHealth() <= 0) {
                        enemy.setAlive(false);
                    }

                    // Ütközés értesítés
                    broadcastUDPToAll("COLLISION:" +
                            projectile.getProjectileId() + ":" +
                            enemy.getEnemyId() + ":" +
                            projectile.getDamage() + ":" +
                            enemy.getHealth() + ":" +
                            enemy.isAlive());
                    break;
                }
            }
        }
    }

    public void broadcastCollisionEvent(String collisionMessage) {
        //System.out.println("💥 BROADCAST COLLISION: " + collisionMessage);
        broadcastUDPToAll("COLLISION:" + collisionMessage);

        // TCP backup küldése fontos eseményekhez
        if (collisionMessage.startsWith("PLAYER_ENEMY:") ||
                collisionMessage.startsWith("PROJECTILE_ENEMY:")) {
            broadcastTCPMessage("COLLISION_BACKUP:" + collisionMessage);
        }
    }

    public void cleanupCollisions() {
        if (gameState.getCollisionManager() != null) {
            gameState.getCollisionManager().cleanup();
        }
    }

    public void resetCollisions() {
        if (gameState.getCollisionManager() != null) {
            gameState.getCollisionManager().resetAllCollisions();
        }
    }

    public void runCollisionTest() {
        if (gameState.getCollisionManager() != null) {
            gameState.getCollisionManager().runCollisionTest();
        }
    }

    public List<String> checkEnvironmentAt(float x, float y, float radius) {
        if (gameState.getCollisionManager() != null) {
            return gameState.getCollisionManager().checkEnvironmentCollisions(x, y, radius);
        }
        return new ArrayList<>();
    }

    public Map<String, Object> getCollisionReport() {
        if (gameState.getCollisionManager() != null) {
            return gameState.getCollisionManager().getCollisionReport();
        }
        return Collections.singletonMap("error", "CollisionManager not available");
    }

    public void printServerCollisionStats() {
        if (gameState.getCollisionManager() != null) {
            gameState.getCollisionManager().printStats();
        } else {
            System.out.println("❌ CollisionManager not initialized");
        }
    }

    public CollisionManager getCollisionManager() {
        return gameState.getCollisionManager();
    }

    private void processPlayerInput(int playerId, String command, String data) {
        if (!gameRunning) return;

        PlayerSession session = connectedPlayers.get(playerId);
        if (session != null) {
            session.setLastInput(command + ":" + data);
            session.addInput(command + ":" + data);

            // ✨ CSAK PLAYER_POSITION-t kezeljük külön, minden mást továbbítunk
            if ("PLAYER_POSITION".equals(command)) {
                handlePlayerPosition(playerId, data);  // ✨ Csak 2 paraméter
            } else {
                // Egyéb command-ok (PLAYER_INPUT stb.) továbbítása eredeti formátumban
                broadcastUDPToOthers(playerId, playerId + ":" + command + ":" + data);
            }
        }
    }

    private void handlePlayerPosition(int playerId, String positionData) {
        try {
            String[] coords = positionData.split(",");
            if (coords.length < 2) {
                System.err.println("❌ Hiányos koordináta adat: " + positionData);
                return;
            }

            // ✨ JAVÍTÁS: explicit angol locale a parse-oláshoz
            float x = Float.parseFloat(coords[0]);
            float y = Float.parseFloat(coords[1]);

            // ✨ DEBUG: nézzük meg, mit kapunk
            //System.out.println("🔢 SZERVER PARSOLÁS: '" + coords[0] + "' -> " + x + ", '" + coords[1] + "' -> " + y);

            // Frissítsd a játék állapotot
            PlayerState playerState = gameState.getPlayerStates().get(playerId);
            if (playerState != null) {
                playerState.setX(x);
                playerState.setY(y);
            }

            // ✨ JAVÍTÁS: itt is használj Locale.US-t!
            String updateMessage = String.format(Locale.US, "%d:PLAYER_POSITION:%.4f,%.4f", playerId, x, y);
            broadcastUDPToOthers(playerId, updateMessage);

            //System.out.println("📍 Player " + playerId + " position: " + x + ", " + y);

        } catch (NumberFormatException e) {
            System.err.println("❌ Invalid position data from player " + playerId + ": " + positionData);
            System.err.println("❌ Parse error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error in handlePlayerPosition: " + e.getMessage());
        }
    }

    private void broadcastGameState() {
        if (!gameRunning) return;

        String gameStateData = "GAME_STATE:" + gameState.serializeGameState();
        broadcastUDPToAll(gameStateData);
    }

    private void broadcastDungeonData() {
        // Küldjük el a dungeon adatait minden kliensnek

        if (sharedDungeonSeed == -1) {
            sharedDungeonSeed = System.currentTimeMillis();
            System.out.println("🌱 SHARED DUNGEON SEED GENERÁLVA: " + sharedDungeonSeed);
        }

        updateSharedSpawnFromSeed();

        // ✨ JAVÍTOTT: Küldjük el a DUNGEON_SEED-et minden kliensnek
        broadcastTCPMessage("DUNGEON_SEED:" + sharedDungeonSeed);
        //System.out.println("📤 Broadcast dungeon seed to all players: " + sharedDungeonSeed);

        // ✨ KÜLDJÜK EL A JÁTÉKOS ADATOKAT IS
        for (PlayerSession session : connectedPlayers.values()) {
            broadcastTCPMessage(String.format(Locale.US,
                    "PLAYER_JOINED:%d:%s:%s:%.2f:%.2f",
                    session.getPlayerId(),
                    session.getPlayerName(),
                    session.getPlayerAbility(),
                    sharedSpawnX,
                    sharedSpawnY));
        }

        // ✨ ÉRTESÍTJÜK, HOGY A JÁTÉK KEZDŐDIK
        broadcastTCPMessage("GAME_STARTING");
        System.out.println("📤 Broadcast GAME_STARTING to all players");
    }

    private void updateSharedSpawnFromSeed() {
        if (sharedDungeonSeed == -1) {
            return;
        }

        DungeonGenerator.SpawnLocation spawn = DungeonGenerator.computeSpawnLocation(sharedDungeonSeed, 32);
        sharedSpawnX = spawn.getX();
        sharedSpawnY = spawn.getY();

        for (PlayerState state : gameState.getPlayerStates().values()) {
            state.setX(sharedSpawnX);
            state.setY(sharedSpawnY);
        }
    }

    // ✨ JAVÍTOTT: TCP válasz küldése
    private void sendTCPResponse(Socket socket, String message) {
        try {
            if (socket != null && !socket.isClosed() && socket.isConnected()) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(message);
                System.out.println("📤 TCP to " + socket.getInetAddress() + ": " + message);
            }
        } catch (IOException e) {
            System.err.println("❌ TCP send error: " + e.getMessage());
        }
    }

    private void broadcastTCPMessage(String message) {
        for (PlayerSession session : connectedPlayers.values()) {
            if (session.getClientSocket() != null && !session.getClientSocket().isClosed()) {
                sendTCPResponse(session.getClientSocket(), message);
            }
        }
    }

    private void handleGateTrigger(PlayerSession session, String gateData) {
        if (gateData == null || gateData.isEmpty()) {
            return;
        }

        broadcastTCPMessage("GATE_TRIGGER:" + gateData);
    }

    private void handleTileUpdate(PlayerSession session, String tileData) {
        if (tileData == null || tileData.isEmpty()) {
            return;
        }

        broadcastTCPMessage("TILE_UPDATE:" + tileData);
    }

    private void broadcastUDPToAll(String message) {
        //System.out.println("📤 Broadcasting UDP to " + connectedPlayers.size() + " players: " + message);
        for (PlayerSession session : connectedPlayers.values()) {
//            System.out.println("  -> To player " + session.getPlayerId() + " at " +
//                    session.getUdpAddress() + ":" + session.getUdpPort());
            sendUDPMessage(session, message);
        }
    }

    private void broadcastUDPToOthers(int excludePlayerId, String message) {
        for (PlayerSession session : connectedPlayers.values()) {
            if (session.getPlayerId() != excludePlayerId) {
                sendUDPMessage(session, message);
            }
        }
    }

    private void sendUDPMessage(PlayerSession session, String message) {
        if (session.getUdpAddress() == null) {
            // Ha nincs UDP cím, próbáljuk meg a TCP címét használni
            session.setUdpAddress(session.getClientSocket().getInetAddress());
            session.setUdpPort(UDP_PORT);
        }

        try {
            byte[] data = message.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(
                    data, data.length,
                    session.getUdpAddress(),
                    session.getUdpPort()
            );
            udpSocket.send(packet);
        } catch (IOException e) {
            System.err.println("❌ UDP send error to player " + session.getPlayerId() + ": " + e.getMessage());
        }
    }

    private void disconnectPlayer(int playerId) {
        PlayerSession session = connectedPlayers.remove(playerId);
        if (session != null) {
            try {
                if (session.getClientSocket() != null && !session.getClientSocket().isClosed()) {
                    session.getClientSocket().close();
                }
            } catch (IOException e) {
                System.err.println("❌ Error closing player socket: " + e.getMessage());
            }

            // Eltávolítjuk a játékos állapotát is
            gameState.removePlayerState(playerId);

            //System.out.println("🔌 Player " + playerId + " disconnected");
            broadcastTCPMessage("PLAYER_DISCONNECTED:" + playerId);
        }
    }

    public void stopServer() {
        try {
            gameRunning = false;

            // Értesítsük a játékosokat
            broadcastTCPMessage("SERVER_SHUTDOWN");

            if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
                tcpServerSocket.close();
            }

            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }

            threadPool.shutdown();

            // Zárjuk le az összes játékos kapcsolatát
            for (PlayerSession session : connectedPlayers.values()) {
                try {
                    if (session.getClientSocket() != null && !session.getClientSocket().isClosed()) {
                        session.getClientSocket().close();
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
            connectedPlayers.clear();

            //System.out.println("🛑 Multiplayer Game Server stopped");

        } catch (IOException e) {
            System.err.println("❌ Error stopping server: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        //System.out.println("🎮 STARTING MULTIPLAYER GAME SERVER...");
        MultiplayerGameServer server = new MultiplayerGameServer();
        server.startServer();

//        System.out.println("✅ SERVER IS NOW RUNNING on port 5555");
//        System.out.println("📍 Connect clients to: localhost:5555");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Shutting down server...");
            server.stopServer();
        }));
    }
}